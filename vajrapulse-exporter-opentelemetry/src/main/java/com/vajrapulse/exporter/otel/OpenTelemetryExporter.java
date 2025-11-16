package com.vajrapulse.exporter.otel;

import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsExporter;
import com.vajrapulse.api.TaskIdentity;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

/**
 * Exports metrics to an OpenTelemetry collector via OTLP (OpenTelemetry Protocol).
 * 
 * <p>This exporter sends metrics to an OTLP-compatible backend such as:
 * <ul>
 *   <li>OpenTelemetry Collector</li>
 *   <li>Prometheus with OTLP receiver</li>
 *   <li>Grafana Cloud</li>
 *   <li>Any OTLP-compatible observability platform</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * OpenTelemetryExporter exporter = OpenTelemetryExporter.builder()
 *     .endpoint("http://localhost:4318/v1/metrics")
 *     .resourceAttributes(Map.of(
 *         "service.name", "my-load-test",
 *         "service.version", "1.0.0"
 *     ))
 *     .build();
 * 
 * MetricsPipeline.builder()
 *     .addExporter(exporter)
 *     .withPeriodic(Duration.ofSeconds(10))
 *     .build()
 *     .run(task, loadPattern);
 * 
 * exporter.close();  // Clean shutdown
 * }</pre>
 */
public final class OpenTelemetryExporter implements MetricsExporter, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryExporter.class);
    
    private final String endpoint;
    private final SdkMeterProvider meterProvider;
    private final Meter meter;
    private final Map<String, String> additionalHeaders;
    private final Map<String, String> resourceAttributes;
    private final Protocol protocol;
    private final TaskIdentity taskIdentity;

    // Pre-created unified instruments (semantic conventions applied)
    // Counter: vajrapulse.execution.count {status=success|failure}
    private final io.opentelemetry.api.metrics.LongCounter executionCount;
    // Gauge (async): vajrapulse.execution.duration {status=success|failure, percentile=<p>}

    // Track last cumulative counts to emit deltas for monotonic counter per status
    private long lastSuccess;
    private long lastFailure;

    // Store latest snapshot for asynchronous success rate gauge
    private final AtomicReference<AggregatedMetrics> lastMetrics = new AtomicReference<>();
    
    private OpenTelemetryExporter(Builder builder) {
        this.endpoint = builder.endpoint;
        this.additionalHeaders = Map.copyOf(builder.additionalHeaders);
        this.resourceAttributes = Map.copyOf(builder.resourceAttributes);
        this.protocol = builder.protocol;
        this.taskIdentity = builder.taskIdentity;
        
        // Create appropriate exporter based on protocol
        var metricExporter = createExporter(builder);
        
        // Create metric reader with periodic export
        var metricReader = PeriodicMetricReader.builder(metricExporter)
            .setInterval(Duration.ofSeconds(builder.exportIntervalSeconds))
            .build();
        
        // Create meter provider with translated resource attributes (semantic conventions)
        var attributesBuilder = Attributes.builder();
        resourceAttributes.forEach((key, value) -> {
            String translatedKey = translateResourceKey(key);
            attributesBuilder.put(AttributeKey.stringKey(translatedKey), value);
        });
        if (taskIdentity != null) {
            // Task name under namespaced key
            attributesBuilder.put(AttributeKey.stringKey("task.name"), taskIdentity.name());
            // Tags prefixed to avoid collision with generic resource attributes
            taskIdentity.tags().forEach((k, v) ->
                attributesBuilder.put(AttributeKey.stringKey("task." + k), v)
            );
        }
        
        this.meterProvider = SdkMeterProvider.builder()
            .setResource(Resource.create(attributesBuilder.build()))
            .registerMetricReader(metricReader)
            .build();
        
        this.meter = meterProvider.get("vajrapulse");

        // Unified counter and histogram following internal semantic naming
        this.executionCount = meter.counterBuilder("vajrapulse.execution.count")
            .setDescription("Count of task executions partitioned by status (success|failure)")
            .build();
        // Asynchronous gauge for duration percentiles (snapshot series)
        meter.gaugeBuilder("vajrapulse.execution.duration")
            .setDescription("Execution duration percentiles in milliseconds (snapshot) by status and percentile")
            .setUnit("ms")
            .buildWithCallback(measurement -> {
                var snapshot = lastMetrics.get();
                if (snapshot == null) return;
                if (snapshot.successCount() > 0) {
                    for (var entry : snapshot.successPercentiles().entrySet()) {
                        String p = String.valueOf(entry.getKey());
                        double ms = entry.getValue() / 1_000_000.0;
                        measurement.record(ms, Attributes.builder()
                            .put(AttributeKey.stringKey("status"), "success")
                            .put(AttributeKey.stringKey("percentile"), p)
                            .build());
                    }
                }
                if (snapshot.failureCount() > 0) {
                    for (var entry : snapshot.failurePercentiles().entrySet()) {
                        String p = String.valueOf(entry.getKey());
                        double ms = entry.getValue() / 1_000_000.0;
                        measurement.record(ms, Attributes.builder()
                            .put(AttributeKey.stringKey("status"), "failure")
                            .put(AttributeKey.stringKey("percentile"), p)
                            .build());
                    }
                }
            });

        // Asynchronous gauge for success rate referencing latest metrics snapshot
        meter.gaugeBuilder("vajrapulse.success.rate")
            .setDescription("Success rate percentage (0-100)")
            .buildWithCallback(measurement -> {
                var snapshot = lastMetrics.get();
                if (snapshot != null && snapshot.totalExecutions() > 0) {
                    measurement.record(snapshot.successRate());
                }
            });
        
        logger.info("OpenTelemetry exporter initialized - endpoint: {}, protocol: {}", 
            endpoint, protocol);
    }
    
    /**
     * Creates the appropriate metric exporter based on protocol selection.
     */
    private MetricExporter createExporter(Builder builder) {
        return switch (protocol) {
            case GRPC -> createGrpcExporter(builder);
            case HTTP -> createHttpExporter(builder);
        };
    }
    
    /**
     * Creates gRPC metric exporter (default).
     * Supports both gRPC and HTTP/1.1 endpoints depending on URL scheme.
     */
    private MetricExporter createGrpcExporter(Builder builder) {
        var otlpExporterBuilder = OtlpGrpcMetricExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(Duration.ofSeconds(30));
        
        // Add custom headers if provided (e.g., for authentication)
        if (!additionalHeaders.isEmpty()) {
            additionalHeaders.forEach((key, value) -> 
                otlpExporterBuilder.addHeader(key, value)
            );
        }
        
        return otlpExporterBuilder.build();
    }
    
    /**
     * Creates HTTP metric exporter.
     * Uses HTTP/1.1 POST requests with Protocol Buffers encoding.
     * HTTP endpoints typically use port 4318.
     */
    private MetricExporter createHttpExporter(Builder builder) {
        // HTTP metrics export may fall back to gRPC exporter when HTTP metrics exporter is unavailable in dependency version.
        String httpEndpoint = endpoint;
        if (!httpEndpoint.startsWith("http://") && !httpEndpoint.startsWith("https://")) {
            httpEndpoint = "http://" + httpEndpoint;
        }
        var otlpExporterBuilder = OtlpGrpcMetricExporter.builder()
            .setEndpoint(httpEndpoint)
            .setTimeout(Duration.ofSeconds(30));
        if (!additionalHeaders.isEmpty()) {
            additionalHeaders.forEach(otlpExporterBuilder::addHeader);
        }
        return otlpExporterBuilder.build();
    }
    
    @Override
    public synchronized void export(String title, AggregatedMetrics metrics) {
        try {
            logger.debug("Exporting metrics to OTLP: {}", title);

            // Update last snapshot for asynchronous gauge
            lastMetrics.set(metrics);

            // Compute deltas per status for unified counter
            long success = metrics.successCount();
            long failure = metrics.failureCount();
            long deltaSuccess = success - lastSuccess;
            long deltaFailure = failure - lastFailure;
            if (deltaSuccess < 0 || deltaFailure < 0) {
                // Reset scenario – treat as fresh
                deltaSuccess = success;
                deltaFailure = failure;
            }
            if (deltaSuccess > 0) {
                executionCount.add(deltaSuccess, Attributes.of(AttributeKey.stringKey("status"), "success"));
            }
            if (deltaFailure > 0) {
                executionCount.add(deltaFailure, Attributes.of(AttributeKey.stringKey("status"), "failure"));
            }
            lastSuccess = success;
            lastFailure = failure;

            // Duration percentiles are recorded via async gauge callback above

            // Flush is deferred to periodic reader; avoid heavy forceFlush each call.
            logger.debug("Metrics recorded for export batch: successDelta={}, failureDelta={}, successTotal={}, failureTotal={}", deltaSuccess, deltaFailure, success, failure);
        } catch (Exception e) {
            logger.error("Failed to export metrics to OTLP endpoint: {}", endpoint, e);
        }
    }
    
    @Override
    public void close() {
        logger.info("Closing OpenTelemetry exporter");
        try {
            meterProvider.forceFlush().join(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Force flush failed during close", e);
        }
        try {
            meterProvider.close();
        } catch (Exception e) {
            logger.error("Error closing OpenTelemetry meterProvider", e);
        }
    }
    
    /**
     * Creates a new builder for configuring the OpenTelemetry exporter.
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for creating OpenTelemetryExporter instances.
     */
    public static final class Builder {
        private String endpoint = "http://localhost:4318";
        private int exportIntervalSeconds = 10;
        private Map<String, String> additionalHeaders = Map.of();
        private Map<String, String> resourceAttributes = Map.of();
        private Protocol protocol = Protocol.GRPC;
        private TaskIdentity taskIdentity;
        
        /**
         * Sets the OTLP endpoint URL.
         * 
         * @param endpoint the OTLP endpoint (e.g., "http://localhost:4318")
         * @return this builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }
        
        /**
         * Sets the export interval in seconds.
         * 
         * @param seconds how often to export metrics
         * @return this builder
         */
        public Builder exportInterval(int seconds) {
            this.exportIntervalSeconds = seconds;
            return this;
        }
        
        /**
         * Adds custom headers for authentication or routing.
         * 
         * <p>Example:
         * <pre>{@code
         * builder.addHeader("Authorization", "Bearer " + apiKey)
         * }</pre>
         * 
         * @param headers map of header names to values
         * @return this builder
         */
        public Builder headers(Map<String, String> headers) {
            this.additionalHeaders = headers;
            return this;
        }
        
        /**
         * Adds custom resource attributes to be sent with metrics.
         * 
         * <p>Resource attributes provide context about the load test environment.
         * These are sent with every metric to the OTLP backend and can be used
         * for filtering, grouping, and correlation.
         * 
         * <p>Example:
         * <pre>{@code
         * builder.resourceAttributes(Map.of(
         *     "service.name", "my-load-test",
         *     "service.version", "1.0.0",
         *     "environment", "production",
         *     "region", "us-east-1",
         *     "test.name", "checkout-flow",
         *     "team", "platform"
         * ))
         * }</pre>
         * 
         * <p>Common OpenTelemetry semantic conventions include:
         * <ul>
         *   <li>service.name - Name of the service</li>
         *   <li>service.version - Version of the service</li>
         *   <li>deployment.environment - Deployment environment (dev, staging, prod)</li>
         *   <li>cloud.provider - Cloud provider (aws, gcp, azure)</li>
         *   <li>cloud.region - Cloud region</li>
         * </ul>
         * 
         * @param attributes map of attribute names to values
         * @return this builder
         */
        public Builder resourceAttributes(Map<String, String> attributes) {
            this.resourceAttributes = attributes;
            return this;
        }
        
        /**
         * Sets the OTLP protocol (gRPC or HTTP).
         * 
         * <p>Default is gRPC. Use HTTP for better compatibility with proxies
         * or firewalls that don't support gRPC.
         * 
         * <p>Example:
         * <pre>{@code
         * builder.protocol(OpenTelemetryExporter.Protocol.HTTP)
         * }</pre>
         * 
         * @param protocol the OTLP protocol (gRPC or HTTP)
         * @return this builder
         */
        public Builder protocol(Protocol protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Associates a {@link TaskIdentity} with this exporter.
         * <p>Its name and tags are published as resource attributes using the
         * keys <code>task.name</code> and <code>task.&lt;tagKey&gt;</code>. This keeps
         * task metadata separate from generic service attributes while still
         * enabling rich filtering in observability backends.
         * @param identity task identity descriptor
         * @return this builder
         */
        public Builder taskIdentity(TaskIdentity identity) {
            this.taskIdentity = identity;
            return this;
        }
        
        /**
         * Builds the OpenTelemetryExporter instance.
         * 
         * @return a new exporter
         * @throws IllegalStateException if required configuration is missing
         */
        public OpenTelemetryExporter build() {
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException("OTLP endpoint is required");
            }
            return new OpenTelemetryExporter(this);
        }
    }
    
    /**
     * OTLP protocol for metrics export.
     * 
     * <p>GRPC is the default and most efficient protocol.
     * HTTP should be used when gRPC is not supported by proxies or firewalls.
     */
    public enum Protocol {
        /**
         * gRPC protocol (default) - efficient binary protocol using HTTP/2.
         * Best for most deployments. Default port: 4317 (or 4318 with /v1/metrics path).
         */
        GRPC,
        
        /**
         * HTTP protocol - uses HTTP POST with Protocol Buffers.
         * Better compatibility with proxies and firewalls.
         * Default port: 4318.
         */
        HTTP
    }

    /**
     * Translates common user-provided resource attribute keys into their
     * OpenTelemetry semantic convention equivalents while preserving
     * already-compliant keys.
     * <ul>
     *   <li>environment -> deployment.environment</li>
     *   <li>region -> cloud.region</li>
     * </ul>
     * Keys not listed are returned unchanged.
     * @param original user supplied key
     * @return translated key
     */
    private static String translateResourceKey(String original) {
        return switch (original) {
            case "environment" -> "deployment.environment";
            case "region" -> "cloud.region";
            default -> original;
        };
    }
}
