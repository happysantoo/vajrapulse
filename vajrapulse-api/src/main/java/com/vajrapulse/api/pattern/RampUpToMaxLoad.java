package com.vajrapulse.api.pattern;

import java.time.Duration;

/**
 * A load pattern that ramps up to max TPS, then sustains at that level.
 * 
 * <p>Example:
 * <pre>{@code
 * // Ramp from 0 to 200 TPS over 30 seconds, then sustain at 200 for 5 minutes
 * LoadPattern load = new RampUpToMaxLoad(
 *     200.0,
 *     Duration.ofSeconds(30),
 *     Duration.ofMinutes(5)
 * );
 * }</pre>
 * 
 * @param maxTps maximum target TPS
 * @param rampDuration time to ramp from 0 to max
 * @param sustainDuration time to sustain at max after ramp
 */
public record RampUpToMaxLoad(
    double maxTps,
    Duration rampDuration,
    Duration sustainDuration
) implements LoadPattern {
    
    public RampUpToMaxLoad {
        LoadPatternValidator.validateTps("Max TPS", maxTps);
        LoadPatternValidator.validateDuration("Ramp duration", rampDuration);
        LoadPatternValidator.validateDuration("Sustain duration", sustainDuration);
    }
    
    /**
     * Delegate ramp-up phase to RampUpLoad for code reuse.
     */
    private RampUpLoad rampUpPhase() {
        return new RampUpLoad(maxTps, rampDuration);
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        long rampMillis = rampDuration.toMillis();
        if (elapsedMillis < rampMillis) {
            // Use RampUpLoad for ramp-up phase
            return rampUpPhase().calculateTps(elapsedMillis);
        }
        // Sustain phase: return max TPS
        return maxTps;
    }
    
    @Override
    public Duration getDuration() {
        return rampDuration.plus(sustainDuration);
    }
}
