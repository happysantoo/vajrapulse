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
     * Gets the current failure rate as a percentage (0.0 to 100.0).
     * 
     * <p>This method returns the all-time failure rate, which may include
     * historical failures. For recovery decisions, consider using
     * {@link #getRecentFailureRate(int)} instead.
     * 
     * @return failure rate as percentage (0.0-100.0)
     */
    double getFailureRate();
    
    /**
     * Gets the failure rate over a recent time window.
     * 
     * <p>This method calculates the failure rate for the last N seconds,
     * excluding older failures. This is useful for recovery decisions where
     * recent failures matter more than historical failures.
     * 
     * <p>If the provider doesn't support recent window calculation, it may
     * return the all-time failure rate as a fallback.
     * 
     * @param windowSeconds the time window in seconds (e.g., 10)
     * @return failure rate as percentage (0.0-100.0) for the recent window
     * @since 0.9.8
     */
    default double getRecentFailureRate(int windowSeconds) {
        // Default implementation: return all-time rate
        // Providers can override to provide recent window calculation
        return getFailureRate();
    }
    
    /**
     * Gets the total number of executions.
     * 
     * @return total execution count
     */
    long getTotalExecutions();
    
    /**
     * Gets the total number of failed executions.
     * 
     * <p>This method returns the absolute count of failures,
     * which is useful for:
     * <ul>
     *   <li>Alerting on absolute failure thresholds (e.g., "alert if > 100 failures")</li>
     *   <li>Tracking failure trends over time</li>
     *   <li>Debugging and analysis</li>
     * </ul>
     * 
     * @return total failure count
     * @since 0.9.9
     */
    default long getFailureCount() {
        return 0L;
    }
}

