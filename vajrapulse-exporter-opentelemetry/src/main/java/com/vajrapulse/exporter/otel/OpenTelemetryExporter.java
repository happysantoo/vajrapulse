package com.vajrapulse.exporter.otel;

import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsExporter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
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
 *     .serviceName("my-load-test")
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
    private final String serviceName;
    private final SdkMeterProvider meterProvider;
    private final Meter meter;
    private final Map<String, String> additionalHeaders;
    
    private OpenTelemetryExporter(Builder builder) {
        this.endpoint = builder.endpoint;
        this.serviceName = builder.serviceName;
        this.additionalHeaders = Map.copyOf(builder.additionalHeaders);
        
        // Create OTLP exporter
        var otlpExporterBuilder = OtlpGrpcMetricExporter.builder()
            .setEndpoint(endpoint)
            .setTimeout(Duration.ofSeconds(30));
        
        // Add custom headers if provided (e.g., for authentication)
        if (!additionalHeaders.isEmpty()) {
            additionalHeaders.forEach((key, value) -> 
                otlpExporterBuilder.addHeader(key, value)
            );
        }
        
        var otlpExporter = otlpExporterBuilder.build();
        
        // Create metric reader with periodic export
        var metricReader = PeriodicMetricReader.builder(otlpExporter)
            .setInterval(Duration.ofSeconds(builder.exportIntervalSeconds))
            .build();
        
        // Create meter provider with service resource
        this.meterProvider = SdkMeterProvider.builder()
            .setResource(Resource.create(
                Attributes.of(
                    AttributeKey.stringKey("service.name"), serviceName,
                    AttributeKey.stringKey("service.version"), "1.0.0"
                )
            ))
            .registerMetricReader(metricReader)
            .build();
        
        this.meter = meterProvider.get("vajrapulse");
        
        logger.info("OpenTelemetry exporter initialized - endpoint: {}, service: {}", 
            endpoint, serviceName);
    }
    
    @Override
    public void export(String title, AggregatedMetrics metrics) {
        try {
            logger.debug("Exporting metrics to OTLP: {}", title);
            
            // Record total executions
            meter.counterBuilder("vajrapulse.executions.total")
                .setDescription("Total number of task executions")
                .build()
                .add(metrics.totalExecutions());
            
            // Record success count
            meter.counterBuilder("vajrapulse.executions.success")
                .setDescription("Number of successful task executions")
                .build()
                .add(metrics.successCount());
            
            // Record failure count
            meter.counterBuilder("vajrapulse.executions.failure")
                .setDescription("Number of failed task executions")
                .build()
                .add(metrics.failureCount());
            
            // Record success rate as a gauge
            meter.gaugeBuilder("vajrapulse.success.rate")
                .setDescription("Success rate percentage (0-100)")
                .buildWithCallback(measurement -> 
                    measurement.record(metrics.successRate())
                );
            
            // Record success latency percentiles
            if (metrics.successCount() > 0) {
                for (var entry : metrics.successPercentiles().entrySet()) {
                    double percentile = entry.getKey();
                    double latencyMs = entry.getValue() / 1_000_000.0; // nanos to millis
                    
                    meter.histogramBuilder("vajrapulse.latency.success")
                        .setDescription("Success latency distribution")
                        .setUnit("ms")
                        .build()
                        .record(latencyMs, 
                            Attributes.of(
                                AttributeKey.doubleKey("percentile"), 
                                percentile
                            )
                        );
                }
            }
            
            // Record failure latency percentiles
            if (metrics.failureCount() > 0) {
                for (var entry : metrics.failurePercentiles().entrySet()) {
                    double percentile = entry.getKey();
                    double latencyMs = entry.getValue() / 1_000_000.0; // nanos to millis
                    
                    meter.histogramBuilder("vajrapulse.latency.failure")
                        .setDescription("Failure latency distribution")
                        .setUnit("ms")
                        .build()
                        .record(latencyMs,
                            Attributes.of(
                                AttributeKey.doubleKey("percentile"),
                                percentile
                            )
                        );
                }
            }
            
            // Force flush to ensure metrics are sent
            meterProvider.forceFlush().join(10, TimeUnit.SECONDS);
            
            logger.debug("Successfully exported metrics to OTLP");
            
        } catch (Exception e) {
            logger.error("Failed to export metrics to OTLP endpoint: {}", endpoint, e);
            // Don't throw - exporters should be resilient
        }
    }
    
    @Override
    public void close() {
        logger.info("Closing OpenTelemetry exporter");
        try {
            meterProvider.forceFlush().join(10, TimeUnit.SECONDS);
            meterProvider.close();
        } catch (Exception e) {
            logger.error("Error closing OpenTelemetry exporter", e);
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
        private String serviceName = "vajrapulse-load-test";
        private int exportIntervalSeconds = 10;
        private Map<String, String> additionalHeaders = Map.of();
        
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
         * Sets the service name for resource attribution.
         * 
         * @param serviceName the service name
         * @return this builder
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
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
         * Builds the OpenTelemetryExporter instance.
         * 
         * @return a new exporter
         * @throws IllegalStateException if required configuration is missing
         */
        public OpenTelemetryExporter build() {
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalStateException("OTLP endpoint is required");
            }
            if (serviceName == null || serviceName.isBlank()) {
                throw new IllegalStateException("Service name is required");
            }
            return new OpenTelemetryExporter(this);
        }
    }
}
