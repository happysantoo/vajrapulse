package com.vajrapulse.api.pattern.adaptive;

/**
 * Strategy for handling a specific phase of adaptive load pattern.
 * 
 * <p>This interface encapsulates the behavior for each phase (RAMP_UP,
 * RAMP_DOWN, SUSTAIN), allowing the pattern to delegate phase-specific
 * logic to dedicated strategy implementations.
 * 
 * <p>Implementations should be stateless and thread-safe, as they may
 * be called concurrently from multiple threads.
 * 
 * @since 0.9.9
 */
public interface PhaseStrategy {
    
    /**
     * Handles the phase logic and returns the new TPS value.
     * 
     * <p>This method is called during each adjustment interval to
     * determine the appropriate TPS for the current phase. The strategy
     * may also trigger phase transitions if conditions warrant.
     * 
     * @param context phase execution context containing state, config, and dependencies
     * @param elapsedMillis elapsed time since pattern start (milliseconds)
     * @return new TPS value for this phase
     */
    double handle(PhaseContext context, long elapsedMillis);
    
}

