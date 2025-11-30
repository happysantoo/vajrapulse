package com.vajrapulse.core.backpressure;

import com.vajrapulse.api.BackpressureHandler;
import com.vajrapulse.api.BackpressureHandler.HandlingResult;
import com.vajrapulse.api.BackpressureHandler.BackpressureContext;

import java.time.Duration;

/**
 * Factory for built-in backpressure handlers.
 * 
 * <p>Provides common strategies for handling requests during backpressure:
 * <ul>
 *   <li>{@link #DROP} - Skip requests silently</li>
 *   <li>{@link #QUEUE} - Buffer requests (default)</li>
 *   <li>{@link #REJECT} - Fail fast with error</li>
 *   <li>{@link #retry(Duration, int)} - Retry after delay</li>
 *   <li>{@link #DEGRADE} - Reduce request quality</li>
 *   <li>{@link #threshold(double, double, double)} - Multi-level strategy</li>
 * </ul>
 * 
 * @since 0.9.6
 */
public final class BackpressureHandlers {
    
    private BackpressureHandlers() {
        // Utility class
    }
    
    /**
     * DROP handler: Silently skips requests when backpressure is detected.
     * 
     * <p>Use when:
     * <ul>
     *   <li>Test accuracy is more important than request completion</li>
     *   <li>You want to measure system capacity, not overload behavior</li>
     *   <li>Dropped requests should not be counted as failures</li>
     * </ul>
     */
    public static final BackpressureHandler DROP = new BackpressureHandler() {
        @Override
        public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
            return HandlingResult.DROPPED;
        }
    };
    
    /**
     * QUEUE handler: Buffers requests when backpressure is detected (default behavior).
     * 
     * <p>Use when:
     * <ul>
     *   <li>You want to test system behavior under sustained load</li>
     *   <li>All requests should eventually be processed</li>
     *   <li>Queue depth is acceptable</li>
     * </ul>
     */
    public static final BackpressureHandler QUEUE = new BackpressureHandler() {
        @Override
        public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
            return HandlingResult.QUEUED;
        }
    };
    
    /**
     * REJECT handler: Fails requests immediately when backpressure is detected.
     * 
     * <p>Use when:
     * <ul>
     *   <li>You want to simulate "fail fast" behavior</li>
     *   <li>You want to test client-side error handling</li>
     *   <li>You want to prevent queue buildup</li>
     * </ul>
     */
    public static final BackpressureHandler REJECT = new BackpressureHandler() {
        @Override
        public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
            return HandlingResult.REJECTED;
        }
    };
    
    /**
     * DEGRADE handler: Reduces request quality when backpressure is detected.
     * 
     * <p>Use when:
     * <ul>
     *   <li>You want to test graceful degradation</li>
     *   <li>You want to maintain throughput at reduced quality</li>
     *   <li>You want to test adaptive quality mechanisms</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> Degradation logic must be implemented in the task itself.
     * This handler only marks requests for degraded processing.
     */
    public static final BackpressureHandler DEGRADE = new BackpressureHandler() {
        @Override
        public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
            return HandlingResult.DEGRADED;
        }
    };
    
    /**
     * Creates a retry handler that retries requests after a delay when backpressure is detected.
     * 
     * <p>Use when:
     * <ul>
     *   <li>You want to test retry logic</li>
     *   <li>Transient backpressure is expected</li>
     *   <li>You want to maximize request completion</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> Retry logic is currently not fully implemented.
     * This handler returns RETRY result, but actual retry must be handled by the caller.
     * 
     * @param retryDelay delay before retry
     * @param maxRetries maximum number of retries
     * @return retry handler
     * @throws IllegalArgumentException if retryDelay is null or negative, or maxRetries &lt; 0
     */
    public static BackpressureHandler retry(Duration retryDelay, int maxRetries) {
        if (retryDelay == null || retryDelay.isNegative()) {
            throw new IllegalArgumentException("Retry delay must not be null or negative");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries must not be negative");
        }
        return new RetryBackpressureHandler(retryDelay, maxRetries);
    }
    
    /**
     * Creates a threshold-based handler that uses different strategies at different backpressure levels.
     * 
     * <p>Strategy selection:
     * <ul>
     *   <li>0.0 - queueThreshold: ACCEPT (normal processing)</li>
     *   <li>queueThreshold - rejectThreshold: QUEUE (buffer requests)</li>
     *   <li>rejectThreshold - dropThreshold: REJECT (fail fast)</li>
     *   <li>dropThreshold - 1.0: DROP (skip requests)</li>
     * </ul>
     * 
     * <p>Example:
     * <pre>{@code
     * BackpressureHandler handler = BackpressureHandlers.threshold(0.5, 0.7, 0.9);
     * // Queue when backpressure >= 50%
     * // Reject when backpressure >= 70%
     * // Drop when backpressure >= 90%
     * }</pre>
     * 
     * @param queueThreshold backpressure level to start queuing (0.0 to 1.0)
     * @param rejectThreshold backpressure level to start rejecting (0.0 to 1.0)
     * @param dropThreshold backpressure level to start dropping (0.0 to 1.0)
     * @return threshold-based handler
     * @throws IllegalArgumentException if thresholds are invalid or not in ascending order
     */
    public static BackpressureHandler threshold(double queueThreshold, double rejectThreshold, double dropThreshold) {
        if (queueThreshold < 0.0 || queueThreshold > 1.0) {
            throw new IllegalArgumentException("Queue threshold must be between 0.0 and 1.0");
        }
        if (rejectThreshold < 0.0 || rejectThreshold > 1.0) {
            throw new IllegalArgumentException("Reject threshold must be between 0.0 and 1.0");
        }
        if (dropThreshold < 0.0 || dropThreshold > 1.0) {
            throw new IllegalArgumentException("Drop threshold must be between 0.0 and 1.0");
        }
        if (queueThreshold >= rejectThreshold || rejectThreshold >= dropThreshold) {
            throw new IllegalArgumentException("Thresholds must be in ascending order: queue < reject < drop");
        }
        return new ThresholdBackpressureHandler(queueThreshold, rejectThreshold, dropThreshold);
    }
    
    /**
     * Retry backpressure handler implementation.
     */
    private static final class RetryBackpressureHandler implements BackpressureHandler {
        private final Duration retryDelay;
        private final int maxRetries;
        
        RetryBackpressureHandler(Duration retryDelay, int maxRetries) {
            this.retryDelay = retryDelay;
            this.maxRetries = maxRetries;
        }
        
        @Override
        public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
            // TODO: Implement retry tracking per iteration
            // For now, return RETRY result
            return HandlingResult.RETRY;
        }
    }
    
    /**
     * Threshold-based backpressure handler implementation.
     */
    private static final class ThresholdBackpressureHandler implements BackpressureHandler {
        private final double queueThreshold;
        private final double rejectThreshold;
        private final double dropThreshold;
        
        ThresholdBackpressureHandler(double queueThreshold, double rejectThreshold, double dropThreshold) {
            this.queueThreshold = queueThreshold;
            this.rejectThreshold = rejectThreshold;
            this.dropThreshold = dropThreshold;
        }
        
        @Override
        public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
            if (backpressureLevel < queueThreshold) {
                return HandlingResult.ACCEPTED;
            } else if (backpressureLevel < rejectThreshold) {
                return HandlingResult.QUEUED;
            } else if (backpressureLevel < dropThreshold) {
                return HandlingResult.REJECTED;
            } else {
                return HandlingResult.DROPPED;
            }
        }
    }
}

