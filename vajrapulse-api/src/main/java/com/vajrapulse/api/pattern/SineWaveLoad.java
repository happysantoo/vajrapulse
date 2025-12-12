package com.vajrapulse.api.pattern;

import java.time.Duration;
import java.util.Objects;

/**
 * Sinusoidal load pattern oscillating around a mean rate.
 * <p>Useful for testing systems under smooth cyclical variation.
 *
 * <pre>{@code
 * var pattern = new SineWaveLoad(
 *     150.0,                   // mean
 *     50.0,                    // amplitude
 *     Duration.ofSeconds(30),  // total duration
 *     Duration.ofSeconds(10)   // period
 * );
 * }</pre>
 */
public record SineWaveLoad(
    double meanRate,
    double amplitude,
    Duration totalDuration,
    Duration period
) implements LoadPattern {

    public SineWaveLoad {
        if (meanRate < 0.0 || amplitude < 0.0) {
            throw new IllegalArgumentException("meanRate and amplitude must be >= 0");
        }
        Objects.requireNonNull(totalDuration, "totalDuration");
        Objects.requireNonNull(period, "period");
        if (totalDuration.isNegative() || totalDuration.isZero()) {
            throw new IllegalArgumentException("totalDuration must be > 0");
        }
        if (period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("period must be > 0");
        }
    }

    @Override
    public double calculateTps(long elapsedMillis) {
        if (elapsedMillis < 0) return 0.0;
        long p = period.toMillis();
        double angle = 2.0 * Math.PI * (elapsedMillis % p) / (double) p;
        double value = meanRate + amplitude * Math.sin(angle);
        return value < 0.0 ? 0.0 : value;
    }

    @Override
    public Duration getDuration() {
        return totalDuration;
    }
}
