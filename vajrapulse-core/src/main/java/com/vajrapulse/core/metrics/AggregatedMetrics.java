package com.vajrapulse.core.metrics;

/**
 * Aggregated metrics snapshot at a point in time.
 * 
 * <p>All latency values are in nanoseconds.
 * 
 * @param totalExecutions total number of executions
 * @param successCount number of successful executions
 * @param failureCount number of failed executions
 * @param successPercentiles map of percentile→latency nanos for successes
 * @param failurePercentiles map of percentile→latency nanos for failures
 */
public record AggregatedMetrics(
    long totalExecutions,
    long successCount,
    long failureCount,
    java.util.Map<Double, Double> successPercentiles,
    java.util.Map<Double, Double> failurePercentiles
) {
    /**
     * Calculates the success rate as a percentage.
     * 
     * @return success rate (0.0 to 100.0)
     */
    public double successRate() {
        if (totalExecutions == 0) {
            return 0.0;
        }
        return (successCount * 100.0) / totalExecutions;
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
        return (failureCount * 100.0) / totalExecutions;
    }
}
