package com.vajrapulse.api.pattern.adaptive;

/**
 * Context for phase strategy execution.
 * 
 * <p>This record provides all the information a strategy needs
 * to make decisions, including current state, configuration,
 * metrics, and decision policy.
 * 
 * @param current current adaptive state
 * @param config pattern configuration
 * @param metrics current metrics snapshot
 * @param decisionPolicy decision policy for ramp decisions
 * @param pattern the adaptive load pattern (for triggering transitions)
 * 
 * @see PhaseStrategy
 * @since 0.9.9
 */
public record PhaseContext(
    AdaptiveState current,
    AdaptiveConfig config,
    MetricsSnapshot metrics,
    RampDecisionPolicy decisionPolicy,
    AdaptiveLoadPattern pattern
) {
    /**
     * Creates a phase context with validation.
     */
    public PhaseContext {
        if (current == null) {
            throw new IllegalArgumentException("Current state must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        if (metrics == null) {
            throw new IllegalArgumentException("Metrics must not be null");
        }
        if (decisionPolicy == null) {
            throw new IllegalArgumentException("Decision policy must not be null");
        }
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern must not be null");
        }
    }
}

