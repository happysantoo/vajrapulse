package com.vajrapulse.api.pattern.adaptive;

import java.util.Objects;

/**
 * Decision engine for adaptive load patterns.
 * 
 * <p>This class encapsulates all decision logic for adaptive load patterns,
 * making the decision process testable and maintainable. It determines when
 * to ramp up, ramp down, or sustain TPS based on current state and metrics.
 * 
 * <p><strong>Thread Safety:</strong> This class is thread-safe and stateless.
 * All methods are pure functions that take inputs and return decisions.
 * 
 * @since 0.9.10
 */
public final class AdaptiveDecisionEngine {
    
    /**
     * Recovery TPS ratio (hardcoded to 50% of last known good TPS).
     */
    private static final double RECOVERY_TPS_RATIO = 0.5;
    
    private AdaptiveDecisionEngine() {
        // Utility class - prevent instantiation
        throw new AssertionError("AdaptiveDecisionEngine should not be instantiated");
    }
    
    /**
     * Makes an adjustment decision based on current state and metrics.
     * 
     * <p>This is the main entry point for decision making. It delegates
     * to phase-specific decision methods based on the current phase.
     * 
     * @param current current state
     * @param metrics current metrics snapshot
     * @param config configuration
     * @param decisionPolicy decision policy
     * @param elapsedMillis current elapsed time
     * @return adjustment decision
     * @throws NullPointerException if any parameter is null
     */
    public static AdjustmentDecision decide(
            AdaptiveState current,
            MetricsSnapshot metrics,
            AdaptiveConfig config,
            RampDecisionPolicy decisionPolicy,
            long elapsedMillis) {
        
        Objects.requireNonNull(current, "Current state must not be null");
        Objects.requireNonNull(metrics, "Metrics must not be null");
        Objects.requireNonNull(config, "Config must not be null");
        Objects.requireNonNull(decisionPolicy, "Decision policy must not be null");
        
        return switch (current.phase()) {
            case RAMP_UP -> decideRampUp(current, metrics, config, decisionPolicy, elapsedMillis);
            case RAMP_DOWN -> decideRampDown(current, metrics, config, decisionPolicy, elapsedMillis);
            case SUSTAIN -> decideSustain(current, metrics, config, decisionPolicy, elapsedMillis);
        };
    }
    
    /**
     * Decides what to do in RAMP_UP phase.
     * 
     * @param current current state
     * @param metrics current metrics
     * @param config configuration
     * @param decisionPolicy decision policy
     * @param elapsedMillis current elapsed time
     * @return adjustment decision
     */
    private static AdjustmentDecision decideRampUp(
            AdaptiveState current,
            MetricsSnapshot metrics,
            AdaptiveConfig config,
            RampDecisionPolicy decisionPolicy,
            long elapsedMillis) {
        
        // Check for errors/backpressure
        if (decisionPolicy.shouldRampDown(metrics)) {
            return createDecision(AdaptivePhase.RAMP_DOWN, 
                calculateRampDownTps(current.currentTps(), config),
                "Errors/backpressure detected");
        }
        
        // Check if max TPS reached
        AdjustmentDecision maxTpsDecision = checkMaxTpsReached(current.currentTps(), config);
        if (maxTpsDecision != null) {
            return maxTpsDecision;
        }
        
        // Check for stability
        if (isStable(current, metrics, config, decisionPolicy)) {
            return createDecision(AdaptivePhase.SUSTAIN, current.currentTps(), "Stability detected");
        }
        
        // Continue ramping up or hold
        return decideRampUpContinuation(current, metrics, config, decisionPolicy);
    }
    
    /**
     * Decides whether to continue ramping up or hold current TPS.
     * 
     * @param current current state
     * @param metrics current metrics
     * @param config configuration
     * @param decisionPolicy decision policy
     * @return decision to ramp up or hold
     */
    private static AdjustmentDecision decideRampUpContinuation(
            AdaptiveState current,
            MetricsSnapshot metrics,
            AdaptiveConfig config,
            RampDecisionPolicy decisionPolicy) {
        
        if (decisionPolicy.shouldRampUp(metrics)) {
            double newTps = calculateRampUpTps(current.currentTps(), config);
            AdjustmentDecision maxTpsDecision = checkMaxTpsReached(newTps, config);
            if (maxTpsDecision != null) {
                return maxTpsDecision;
            }
            return createDecision(AdaptivePhase.RAMP_UP, newTps, "Conditions good, ramping up");
        }
        
        return createDecision(AdaptivePhase.RAMP_UP, current.currentTps(), "Moderate backpressure, holding");
    }
    
    /**
     * Decides what to do in RAMP_DOWN phase.
     * 
     * @param current current state
     * @param metrics current metrics
     * @param config configuration
     * @param decisionPolicy decision policy
     * @param elapsedMillis current elapsed time
     * @return adjustment decision
     */
    private static AdjustmentDecision decideRampDown(
            AdaptiveState current,
            MetricsSnapshot metrics,
            AdaptiveConfig config,
            RampDecisionPolicy decisionPolicy,
            long elapsedMillis) {
        
        // Check if at minimum (recovery mode)
        if (current.inRecovery()) {
            return decideRecovery(current, metrics, config, decisionPolicy);
        }
        
        // Check for stability when conditions improve
        if (!decisionPolicy.shouldRampDown(metrics)) {
            return decideStabilityDuringRampDown(current, metrics, config, decisionPolicy);
        }
        
        // Continue ramping down
        return createDecision(AdaptivePhase.RAMP_DOWN, 
            calculateRampDownTps(current.currentTps(), config),
            "Errors/backpressure persist, ramping down");
    }
    
    /**
     * Decides recovery action when at minimum TPS.
     * 
     * @param current current state
     * @param metrics current metrics
     * @param config configuration
     * @param decisionPolicy decision policy
     * @return recovery decision
     */
    private static AdjustmentDecision decideRecovery(
            AdaptiveState current,
            MetricsSnapshot metrics,
            AdaptiveConfig config,
            RampDecisionPolicy decisionPolicy) {
        
        if (decisionPolicy.canRecoverFromMinimum(metrics)) {
            return createDecision(AdaptivePhase.RAMP_UP, 
                calculateRecoveryTps(current.lastKnownGoodTps(), config),
                "Recovery: conditions improved");
        }
        return createDecision(AdaptivePhase.RAMP_DOWN, config.minTps(),
            "Recovery: waiting for conditions to improve");
    }
    
    /**
     * Decides action when conditions improve during ramp down.
     * 
     * @param current current state
     * @param metrics current metrics
     * @param config configuration
     * @param decisionPolicy decision policy
     * @return decision (either SUSTAIN if stable, or continue RAMP_DOWN)
     */
    private static AdjustmentDecision decideStabilityDuringRampDown(
            AdaptiveState current,
            MetricsSnapshot metrics,
            AdaptiveConfig config,
            RampDecisionPolicy decisionPolicy) {
        
        if (isStable(current, metrics, config, decisionPolicy)) {
            return createDecision(AdaptivePhase.SUSTAIN, current.currentTps(),
                "Stability detected during ramp down");
        }
        return createDecision(AdaptivePhase.RAMP_DOWN, current.currentTps(),
            "Conditions improved, checking stability");
    }
    
    /**
     * Decides what to do in SUSTAIN phase.
     * 
     * @param current current state
     * @param metrics current metrics
     * @param config configuration
     * @param decisionPolicy decision policy
     * @param elapsedMillis current elapsed time
     * @return adjustment decision
     */
    private static AdjustmentDecision decideSustain(
            AdaptiveState current,
            MetricsSnapshot metrics,
            AdaptiveConfig config,
            RampDecisionPolicy decisionPolicy,
            long elapsedMillis) {
        
        // Check if conditions worsened
        if (decisionPolicy.shouldRampDown(metrics)) {
            return createDecision(AdaptivePhase.RAMP_DOWN, 
                calculateRampDownTps(current.currentTps(), config),
                "Conditions worsened during sustain");
        }
        
        // Check if sustain duration elapsed and can ramp up
        AdjustmentDecision afterSustainDecision = checkAfterSustainDuration(
            current, metrics, config, decisionPolicy, elapsedMillis);
        if (afterSustainDecision != null) {
            return afterSustainDecision;
        }
        
        // Continue sustaining
        return createDecision(AdaptivePhase.SUSTAIN, current.currentTps(), "Continuing to sustain");
    }
    
    /**
     * Checks if sustain duration has elapsed and returns decision to ramp up if conditions allow.
     * 
     * @param current current state
     * @param metrics current metrics
     * @param config configuration
     * @param decisionPolicy decision policy
     * @param elapsedMillis current elapsed time
     * @return decision to ramp up if duration elapsed and conditions allow, null otherwise
     */
    private static AdjustmentDecision checkAfterSustainDuration(
            AdaptiveState current,
            MetricsSnapshot metrics,
            AdaptiveConfig config,
            RampDecisionPolicy decisionPolicy,
            long elapsedMillis) {
        
        long phaseDuration = elapsedMillis - current.phaseStartTime();
        if (phaseDuration >= config.sustainDuration().toMillis()) {
            if (decisionPolicy.shouldRampUp(metrics) && current.currentTps() < config.maxTps()) {
                return createDecision(AdaptivePhase.RAMP_UP, 
                    calculateRampUpTps(current.currentTps(), config),
                    "Sustain duration elapsed, ramping up");
            }
        }
        return null;
    }
    
    /**
     * Checks if TPS has reached maximum and returns transition decision if so.
     * 
     * @param tps the TPS to check
     * @param config configuration
     * @return transition decision to SUSTAIN if max reached, null otherwise
     */
    private static AdjustmentDecision checkMaxTpsReached(double tps, AdaptiveConfig config) {
        if (tps >= config.maxTps()) {
            return createDecision(AdaptivePhase.SUSTAIN, tps, "Max TPS reached");
        }
        return null;
    }
    
    /**
     * Checks if current TPS is stable.
     * 
     * <p>Stability means:
     * - Conditions are good (error rate low, backpressure low)
     * - Been stable for required number of intervals (after incrementing)
     * 
     * @param current current state
     * @param metrics current metrics
     * @param config configuration
     * @param decisionPolicy decision policy
     * @return true if stable
     */
    private static boolean isStable(
            AdaptiveState current,
            MetricsSnapshot metrics,
            AdaptiveConfig config,
            RampDecisionPolicy decisionPolicy) {
        
        // Conditions must be good
        if (!decisionPolicy.shouldRampUp(metrics)) {
            return false;
        }
        
        // Check if we'll have enough stable intervals after incrementing
        int newStableCount = current.stableIntervalsCount() + 1;
        return decisionPolicy.shouldSustain(newStableCount, config.stableIntervalsRequired());
    }
    
    /**
     * Calculates new TPS after ramping up, clamped to max TPS.
     * 
     * @param currentTps current TPS
     * @param config configuration
     * @return new TPS after increment, clamped to max
     */
    private static double calculateRampUpTps(double currentTps, AdaptiveConfig config) {
        return Math.min(config.maxTps(), currentTps + config.rampIncrement());
    }
    
    /**
     * Calculates new TPS after ramping down, clamped to min TPS.
     * 
     * @param currentTps current TPS
     * @param config configuration
     * @return new TPS after decrement, clamped to min
     */
    private static double calculateRampDownTps(double currentTps, AdaptiveConfig config) {
        return Math.max(config.minTps(), currentTps - config.rampDecrement());
    }
    
    /**
     * Calculates recovery TPS from last known good TPS.
     * 
     * @param lastKnownGoodTps last known good TPS
     * @param config configuration
     * @return recovery TPS (50% of last known good, clamped to min)
     */
    private static double calculateRecoveryTps(double lastKnownGoodTps, AdaptiveConfig config) {
        return Math.max(config.minTps(), lastKnownGoodTps * RECOVERY_TPS_RATIO);
    }
    
    /**
     * Creates an adjustment decision.
     * 
     * @param newPhase new phase
     * @param newTps new TPS
     * @param reason reason for the decision
     * @return adjustment decision
     */
    private static AdjustmentDecision createDecision(AdaptivePhase newPhase, double newTps, String reason) {
        return new AdjustmentDecision(newPhase, newTps, reason);
    }
    
    /**
     * Decision result.
     * 
     * <p>Package-private to allow AdaptiveLoadPattern to use it.
     */
    record AdjustmentDecision(
        AdaptivePhase newPhase,
        double newTps,
        String reason  // For logging/debugging
    ) {
        /**
         * Creates an adjustment decision.
         * 
         * @param newPhase new phase
         * @param newTps new TPS
         * @param reason reason for the decision
         */
        AdjustmentDecision {
            Objects.requireNonNull(newPhase, "Phase must not be null");
            Objects.requireNonNull(reason, "Reason must not be null");
        }
    }
}
