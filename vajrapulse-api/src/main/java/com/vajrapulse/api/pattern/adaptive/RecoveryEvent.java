package com.vajrapulse.api.pattern.adaptive;

/**
 * Event representing recovery mode entry in an adaptive load pattern.
 * 
 * <p>This event is emitted when TPS reaches minimum and the pattern
 * is waiting for conditions to improve before ramping up again.
 * 
 * @param lastKnownGoodTps the last known good TPS before recovery
 * @param recoveryTps the TPS used for recovery (typically 50% of last known good)
 * @param timestamp when recovery started (milliseconds since epoch)
 * 
 * @see AdaptivePatternListener
 * @since 0.9.9
 */
public record RecoveryEvent(
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

