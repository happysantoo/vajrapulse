package com.vajrapulse.api.pattern.adaptive;

/**
 * Represents the current phase of an adaptive load pattern.
 * 
 * <p>An adaptive load pattern operates in one of three phases:
 * <ul>
 *   <li>{@link #RAMP_UP} - Ramping up TPS until errors occur</li>
 *   <li>{@link #RAMP_DOWN} - Ramping down TPS to find stable point (includes recovery behavior when at minimum TPS)</li>
 *   <li>{@link #SUSTAIN} - Sustaining at stable TPS</li>
 * </ul>
 * 
 * @see AdaptiveLoadPattern
 * @since 0.9.9
 */
public enum AdaptivePhase {
    /** Ramping up TPS until errors occur */
    RAMP_UP,
    /** 
     * Ramping down TPS to find stable point.
     * Includes recovery behavior when at minimum TPS.
     */
    RAMP_DOWN,
    /** Sustaining at stable TPS */
    SUSTAIN
}

