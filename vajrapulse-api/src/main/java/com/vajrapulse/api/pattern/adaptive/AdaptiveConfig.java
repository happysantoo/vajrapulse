package com.vajrapulse.api.pattern.adaptive;

import java.time.Duration;

/**
 * Simplified configuration for adaptive load pattern.
 * 
 * <p>This record contains essential configuration parameters only.
 * Decision thresholds (error threshold, backpressure thresholds) are
 * configured in {@link RampDecisionPolicy} instead.
 * 
 * <p>Use {@link #defaults()} to get a configuration with
 * sensible defaults, or create a custom configuration
 * with specific values.
 * 
 * @since 0.9.9
 */
public record AdaptiveConfig(
    double initialTps,
    double maxTps,
    double minTps,
    double rampIncrement,
    double rampDecrement,
    Duration rampInterval,
    Duration sustainDuration,
    int stableIntervalsRequired,
    Duration initialRampDuration
) {
    /**
     * Creates a configuration with validation.
     * 
     * @param initialTps starting TPS (must be positive)
     * @param maxTps maximum TPS limit (must be positive or Double.POSITIVE_INFINITY)
     * @param minTps minimum TPS limit (must be non-negative)
     * @param rampIncrement TPS increase per interval (must be positive)
     * @param rampDecrement TPS decrease per interval (must be positive)
     * @param rampInterval time between adjustments (must be positive)
     * @param sustainDuration duration to sustain at stable point (must be positive)
     * @param stableIntervalsRequired number of stable intervals required (must be at least 1)
     * @param initialRampDuration duration to gradually ramp from 0 to initialTps (must be non-negative, 0 means no initial ramp)
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public AdaptiveConfig {
        if (initialTps <= 0) {
            throw new IllegalArgumentException("Initial TPS must be positive, got: " + initialTps);
        }
        if (maxTps <= 0 && !Double.isInfinite(maxTps)) {
            throw new IllegalArgumentException("Max TPS must be positive or infinite, got: " + maxTps);
        }
        if (minTps < 0) {
            throw new IllegalArgumentException("Min TPS must be non-negative, got: " + minTps);
        }
        if (minTps >= maxTps && !Double.isInfinite(maxTps)) {
            throw new IllegalArgumentException("Min TPS must be less than max TPS, got: min=" + minTps + ", max=" + maxTps);
        }
        if (rampIncrement <= 0) {
            throw new IllegalArgumentException("Ramp increment must be positive, got: " + rampIncrement);
        }
        if (rampDecrement <= 0) {
            throw new IllegalArgumentException("Ramp decrement must be positive, got: " + rampDecrement);
        }
        if (rampInterval == null || rampInterval.isNegative() || rampInterval.isZero()) {
            throw new IllegalArgumentException("Ramp interval must be positive, got: " + rampInterval);
        }
        if (sustainDuration == null || sustainDuration.isNegative() || sustainDuration.isZero()) {
            throw new IllegalArgumentException("Sustain duration must be positive, got: " + sustainDuration);
        }
        if (stableIntervalsRequired < 1) {
            throw new IllegalArgumentException("Stable intervals required must be at least 1, got: " + stableIntervalsRequired);
        }
        if (initialRampDuration == null || initialRampDuration.isNegative()) {
            throw new IllegalArgumentException("Initial ramp duration must be non-negative, got: " + initialRampDuration);
        }
    }
    
    /**
     * Returns a configuration with sensible defaults.
     * 
     * <p>Default values:
     * <ul>
     *   <li>Initial TPS: 100.0</li>
     *   <li>Max TPS: 5000.0</li>
     *   <li>Min TPS: 10.0</li>
     *   <li>Ramp increment: 50.0</li>
     *   <li>Ramp decrement: 100.0</li>
     *   <li>Ramp interval: 1 minute</li>
     *   <li>Sustain duration: 10 minutes</li>
     *   <li>Stable intervals required: 3</li>
     *   <li>Initial ramp duration: 0 (no gradual ramp-up)</li>
     * </ul>
     * 
     * @return default configuration
     */
    public static AdaptiveConfig defaults() {
        return new AdaptiveConfig(
            100.0,                      // initialTps
            5000.0,                     // maxTps
            10.0,                       // minTps
            50.0,                       // rampIncrement
            100.0,                      // rampDecrement
            Duration.ofMinutes(1),     // rampInterval
            Duration.ofMinutes(10),     // sustainDuration
            3,                          // stableIntervalsRequired
            Duration.ZERO               // initialRampDuration (no gradual ramp-up by default)
        );
    }
}
