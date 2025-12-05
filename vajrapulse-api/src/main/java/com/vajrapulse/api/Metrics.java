package com.vajrapulse.api;

import java.util.Map;

/**
 * Interface for metrics data used in assertions.
 * 
 * <p>This interface provides access to key metrics needed for assertion evaluation
 * without requiring a dependency on the core module. Implementations in the core
 * module implement this interface to enable assertion evaluation.
 * 
 * <p>All latency values are in nanoseconds.
 * 
 * @since 0.9.7
 */
public interface Metrics {
    
    /**
     * Returns the total number of executions.
     * 
     * @return total executions
     */
    long totalExecutions();
    
    /**
     * Returns the number of successful executions.
     * 
     * @return success count
     */
    long successCount();
    
    /**
     * Returns the number of failed executions.
     * 
     * @return failure count
     */
    long failureCount();
    
    /**
     * Returns the success rate as a percentage (0.0 to 100.0).
     * 
     * @return success rate percentage
     */
    double successRate();
    
    /**
     * Returns the failure rate as a percentage (0.0 to 100.0).
     * 
     * @return failure rate percentage
     */
    double failureRate();
    
    /**
     * Returns the response TPS (transactions per second).
     * 
     * @return response TPS
     */
    double responseTps();
    
    /**
     * Returns the success TPS (successful transactions per second).
     * 
     * @return success TPS
     */
    double successTps();
    
    /**
     * Returns the failure TPS (failed transactions per second).
     * 
     * @return failure TPS
     */
    double failureTps();
    
    /**
     * Returns the success latency percentiles.
     * 
     * <p>Map keys are percentiles (0.0 to 1.0), values are latencies in nanoseconds.
     * 
     * @return map of percentile → latency (nanoseconds)
     */
    Map<Double, Double> successPercentiles();
    
    /**
     * Returns the failure latency percentiles.
     * 
     * <p>Map keys are percentiles (0.0 to 1.0), values are latencies in nanoseconds.
     * 
     * @return map of percentile → latency (nanoseconds)
     */
    Map<Double, Double> failurePercentiles();
}

