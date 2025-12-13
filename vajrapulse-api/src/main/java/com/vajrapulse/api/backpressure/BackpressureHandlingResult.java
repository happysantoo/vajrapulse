package com.vajrapulse.api.backpressure;

/**
 * Result of handling a request during backpressure.
 * 
 * <p>This enum indicates what action was taken when backpressure
 * was detected and a handler processed the request.
 * 
 * @see BackpressureHandler
 * @since 0.9.9
 */
public enum BackpressureHandlingResult {
    /** Request was dropped (not executed) */
    DROPPED,
    /** Request was queued (will be executed later) */
    QUEUED,
    /** Request was rejected (failed immediately) */
    REJECTED,
    /** Request was accepted (normal processing) */
    ACCEPTED
}
