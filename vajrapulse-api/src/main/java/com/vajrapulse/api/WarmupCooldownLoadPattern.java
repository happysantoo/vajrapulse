package com.vajrapulse.api;

import java.time.Duration;

/**
 * Wraps a load pattern with warm-up and cool-down phases.
 * 
 * <p>This wrapper adds three phases to any load pattern:
 * <ol>
 *   <li><strong>Warm-up Phase</strong>: Gradually ramps from 0 TPS to the initial TPS
 *       of the base pattern. Metrics are not recorded during this phase.</li>
 *   <li><strong>Steady-State Phase</strong>: Executes the base pattern at full TPS.
 *       Metrics are recorded during this phase.</li>
 *   <li><strong>Cool-down Phase</strong>: Gradually ramps from the final TPS to 0 TPS.
 *       Metrics are not recorded during this phase.</li>
 * </ol>
 * 
 * <p>This provides clean separation between initialization and measurement phases,
 * allowing accurate performance baselines without warm-up artifacts.
 * 
 * <p>Example:
 * <pre>{@code
 * // Base pattern: 100 TPS for 5 minutes
 * LoadPattern basePattern = new StaticLoad(100.0, Duration.ofMinutes(5));
 * 
 * // Add 30s warm-up and 10s cool-down
 * LoadPattern pattern = new WarmupCooldownLoadPattern(
 *     basePattern,
 *     Duration.ofSeconds(30),  // Warm-up: 30 seconds
 *     Duration.ofSeconds(10)   // Cool-down: 10 seconds
 * );
 * 
 * // Total duration: 30s warm-up + 5m steady-state + 10s cool-down = 5m 40s
 * }</pre>
 * 
 * <p><strong>Phase Detection:</strong> Use {@link #getCurrentPhase(long)} to determine
 * which phase is active at a given elapsed time. This is useful for metrics collection
 * (skip during warm-up/cool-down) and logging.
 * 
 * @since 0.9.7
 */
public final class WarmupCooldownLoadPattern implements LoadPattern {
    
    /**
     * Represents the current phase of the load pattern.
     * 
     * @since 0.9.7
     */
    public enum Phase {
        /** Warm-up phase: ramping from 0 to initial TPS */
        WARMUP,
        /** Steady-state phase: executing base pattern, metrics recorded */
        STEADY_STATE,
        /** Cool-down phase: ramping from final TPS to 0 */
        COOLDOWN,
        /** Test complete: all phases finished */
        COMPLETE
    }
    
    private final LoadPattern basePattern;
    private final Duration warmupDuration;
    private final Duration cooldownDuration;
    
    /**
     * Creates a warm-up/cool-down wrapper around a base load pattern.
     * 
     * @param basePattern the base load pattern to wrap (must not be null)
     * @param warmupDuration warm-up phase duration (must not be null or negative)
     * @param cooldownDuration cool-down phase duration (must not be null or negative)
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public WarmupCooldownLoadPattern(
            LoadPattern basePattern,
            Duration warmupDuration,
            Duration cooldownDuration) {
        if (basePattern == null) {
            throw new IllegalArgumentException("Base pattern must not be null");
        }
        if (warmupDuration == null || warmupDuration.isNegative()) {
            throw new IllegalArgumentException("Warm-up duration must not be null or negative: " + warmupDuration);
        }
        if (cooldownDuration == null || cooldownDuration.isNegative()) {
            throw new IllegalArgumentException("Cool-down duration must not be null or negative: " + cooldownDuration);
        }
        this.basePattern = basePattern;
        this.warmupDuration = warmupDuration;
        this.cooldownDuration = cooldownDuration;
    }
    
    /**
     * Creates a warm-up/cool-down wrapper with only warm-up phase.
     * 
     * @param basePattern the base load pattern to wrap
     * @param warmupDuration warm-up phase duration
     * @return wrapper with warm-up only (no cool-down)
     */
    public static WarmupCooldownLoadPattern withWarmup(
            LoadPattern basePattern,
            Duration warmupDuration) {
        return new WarmupCooldownLoadPattern(basePattern, warmupDuration, Duration.ZERO);
    }
    
    /**
     * Creates a warm-up/cool-down wrapper with only cool-down phase.
     * 
     * @param basePattern the base load pattern to wrap
     * @param cooldownDuration cool-down phase duration
     * @return wrapper with cool-down only (no warm-up)
     */
    public static WarmupCooldownLoadPattern withCooldown(
            LoadPattern basePattern,
            Duration cooldownDuration) {
        return new WarmupCooldownLoadPattern(basePattern, Duration.ZERO, cooldownDuration);
    }
    
    /**
     * Determines the current phase at the given elapsed time.
     * 
     * @param elapsedMillis milliseconds since test start
     * @return the current phase
     */
    public Phase getCurrentPhase(long elapsedMillis) {
        long warmupEnd = warmupDuration.toMillis();
        long steadyStateEnd = warmupEnd + basePattern.getDuration().toMillis();
        long totalDuration = steadyStateEnd + cooldownDuration.toMillis();
        
        if (elapsedMillis < warmupEnd) {
            return Phase.WARMUP;
        } else if (elapsedMillis < steadyStateEnd) {
            return Phase.STEADY_STATE;
        } else if (elapsedMillis < totalDuration) {
            return Phase.COOLDOWN;
        } else {
            return Phase.COMPLETE;
        }
    }
    
    /**
     * Checks if metrics should be recorded at the given elapsed time.
     * 
     * <p>Metrics are only recorded during the steady-state phase.
     * 
     * @param elapsedMillis milliseconds since test start
     * @return true if metrics should be recorded, false otherwise
     */
    public boolean shouldRecordMetrics(long elapsedMillis) {
        return getCurrentPhase(elapsedMillis) == Phase.STEADY_STATE;
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        long warmupEnd = warmupDuration.toMillis();
        long steadyStateEnd = warmupEnd + basePattern.getDuration().toMillis();
        long totalDuration = steadyStateEnd + cooldownDuration.toMillis();
        
        if (elapsedMillis < warmupEnd) {
            // Warm-up phase: ramp from 0 to initial TPS of base pattern
            if (warmupDuration.isZero()) {
                // No warm-up, jump to steady-state
                return basePattern.calculateTps(0);
            }
            double initialTps = basePattern.calculateTps(0);
            double progress = (double) elapsedMillis / warmupDuration.toMillis();
            return initialTps * progress;
        } else if (elapsedMillis < steadyStateEnd) {
            // Steady-state phase: use base pattern
            long steadyStateElapsed = elapsedMillis - warmupEnd;
            return basePattern.calculateTps(steadyStateElapsed);
        } else if (elapsedMillis < totalDuration) {
            // Cool-down phase: ramp from final TPS to 0
            if (cooldownDuration.isZero()) {
                // No cool-down, return 0 immediately
                return 0.0;
            }
            long cooldownElapsed = elapsedMillis - steadyStateEnd;
            double finalTps = basePattern.calculateTps(basePattern.getDuration().toMillis());
            double progress = 1.0 - ((double) cooldownElapsed / cooldownDuration.toMillis());
            return Math.max(0.0, finalTps * progress);
        } else {
            // Test complete
            return 0.0;
        }
    }
    
    @Override
    public Duration getDuration() {
        return warmupDuration.plus(basePattern.getDuration()).plus(cooldownDuration);
    }
    
    /**
     * Returns the base load pattern.
     * 
     * @return the base pattern
     */
    public LoadPattern getBasePattern() {
        return basePattern;
    }
    
    /**
     * Returns the warm-up duration.
     * 
     * @return warm-up duration
     */
    public Duration getWarmupDuration() {
        return warmupDuration;
    }
    
    /**
     * Returns the cool-down duration.
     * 
     * @return cool-down duration
     */
    public Duration getCooldownDuration() {
        return cooldownDuration;
    }
}

