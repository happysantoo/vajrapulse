package com.vajrapulse.api.pattern.adaptive;

/**
 * Snapshot of metrics for decision making.
 * 
 * <p>This record provides a consistent view of metrics at a point in time,
 * used by decision policies to make ramp decisions.
 * 
 * @param failureRate all-time failure rate as ratio (0.0-1.0)
 * @param recentFailureRate recent window failure rate as ratio (0.0-1.0)
 * @param backpressure backpressure level (0.0-1.0)
 * @param totalExecutions total number of executions
 * 
 * @since 0.9.9
 */
public record MetricsSnapshot(
    double failureRate,
    double recentFailureRate,
    double backpressure,
    long totalExecutions
) {
    /**
     * Creates a metrics snapshot with validation.
     * 
     * @param failureRate all-time failure rate as ratio (0.0-1.0)
     * @param recentFailureRate recent window failure rate as ratio (0.0-1.0)
     * @param backpressure backpressure level (0.0-1.0)
     * @param totalExecutions total number of executions
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public MetricsSnapshot {
        if (failureRate < 0.0 || failureRate > 1.0) {
            throw new IllegalArgumentException("Failure rate must be between 0.0 and 1.0, got: " + failureRate);
        }
        if (recentFailureRate < 0.0 || recentFailureRate > 1.0) {
            throw new IllegalArgumentException("Recent failure rate must be between 0.0 and 1.0, got: " + recentFailureRate);
        }
        if (backpressure < 0.0 || backpressure > 1.0) {
            throw new IllegalArgumentException("Backpressure must be between 0.0 and 1.0, got: " + backpressure);
        }
        if (totalExecutions < 0) {
            throw new IllegalArgumentException("Total executions must be non-negative, got: " + totalExecutions);
        }
    }
}

