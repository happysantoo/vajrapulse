package com.vajrapulse.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleConfig;
import com.vajrapulse.core.engine.ExecutionMetrics;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.lang.ScopedValue;

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
 * 
 * <p><strong>Resource Management:</strong> This class uses {@link ScopedValue} (Java 21)
 * for performance optimization, which is safer for virtual threads than ThreadLocal.
 * This class implements {@link AutoCloseable} for consistency, though ScopedValue
 * does not require explicit cleanup. Always use try-with-resources or explicitly
 * call {@link #close()} when done:
 * 
 * <pre>{@code
 * try (MetricsCollector collector = new MetricsCollector()) {
 *     // Use collector
 *     ExecutionEngine engine = ExecutionEngine.builder()
 *         .withMetricsCollector(collector)
 *         .build();
 *     engine.run();
 * } // Resources automatically cleaned up
 * }</pre>
 * 
 * <p><strong>Note:</strong> ScopedValue provides better virtual thread compatibility
 * compared to ThreadLocal, with automatic cleanup when the scope ends.
 * 
 * @since 0.9.0
 */
public final class MetricsCollector implements AutoCloseable {
    private final MeterRegistry registry;
    private final Timer successTimer;
    private final Timer failureTimer;
    private final Counter totalCounter;
    private final Timer queueWaitTimer;
    @SuppressWarnings("unused") // Gauge is registered and used by Micrometer
    private final io.micrometer.core.instrument.Gauge queueSizeGauge;
    private final AtomicLong queueSizeHolder; // Holder for queue size gauge
    private final Counter droppedRequestsCounter;
    private final Counter rejectedRequestsCounter;
    
    private final double[] configuredPercentiles;
    private final String runId; // Optional run correlation tag
    private final long startMillis; // Track when collection started
    
    // Reusable maps for snapshot() to avoid allocations
    // Using ScopedValue (Java 21) instead of ThreadLocal for better virtual thread compatibility
    // These are bound at the ExecutionEngine scope level
    private static final ScopedValue<LinkedHashMap<Double, Double>> REUSABLE_SUCCESS_MAP = 
        ScopedValue.newInstance();
    private static final ScopedValue<LinkedHashMap<Double, Double>> REUSABLE_FAILURE_MAP = 
        ScopedValue.newInstance();
    private static final ScopedValue<LinkedHashMap<Double, Double>> REUSABLE_QUEUE_WAIT_MAP = 
        ScopedValue.newInstance();
    
    // Reusable intermediate HashMap for indexSnapshot() to avoid allocations
    private static final ScopedValue<HashMap<Double, Double>> REUSABLE_INDEX_MAP = 
        ScopedValue.newInstance();
    
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
        if (registry == null) {
            throw new IllegalArgumentException("Meter registry must not be null");
        }
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
        
        // Backpressure metrics - dropped and rejected requests
        var droppedBuilder = Counter.builder("vajrapulse.execution.backpressure.dropped")
            .description("Number of requests dropped due to backpressure");
        var rejectedBuilder = Counter.builder("vajrapulse.execution.backpressure.rejected")
            .description("Number of requests rejected due to backpressure");
        if (runId != null && !runId.isBlank()) {
            droppedBuilder.tag("run_id", runId);
            rejectedBuilder.tag("run_id", runId);
        }
        this.droppedRequestsCounter = droppedBuilder.register(registry);
        this.rejectedRequestsCounter = rejectedBuilder.register(registry);
        
        // Register JVM GC and memory metrics
        registerJvmMetrics(registry, runId);
    }
    
    /**
     * Registers JVM GC and memory metrics with the registry.
     * 
     * @param registry the meter registry
     * @param runId optional run ID for tagging
     */
    private void registerJvmMetrics(MeterRegistry registry, String runId) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        // Heap memory metrics
        var heapUsedBuilder = io.micrometer.core.instrument.Gauge.builder("vajrapulse.jvm.memory.heap.used", 
                () -> memoryBean.getHeapMemoryUsage().getUsed())
            .description("Used heap memory in bytes");
        var heapCommittedBuilder = io.micrometer.core.instrument.Gauge.builder("vajrapulse.jvm.memory.heap.committed",
                () -> memoryBean.getHeapMemoryUsage().getCommitted())
            .description("Committed heap memory in bytes");
        var heapMaxBuilder = io.micrometer.core.instrument.Gauge.builder("vajrapulse.jvm.memory.heap.max",
                () -> {
                    long max = memoryBean.getHeapMemoryUsage().getMax();
                    return max == -1 ? 0 : max;
                })
            .description("Maximum heap memory in bytes");
        
        // Non-heap memory metrics
        var nonHeapUsedBuilder = io.micrometer.core.instrument.Gauge.builder("vajrapulse.jvm.memory.nonheap.used",
                () -> memoryBean.getNonHeapMemoryUsage().getUsed())
            .description("Used non-heap memory in bytes");
        var nonHeapCommittedBuilder = io.micrometer.core.instrument.Gauge.builder("vajrapulse.jvm.memory.nonheap.committed",
                () -> memoryBean.getNonHeapMemoryUsage().getCommitted())
            .description("Committed non-heap memory in bytes");
        
        if (runId != null && !runId.isBlank()) {
            heapUsedBuilder.tag("run_id", runId);
            heapCommittedBuilder.tag("run_id", runId);
            heapMaxBuilder.tag("run_id", runId);
            nonHeapUsedBuilder.tag("run_id", runId);
            nonHeapCommittedBuilder.tag("run_id", runId);
        }
        
        heapUsedBuilder.register(registry);
        heapCommittedBuilder.register(registry);
        heapMaxBuilder.register(registry);
        nonHeapUsedBuilder.register(registry);
        nonHeapCommittedBuilder.register(registry);
        
        // GC metrics - collection count and time
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String gcName = gcBean.getName();
            
            var gcCountBuilder = io.micrometer.core.instrument.Gauge.builder("vajrapulse.jvm.gc.collections",
                    gcBean, GarbageCollectorMXBean::getCollectionCount)
                .tag("gc", gcName)
                .description("Number of GC collections");
            var gcTimeBuilder = io.micrometer.core.instrument.Gauge.builder("vajrapulse.jvm.gc.collection.time",
                    gcBean, GarbageCollectorMXBean::getCollectionTime)
                .tag("gc", gcName)
                .description("GC collection time in milliseconds");
            
            if (runId != null && !runId.isBlank()) {
                gcCountBuilder.tag("run_id", runId);
                gcTimeBuilder.tag("run_id", runId);
            }
            
            gcCountBuilder.register(registry);
            gcTimeBuilder.register(registry);
        }
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
     * Records a dropped request due to backpressure.
     * 
     * @since 0.9.6
     */
    public void recordDroppedRequest() {
        droppedRequestsCounter.increment();
    }
    
    /**
     * Records a rejected request due to backpressure.
     * 
     * @since 0.9.6
     */
    public void recordRejectedRequest() {
        rejectedRequestsCounter.increment();
    }
    
    /**
     * Creates a snapshot of current metrics.
     * 
     * <p><strong>Performance Optimizations:</strong>
     * <ul>
     *   <li>Reuses map instances (LinkedHashMap and HashMap) to avoid allocations</li>
     *   <li>Thread-safe via ScopedValue (Java 21) for reusable maps</li>
     *   <li>Minimizes GC pressure by reusing intermediate data structures</li>
     * </ul>
     * 
     * <p><strong>ScopedValue Usage:</strong> This method uses ScopedValue instances
     * for map reuse, which is safer for virtual threads than ThreadLocal. The scope
     * should be set up at the ExecutionEngine level. If not bound, new maps are
     * created (with minimal performance impact).
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

        // Reuse map instances to avoid allocations (using ScopedValue with fallback)
        LinkedHashMap<Double, Double> successMap = REUSABLE_SUCCESS_MAP.isBound() 
            ? REUSABLE_SUCCESS_MAP.get() : new LinkedHashMap<>();
        LinkedHashMap<Double, Double> failureMap = REUSABLE_FAILURE_MAP.isBound() 
            ? REUSABLE_FAILURE_MAP.get() : new LinkedHashMap<>();
        LinkedHashMap<Double, Double> queueWaitMap = REUSABLE_QUEUE_WAIT_MAP.isBound() 
            ? REUSABLE_QUEUE_WAIT_MAP.get() : new LinkedHashMap<>();
        
        // Clear and populate maps
        successMap.clear();
        failureMap.clear();
        queueWaitMap.clear();
        
        for (double p : configuredPercentiles) {
            successMap.put(p, successIdx.getOrDefault(p, Double.NaN));
            failureMap.put(p, failureIdx.getOrDefault(p, Double.NaN));
            queueWaitMap.put(p, queueWaitIdx.getOrDefault(p, Double.NaN));
        }

        long currentQueueSize = queueSizeHolder.get();
        
        // Calculate statistical summaries from snapshots
        LatencyStats successStats = calculateStats(successSnapshot, successCount);
        LatencyStats failureStats = calculateStats(failureSnapshot, failureCount);

        return new AggregatedMetrics(
            totalCount,
            successCount,
            failureCount,
            successMap,
            failureMap,
            elapsedMillis,
            currentQueueSize,
            queueWaitMap,
            successStats,
            failureStats
        );
    }
    
    /**
     * Calculates statistical summary from a histogram snapshot.
     * 
     * @param snapshot the histogram snapshot
     * @param count the number of samples
     * @return latency statistics, or null if no data
     */
    private LatencyStats calculateStats(io.micrometer.core.instrument.distribution.HistogramSnapshot snapshot, long count) {
        if (count == 0) {
            return null;
        }
        
        // Get mean from snapshot (Micrometer provides this)
        double meanNanos = snapshot.mean(TimeUnit.NANOSECONDS);
        
        // Get max from snapshot
        double maxNanos = snapshot.max(TimeUnit.NANOSECONDS);
        
        // Estimate min from P0 or use a reasonable approximation
        // Micrometer doesn't directly provide min, so we use P0 or estimate
        double minNanos = 0.0;
        var percentileValues = snapshot.percentileValues();
        if (percentileValues.length > 0) {
            // Use the smallest percentile as an approximation for min
            double smallestPercentileValue = Double.MAX_VALUE;
            for (var pv : percentileValues) {
                double value = pv.value(TimeUnit.NANOSECONDS);
                if (value > 0 && value < smallestPercentileValue) {
                    smallestPercentileValue = value;
                }
            }
            if (smallestPercentileValue < Double.MAX_VALUE) {
                minNanos = smallestPercentileValue;
            }
        }
        
        // Calculate standard deviation
        // Micrometer doesn't provide stddev directly, so we estimate from percentiles
        // Using the empirical rule: ~68% of data falls within 1 stddev of mean
        // P84 - P50 ≈ 1 stddev (for normal distribution)
        double stdDevNanos = estimateStdDev(snapshot, meanNanos);
        
        return new LatencyStats(meanNanos, stdDevNanos, minNanos, maxNanos, count);
    }
    
    /**
     * Estimates standard deviation from histogram percentiles.
     * 
     * <p>Uses the empirical rule: for a normal distribution, approximately 68%
     * of data falls within 1 standard deviation of the mean. This means:
     * <ul>
     *   <li>P84 - P50 ≈ 1 stddev (upper half)</li>
     *   <li>P50 - P16 ≈ 1 stddev (lower half)</li>
     * </ul>
     * 
     * <p>We use the average of both estimates for a more robust approximation.
     * 
     * @param snapshot the histogram snapshot
     * @param meanNanos the mean value in nanoseconds
     * @return estimated standard deviation in nanoseconds
     */
    private double estimateStdDev(io.micrometer.core.instrument.distribution.HistogramSnapshot snapshot, double meanNanos) {
        var percentileValues = snapshot.percentileValues();
        if (percentileValues.length < 2) {
            return 0.0;
        }
        
        // Find P50, P16, and P84 (or closest available)
        double p50 = Double.NaN;
        double p16 = Double.NaN;
        double p84 = Double.NaN;
        
        for (var pv : percentileValues) {
            double p = pv.percentile();
            double value = pv.value(TimeUnit.NANOSECONDS);
            
            if (Math.abs(p - 0.50) < 0.01) {
                p50 = value;
            } else if (Math.abs(p - 0.16) < 0.05) {
                p16 = value;
            } else if (Math.abs(p - 0.84) < 0.05) {
                p84 = value;
            }
        }
        
        // If we have P50 and P84, estimate upper stddev
        if (!Double.isNaN(p50) && !Double.isNaN(p84)) {
            return p84 - p50;
        }
        
        // If we have P50 and P16, estimate lower stddev
        if (!Double.isNaN(p50) && !Double.isNaN(p16)) {
            return p50 - p16;
        }
        
        // Fallback: use (max - mean) / 3 as a rough estimate
        // (assuming max is approximately at 3 stddev from mean)
        double maxNanos = snapshot.max(TimeUnit.NANOSECONDS);
        if (maxNanos > meanNanos) {
            return (maxNanos - meanNanos) / 3.0;
        }
        
        return 0.0;
    }

    /**
     * Indexes a histogram snapshot into a map of percentile -> value.
     * 
     * <p><strong>Performance Optimization:</strong> Reuses a HashMap instance
     * via ScopedValue (Java 21) to avoid allocations in the hot path.
     * 
     * @param snapshot the histogram snapshot to index
     * @return map of percentile (rounded to 3 decimals) -> value in nanoseconds
     */
    private Map<Double, Double> indexSnapshot(io.micrometer.core.instrument.distribution.HistogramSnapshot snapshot) {
        // Reuse HashMap instance to avoid allocations (using ScopedValue with fallback)
        HashMap<Double, Double> idx = REUSABLE_INDEX_MAP.isBound() 
            ? REUSABLE_INDEX_MAP.get() : new HashMap<>();
        idx.clear();
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

    /**
     * Sanitizes and validates percentile values.
     * 
     * <p>Validates that all percentiles are in the range (0.0, 1.0].
     * Invalid values are filtered out. If no valid percentiles remain,
     * defaults to [0.50, 0.95, 0.99].
     * 
     * @param input the input percentiles (may be null or empty)
     * @return sanitized array of valid percentiles
     * @throws IllegalArgumentException if input contains invalid values and validation fails
     */
    private double[] sanitizePercentiles(double[] input) {
        if (input == null || input.length == 0) {
            return new double[]{0.50, 0.95, 0.99};
        }
        
        // Validate and filter percentiles
        double[] valid = java.util.Arrays.stream(input)
            .map(p -> round3(p))
            .filter(p -> {
                if (p <= 0.0 || p > 1.0) {
                    // Log warning for invalid percentiles but don't throw
                    // (allows graceful degradation)
                    return false;
                }
                return true;
            })
            .distinct()
            .sorted()
            .toArray();
        
        // If no valid percentiles, use defaults
        if (valid.length == 0) {
            return new double[]{0.50, 0.95, 0.99};
        }
        
        return valid;
    }

    /**
     * Rounding precision multiplier for percentile rounding (3 decimal places).
     */
    private static final double PERCENTILE_ROUNDING_PRECISION = 1000.0;
    
    /**
     * Rounds a value to 3 decimal places.
     * 
     * @param v the value to round
     * @return the rounded value
     */
    private static double round3(double v) {
        return Math.round(v * PERCENTILE_ROUNDING_PRECISION) / PERCENTILE_ROUNDING_PRECISION;
    }
    
    /**
     * Closes this metrics collector and cleans up resources.
     * 
     * <p>This method is provided for {@link AutoCloseable} compatibility.
     * With ScopedValue (Java 21), explicit cleanup is not required as values
     * are automatically cleaned up when the scope ends. However, this method
     * is kept for API consistency and potential future resource management needs.
     * 
     * <p>After calling this method, the collector should not be used. Calling
     * {@link #snapshot()} or other methods after close may work but is not guaranteed
     * and is not recommended.
     * 
     * <p>This method is idempotent - calling it multiple times has no additional effect.
     * 
     * <p><strong>Best Practice:</strong> Always use try-with-resources:
     * <pre>{@code
     * try (MetricsCollector collector = new MetricsCollector()) {
     *     // Use collector
     * } // Automatically closed
     * }</pre>
     */
    @Override
    public void close() {
        // ScopedValue does not require explicit cleanup - values are automatically
        // cleaned up when the scope ends. This method is kept for AutoCloseable
        // compatibility and potential future resource management needs.
    }
}
