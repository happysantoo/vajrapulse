package com.vajrapulse.api;

import java.time.Duration;

/**
 * A load pattern that ramps up linearly from 0 to max TPS.
 * 
 * <p>Example:
 * <pre>{@code
 * // Ramp from 0 to 200 TPS over 30 seconds
 * LoadPattern load = new RampUpLoad(200.0, Duration.ofSeconds(30));
 * }</pre>
 * 
 * @param maxTps maximum target TPS
 * @param rampDuration time to ramp from 0 to max
 */
public record RampUpLoad(double maxTps, Duration rampDuration) implements LoadPattern {
    
    public RampUpLoad {
        if (maxTps <= 0) {
            throw new IllegalArgumentException("Max TPS must be positive: " + maxTps);
        }
        if (rampDuration.isNegative() || rampDuration.isZero()) {
            throw new IllegalArgumentException("Ramp duration must be positive: " + rampDuration);
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
        return rampDuration;
    }
}
