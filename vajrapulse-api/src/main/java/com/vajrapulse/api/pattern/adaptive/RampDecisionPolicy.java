package com.vajrapulse.api.pattern.adaptive;

/**
 * Policy for making ramp decisions in adaptive load patterns.
 * 
 * <p>This interface encapsulates the decision logic for when to
 * ramp up, ramp down, or sustain TPS based on metrics.
 * 
 * <p>Implementations should be thread-safe as they may be called
 * concurrently from multiple threads.
 * 
 * @since 0.9.9
 */
public interface RampDecisionPolicy {
    
    /**
     * Determines if TPS should be ramped up.
     * 
     * <p>Typically returns true when:
     * <ul>
     *   <li>Error rate is below threshold</li>
     *   <li>Backpressure is low</li>
     * </ul>
     * 
     * @param metrics current metrics snapshot
     * @return true if should ramp up
     */
    boolean shouldRampUp(MetricsSnapshot metrics);
    
    /**
     * Determines if TPS should be ramped down.
     * 
     * <p>Typically returns true when:
     * <ul>
     *   <li>Error rate exceeds threshold, OR</li>
     *   <li>Backpressure is high</li>
     * </ul>
     * 
     * @param metrics current metrics snapshot
     * @return true if should ramp down
     */
    boolean shouldRampDown(MetricsSnapshot metrics);
    
    /**
     * Determines if TPS should be sustained.
     * 
     * <p>Typically returns true when:
     * <ul>
     *   <li>Stable point found (stable intervals count >= required)</li>
     *   <li>Max TPS reached</li>
     *   <li>Intermediate stability detected</li>
     * </ul>
     * 
     * @param metrics current metrics snapshot
     * @param stability current stability tracking
     * @return true if should sustain
     */
    boolean shouldSustain(MetricsSnapshot metrics, AdaptiveStabilityTracking stability);
    
    /**
     * Determines if recovery from minimum TPS is possible.
     * 
     * <p>This method is used when TPS has reached minimum to determine
     * if conditions have improved enough to start ramping up again.
     * 
     * <p>Typically returns true when:
     * <ul>
     *   <li>Backpressure is low, OR</li>
     *   <li>Recent error rate is low AND backpressure is moderate</li>
     * </ul>
     * 
     * @param metrics current metrics snapshot (including recent failure rate)
     * @return true if can recover from minimum TPS
     */
    boolean canRecoverFromMinimum(MetricsSnapshot metrics);
}

