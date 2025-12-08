package com.vajrapulse.api;

import java.time.Duration;

/**
 * Configuration for adaptive load pattern.
 * 
 * <p>This record consolidates all configuration parameters
 * for the adaptive load pattern, including thresholds,
 * intervals, and limits.
 * 
 * <p>Use {@link #defaults()} to get a configuration with
 * sensible defaults, or create a custom configuration
 * with specific values.
 * 
 * @since 0.9.9
 */
public record AdaptiveConfig(
    double initialTps,
    double rampIncrement,
    double rampDecrement,
    Duration rampInterval,
    double maxTps,
    double minTps,
    Duration sustainDuration,
    double errorThreshold,
    double backpressureRampUpThreshold,
    double backpressureRampDownThreshold,
    int stableIntervalsRequired,
    double tpsTolerance,
    double recoveryTpsRatio
) {
    /**
     * Creates a configuration with validation.
     * 
     * @param initialTps starting TPS (must be positive)
     * @param rampIncrement TPS increase per interval (must be positive)
     * @param rampDecrement TPS decrease per interval (must be positive)
     * @param rampInterval time between adjustments (must be positive)
     * @param maxTps maximum TPS limit (must be positive or Double.POSITIVE_INFINITY)
     * @param minTps minimum TPS limit (must be non-negative)
     * @param sustainDuration duration to sustain at stable point (must be positive)
     * @param errorThreshold error rate threshold 0.0-1.0 (must be between 0 and 1)
     * @param backpressureRampUpThreshold backpressure threshold for ramping up 0.0-1.0
     * @param backpressureRampDownThreshold backpressure threshold for ramping down 0.0-1.0
     * @param stableIntervalsRequired number of stable intervals required (must be at least 1)
     * @param tpsTolerance TPS tolerance for stability detection (must be non-negative)
     * @param recoveryTpsRatio ratio of last known good TPS to use for recovery 0.0-1.0
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public AdaptiveConfig {
        if (initialTps <= 0) {
            throw new IllegalArgumentException("Initial TPS must be positive, got: " + initialTps);
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
        if (maxTps <= 0 && !Double.isInfinite(maxTps)) {
            throw new IllegalArgumentException("Max TPS must be positive or infinite, got: " + maxTps);
        }
        if (minTps < 0) {
            throw new IllegalArgumentException("Min TPS must be non-negative, got: " + minTps);
        }
        if (minTps >= maxTps && !Double.isInfinite(maxTps)) {
            throw new IllegalArgumentException("Min TPS must be less than max TPS, got: min=" + minTps + ", max=" + maxTps);
        }
        if (sustainDuration == null || sustainDuration.isNegative() || sustainDuration.isZero()) {
            throw new IllegalArgumentException("Sustain duration must be positive, got: " + sustainDuration);
        }
        if (errorThreshold < 0.0 || errorThreshold > 1.0) {
            throw new IllegalArgumentException("Error threshold must be between 0.0 and 1.0, got: " + errorThreshold);
        }
        if (backpressureRampUpThreshold < 0.0 || backpressureRampUpThreshold > 1.0) {
            throw new IllegalArgumentException("Backpressure ramp up threshold must be between 0.0 and 1.0, got: " + backpressureRampUpThreshold);
        }
        if (backpressureRampDownThreshold < 0.0 || backpressureRampDownThreshold > 1.0) {
            throw new IllegalArgumentException("Backpressure ramp down threshold must be between 0.0 and 1.0, got: " + backpressureRampDownThreshold);
        }
        if (backpressureRampUpThreshold >= backpressureRampDownThreshold) {
            throw new IllegalArgumentException("Backpressure ramp up threshold must be less than ramp down threshold, got: up=" + backpressureRampUpThreshold + ", down=" + backpressureRampDownThreshold);
        }
        if (stableIntervalsRequired < 1) {
            throw new IllegalArgumentException("Stable intervals required must be at least 1, got: " + stableIntervalsRequired);
        }
        if (tpsTolerance < 0.0) {
            throw new IllegalArgumentException("TPS tolerance must be non-negative, got: " + tpsTolerance);
        }
        if (recoveryTpsRatio < 0.0 || recoveryTpsRatio > 1.0) {
            throw new IllegalArgumentException("Recovery TPS ratio must be between 0.0 and 1.0, got: " + recoveryTpsRatio);
        }
    }
    
    /**
     * Returns a configuration with sensible defaults.
     * 
     * <p>Default values:
     * <ul>
     *   <li>Initial TPS: 100.0</li>
     *   <li>Ramp increment: 50.0</li>
     *   <li>Ramp decrement: 100.0</li>
     *   <li>Ramp interval: 1 minute</li>
     *   <li>Max TPS: 5000.0</li>
     *   <li>Min TPS: 10.0</li>
     *   <li>Sustain duration: 10 minutes</li>
     *   <li>Error threshold: 0.01 (1%)</li>
     *   <li>Backpressure ramp up threshold: 0.3</li>
     *   <li>Backpressure ramp down threshold: 0.7</li>
     *   <li>Stable intervals required: 3</li>
     *   <li>TPS tolerance: 50.0</li>
     *   <li>Recovery TPS ratio: 0.5 (50%)</li>
     * </ul>
     * 
     * @return default configuration
     */
    public static AdaptiveConfig defaults() {
        return new AdaptiveConfig(
            100.0,                      // initialTps
            50.0,                       // rampIncrement
            100.0,                      // rampDecrement
            Duration.ofMinutes(1),      // rampInterval
            5000.0,                     // maxTps
            10.0,                       // minTps
            Duration.ofMinutes(10),     // sustainDuration
            0.01,                       // errorThreshold (1%)
            0.3,                        // backpressureRampUpThreshold
            0.7,                        // backpressureRampDownThreshold
            3,                          // stableIntervalsRequired
            50.0,                       // tpsTolerance
            0.5                         // recoveryTpsRatio (50%)
        );
    }
}

