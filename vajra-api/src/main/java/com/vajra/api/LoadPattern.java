package com.vajra.api;

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
}
