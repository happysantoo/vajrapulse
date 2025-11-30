package com.vajrapulse.api;

import java.util.Map;

/**
 * Defines how to handle requests when backpressure is detected.
 * 
 * <p>When backpressure is detected (via BackpressureProvider), the framework
 * needs to decide what to do with requests that cannot be processed immediately.
 * This interface provides extensible strategies for handling request loss.
 * 
 * <p>Built-in strategies are available via {@code BackpressureHandlers} factory class:
 * <ul>
 *   <li>{@code DROP} - Skip requests silently</li>
 *   <li>{@code QUEUE} - Buffer requests (default)</li>
 *   <li>{@code REJECT} - Fail fast with error</li>
 *   <li>{@code RETRY} - Retry after delay</li>
 *   <li>{@code DEGRADE} - Reduce request quality</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> Implementations must be thread-safe
 * as this interface may be called from multiple threads concurrently.
 * 
 * @since 0.9.6
 */
public interface BackpressureHandler {
    /**
     * Handles a request when backpressure is detected.
     * 
     * <p>This method is called when:
     * <ul>
     *   <li>Backpressure level exceeds threshold (configurable, default 0.7)</li>
     *   <li>Executor queue is full</li>
     *   <li>Connection pool is exhausted</li>
     *   <li>Other backpressure conditions are met</li>
     * </ul>
     * 
     * @param iteration the iteration number
     * @param backpressureLevel the current backpressure level (0.0 to 1.0)
     * @param context additional context (queue depth, connection pool state, etc.)
     * @return handling result indicating what action was taken
     */
    HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context);
    
    /**
     * Result of handling a request during backpressure.
     * 
     * @since 0.9.6
     */
    enum HandlingResult {
        /** Request was dropped (not executed) */
        DROPPED,
        /** Request was queued (will be executed later) */
        QUEUED,
        /** Request was rejected (failed immediately) */
        REJECTED,
        /** Request will be retried */
        RETRY,
        /** Request was degraded (reduced quality) */
        DEGRADED,
        /** Request was accepted (normal processing) */
        ACCEPTED
    }
    
    /**
     * Context information available during backpressure handling.
     * 
     * @param queueDepth current queue depth
     * @param maxQueueDepth maximum queue depth (0 if unbounded)
     * @param activeConnections active connections (0 if not applicable)
     * @param maxConnections maximum connections (0 if not applicable)
     * @param errorRate current error rate (0.0 to 100.0)
     * @param customMetrics custom metrics map (may be empty)
     * 
     * @since 0.9.6
     */
    record BackpressureContext(
        long queueDepth,
        long maxQueueDepth,
        long activeConnections,
        long maxConnections,
        double errorRate,
        Map<String, Object> customMetrics
    ) {
        /**
         * Compact constructor to ensure customMetrics is immutable.
         */
        public BackpressureContext {
            // Ensure customMetrics is immutable to avoid SpotBugs EI_EXPOSE_REP
            customMetrics = customMetrics != null ? Map.copyOf(customMetrics) : Map.of();
        }
        
        /**
         * Creates a context with default values.
         * 
         * @param queueDepth current queue depth
         * @param errorRate current error rate
         * @return context with default values
         */
        public static BackpressureContext of(long queueDepth, double errorRate) {
            return new BackpressureContext(
                queueDepth,
                0L,
                0L,
                0L,
                errorRate,
                Map.of() // Already immutable
            );
        }
    }
}

