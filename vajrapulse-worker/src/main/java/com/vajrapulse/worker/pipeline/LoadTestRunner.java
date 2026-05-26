package com.vajrapulse.worker.pipeline;

import com.vajrapulse.api.metrics.RunContext;
import com.vajrapulse.api.metrics.SystemInfo;
import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.pattern.StaticLoad;
import com.vajrapulse.api.pattern.RampUpLoad;
import com.vajrapulse.api.pattern.RampUpToMaxLoad;
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern;
import com.vajrapulse.api.pattern.adaptive.AdaptiveConfig;
import com.vajrapulse.api.metrics.MetricsProvider;
import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.engine.MetricsProviderAdapter;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.metrics.MetricsExporter;
import com.vajrapulse.core.metrics.PeriodicMetricsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-level convenience runner for executing load tests with metrics collection,
 * optional periodic reporting, and final export. Lives in worker layer to keep core minimal.
 * 
 * <p>Implements {@link AutoCloseable} to manage exporter lifecycle automatically.
 * When used with try-with-resources, exporters are closed after final metrics are exported.
 * 
 * <p>Example:
 * <pre>{@code
 * try (LoadTestRunner runner = LoadTestRunner.builder()
 *         .addExporter(new OpenTelemetryExporter(...))
 *         .withPeriodic(Duration.ofSeconds(5))
 *         .build()) {
 *     runner.run(task, loadPattern);
 * } // Automatic final export and cleanup
 * }</pre>
 * 
 * @since 0.9.0
 */
public final class LoadTestRunner implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(LoadTestRunner.class);

    private final MetricsCollector collector;
    private final List<MetricsExporter> exporters;
    private final Duration periodicInterval;
    private final boolean fireImmediateLive;
    private final String runId;

    private LoadTestRunner(MetricsCollector collector, List<MetricsExporter> exporters, 
                          Duration periodicInterval, boolean fireImmediateLive, String runId) {
        this.collector = collector;
        this.exporters = exporters;
        this.periodicInterval = periodicInterval;
        this.fireImmediateLive = fireImmediateLive;
        this.runId = runId != null ? runId : (collector.getRunId() != null ? collector.getRunId() : generateRunId());
    }
    
    private static String generateRunId() {
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    public static Builder builder() { return new Builder(); }

    /**
     * Returns a MetricsProvider that provides real-time metrics from this runner.
     * 
     * <p>The MetricsProvider is updated as tasks execute, providing current
     * failure rate and total execution count for use with AdaptiveLoadPattern.
     * 
     * <p>Example:
     * <pre>{@code
     * try (LoadTestRunner runner = LoadTestRunner.builder()
     *         .addExporter(new ConsoleMetricsExporter())
     *         .build()) {
     *     MetricsProvider provider = runner.getMetricsProvider();
     *     AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
     *         100.0, Duration.ofSeconds(10), ..., provider);
     *     runner.run(task, pattern);
     * }
     * }</pre>
     * 
     * <p><strong>Note:</strong> The returned MetricsProvider uses caching internally
     * to optimize performance. Metrics are refreshed at most once per 100ms by default.
     * 
     * @return a MetricsProvider backed by this runner's metrics collector
     * @since 0.9.6
     */
    public MetricsProvider getMetricsProvider() {
        return new MetricsProviderAdapter(collector);
    }

    /** Executes the task under the provided load pattern returning final aggregated metrics. */
    public AggregatedMetrics run(TaskLifecycle task, LoadPattern loadPattern) throws Exception {
        Instant startTime = Instant.now();
        
        PeriodicMetricsReporter reporter = null;
        if (periodicInterval != null && !exporters.isEmpty()) {
            reporter = new PeriodicMetricsReporter(collector, exporters.get(0), periodicInterval, fireImmediateLive);
            reporter.start();
        }

        AggregatedMetrics finalSnapshot;
        try {
            try (ExecutionEngine engine = ExecutionEngine.builder()
                    .withTask(task)
                    .withLoadPattern(loadPattern)
                    .withMetricsCollector(collector)
                    .withShutdownHook(false)  // LoadTestRunner manages lifecycle, no hooks needed
                    .build()) {
                engine.run();
                finalSnapshot = collector.snapshot();
            }
        } finally {
            if (reporter != null) {
                reporter.close();
            }
        }

        Instant endTime = Instant.now();
        
        // Create RunContext with metadata
        RunContext context = createRunContext(task, loadPattern, startTime, endTime);

        // Export final metrics to all exporters
        for (MetricsExporter exporter : exporters) {
            try {
                exporter.export("Final Results", finalSnapshot, context);
            } catch (Exception e) {
                logger.error("Failed to export final metrics to {}: {}", 
                    exporter.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        
        return finalSnapshot;
    }
    
    private RunContext createRunContext(TaskLifecycle task, LoadPattern loadPattern, 
                                       Instant startTime, Instant endTime) {
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("task_class", task.getClass().getSimpleName());
        configuration.put("pattern_class", loadPattern.getClass().getSimpleName());
        configuration.put("duration_ms", loadPattern.getDuration().toMillis());
        
        addPatternConfiguration(loadPattern, configuration);
        
        return RunContext.of(
            runId,
            startTime,
            endTime,
            task.getClass().getSimpleName(),
            loadPattern.getClass().getSimpleName(),
            configuration,
            SystemInfo.current()
        );
    }
    
    /**
     * Adds pattern-specific configuration to the map.
     * 
     * <p>Uses pattern type checking instead of reflection for type-safe
     * configuration extraction. This avoids reflection overhead and
     * provides compile-time safety.
     * 
     * @param loadPattern the load pattern
     * @param configuration the configuration map to populate
     */
    private void addPatternConfiguration(LoadPattern loadPattern, Map<String, Object> configuration) {
        if (loadPattern instanceof StaticLoad staticLoad) {
            configuration.put("tps", staticLoad.tps());
        } else if (loadPattern instanceof RampUpLoad rampLoad) {
            configuration.put("startTps", 0.0);
            configuration.put("endTps", rampLoad.maxTps());
        } else if (loadPattern instanceof RampUpToMaxLoad rampSustainLoad) {
            configuration.put("startTps", 0.0);
            configuration.put("endTps", rampSustainLoad.maxTps());
        } else if (loadPattern instanceof AdaptiveLoadPattern adaptivePattern) {
            AdaptiveConfig config = adaptivePattern.getConfig();
            configuration.put("initialTps", config.initialTps());
            configuration.put("maxTps", config.maxTps());
            configuration.put("minTps", config.minTps());
        }
        // For unknown patterns, no additional configuration is extracted
    }

    /**
     * Closes all AutoCloseable exporters.
     * 
     * <p>This method is automatically called when using try-with-resources.
     * It ensures exporters are properly closed after final metrics have been exported.
     * 
     * <p>Note: Final metrics export happens in {@link #run(TaskLifecycle, LoadPattern)} before
     * this method is called, guaranteeing all metrics are flushed before cleanup.
     */
    @Override
    public void close() {
        for (MetricsExporter exporter : exporters) {
            if (exporter instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                    logger.debug("Closed exporter: {}", exporter.getClass().getSimpleName());
                } catch (Exception e) {
                    logger.error("Failed to close exporter: {}", exporter.getClass().getSimpleName(), e);
                }
            }
        }
    }

    public static final class Builder {
        private MetricsCollector collector;
        private final List<MetricsExporter> exporters = new ArrayList<>();
        private Duration periodicInterval;
        private boolean fireImmediateLive;
        private double[] percentiles;
        private java.time.Duration[] sloBuckets;
        private String runId;

        /** Provide a fully constructed collector. If set, `withPercentiles`/`withSloBuckets` must not be used. */
        public Builder withCollector(MetricsCollector collector) {
            this.collector = collector;
            return this;
        }
        /** Explicit runId for correlation; if absent one will be generated only if needed. */
        public Builder withRunId(String runId) {
            this.runId = runId;
            return this;
        }

        /** Add a metrics exporter. The first exporter is used for live updates when periodic reporting is enabled. */
        public Builder addExporter(MetricsExporter exporter) {
            this.exporters.add(exporter);
            return this;
        }

        /** Enable periodic live reporting at the given interval. */
        public Builder withPeriodic(Duration interval) {
            this.periodicInterval = interval;
            return this;
        }

        /** Configure custom latency percentiles (values in (0,1]). */
        public Builder withPercentiles(double... percentiles) {
            this.percentiles = percentiles;
            return this;
        }

        /** Configure Timer SLO buckets for histogram publishing. */
        public Builder withSloBuckets(java.time.Duration... sloBuckets) {
            this.sloBuckets = sloBuckets;
            return this;
        }

        /** Fire the first live snapshot immediately when starting periodic reporting. */
        public Builder withImmediateLive(boolean fireImmediately) {
            this.fireImmediateLive = fireImmediately;
            return this;
        }

        public LoadTestRunner build() {
            if (collector != null && (percentiles != null || (sloBuckets != null && sloBuckets.length > 0))) {
                throw new IllegalStateException("withCollector cannot be combined with withPercentiles/withSloBuckets");
            }
            MetricsCollector effectiveCollector = collector;
            String effectiveRunId = runId;
            
            if (effectiveCollector == null) {
                if ((sloBuckets != null && sloBuckets.length > 0) || percentiles != null) {
                    double[] pts = (percentiles != null) ? percentiles : new double[]{0.50, 0.95, 0.99};
                    if (effectiveRunId != null && !effectiveRunId.isBlank()) {
                        effectiveCollector = MetricsCollector.createWithRunId(effectiveRunId, pts, sloBuckets == null ? new java.time.Duration[]{} : sloBuckets);
                    } else {
                        effectiveCollector = MetricsCollector.createWith(pts, sloBuckets == null ? new java.time.Duration[]{} : sloBuckets);
                    }
                } else {
                    if (effectiveRunId != null && !effectiveRunId.isBlank()) {
                        effectiveCollector = MetricsCollector.createWithRunId(effectiveRunId, new double[]{0.50, 0.95, 0.99});
                    } else {
                        effectiveCollector = new MetricsCollector();
                    }
                }
            }
            return new LoadTestRunner(effectiveCollector, List.copyOf(exporters), periodicInterval, fireImmediateLive, effectiveRunId);
        }
    }
}
