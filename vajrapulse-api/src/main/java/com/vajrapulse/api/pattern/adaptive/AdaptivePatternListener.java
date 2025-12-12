package com.vajrapulse.api.pattern.adaptive;

/**
 * Listener for adaptive load pattern events.
 * 
 * <p>Implementations can be registered with {@link AdaptiveLoadPattern}
 * to receive notifications about significant events during pattern execution.
 * 
 * <p>All methods have default implementations that do nothing, allowing
 * implementations to only override the methods they care about.
 * 
 * <p>Example:
 * <pre>{@code
 * AdaptivePatternListener listener = new AdaptivePatternListener() {
 *     @Override
 *     public void onPhaseTransition(PhaseTransitionEvent event) {
 *         System.out.println("Phase changed: " + event.from() + " -> " + event.to());
 *     }
 * };
 * 
 * AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
 *     .metricsProvider(metrics)
 *     .listener(listener)
 *     .build();
 * }</pre>
 * 
 * <p><strong>Thread Safety:</strong> Listener methods may be called from
 * multiple threads. Implementations must be thread-safe.
 * 
 * @since 0.9.9
 */
public interface AdaptivePatternListener {
    
    /**
     * Called when the pattern transitions between phases.
     * 
     * <p>This is called whenever the pattern moves from one phase
     * (RAMP_UP, RAMP_DOWN, SUSTAIN) to another.
     * 
     * @param event phase transition event
     */
    default void onPhaseTransition(PhaseTransitionEvent event) {
        // Default: do nothing
    }
    
    /**
     * Called when TPS changes significantly.
     * 
     * <p>This is called when TPS changes by more than the configured
     * tolerance, or when explicitly set (e.g., during recovery).
     * 
     * @param event TPS change event
     */
    default void onTpsChange(TpsChangeEvent event) {
        // Default: do nothing
    }
    
    /**
     * Called when a stable TPS point is detected.
     * 
     * <p>This is called when the pattern identifies a stable operating
     * point after the required number of consecutive stable intervals.
     * 
     * @param event stability detection event
     */
    default void onStabilityDetected(StabilityDetectedEvent event) {
        // Default: do nothing
    }
    
    /**
     * Called when the pattern enters recovery mode.
     * 
     * <p>This is called when TPS reaches minimum and the pattern
     * is waiting for conditions to improve before ramping up again.
     * 
     * @param event recovery event
     */
    default void onRecovery(RecoveryEvent event) {
        // Default: do nothing
    }
}

