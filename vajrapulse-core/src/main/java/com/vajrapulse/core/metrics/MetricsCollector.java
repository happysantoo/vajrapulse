package com.vajrapulse.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleConfig;
import com.vajrapulse.core.engine.ExecutionMetrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
    private final Timer queueWaitTimer;
    @SuppressWarnings("unused") // Gauge is registered and used by Micrometer
    private final io.micrometer.core.instrument.Gauge queueSizeGauge;
    private final AtomicLong queueSizeHolder; // Holder for queue size gauge
    private final double[] configuredPercentiles;
    private final String runId; // Optional run correlation tag
    private final long startMillis; // Track when collection started
    
    /**
     * Creates a collector with default SimpleMeterRegistry.
     */
    public MetricsCollector() {
        this(createDefaultRegistry(), null, new double[]{0.50, 0.95, 0.99});
    }

    /** Convenience constructor supplying only custom percentiles using default registry. */
    public MetricsCollector(double... percentiles) {
        this(createDefaultRegistry(), null, percentiles);
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
        return new MetricsCollector(registry, null, percentiles, sloBuckets);
    }

    /** Factory including runId tag. */
    public static MetricsCollector createWithRunId(String runId, double[] percentiles, Duration... sloBuckets) {
        return createWithRunId(createDefaultRegistry(), runId, percentiles, sloBuckets);
    }

    public static MetricsCollector createWithRunId(MeterRegistry registry, String runId, double[] percentiles, Duration... sloBuckets) {
        return new MetricsCollector(registry, runId, percentiles, sloBuckets);
    }
    
    /**
     * Creates a collector with the specified registry.
     * 
     * @param registry the meter registry to use
     */
    public MetricsCollector(MeterRegistry registry, double... percentiles) {
        this(registry, null, percentiles, (Duration[]) null);
    }

    private MetricsCollector(MeterRegistry registry, String runId, double[] percentiles, Duration... sloBuckets) {
        this.registry = registry;
        this.runId = runId;
        this.configuredPercentiles = sanitizePercentiles(percentiles);
        this.startMillis = System.currentTimeMillis();

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
        
        var successBuilder = Timer.builder("vajrapulse.execution.duration")
            .tag("status", "success")
            .description("Successful task execution duration")
            .publishPercentiles(configuredPercentiles)
            .publishPercentileHistogram()
            .percentilePrecision(2)
            .serviceLevelObjectives(objectives);
        var failureBuilder = Timer.builder("vajrapulse.execution.duration")
            .tag("status", "failure")
            .description("Failed task execution duration")
            .publishPercentiles(configuredPercentiles)
            .publishPercentileHistogram()
            .percentilePrecision(2)
            .serviceLevelObjectives(objectives);
        if (runId != null && !runId.isBlank()) {
            successBuilder.tag("run_id", runId);
            failureBuilder.tag("run_id", runId);
        }
        this.successTimer = successBuilder.register(registry);
        this.failureTimer = failureBuilder.register(registry);
        var totalBuilder = Counter.builder("vajrapulse.execution.total")
            .description("Total task executions");
        if (runId != null && !runId.isBlank()) {
            totalBuilder.tag("run_id", runId);
        }
        this.totalCounter = totalBuilder.register(registry);
        
        // Queue metrics
        var queueWaitBuilder = Timer.builder("vajrapulse.execution.queue.wait_time")
            .description("Time tasks wait in queue before execution starts")
            .publishPercentiles(configuredPercentiles)
            .publishPercentileHistogram()
            .percentilePrecision(2)
            .serviceLevelObjectives(
                Duration.ofNanos(1_000_000),      // 1ms
                Duration.ofNanos(10_000_000),       // 10ms
                Duration.ofNanos(50_000_000),      // 50ms
                Duration.ofNanos(100_000_000),     // 100ms
                Duration.ofNanos(500_000_000)      // 500ms
            );
        if (runId != null && !runId.isBlank()) {
            queueWaitBuilder.tag("run_id", runId);
        }
        this.queueWaitTimer = queueWaitBuilder.register(registry);
        
        // Queue size gauge - will be updated by ExecutionEngine
        // This tracks tasks that have been submitted but not yet started executing
        this.queueSizeHolder = new AtomicLong(0);
        var queueSizeBuilder = io.micrometer.core.instrument.Gauge.builder("vajrapulse.execution.queue.size", queueSizeHolder, AtomicLong::get)
            .description("Number of task executions waiting in queue (submitted but not yet started executing)");
        if (runId != null && !runId.isBlank()) {
            queueSizeBuilder.tag("run_id", runId);
        }
        this.queueSizeGauge = queueSizeBuilder.register(registry);
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
     * Records queue wait time for a task.
     * 
     * @param waitTimeNanos the time the task waited in the queue (nanoseconds)
     */
    public void recordQueueWait(long waitTimeNanos) {
        queueWaitTimer.record(waitTimeNanos, TimeUnit.NANOSECONDS);
    }
    
    /**
     * Updates the queue size gauge.
     * This should be called by ExecutionEngine to keep the gauge current.
     * 
     * @param queueSize the current queue size
     */
    public void updateQueueSize(long queueSize) {
        queueSizeHolder.set(queueSize);
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
        long elapsedMillis = System.currentTimeMillis() - startMillis;

        var successSnapshot = successTimer.takeSnapshot();
        var failureSnapshot = failureTimer.takeSnapshot();
        var queueWaitSnapshot = queueWaitTimer.takeSnapshot();

        Map<Double, Double> successIdx = indexSnapshot(successSnapshot);
        Map<Double, Double> failureIdx = indexSnapshot(failureSnapshot);
        Map<Double, Double> queueWaitIdx = indexSnapshot(queueWaitSnapshot);

        java.util.Map<Double, Double> successMap = new java.util.LinkedHashMap<>();
        java.util.Map<Double, Double> failureMap = new java.util.LinkedHashMap<>();
        java.util.Map<Double, Double> queueWaitMap = new java.util.LinkedHashMap<>();
        for (double p : configuredPercentiles) {
            successMap.put(p, successIdx.getOrDefault(p, Double.NaN));
            failureMap.put(p, failureIdx.getOrDefault(p, Double.NaN));
            queueWaitMap.put(p, queueWaitIdx.getOrDefault(p, Double.NaN));
        }

        long currentQueueSize = queueSizeHolder.get();

        return new AggregatedMetrics(
            totalCount,
            successCount,
            failureCount,
            successMap,
            failureMap,
            elapsedMillis,
            currentQueueSize,
            queueWaitMap
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
     * <p><strong>Note:</strong> The returned registry is mutable and shared.
     * Callers should not modify it directly. This method exists for advanced
     * use cases where direct registry access is needed (e.g., custom metrics).
     * 
     * @return the meter registry (mutable, handle with care)
     */
    public MeterRegistry getRegistry() {
        return registry;
    }

    /** Returns runId if present, else null. */
    public String getRunId() { return runId; }

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
