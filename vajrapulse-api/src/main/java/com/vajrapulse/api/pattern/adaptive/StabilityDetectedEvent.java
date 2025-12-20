package com.vajrapulse.api.pattern.adaptive;

/**
 * Event representing stability detection in an adaptive load pattern.
 * 
 * <p>This event is emitted when the pattern identifies a stable operating
 * point after the required number of consecutive stable intervals.
 * 
 * @param stableTps the detected stable TPS value
 * @param timestamp when stability was detected (milliseconds since epoch)
 * 
 * @see AdaptivePatternListener
 * @since 0.9.9
 */
public record StabilityDetectedEvent(
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

