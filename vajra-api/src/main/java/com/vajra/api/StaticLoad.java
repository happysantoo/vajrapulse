package com.vajra.api;

import java.time.Duration;

/**
 * A load pattern with constant TPS for a fixed duration.
 * 
 * <p>Example:
 * <pre>{@code
 * // 100 TPS for 5 minutes
 * LoadPattern load = new StaticLoad(100.0, Duration.ofMinutes(5));
 * }</pre>
 * 
 * @param tps target transactions per second
 * @param duration total test duration
 */
public record StaticLoad(double tps, Duration duration) implements LoadPattern {
    
    public StaticLoad {
        if (tps <= 0) {
            throw new IllegalArgumentException("TPS must be positive: " + tps);
        }
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Duration must be positive: " + duration);
        }
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        return tps;
    }
    
    @Override
    public Duration getDuration() {
        return duration;
    }
}
