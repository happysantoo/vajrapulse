package com.vajrapulse.api.pattern.adaptive;

/**
 * Event representing a significant TPS change in an adaptive load pattern.
 * 
 * <p>This event is emitted when TPS changes by more than the configured
 * tolerance, or when explicitly set (e.g., during recovery).
 * 
 * @param previousTps the previous TPS value
 * @param newTps the new TPS value
 * @param timestamp when the change occurred (milliseconds since epoch)
 * 
 * @see AdaptivePatternListener
 * @since 0.9.9
 */
public record TpsChangeEvent(
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

