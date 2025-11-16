package com.vajrapulse.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Periodic spike load pattern.
 * <p>Base rate applies most of the time; at fixed intervals a spike
 * rate applies for a configured spike duration.
 * <p>Useful for stress testing burst handling.
 *
 * <pre>{@code
 * var pattern = new SpikeLoad(
 *     100.0,               // base
 *     500.0,               // spike
 *     Duration.ofSeconds(60), // total duration
 *     Duration.ofSeconds(10), // spike every 10s
 *     Duration.ofSeconds(2)   // spike lasts 2s
 * );
 * }</pre>
 */
public record SpikeLoad(
    double baseRate,
    double spikeRate,
    Duration totalDuration,
    Duration spikeInterval,
    Duration spikeDuration
) implements LoadPattern {

    public SpikeLoad {
        if (baseRate < 0.0 || spikeRate < 0.0) {
            throw new IllegalArgumentException("rates must be >= 0");
        }
        Objects.requireNonNull(totalDuration, "totalDuration");
        Objects.requireNonNull(spikeInterval, "spikeInterval");
        Objects.requireNonNull(spikeDuration, "spikeDuration");
        if (totalDuration.isNegative() || totalDuration.isZero()) {
            throw new IllegalArgumentException("totalDuration must be > 0");
        }
        if (spikeInterval.isNegative() || spikeInterval.isZero()) {
            throw new IllegalArgumentException("spikeInterval must be > 0");
        }
        if (spikeDuration.isNegative() || spikeDuration.isZero()) {
            throw new IllegalArgumentException("spikeDuration must be > 0");
        }
        if (spikeDuration.compareTo(spikeInterval) >= 0) {
            throw new IllegalArgumentException("spikeDuration must be < spikeInterval");
        }
    }

    @Override
    public double calculateTps(long elapsedMillis) {
        if (elapsedMillis < 0) return 0.0;
        long intervalMs = spikeInterval.toMillis();
        long durMs = spikeDuration.toMillis();
        long position = elapsedMillis % intervalMs;
        return position < durMs ? spikeRate : baseRate;
    }

    @Override
    public Duration getDuration() {
        return totalDuration;
    }
}
