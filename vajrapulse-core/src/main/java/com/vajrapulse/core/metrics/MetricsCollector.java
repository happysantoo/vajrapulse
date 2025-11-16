package com.vajrapulse.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleConfig;
import com.vajrapulse.core.engine.ExecutionMetrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

/**
 * Collects and aggregates execution metrics using Micrometer.
 * 
 * <p>This class records:
 * <ul>
 *   <li>Success/failure counts</li>
 *   <li>Latency distribution with percentiles</li>
 *   <li>Total execution count</li>
 * </ul>
 * 
 * <p>Thread-safe for concurrent metric recording.
 */
public final class MetricsCollector {
    private final MeterRegistry registry;
    private final Timer successTimer;
    private final Timer failureTimer;
    private final Counter totalCounter;
    private final double[] configuredPercentiles;
    
    /**
     * Creates a collector with default SimpleMeterRegistry.
     */
    public MetricsCollector() {
        this(createDefaultRegistry(), new double[]{0.50, 0.95, 0.99});
    }

    /** Convenience constructor supplying only custom percentiles using default registry. */
    public MetricsCollector(double... percentiles) {
        this(createDefaultRegistry(), percentiles);
    }
    
    private static SimpleMeterRegistry createDefaultRegistry() {
        return new SimpleMeterRegistry(SimpleConfig.DEFAULT, io.micrometer.core.instrument.Clock.SYSTEM);
    }

    /**
     * Factory allowing custom percentiles and SLO buckets with default registry.
     */
    public static MetricsCollector createWith(double[] percentiles, Duration... sloBuckets) {
        return createWith(createDefaultRegistry(), percentiles, sloBuckets);
    }

    /**
     * Factory allowing custom registry, percentiles and SLO buckets.
     */
    public static MetricsCollector createWith(MeterRegistry registry, double[] percentiles, Duration... sloBuckets) {
        return new MetricsCollector(registry, percentiles, sloBuckets);
    }
    
    /**
     * Creates a collector with the specified registry.
     * 
     * @param registry the meter registry to use
     */
    public MetricsCollector(MeterRegistry registry, double... percentiles) {
        this(registry, percentiles, (Duration[]) null);
    }

    private MetricsCollector(MeterRegistry registry, double[] percentiles, Duration... sloBuckets) {
        this.registry = registry;
        this.configuredPercentiles = sanitizePercentiles(percentiles);

        Duration[] objectives = (sloBuckets != null && sloBuckets.length > 0)
            ? sloBuckets
            : new Duration[]{
                Duration.ofMillis(10),
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(200),
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
            };
        
        this.successTimer = Timer.builder("vajrapulse.execution.duration")
            .tag("status", "success")
            .description("Successful task execution duration")
            .publishPercentiles(configuredPercentiles)
            .publishPercentileHistogram()
            .percentilePrecision(2)
            .serviceLevelObjectives(objectives)
            .register(registry);
        
        this.failureTimer = Timer.builder("vajrapulse.execution.duration")
            .tag("status", "failure")
            .description("Failed task execution duration")
            .publishPercentiles(configuredPercentiles)
            .publishPercentileHistogram()
            .percentilePrecision(2)
            .serviceLevelObjectives(objectives)
            .register(registry);
        
        this.totalCounter = Counter.builder("vajrapulse.execution.total")
            .description("Total task executions")
            .register(registry);
    }
    
    /**
     * Records metrics from a task execution.
     * 
     * @param metrics the execution metrics to record
     */
    public void record(ExecutionMetrics metrics) {
        totalCounter.increment();
        
        long durationNanos = metrics.durationNanos();
        if (metrics.isSuccess()) {
            successTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        } else {
            failureTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }
    
    /**
     * Creates a snapshot of current metrics.
     * 
     * @return aggregated metrics snapshot
     */
    public AggregatedMetrics snapshot() {
        long successCount = successTimer.count();
        long failureCount = failureTimer.count();
        long totalCount = (long) totalCounter.count();

        var successSnapshot = successTimer.takeSnapshot();
        var failureSnapshot = failureTimer.takeSnapshot();

        Map<Double, Double> successIdx = indexSnapshot(successSnapshot);
        Map<Double, Double> failureIdx = indexSnapshot(failureSnapshot);

        java.util.Map<Double, Double> successMap = new java.util.LinkedHashMap<>();
        java.util.Map<Double, Double> failureMap = new java.util.LinkedHashMap<>();
        for (double p : configuredPercentiles) {
            successMap.put(p, successIdx.getOrDefault(p, Double.NaN));
            failureMap.put(p, failureIdx.getOrDefault(p, Double.NaN));
        }

        return new AggregatedMetrics(
            totalCount,
            successCount,
            failureCount,
            successMap,
            failureMap
        );
    }

    private Map<Double, Double> indexSnapshot(io.micrometer.core.instrument.distribution.HistogramSnapshot snapshot) {
        Map<Double, Double> idx = new HashMap<>();
        for (var pv : snapshot.percentileValues()) {
            double key = round3(pv.percentile());
            idx.put(key, pv.value(TimeUnit.NANOSECONDS));
        }
        return idx;
    }
    
    /**
     * Returns the underlying meter registry.
     * 
     * @return the meter registry
     */
    public MeterRegistry getRegistry() {
        return registry;
    }

    private double[] sanitizePercentiles(double[] input) {
        if (input == null || input.length == 0) {
            return new double[]{0.50, 0.95, 0.99};
        }
        return java.util.Arrays.stream(input)
            .map(p -> round3(p))
            .filter(p -> p > 0.0 && p <= 1.0)
            .distinct()
            .sorted()
            .toArray();
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
