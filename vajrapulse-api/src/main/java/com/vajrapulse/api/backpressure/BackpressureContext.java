package com.vajrapulse.api.backpressure;

import java.util.Map;

/**
 * Context information available during backpressure handling.
 * 
 * <p>This record provides detailed information about the current
 * system state when backpressure is detected, allowing handlers
 * to make informed decisions about how to process requests.
 * 
 * @param queueDepth current queue depth
 * @param maxQueueDepth maximum queue depth (0 if unbounded)
 * @param activeConnections active connections (0 if not applicable)
 * @param maxConnections maximum connections (0 if not applicable)
 * @param errorRate current error rate (0.0 to 100.0)
 * @param customMetrics custom metrics map (may be empty)
 * 
 * @see BackpressureHandler
 * @since 0.9.9
 */
public record BackpressureContext(
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
