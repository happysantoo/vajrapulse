package com.vajrapulse.api.pattern;

import java.time.Duration;

/**
 * Defines how load (transactions per second) changes over time.
 * 
 * <p>Implementations specify both the TPS calculation logic and the total duration.
 * The framework calls {@link #calculateTps(long)} at regular intervals to determine
 * the target rate.
 * 
 * <p>Built-in implementations:
 * <ul>
 *   <li>{@link StaticLoad} - Constant TPS for a fixed duration
 *   <li>{@link RampUpLoad} - Linear increase from 0 to max TPS
 *   <li>{@link RampUpToMaxLoad} - Ramp up then sustain at max
 * </ul>
 * 
 * @see StaticLoad
 * @see RampUpLoad
 * @see RampUpToMaxLoad
 */
public interface LoadPattern {
    
    /**
     * Calculates the target TPS at the given elapsed time.
     * 
     * @param elapsedMillis milliseconds since test start
     * @return target transactions per second at this point
     */
    double calculateTps(long elapsedMillis);
    
    /**
     * Returns the total duration of this load pattern.
     * 
     * @return total test duration
     */
    Duration getDuration();
    
    /**
     * Returns true if this pattern supports warm-up/cool-down metrics exclusion.
     * 
     * <p>Patterns that support warm-up/cool-down phases (e.g., {@link WarmupCooldownLoadPattern})
     * should override this method to return {@code true} and implement
     * {@link #shouldRecordMetrics(long)} to control when metrics are recorded.
     * 
     * @return true if this pattern supports warm-up/cool-down phases
     * @since 0.9.9
     */
    default boolean supportsWarmupCooldown() {
        return false;
    }
    
    /**
     * Returns true if metrics should be recorded at the given elapsed time.
     * 
     * <p>This method is only called if {@link #supportsWarmupCooldown()} returns {@code true}.
     * Patterns that support warm-up/cool-down should override this to exclude metrics
     * during warm-up and cool-down phases.
     * 
     * <p>Default implementation returns {@code true} (record metrics at all times).
     * 
     * @param elapsedMillis milliseconds since test start
     * @return true if metrics should be recorded, false otherwise
     * @since 0.9.9
     */
    default boolean shouldRecordMetrics(long elapsedMillis) {
        return true;
    }
    
}
