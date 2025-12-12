package com.vajrapulse.api.pattern.adaptive;

/**
 * Core state of an adaptive load pattern.
 * 
 * <p>This record tracks the essential state information
 * that is always present regardless of phase.
 * 
 * @param phase the current phase
 * @param currentTps the current TPS value
 * @param lastAdjustmentTime timestamp when TPS was last adjusted (millis)
 * @param phaseStartTime timestamp when current phase started (millis)
 * @param rampDownAttempts number of ramp-down attempts
 * @param phaseTransitionCount total number of phase transitions
 * 
 * @see AdaptiveLoadPattern
 * @since 0.9.9
 */
public record AdaptiveCoreState(
    AdaptivePhase phase,
    double currentTps,
    long lastAdjustmentTime,
    long phaseStartTime,
    int rampDownAttempts,
    long phaseTransitionCount
) {
    /**
     * Creates a core state with validation.
     */
    public AdaptiveCoreState {
        if (phase == null) {
            throw new IllegalArgumentException("Phase must not be null");
        }
        if (currentTps < 0) {
            throw new IllegalArgumentException("Current TPS must be non-negative");
        }
        if (rampDownAttempts < 0) {
            throw new IllegalArgumentException("Ramp down attempts must be non-negative");
        }
        if (phaseTransitionCount < 0) {
            throw new IllegalArgumentException("Phase transition count must be non-negative");
        }
    }
}

