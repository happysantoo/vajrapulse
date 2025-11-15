package com.vajra.api;

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
        if (maxTps <= 0) {
            throw new IllegalArgumentException("Max TPS must be positive: " + maxTps);
        }
        if (rampDuration.isNegative() || rampDuration.isZero()) {
            throw new IllegalArgumentException("Ramp duration must be positive: " + rampDuration);
        }
        if (sustainDuration.isNegative() || sustainDuration.isZero()) {
            throw new IllegalArgumentException("Sustain duration must be positive: " + sustainDuration);
        }
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        long rampMillis = rampDuration.toMillis();
        if (elapsedMillis >= rampMillis) {
            return maxTps;
        }
        return maxTps * elapsedMillis / (double) rampMillis;
    }
    
    @Override
    public Duration getDuration() {
        return rampDuration.plus(sustainDuration);
    }
}
