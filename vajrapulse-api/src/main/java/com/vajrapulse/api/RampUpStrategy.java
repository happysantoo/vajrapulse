package com.vajrapulse.api;

/**
 * Strategy for handling the RAMP_UP phase.
 * 
 * <p>This strategy:
 * <ul>
 *   <li>Checks if max TPS is reached (transitions to SUSTAIN)</li>
 *   <li>Checks for intermediate stability (transitions to SUSTAIN)</li>
 *   <li>Otherwise continues at current TPS (adjustments happen in checkAndAdjust)</li>
 * </ul>
 * 
 * @since 0.9.9
 */
public final class RampUpStrategy implements PhaseStrategy {
    
    @Override
    public double handle(PhaseContext context, long elapsedMillis) {
        AdaptiveLoadPattern.AdaptiveState current = context.current();
        AdaptiveConfig config = context.config();
        AdaptiveLoadPattern pattern = context.pattern();
        
        // Check if we've hit max TPS
        if (current.currentTps() >= config.maxTps()) {
            // Treat max TPS as stable point
            pattern.transitionPhase(AdaptiveLoadPattern.Phase.SUSTAIN, elapsedMillis, config.maxTps());
            return config.maxTps();
        }
        
        // Check if stable at current TPS (intermediate stability)
        if (pattern.isStableAtCurrentTps(current.currentTps(), elapsedMillis)) {
            pattern.transitionPhase(AdaptiveLoadPattern.Phase.SUSTAIN, elapsedMillis, current.currentTps());
            return current.currentTps();
        }
        
        return current.currentTps();
    }
}

