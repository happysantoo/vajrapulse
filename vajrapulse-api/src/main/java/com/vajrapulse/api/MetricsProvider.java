package com.vajrapulse.api;

/**
 * Provides metrics feedback for adaptive load patterns.
 * 
 * <p>This interface allows load patterns to query execution metrics
 * without creating a dependency on the metrics implementation.
 * 
 * <p>Implementations should provide thread-safe access to metrics.
 * 
 * @since 0.9.5
 */
public interface MetricsProvider {
    
    /**
     * Gets the current failure rate as a percentage.
     * 
     * @return failure rate (0.0 to 100.0)
     */
    double getFailureRate();
    
    /**
     * Gets the total number of executions.
     * 
     * @return total execution count
     */
    long getTotalExecutions();
}

