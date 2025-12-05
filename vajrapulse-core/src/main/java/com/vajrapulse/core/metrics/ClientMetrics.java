package com.vajrapulse.core.metrics;

/**
 * Client-side metrics for connection pools, queues, and client-side bottlenecks.
 * 
 * <p>These metrics help identify client-side issues that may not be reflected
 * in server-side response metrics. For example, connection pool exhaustion
 * or queue saturation can cause performance degradation even when the server
 * is responding normally.
 * 
 * <p>All time values are in nanoseconds.
 * 
 * @param activeConnections number of active connections in the pool
 * @param idleConnections number of idle connections in the pool
 * @param waitingConnections number of threads waiting for a connection
 * @param queueDepth current depth of the request queue
 * @param queueWaitTimeNanos total time spent waiting in queue (nanoseconds)
 * @param connectionTimeouts number of connection timeout errors
 * @param requestTimeouts number of request timeout errors
 * @param connectionRefused number of connection refused errors
 * @since 0.9.7
 */
public record ClientMetrics(
    long activeConnections,
    long idleConnections,
    long waitingConnections,
    long queueDepth,
    long queueWaitTimeNanos,
    long connectionTimeouts,
    long requestTimeouts,
    long connectionRefused
) {
    /**
     * Default constructor with all values set to zero.
     */
    public ClientMetrics() {
        this(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }
    
    /**
     * Creates ClientMetrics with only connection pool metrics.
     * 
     * @param activeConnections number of active connections
     * @param idleConnections number of idle connections
     * @param waitingConnections number of threads waiting for connection
     * @return ClientMetrics instance
     */
    public static ClientMetrics connectionPool(long activeConnections, long idleConnections, long waitingConnections) {
        return new ClientMetrics(activeConnections, idleConnections, waitingConnections, 0L, 0L, 0L, 0L, 0L);
    }
    
    /**
     * Creates ClientMetrics with only queue metrics.
     * 
     * @param queueDepth current queue depth
     * @param queueWaitTimeNanos total queue wait time in nanoseconds
     * @return ClientMetrics instance
     */
    public static ClientMetrics queue(long queueDepth, long queueWaitTimeNanos) {
        return new ClientMetrics(0L, 0L, 0L, queueDepth, queueWaitTimeNanos, 0L, 0L, 0L);
    }
    
    /**
     * Creates ClientMetrics with only timeout metrics.
     * 
     * @param connectionTimeouts number of connection timeouts
     * @param requestTimeouts number of request timeouts
     * @return ClientMetrics instance
     */
    public static ClientMetrics timeouts(long connectionTimeouts, long requestTimeouts) {
        return new ClientMetrics(0L, 0L, 0L, 0L, 0L, connectionTimeouts, requestTimeouts, 0L);
    }
    
    /**
     * Calculates total connections (active + idle).
     * 
     * @return total connections
     */
    public long totalConnections() {
        return activeConnections + idleConnections;
    }
    
    /**
     * Calculates connection pool utilization (active / total).
     * 
     * @return utilization ratio (0.0 to 1.0), or 0.0 if no connections
     */
    public double connectionPoolUtilization() {
        long total = totalConnections();
        if (total == 0) {
            return 0.0;
        }
        return (double) activeConnections / total;
    }
    
    /**
     * Calculates average queue wait time in milliseconds.
     * 
     * @return average wait time in milliseconds, or 0.0 if no queue operations
     */
    public double averageQueueWaitTimeMs() {
        // Note: This is a simple calculation. In practice, you'd need
        // to track the number of queue operations separately.
        // For now, this returns the total wait time converted to milliseconds.
        return queueWaitTimeNanos / 1_000_000.0;
    }
}

