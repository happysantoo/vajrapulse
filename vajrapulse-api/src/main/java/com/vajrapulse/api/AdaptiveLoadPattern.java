package com.vajrapulse.api;

import java.time.Duration;

/**
 * Adaptive load pattern that automatically finds the maximum sustainable TPS.
 * 
 * <p>This pattern:
 * <ol>
 *   <li>Starts at initial TPS</li>
 *   <li>Ramps up by increment amount at fixed intervals until errors occur</li>
 *   <li>Ramps down by decrement amount when errors exceed threshold</li>
 *   <li>Finds stable point (2-3 consecutive intervals with low error rate)</li>
 *   <li>Sustains at stable point for configured duration</li>
 *   <li>Continues at stable TPS indefinitely after sustain duration</li>
 * </ol>
 * 
 * <p>Example:
 * <pre>{@code
 * MetricsProvider metrics = ...; // From MetricsCollector
 * AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
 *     100.0,                          // Start at 100 TPS
 *     50.0,                           // Increase 50 TPS per interval
 *     100.0,                          // Decrease 100 TPS per interval when errors occur
 *     Duration.ofMinutes(1),          // Check/adjust every minute
 *     5000.0,                         // Max 5000 TPS (or Double.POSITIVE_INFINITY)
 *     Duration.ofMinutes(10),         // Sustain at stable point for 10 minutes
 *     0.01,                           // 1% error rate threshold
 *     metrics                          // For feedback
 * );
 * }</pre>
 * 
 * <p><strong>Thread Safety:</strong> This class is thread-safe for concurrent
 * access from multiple threads calling {@link #calculateTps(long)}.
 * 
 * @since 0.9.5
 */
public final class AdaptiveLoadPattern implements LoadPattern {
    
    @SuppressWarnings("unused") // Stored for potential future use (metrics, debugging)
    private final double initialTps;
    private final double rampIncrement;
    private final double rampDecrement;
    private final Duration rampInterval;
    private final double maxTps;
    private final Duration sustainDuration;
    private final double errorThreshold;
    private final MetricsProvider metricsProvider;
    
    // State (volatile for thread safety)
    private volatile Phase currentPhase = Phase.RAMP_UP;
    private volatile double currentTps;
    private volatile long lastAdjustmentTime;
    private volatile double stableTps = -1.0;  // -1 means not found yet
    private volatile long phaseStartTime;
    private volatile int stableIntervalsCount = 0;
    private volatile int rampDownAttempts = 0;
    private volatile long phaseTransitionCount = 0;
    
    private static final int MAX_RAMP_DOWN_ATTEMPTS = 10;
    private static final int STABLE_INTERVALS_REQUIRED = 3;
    
    /**
     * Represents the current phase of the adaptive load pattern.
     * 
     * @since 0.9.5
     */
    public enum Phase {
        /** Ramping up TPS until errors occur */
        RAMP_UP,
        /** Ramping down TPS to find stable point */
        RAMP_DOWN,
        /** Sustaining at stable TPS */
        SUSTAIN,
        /** Test complete (only if stable point never found) */
        COMPLETE
    }
    
    /**
     * Creates a new adaptive load pattern.
     * 
     * @param initialTps starting TPS (must be positive)
     * @param rampIncrement TPS increase per interval (must be positive)
     * @param rampDecrement TPS decrease per interval (must be positive)
     * @param rampInterval time between adjustments (must be positive)
     * @param maxTps maximum TPS limit (must be positive or Double.POSITIVE_INFINITY)
     * @param sustainDuration duration to sustain at stable point (must be positive)
     * @param errorThreshold error rate threshold 0.0-1.0 (must be between 0 and 1)
     * @param metricsProvider provides execution metrics (must not be null)
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public AdaptiveLoadPattern(
            double initialTps,
            double rampIncrement,
            double rampDecrement,
            Duration rampInterval,
            double maxTps,
            Duration sustainDuration,
            double errorThreshold,
            MetricsProvider metricsProvider) {
        
        if (initialTps <= 0) {
            throw new IllegalArgumentException("Initial TPS must be positive: " + initialTps);
        }
        if (rampIncrement <= 0) {
            throw new IllegalArgumentException("Ramp increment must be positive: " + rampIncrement);
        }
        if (rampDecrement <= 0) {
            throw new IllegalArgumentException("Ramp decrement must be positive: " + rampDecrement);
        }
        if (rampInterval.isNegative() || rampInterval.isZero()) {
            throw new IllegalArgumentException("Ramp interval must be positive: " + rampInterval);
        }
        if (maxTps <= 0 && !Double.isInfinite(maxTps)) {
            throw new IllegalArgumentException("Max TPS must be positive or infinite: " + maxTps);
        }
        if (sustainDuration.isNegative() || sustainDuration.isZero()) {
            throw new IllegalArgumentException("Sustain duration must be positive: " + sustainDuration);
        }
        if (errorThreshold < 0.0 || errorThreshold > 1.0) {
            throw new IllegalArgumentException("Error threshold must be between 0.0 and 1.0: " + errorThreshold);
        }
        if (metricsProvider == null) {
            throw new IllegalArgumentException("Metrics provider must not be null");
        }
        
        this.initialTps = initialTps;
        this.rampIncrement = rampIncrement;
        this.rampDecrement = rampDecrement;
        this.rampInterval = rampInterval;
        this.maxTps = maxTps;
        this.sustainDuration = sustainDuration;
        this.errorThreshold = errorThreshold;
        this.metricsProvider = metricsProvider;
        
        // Initialize state
        this.currentTps = initialTps;
        this.lastAdjustmentTime = -1;  // Will be set on first call
        this.phaseStartTime = -1;  // Will be set on first call
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        if (elapsedMillis < 0) {
            return 0.0;
        }
        
        // Initialize on first call
        if (lastAdjustmentTime < 0) {
            lastAdjustmentTime = 0;
            phaseStartTime = 0;
        }
        
        // Check if it's time to adjust
        long timeSinceLastAdjustment = elapsedMillis - lastAdjustmentTime;
        if (timeSinceLastAdjustment >= rampInterval.toMillis()) {
            checkAndAdjust(elapsedMillis);
        }
        
        // Handle phase-specific logic
        return switch (currentPhase) {
            case RAMP_UP -> handleRampUp(elapsedMillis);
            case RAMP_DOWN -> handleRampDown(elapsedMillis);
            case SUSTAIN -> handleSustain(elapsedMillis);
            case COMPLETE -> 0.0;  // Test ends (only if stable point never found)
        };
    }
    
    private double handleRampUp(long elapsedMillis) {
        // Check if we've hit max TPS
        if (currentTps >= maxTps) {
            // Treat max TPS as stable point
            stableTps = maxTps;
            transitionPhase(Phase.SUSTAIN, elapsedMillis);
            return stableTps;
        }
        
        return currentTps;
    }
    
    private double handleRampDown(long elapsedMillis) {
        // Check if we've exhausted attempts
        if (rampDownAttempts >= MAX_RAMP_DOWN_ATTEMPTS) {
            transitionPhase(Phase.COMPLETE, elapsedMillis);
            return 0.0;
        }
        
        return currentTps;
    }
    
    private double handleSustain(long elapsedMillis) {
        // After sustain duration, continue indefinitely at stable TPS
        long sustainElapsed = elapsedMillis - phaseStartTime;
        if (sustainElapsed >= sustainDuration.toMillis()) {
            // Continue at stable TPS indefinitely
            return stableTps;
        }
        return stableTps;
    }
    
    private void checkAndAdjust(long elapsedMillis) {
        double errorRate = metricsProvider.getFailureRate() / 100.0;  // Convert percentage to ratio
        
        switch (currentPhase) {
            case RAMP_UP -> {
                if (errorRate >= errorThreshold) {
                    // Errors detected, start ramping down
                    transitionPhase(Phase.RAMP_DOWN, elapsedMillis);
                    currentTps = Math.max(0, currentTps - rampDecrement);
                } else {
                    // No errors, continue ramping up
                    currentTps = Math.min(maxTps, currentTps + rampIncrement);
                }
            }
            case RAMP_DOWN -> {
                rampDownAttempts++;
                if (errorRate < errorThreshold) {
                    stableIntervalsCount++;
                    if (stableIntervalsCount >= STABLE_INTERVALS_REQUIRED) {
                        // Found stable point
                        stableTps = currentTps;
                        transitionPhase(Phase.SUSTAIN, elapsedMillis);
                    }
                } else {
                    // Still errors, continue ramping down
                    stableIntervalsCount = 0;  // Reset counter
                    currentTps = Math.max(0, currentTps - rampDecrement);
                }
            }
            case SUSTAIN -> {
                // Monitor during sustain, but don't adjust
                // After sustain duration, pattern continues indefinitely
            }
            case COMPLETE -> {
                // No adjustments in complete phase
            }
        }
        
        lastAdjustmentTime = elapsedMillis;
    }
    
    private void transitionPhase(Phase newPhase, long elapsedMillis) {
        if (currentPhase != newPhase) {
            currentPhase = newPhase;
            phaseStartTime = elapsedMillis;
            phaseTransitionCount++;
            if (newPhase == Phase.SUSTAIN) {
                stableIntervalsCount = 0;  // Reset for sustain phase
            }
        }
    }
    
    @Override
    public Duration getDuration() {
        // Return a very long duration to support indefinite execution after sustain
        // External duration limits (e.g., from ExecutionEngine) will still apply
        return Duration.ofDays(365);
    }
    
    /**
     * Gets the current phase.
     * 
     * @return current phase
     */
    public Phase getCurrentPhase() {
        return currentPhase;
    }
    
    /**
     * Gets the current target TPS.
     * 
     * @return current TPS
     */
    public double getCurrentTps() {
        return currentTps;
    }
    
    /**
     * Gets the stable TPS found (if any).
     * 
     * @return stable TPS, or -1 if not found yet
     */
    public double getStableTps() {
        return stableTps >= 0 ? stableTps : -1.0;
    }
    
    /**
     * Gets the number of phase transitions that have occurred.
     * 
     * @return phase transition count
     */
    public long getPhaseTransitionCount() {
        return phaseTransitionCount;
    }
}

