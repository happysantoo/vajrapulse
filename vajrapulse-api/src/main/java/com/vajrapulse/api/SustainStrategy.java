package com.vajrapulse.api;

import java.time.Duration;

/**
 * Strategy for handling the SUSTAIN phase.
 * 
 * <p>This strategy:
 * <ul>
 *   <li>Checks if sustain duration has elapsed (transitions to RAMP_UP)</li>
 *   <li>Checks if conditions have worsened (transitions to RAMP_DOWN)</li>
 *   <li>Otherwise continues at stable TPS</li>
 * </ul>
 * 
 * @since 0.9.9
 */
public final class SustainStrategy implements PhaseStrategy {
    
    @Override
    public double handle(PhaseContext context, long elapsedMillis) {
        AdaptiveLoadPattern.AdaptiveState current = context.current();
        AdaptiveConfig config = context.config();
        MetricsSnapshot metrics = context.metrics();
        RampDecisionPolicy decisionPolicy = context.decisionPolicy();
        AdaptiveLoadPattern pattern = context.pattern();
        
        // Check if conditions have worsened - transition to RAMP_DOWN
        if (decisionPolicy.shouldRampDown(metrics)) {
            double newTps = Math.max(config.minTps(), current.currentTps() - config.rampDecrement());
            pattern.transitionPhaseInternal(current, AdaptiveLoadPattern.Phase.RAMP_DOWN, elapsedMillis, current.stableTps(), newTps);
            return newTps;
        }
        
        // Check if sustain duration has elapsed - transition to RAMP_UP
        long phaseStartTime = current.phaseStartTime();
        if (phaseStartTime >= 0) {
            long phaseDuration = elapsedMillis - phaseStartTime;
            if (phaseDuration >= config.sustainDuration().toMillis()) {
                // Sustain duration elapsed - transition to RAMP_UP
                double newTps = Math.min(config.maxTps(), current.currentTps() + config.rampIncrement());
                pattern.transitionPhaseInternal(current, AdaptiveLoadPattern.Phase.RAMP_UP, elapsedMillis, current.stableTps(), newTps);
                return newTps;
            }
        }
        
        // Continue sustaining at stable TPS
        return current.currentTps();
    }
}

