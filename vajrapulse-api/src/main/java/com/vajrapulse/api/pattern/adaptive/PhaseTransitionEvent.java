package com.vajrapulse.api.pattern.adaptive;

/**
 * Event representing a phase transition in an adaptive load pattern.
 * 
 * <p>This event is emitted when the pattern transitions between phases
 * (RAMP_UP, RAMP_DOWN, SUSTAIN).
 * 
 * @param from the previous phase
 * @param to the new phase
 * @param tps the TPS at the time of transition
 * @param timestamp when the transition occurred (milliseconds since epoch)
 * 
 * @see AdaptivePatternListener
 * @since 0.9.9
 */
public record PhaseTransitionEvent(
    AdaptivePhase from,
    AdaptivePhase to,
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

