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
 * @param queueOperationCount total number of queue operations (for calculating average wait time)
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
    long queueOperationCount,
    long connectionTimeouts,
    long requestTimeouts,
    long connectionRefused
) {
    /**
     * Default constructor with all values set to zero.
     */
    public ClientMetrics() {
        this(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
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
        return new ClientMetrics(activeConnections, idleConnections, waitingConnections, 0L, 0L, 0L, 0L, 0L, 0L);
    }
    
    /**
     * Creates ClientMetrics with only queue metrics.
     * 
     * @param queueDepth current queue depth
     * @param queueWaitTimeNanos total queue wait time in nanoseconds
     * @param queueOperationCount total number of queue operations
     * @return ClientMetrics instance
     */
    public static ClientMetrics queue(long queueDepth, long queueWaitTimeNanos, long queueOperationCount) {
        return new ClientMetrics(0L, 0L, 0L, queueDepth, queueWaitTimeNanos, queueOperationCount, 0L, 0L, 0L);
    }
    
    /**
     * Creates ClientMetrics with only timeout metrics.
     * 
     * @param connectionTimeouts number of connection timeouts
     * @param requestTimeouts number of request timeouts
     * @return ClientMetrics instance
     */
    public static ClientMetrics timeouts(long connectionTimeouts, long requestTimeouts) {
        return new ClientMetrics(0L, 0L, 0L, 0L, 0L, 0L, connectionTimeouts, requestTimeouts, 0L);
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
     * <p>The average is calculated as total wait time divided by the number of operations.
     * If no operations have been recorded, returns 0.0.
     * 
     * @return average wait time in milliseconds, or 0.0 if no queue operations
     */
    public double averageQueueWaitTimeMs() {
        if (queueOperationCount == 0) {
            return 0.0;
        }
        return (queueWaitTimeNanos / (double) queueOperationCount) / 1_000_000.0;
    }
    
    /**
     * Builder for creating ClientMetrics instances with a fluent API.
     * 
     * <p>Example usage:
     * <pre>{@code
     * ClientMetrics metrics = ClientMetrics.builder()
     *     .activeConnections(10)
     *     .idleConnections(5)
     *     .waitingConnections(2)
     *     .queueDepth(3)
     *     .queueWaitTimeNanos(50_000_000L)
     *     .queueOperationCount(100L)
     *     .connectionTimeouts(1)
     *     .requestTimeouts(2)
     *     .connectionRefused(0)
     *     .build();
     * }</pre>
     * 
     * @since 0.9.7
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for ClientMetrics.
     * 
     * @since 0.9.7
     */
    public static final class Builder {
        private long activeConnections = 0L;
        private long idleConnections = 0L;
        private long waitingConnections = 0L;
        private long queueDepth = 0L;
        private long queueWaitTimeNanos = 0L;
        private long queueOperationCount = 0L;
        private long connectionTimeouts = 0L;
        private long requestTimeouts = 0L;
        private long connectionRefused = 0L;
        
        private Builder() {
            // Private constructor
        }
        
        /**
         * Sets the number of active connections.
         * 
         * @param activeConnections number of active connections
         * @return this builder
         */
        public Builder activeConnections(long activeConnections) {
            this.activeConnections = activeConnections;
            return this;
        }
        
        /**
         * Sets the number of idle connections.
         * 
         * @param idleConnections number of idle connections
         * @return this builder
         */
        public Builder idleConnections(long idleConnections) {
            this.idleConnections = idleConnections;
            return this;
        }
        
        /**
         * Sets the number of waiting connections.
         * 
         * @param waitingConnections number of waiting connections
         * @return this builder
         */
        public Builder waitingConnections(long waitingConnections) {
            this.waitingConnections = waitingConnections;
            return this;
        }
        
        /**
         * Sets the queue depth.
         * 
         * @param queueDepth queue depth
         * @return this builder
         */
        public Builder queueDepth(long queueDepth) {
            this.queueDepth = queueDepth;
            return this;
        }
        
        /**
         * Sets the total queue wait time in nanoseconds.
         * 
         * @param queueWaitTimeNanos total queue wait time in nanoseconds
         * @return this builder
         */
        public Builder queueWaitTimeNanos(long queueWaitTimeNanos) {
            this.queueWaitTimeNanos = queueWaitTimeNanos;
            return this;
        }
        
        /**
         * Sets the queue operation count.
         * 
         * @param queueOperationCount queue operation count
         * @return this builder
         */
        public Builder queueOperationCount(long queueOperationCount) {
            this.queueOperationCount = queueOperationCount;
            return this;
        }
        
        /**
         * Sets the number of connection timeouts.
         * 
         * @param connectionTimeouts number of connection timeouts
         * @return this builder
         */
        public Builder connectionTimeouts(long connectionTimeouts) {
            this.connectionTimeouts = connectionTimeouts;
            return this;
        }
        
        /**
         * Sets the number of request timeouts.
         * 
         * @param requestTimeouts number of request timeouts
         * @return this builder
         */
        public Builder requestTimeouts(long requestTimeouts) {
            this.requestTimeouts = requestTimeouts;
            return this;
        }
        
        /**
         * Sets the number of connection refused errors.
         * 
         * @param connectionRefused number of connection refused errors
         * @return this builder
         */
        public Builder connectionRefused(long connectionRefused) {
            this.connectionRefused = connectionRefused;
            return this;
        }
        
        /**
         * Builds the ClientMetrics instance.
         * 
         * @return ClientMetrics instance
         */
        public ClientMetrics build() {
            return new ClientMetrics(
                activeConnections,
                idleConnections,
                waitingConnections,
                queueDepth,
                queueWaitTimeNanos,
                queueOperationCount,
                connectionTimeouts,
                requestTimeouts,
                connectionRefused
            );
        }
    }
}

