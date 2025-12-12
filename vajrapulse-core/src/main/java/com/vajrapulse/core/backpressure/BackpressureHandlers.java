package com.vajrapulse.core.backpressure;

import com.vajrapulse.api.metrics.BackpressureHandler;
import com.vajrapulse.api.metrics.BackpressureHandlingResult;
import com.vajrapulse.api.metrics.BackpressureContext;

import java.time.Duration;

/**
 * Factory for built-in backpressure handlers.
 * 
 * <p>Provides common strategies for handling requests during backpressure:
 * <ul>
 *   <li>{@link #DROP} - Skip requests silently</li>
 *   <li>{@link #QUEUE} - Buffer requests (default)</li>
 *   <li>{@link #REJECT} - Fail fast with error</li>
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
        public BackpressureHandlingResult handle(double backpressureLevel, BackpressureContext context) {
            return BackpressureHandlingResult.DROPPED;
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
        public BackpressureHandlingResult handle(double backpressureLevel, BackpressureContext context) {
            return BackpressureHandlingResult.QUEUED;
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
        public BackpressureHandlingResult handle(double backpressureLevel, BackpressureContext context) {
            return BackpressureHandlingResult.REJECTED;
        }
    };
    
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
        public BackpressureHandlingResult handle(double backpressureLevel, BackpressureContext context) {
            if (backpressureLevel < queueThreshold) {
                return BackpressureHandlingResult.ACCEPTED;
            } else if (backpressureLevel < rejectThreshold) {
                return BackpressureHandlingResult.QUEUED;
            } else if (backpressureLevel < dropThreshold) {
                return BackpressureHandlingResult.REJECTED;
            } else {
                return BackpressureHandlingResult.DROPPED;
            }
        }
    }
}

