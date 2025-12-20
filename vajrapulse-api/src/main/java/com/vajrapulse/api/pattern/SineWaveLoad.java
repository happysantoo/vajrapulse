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
        LoadPatternValidator.validateTpsNonNegative("Mean rate", meanRate);
        LoadPatternValidator.validateTpsNonNegative("Amplitude", amplitude);
        LoadPatternValidator.validateDuration("Total duration", totalDuration);
        LoadPatternValidator.validateDuration("Period", period);
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
