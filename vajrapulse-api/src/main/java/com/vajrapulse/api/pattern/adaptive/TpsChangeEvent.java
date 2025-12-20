package com.vajrapulse.api.pattern.adaptive;

/**
 * Event representing a TPS change in an adaptive load pattern.
 * 
 * <p>This event is emitted when TPS changes, providing information about
 * the previous and new TPS values, the current phase, and when the change occurred.
 * 
 * @param previousTps the previous TPS value
 * @param newTps the new TPS value
 * @param phase the current phase when the change occurred
 * @param timestamp when the change occurred (milliseconds since epoch)
 * 
 * @see AdaptivePatternListener
 * @since 0.9.9
 */
public record TpsChangeEvent(
    double previousTps,
    double newTps,
    AdaptivePhase phase,
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
        if (phase == null) {
            throw new IllegalArgumentException("Phase must not be null");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp must be non-negative, got: " + timestamp);
        }
    }
}

