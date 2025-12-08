package com.vajrapulse.api;

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
    
    /**
     * Event representing a phase transition.
     * 
     * @param from the previous phase
     * @param to the new phase
     * @param tps the TPS at the time of transition
     * @param timestamp when the transition occurred (milliseconds since epoch)
     * 
     * @since 0.9.9
     */
    record PhaseTransitionEvent(
        AdaptiveLoadPattern.Phase from,
        AdaptiveLoadPattern.Phase to,
        double tps,
        long timestamp
    ) {
        /**
         * Creates a phase transition event with validation.
         */
        public PhaseTransitionEvent {
            if (from == null) {
                throw new IllegalArgumentException("From phase must not be null");
            }
            if (to == null) {
                throw new IllegalArgumentException("To phase must not be null");
            }
            if (tps < 0) {
                throw new IllegalArgumentException("TPS must be non-negative, got: " + tps);
            }
            if (timestamp < 0) {
                throw new IllegalArgumentException("Timestamp must be non-negative, got: " + timestamp);
            }
        }
    }
    
    /**
     * Event representing a significant TPS change.
     * 
     * @param previousTps the previous TPS value
     * @param newTps the new TPS value
     * @param timestamp when the change occurred (milliseconds since epoch)
     * 
     * @since 0.9.9
     */
    record TpsChangeEvent(
        double previousTps,
        double newTps,
        long timestamp
    ) {
        /**
         * Creates a TPS change event with validation.
         */
        public TpsChangeEvent {
            if (previousTps < 0) {
                throw new IllegalArgumentException("Previous TPS must be non-negative, got: " + previousTps);
            }
            if (newTps < 0) {
                throw new IllegalArgumentException("New TPS must be non-negative, got: " + newTps);
            }
            if (timestamp < 0) {
                throw new IllegalArgumentException("Timestamp must be non-negative, got: " + timestamp);
            }
        }
    }
    
    /**
     * Event representing stability detection.
     * 
     * @param stableTps the detected stable TPS value
     * @param timestamp when stability was detected (milliseconds since epoch)
     * 
     * @since 0.9.9
     */
    record StabilityDetectedEvent(
        double stableTps,
        long timestamp
    ) {
        /**
         * Creates a stability detection event with validation.
         */
        public StabilityDetectedEvent {
            if (stableTps < 0) {
                throw new IllegalArgumentException("Stable TPS must be non-negative, got: " + stableTps);
            }
            if (timestamp < 0) {
                throw new IllegalArgumentException("Timestamp must be non-negative, got: " + timestamp);
            }
        }
    }
    
    /**
     * Event representing recovery mode entry.
     * 
     * @param lastKnownGoodTps the last known good TPS before recovery
     * @param recoveryTps the TPS used for recovery (typically 50% of last known good)
     * @param timestamp when recovery started (milliseconds since epoch)
     * 
     * @since 0.9.9
     */
    record RecoveryEvent(
        double lastKnownGoodTps,
        double recoveryTps,
        long timestamp
    ) {
        /**
         * Creates a recovery event with validation.
         */
        public RecoveryEvent {
            if (lastKnownGoodTps < 0) {
                throw new IllegalArgumentException("Last known good TPS must be non-negative, got: " + lastKnownGoodTps);
            }
            if (recoveryTps < 0) {
                throw new IllegalArgumentException("Recovery TPS must be non-negative, got: " + recoveryTps);
            }
            if (timestamp < 0) {
                throw new IllegalArgumentException("Timestamp must be non-negative, got: " + timestamp);
            }
        }
    }
}

