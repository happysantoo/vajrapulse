package com.vajrapulse.api.pattern.adaptive;

/**
 * Strategy for handling the RAMP_DOWN phase.
 * 
 * <p>This strategy:
 * <ul>
 *   <li>Checks for intermediate stability (transitions to SUSTAIN)</li>
 *   <li>Otherwise continues at current TPS (adjustments and recovery happen in checkAndAdjust)</li>
 * </ul>
 * 
 * @since 0.9.9
 */
public final class RampDownStrategy implements PhaseStrategy {
    
    @Override
    public double handle(PhaseContext context, long elapsedMillis) {
        AdaptiveState current = context.current();
        AdaptiveLoadPattern pattern = context.pattern();
        
        // Check if stable at current TPS (intermediate stability)
        // This allows the pattern to sustain at intermediate TPS levels during ramp-down
        if (pattern.isStableAtCurrentTps(current.currentTps(), elapsedMillis)) {
            pattern.transitionPhase(AdaptivePhase.SUSTAIN, elapsedMillis, current.currentTps());
            return current.currentTps();
        }
        
        // Recovery behavior is handled in checkAndAdjust() when TPS reaches minimum
        // Just return the current TPS here
        return current.currentTps();
    }
}

