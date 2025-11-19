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
 * @param elapsedMillis time elapsed since metrics collection started
 * @param queueSize current number of pending executions in queue
 * @param queueWaitPercentiles map of percentile→wait time nanos for queue wait
 */
public record AggregatedMetrics(
    long totalExecutions,
    long successCount,
    long failureCount,
    java.util.Map<Double, Double> successPercentiles,
    java.util.Map<Double, Double> failurePercentiles,
    long elapsedMillis,
    long queueSize,
    java.util.Map<Double, Double> queueWaitPercentiles
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
    
    /**
     * Calculates actual response TPS (total responses per second).
     * 
     * @return total response TPS
     */
    public double responseTps() {
        if (elapsedMillis == 0) {
            return 0.0;
        }
        return (totalExecutions * 1000.0) / elapsedMillis;
    }
    
    /**
     * Calculates successful response TPS.
     * 
     * @return successful response TPS
     */
    public double successTps() {
        if (elapsedMillis == 0) {
            return 0.0;
        }
        return (successCount * 1000.0) / elapsedMillis;
    }
    
    /**
     * Calculates failed response TPS.
     * 
     * @return failed response TPS
     */
    public double failureTps() {
        if (elapsedMillis == 0) {
            return 0.0;
        }
        return (failureCount * 1000.0) / elapsedMillis;
    }
}
