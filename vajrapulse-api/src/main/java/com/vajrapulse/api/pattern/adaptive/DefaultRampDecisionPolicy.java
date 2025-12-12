package com.vajrapulse.api.pattern.adaptive;

/**
 * Default implementation of ramp decision policy.
 * 
 * <p>This policy uses configurable thresholds for:
 * <ul>
 *   <li>Error rate threshold</li>
 *   <li>Backpressure thresholds (ramp up/down)</li>
 *   <li>Stable intervals required</li>
 * </ul>
 * 
 * <p>Decision logic:
 * <ul>
 *   <li><strong>Ramp Up:</strong> Error rate &lt; threshold AND backpressure &lt; rampUpThreshold</li>
 *   <li><strong>Ramp Down:</strong> Error rate &gt;= threshold OR backpressure &gt;= rampDownThreshold</li>
 *   <li><strong>Sustain:</strong> Stable intervals count &gt;= required OR max TPS reached</li>
 *   <li><strong>Recover:</strong> Backpressure &lt; 0.3 OR (recent error rate &lt; threshold AND backpressure &lt; 0.5)</li>
 * </ul>
 * 
 * @since 0.9.9
 */
public final class DefaultRampDecisionPolicy implements RampDecisionPolicy {
    
    private final double errorThreshold;
    private final double backpressureRampUpThreshold;
    private final double backpressureRampDownThreshold;
    private final int stableIntervalsRequired;
    private final double recoveryBackpressureLowThreshold;
    private final double recoveryBackpressureModerateThreshold;
    
    /**
     * Creates a default policy with standard thresholds.
     * 
     * @param errorThreshold error rate threshold (0.0-1.0)
     * @param backpressureRampUpThreshold backpressure threshold for ramping up (default: 0.3)
     * @param backpressureRampDownThreshold backpressure threshold for ramping down (default: 0.7)
     * @param stableIntervalsRequired number of stable intervals required (default: 3)
     */
    public DefaultRampDecisionPolicy(
            double errorThreshold,
            double backpressureRampUpThreshold,
            double backpressureRampDownThreshold,
            int stableIntervalsRequired) {
        
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
            throw new IllegalArgumentException("Backpressure ramp up threshold must be less than ramp down threshold");
        }
        if (stableIntervalsRequired < 1) {
            throw new IllegalArgumentException("Stable intervals required must be at least 1, got: " + stableIntervalsRequired);
        }
        
        this.errorThreshold = errorThreshold;
        this.backpressureRampUpThreshold = backpressureRampUpThreshold;
        this.backpressureRampDownThreshold = backpressureRampDownThreshold;
        this.stableIntervalsRequired = stableIntervalsRequired;
        this.recoveryBackpressureLowThreshold = 0.3;
        this.recoveryBackpressureModerateThreshold = 0.5;
    }
    
    /**
     * Creates a default policy with standard thresholds.
     * 
     * @param errorThreshold error rate threshold (0.0-1.0)
     */
    public DefaultRampDecisionPolicy(double errorThreshold) {
        this(errorThreshold, 0.3, 0.7, 3);
    }
    
    @Override
    public boolean shouldRampUp(MetricsSnapshot metrics) {
        return metrics.failureRate() < errorThreshold 
            && metrics.backpressure() < backpressureRampUpThreshold;
    }
    
    @Override
    public boolean shouldRampDown(MetricsSnapshot metrics) {
        return metrics.failureRate() >= errorThreshold 
            || metrics.backpressure() >= backpressureRampDownThreshold;
    }
    
    @Override
    public boolean shouldSustain(MetricsSnapshot metrics, AdaptiveStabilityTracking stability) {
        if (stability == null) {
            return false;
        }
        return stability.stableIntervalsCount() >= stableIntervalsRequired;
    }
    
    @Override
    public boolean canRecoverFromMinimum(MetricsSnapshot metrics) {
        // Recovery conditions: backpressure low OR (error rate low AND backpressure moderate)
        // This is lenient to allow recovery even if error rate is slightly elevated
        return metrics.backpressure() < recoveryBackpressureLowThreshold
            || (metrics.recentFailureRate() < errorThreshold 
                && metrics.backpressure() < recoveryBackpressureModerateThreshold);
    }
}

