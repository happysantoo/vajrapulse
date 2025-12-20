package com.vajrapulse.api.pattern.adaptive;

/**
 * Unified state for adaptive load pattern.
 * 
 * <p>All state is contained in a single record for simplicity.
 * This replaces the previous nested state model (AdaptiveState,
 * AdaptiveCoreState, AdaptiveStabilityTracking, AdaptiveRecoveryTracking).
 * 
 * @param phase the current phase
 * @param currentTps the current TPS value
 * @param lastAdjustmentTime timestamp when TPS was last adjusted (millis)
 * @param phaseStartTime timestamp when current phase started (millis)
 * @param stableTps the found stable TPS (-1 if not found)
 * @param stableIntervalsCount count of consecutive stable intervals
 * @param lastKnownGoodTps the highest TPS achieved before problems
 * @param inRecovery true if at minimum TPS waiting for recovery
 * @param phaseTransitionCount total number of phase transitions
 * 
 * @see AdaptiveLoadPattern
 * @since 0.9.9
 */
public record AdaptiveState(
    AdaptivePhase phase,
    double currentTps,
    long lastAdjustmentTime,
    long phaseStartTime,
    double stableTps,
    int stableIntervalsCount,
    double lastKnownGoodTps,
    boolean inRecovery,
    long phaseTransitionCount
) {
    /**
     * Creates an adaptive state with validation.
     */
    public AdaptiveState {
        if (phase == null) {
            throw new IllegalArgumentException("Phase must not be null");
        }
        if (currentTps < 0) {
            throw new IllegalArgumentException("Current TPS must be non-negative, got: " + currentTps);
        }
        if (stableTps < -1) {
            throw new IllegalArgumentException("Stable TPS must be >= -1, got: " + stableTps);
        }
        if (stableIntervalsCount < 0) {
            throw new IllegalArgumentException("Stable intervals count must be non-negative, got: " + stableIntervalsCount);
        }
        if (lastKnownGoodTps < 0) {
            throw new IllegalArgumentException("Last known good TPS must be non-negative, got: " + lastKnownGoodTps);
        }
        if (phaseTransitionCount < 0) {
            throw new IllegalArgumentException("Phase transition count must be non-negative, got: " + phaseTransitionCount);
        }
    }
    
    /**
     * Checks if a stable TPS has been found.
     * 
     * @return true if stable TPS found
     */
    public boolean hasStableTps() {
        return stableTps >= 0;
    }
    
    /**
     * Checks if currently tracking stability.
     * 
     * @return true if tracking stability (stableIntervalsCount > 0)
     */
    public boolean isTrackingStability() {
        return stableIntervalsCount > 0;
    }
}
