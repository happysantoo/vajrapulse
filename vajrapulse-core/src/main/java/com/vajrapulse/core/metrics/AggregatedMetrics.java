package com.vajrapulse.core.metrics;

/**
 * Aggregated metrics snapshot at a point in time.
 * 
 * <p>All latency values are in nanoseconds.
 * 
 * @param totalExecutions total number of executions
 * @param successCount number of successful executions
 * @param failureCount number of failed executions
 * @param successP50 50th percentile latency for successes (nanos)
 * @param successP95 95th percentile latency for successes (nanos)
 * @param successP99 99th percentile latency for successes (nanos)
 * @param failureP50 50th percentile latency for failures (nanos)
 * @param failureP95 95th percentile latency for failures (nanos)
 * @param failureP99 99th percentile latency for failures (nanos)
 */
public record AggregatedMetrics(
    long totalExecutions,
    long successCount,
    long failureCount,
    double successP50,
    double successP95,
    double successP99,
    double failureP50,
    double failureP95,
    double failureP99
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
