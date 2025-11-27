package com.vajrapulse.worker.pipeline;

import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.TaskLifecycle;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.metrics.MetricsExporter;
import com.vajrapulse.core.metrics.PeriodicMetricsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * High-level convenience pipeline bundling execution, metrics collection, optional
 * periodic reporting and final export. Lives in worker layer to keep core minimal.
 * 
 * <p>Implements {@link AutoCloseable} to manage exporter lifecycle automatically.
 * When used with try-with-resources, exporters are closed after final metrics are exported.
 * 
 * <p>Example:
 * <pre>{@code
 * try (MetricsPipeline pipeline = MetricsPipeline.builder()
 *         .addExporter(new OpenTelemetryExporter(...))
 *         .withPeriodic(Duration.ofSeconds(5))
 *         .build()) {
 *     pipeline.run(task, loadPattern);
 * } // Automatic final export and cleanup
 * }</pre>
 */
public final class MetricsPipeline implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MetricsPipeline.class);

    private final MetricsCollector collector;
    private final List<MetricsExporter> exporters;
    private final Duration periodicInterval;
    private final boolean fireImmediateLive;

    private MetricsPipeline(MetricsCollector collector, List<MetricsExporter> exporters, Duration periodicInterval, boolean fireImmediateLive) {
        this.collector = collector;
        this.exporters = exporters;
        this.periodicInterval = periodicInterval;
        this.fireImmediateLive = fireImmediateLive;
    }

    public static Builder builder() { return new Builder(); }

    /** Executes the task under the provided load pattern returning final aggregated metrics. */
    public AggregatedMetrics run(TaskLifecycle task, LoadPattern loadPattern) throws Exception {
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
                    .build()) {
                engine.run();
                finalSnapshot = collector.snapshot();
            }
        } finally {
            if (reporter != null) {
                reporter.close();
            }
        }

        // Export final metrics to all exporters
        for (MetricsExporter exporter : exporters) {
            try {
                exporter.export("Final Results", finalSnapshot);
            } catch (Exception e) {
                logger.error("Exporter {} failed during final export", exporter.getClass().getSimpleName(), e);
            }
        }
        return finalSnapshot;
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

        public MetricsPipeline build() {
            if (collector != null && (percentiles != null || (sloBuckets != null && sloBuckets.length > 0))) {
                throw new IllegalStateException("withCollector cannot be combined with withPercentiles/withSloBuckets");
            }
            MetricsCollector effectiveCollector = collector;
            if (effectiveCollector == null) {
                if ((sloBuckets != null && sloBuckets.length > 0) || percentiles != null) {
                    double[] pts = (percentiles != null) ? percentiles : new double[]{0.50, 0.95, 0.99};
                    if (runId != null && !runId.isBlank()) {
                        effectiveCollector = MetricsCollector.createWithRunId(runId, pts, sloBuckets == null ? new java.time.Duration[]{} : sloBuckets);
                    } else {
                        effectiveCollector = MetricsCollector.createWith(pts, sloBuckets == null ? new java.time.Duration[]{} : sloBuckets);
                    }
                } else {
                    if (runId != null && !runId.isBlank()) {
                        effectiveCollector = MetricsCollector.createWithRunId(runId, new double[]{0.50, 0.95, 0.99});
                    } else {
                        effectiveCollector = new MetricsCollector();
                    }
                }
            }
            return new MetricsPipeline(effectiveCollector, List.copyOf(exporters), periodicInterval, fireImmediateLive);
        }
    }
}
