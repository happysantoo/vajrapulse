package com.vajrapulse.api.backpressure;

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
 *   <li>{@code threshold(double, double, double)} - Multi-level strategy</li>
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
     * @param backpressureLevel the current backpressure level (0.0 to 1.0)
     * @param context additional context (queue depth, connection pool state, etc.)
     * @return handling result indicating what action was taken
     */
    BackpressureHandlingResult handle(double backpressureLevel, BackpressureContext context);
}
