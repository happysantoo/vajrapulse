package com.vajrapulse.core.metrics;

import com.vajrapulse.api.metrics.Metrics;
import com.vajrapulse.core.util.TpsCalculator;
import java.util.Collections;

/**
 * Aggregated metrics snapshot at a point in time.
 * 
 * <p>All latency values are in nanoseconds.
 * 
 * <p>This class implements {@link Metrics} to enable assertion evaluation
 * without creating a dependency from the API module to the core module.
 * 
 * @param totalExecutions total number of executions
 * @param successCount number of successful executions
 * @param failureCount number of failed executions
 * @param successPercentiles map of percentile→latency nanos for successes
 * @param failurePercentiles map of percentile→latency nanos for failures
 * @param elapsedMillis time elapsed since metrics collection started
 * @param queueSize current number of pending executions in queue
 * @param queueWaitPercentiles map of percentile→wait time nanos for queue wait
 * @param successStats statistical summary for successful executions (may be null)
 * @param failureStats statistical summary for failed executions (may be null)
 * @since 0.9.7
 */
public record AggregatedMetrics(
    long totalExecutions,
    long successCount,
    long failureCount,
    java.util.Map<Double, Double> successPercentiles,
    java.util.Map<Double, Double> failurePercentiles,
    long elapsedMillis,
    long queueSize,
    java.util.Map<Double, Double> queueWaitPercentiles,
    LatencyStats successStats,
    LatencyStats failureStats
) implements Metrics {
    /**
     * Percentage multiplier for rate calculations (100.0 = 100%).
     */
    private static final double PERCENTAGE_MULTIPLIER = 100.0;
    
    /**
     * Compact constructor that creates defensive copies of mutable collections.
     */
    public AggregatedMetrics {
        // Create unmodifiable defensive copies of Map fields
        successPercentiles = successPercentiles != null 
            ? Collections.unmodifiableMap(new java.util.LinkedHashMap<>(successPercentiles))
            : Collections.emptyMap();
        failurePercentiles = failurePercentiles != null
            ? Collections.unmodifiableMap(new java.util.LinkedHashMap<>(failurePercentiles))
            : Collections.emptyMap();
        queueWaitPercentiles = queueWaitPercentiles != null
            ? Collections.unmodifiableMap(new java.util.LinkedHashMap<>(queueWaitPercentiles))
            : Collections.emptyMap();
    }
    
    /**
     * Backward-compatible constructor without statistical summary.
     * 
     * @param totalExecutions total number of executions
     * @param successCount number of successful executions
     * @param failureCount number of failed executions
     * @param successPercentiles map of percentile→latency nanos for successes
     * @param failurePercentiles map of percentile→latency nanos for failures
     * @param elapsedMillis time elapsed since metrics collection started
     * @param queueSize current number of pending executions in queue
     * @param queueWaitPercentiles map of percentile→wait time nanos for queue wait
     */
    public AggregatedMetrics(
        long totalExecutions,
        long successCount,
        long failureCount,
        java.util.Map<Double, Double> successPercentiles,
        java.util.Map<Double, Double> failurePercentiles,
        long elapsedMillis,
        long queueSize,
        java.util.Map<Double, Double> queueWaitPercentiles
    ) {
        this(totalExecutions, successCount, failureCount, successPercentiles, 
             failurePercentiles, elapsedMillis, queueSize, queueWaitPercentiles, 
             null, null);
    }
    
    /**
     * Calculates the success rate as a percentage.
     * 
     * @return success rate (0.0 to 100.0)
     */
    public double successRate() {
        if (totalExecutions == 0) {
            return 0.0;
        }
        return (successCount * PERCENTAGE_MULTIPLIER) / totalExecutions;
    }
    
    /**
     * Calculates the failure rate as a percentage.
     * 
     * @return failure rate (0.0 to 100.0)
     */
    public double failureRate() {
        if (totalExecutions == 0) {
            return 0.0;
        }
        return (failureCount * PERCENTAGE_MULTIPLIER) / totalExecutions;
    }
    
    /**
     * Calculates actual response TPS (total responses per second).
     * 
     * @return total response TPS
     */
    public double responseTps() {
        return TpsCalculator.calculateActualTps(totalExecutions, elapsedMillis);
    }
    
    /**
     * Calculates successful response TPS.
     * 
     * @return successful response TPS
     */
    public double successTps() {
        return TpsCalculator.calculateActualTps(successCount, elapsedMillis);
    }
    
    /**
     * Calculates failed response TPS.
     * 
     * @return failed response TPS
     */
    public double failureTps() {
        return TpsCalculator.calculateActualTps(failureCount, elapsedMillis);
    }
    
    /**
     * Returns the mean success latency in nanoseconds.
     * 
     * @return mean success latency, or 0.0 if no statistics available
     */
    public double meanSuccessLatencyNanos() {
        return successStats != null ? successStats.mean() : 0.0;
    }
    
    /**
     * Returns the mean failure latency in nanoseconds.
     * 
     * @return mean failure latency, or 0.0 if no statistics available
     */
    public double meanFailureLatencyNanos() {
        return failureStats != null ? failureStats.mean() : 0.0;
    }
    
    /**
     * Returns the minimum success latency in nanoseconds.
     * 
     * @return minimum success latency, or 0.0 if no statistics available
     */
    public double minSuccessLatencyNanos() {
        return successStats != null ? successStats.min() : 0.0;
    }
    
    /**
     * Returns the maximum success latency in nanoseconds.
     * 
     * @return maximum success latency, or 0.0 if no statistics available
     */
    public double maxSuccessLatencyNanos() {
        return successStats != null ? successStats.max() : 0.0;
    }
    
    /**
     * Returns the standard deviation of success latency in nanoseconds.
     * 
     * @return standard deviation of success latency, or 0.0 if no statistics available
     */
    public double stdDevSuccessLatencyNanos() {
        return successStats != null ? successStats.stdDev() : 0.0;
    }
    
    /**
     * Checks if statistical summary is available.
     * 
     * @return true if statistics are available, false otherwise
     */
    public boolean hasStatistics() {
        return successStats != null || failureStats != null;
    }
}
