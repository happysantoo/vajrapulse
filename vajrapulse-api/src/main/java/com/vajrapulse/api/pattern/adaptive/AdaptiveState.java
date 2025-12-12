package com.vajrapulse.api.pattern.adaptive;

/**
 * Complete state of an adaptive load pattern.
 * 
 * <p>This record composes core state with optional tracking
 * for stability and recovery.
 * 
 * @param core the core state (always present)
 * @param stability stability tracking (null if not tracking)
 * @param recovery recovery tracking (null if not in recovery)
 * 
 * @see AdaptiveLoadPattern
 * @since 0.9.9
 */
public record AdaptiveState(
    AdaptiveCoreState core,
    AdaptiveStabilityTracking stability,
    AdaptiveRecoveryTracking recovery
) {
    /**
     * Creates an adaptive state with validation.
     */
    public AdaptiveState {
        if (core == null) {
            throw new IllegalArgumentException("Core state must not be null");
        }
    }
    
    // Helper methods for backward compatibility and easier access
    /**
     * Gets the current phase.
     * 
     * @return current phase
     */
    public AdaptivePhase phase() {
        return core.phase();
    }
    
    /**
     * Gets the current TPS.
     * 
     * @return current TPS
     */
    public double currentTps() {
        return core.currentTps();
    }
    
    /**
     * Gets the last adjustment time.
     * 
     * @return last adjustment time
     */
    public long lastAdjustmentTime() {
        return core.lastAdjustmentTime();
    }
    
    /**
     * Gets the phase start time.
     * 
     * @return phase start time
     */
    public long phaseStartTime() {
        return core.phaseStartTime();
    }
    
    /**
     * Gets the stable TPS if found.
     * 
     * @return stable TPS or -1 if not found
     */
    public double stableTps() {
        return stability != null && stability.hasStableTps() ? stability.stableTps() : -1;
    }
    
    /**
     * Gets the last known good TPS.
     * 
     * @return last known good TPS or 0 if not in recovery
     */
    public double lastKnownGoodTps() {
        return recovery != null ? recovery.lastKnownGoodTps() : 0;
    }
    
    /**
     * Gets the stable intervals count.
     * 
     * @return stable intervals count
     */
    public int stableIntervalsCount() {
        return stability != null ? stability.stableIntervalsCount() : 0;
    }
    
    /**
     * Gets the stable TPS candidate if tracking.
     * 
     * @return candidate TPS or -1 if not tracking
     */
    public double stableTpsCandidate() {
        return stability != null && stability.isTracking() ? stability.candidateTps() : -1;
    }
    
    /**
     * Gets the stability start time if tracking.
     * 
     * @return stability start time or -1 if not tracking
     */
    public long stabilityStartTime() {
        return stability != null && stability.isTracking() ? stability.candidateStartTime() : -1;
    }
    
    /**
     * Gets the ramp down attempts count.
     * 
     * @return ramp down attempts
     */
    public int rampDownAttempts() {
        return core.rampDownAttempts();
    }
    
    /**
     * Gets the phase transition count.
     * 
     * @return phase transition count
     */
    public long phaseTransitionCount() {
        return core.phaseTransitionCount();
    }
    
    // Builder-style methods for state updates
    /**
     * Creates a new state with updated core state.
     * 
     * @param newCore new core state
     * @return new state with updated core
     */
    public AdaptiveState withCore(AdaptiveCoreState newCore) {
        return new AdaptiveState(newCore, stability, recovery);
    }
    
    /**
     * Creates a new state with updated stability tracking.
     * 
     * @param newStability new stability tracking
     * @return new state with updated stability
     */
    public AdaptiveState withStability(AdaptiveStabilityTracking newStability) {
        return new AdaptiveState(core, newStability, recovery);
    }
    
    /**
     * Creates a new state with updated recovery tracking.
     * 
     * @param newRecovery new recovery tracking
     * @return new state with updated recovery
     */
    public AdaptiveState withRecovery(AdaptiveRecoveryTracking newRecovery) {
        return new AdaptiveState(core, stability, newRecovery);
    }
    
    /**
     * Creates a new state with updated last known good TPS.
     * 
     * <p>The TPS is only updated if it's higher than the current value.
     * 
     * @param tps the TPS to set as last known good (only if higher than current)
     * @return new state with updated lastKnownGoodTps
     */
    public AdaptiveState withLastKnownGoodTps(double tps) {
        AdaptiveRecoveryTracking newRecovery = recovery != null 
            ? new AdaptiveRecoveryTracking(Math.max(recovery.lastKnownGoodTps(), tps), recovery.recoveryStartTime())
            : new AdaptiveRecoveryTracking(tps, -1);
        return new AdaptiveState(core, stability, newRecovery);
    }
}

