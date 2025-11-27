package com.vajrapulse.api;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

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
 * <p><strong>Memory Ordering Guarantees:</strong>
 * <ul>
 *   <li>State updates use {@link AtomicReference#updateAndGet(java.util.function.UnaryOperator)}
 *       which provides sequential consistency (strongest memory ordering)</li>
 *   <li>State reads use {@link AtomicReference#get()} which provides volatile read semantics</li>
 *   <li>All state transitions are atomic and visible to all threads immediately</li>
 *   <li>The immutable {@code AdaptiveState} record ensures no partial state visibility</li>
 * </ul>
 * 
 * <p><strong>Concurrency Model:</strong>
 * <ul>
 *   <li>Multiple threads can call {@code calculateTps()} concurrently</li>
 *   <li>State updates are lock-free using compare-and-swap operations</li>
 *   <li>No blocking or synchronization overhead</li>
 *   <li>Race conditions are handled by retrying state updates if needed</li>
 * </ul>
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
    
    // Immutable state stored atomically
    private final AtomicReference<AdaptiveState> state;
    
    /**
     * Maximum number of ramp-down attempts before giving up and transitioning to COMPLETE phase.
     */
    private static final int MAX_RAMP_DOWN_ATTEMPTS = 10;
    
    /**
     * Number of consecutive stable intervals required to identify a stable TPS point.
     */
    private static final int STABLE_INTERVALS_REQUIRED = 3;
    
    /**
     * Conversion factor from percentage to ratio (100.0% = 1.0).
     */
    private static final double PERCENTAGE_TO_RATIO = 100.0;
    
    /**
     * Immutable state record for thread-safe updates.
     */
    private record AdaptiveState(
        Phase phase,
        double currentTps,
        long lastAdjustmentTime,
        double stableTps,  // -1 means not found yet
        long phaseStartTime,
        int stableIntervalsCount,
        int rampDownAttempts,
        long phaseTransitionCount
    ) {
        AdaptiveState {
            // Compact constructor validation
            if (phase == null) {
                throw new IllegalArgumentException("Phase must not be null");
            }
        }
    }
    
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
        
        // Validate parameters with descriptive error messages
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
        if (sustainDuration == null || sustainDuration.isNegative() || sustainDuration.isZero()) {
            throw new IllegalArgumentException("Sustain duration must be positive, got: " + sustainDuration);
        }
        if (errorThreshold < 0.0 || errorThreshold > 1.0) {
            throw new IllegalArgumentException("Error threshold must be between 0.0 and 1.0, got: " + errorThreshold);
        }
        
        this.initialTps = initialTps;
        this.rampIncrement = rampIncrement;
        this.rampDecrement = rampDecrement;
        this.rampInterval = rampInterval;
        this.maxTps = maxTps;
        this.sustainDuration = sustainDuration;
        this.errorThreshold = errorThreshold;
        this.metricsProvider = java.util.Objects.requireNonNull(metricsProvider, "Metrics provider must not be null");
        
        // Initialize state atomically
        this.state = new AtomicReference<>(new AdaptiveState(
            Phase.RAMP_UP,
            initialTps,
            -1L,  // Will be set on first call
            -1.0,  // Not found yet
            -1L,  // Will be set on first call
            0,
            0,
            0L
        ));
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        if (elapsedMillis < 0) {
            return 0.0;
        }
        
        AdaptiveState current = state.get();
        
        // Initialize on first call
        if (current.lastAdjustmentTime() < 0) {
            state.updateAndGet(s -> new AdaptiveState(
                s.phase(),
                s.currentTps(),
                0L,
                s.stableTps(),
                0L,
                s.stableIntervalsCount(),
                s.rampDownAttempts(),
                s.phaseTransitionCount()
            ));
            current = state.get();
        }
        
        // Check if it's time to adjust
        long timeSinceLastAdjustment = elapsedMillis - current.lastAdjustmentTime();
        if (timeSinceLastAdjustment >= rampInterval.toMillis()) {
            checkAndAdjust(elapsedMillis);
            current = state.get(); // Refresh after adjustment
        }
        
        // Handle phase-specific logic
        return switch (current.phase()) {
            case RAMP_UP -> handleRampUp(elapsedMillis, current);
            case RAMP_DOWN -> handleRampDown(elapsedMillis, current);
            case SUSTAIN -> handleSustain(elapsedMillis, current);
            case COMPLETE -> 0.0;  // Test ends (only if stable point never found)
        };
    }
    
    private double handleRampUp(long elapsedMillis, AdaptiveState current) {
        // Check if we've hit max TPS
        if (current.currentTps() >= maxTps) {
            // Treat max TPS as stable point
            transitionPhase(Phase.SUSTAIN, elapsedMillis, maxTps);
            return maxTps;
        }
        
        return current.currentTps();
    }
    
    private double handleRampDown(long elapsedMillis, AdaptiveState current) {
        // Check if we've exhausted attempts
        if (current.rampDownAttempts() >= MAX_RAMP_DOWN_ATTEMPTS) {
            transitionPhase(Phase.COMPLETE, elapsedMillis, current.stableTps());
            return 0.0;
        }
        
        return current.currentTps();
    }
    
    private double handleSustain(long elapsedMillis, AdaptiveState current) {
        // After sustain duration, continue indefinitely at stable TPS
        long sustainElapsed = elapsedMillis - current.phaseStartTime();
        if (sustainElapsed >= sustainDuration.toMillis()) {
            // Continue at stable TPS indefinitely
            return current.stableTps();
        }
        return current.stableTps();
    }
    
    /**
     * Interval for batching metrics queries to reduce overhead.
     * Metrics are queried at most once per 100ms to balance responsiveness and performance.
     */
    private static final long METRICS_QUERY_BATCH_INTERVAL_MS = 100L;
    
    // Cached metrics to reduce query frequency
    private volatile double cachedFailureRate;
    private volatile long cachedMetricsTimeMillis = -1L;
    
    private void checkAndAdjust(long elapsedMillis) {
        // Batch metrics queries to reduce overhead
        // Query metrics at most once per METRICS_QUERY_BATCH_INTERVAL_MS
        double errorRate;
        if (cachedMetricsTimeMillis < 0 || (elapsedMillis - cachedMetricsTimeMillis) >= METRICS_QUERY_BATCH_INTERVAL_MS) {
            cachedFailureRate = metricsProvider.getFailureRate() / PERCENTAGE_TO_RATIO;  // Convert percentage to ratio
            cachedMetricsTimeMillis = elapsedMillis;
            errorRate = cachedFailureRate;
        } else {
            errorRate = cachedFailureRate;
        }
        
        state.updateAndGet(current -> {
            return switch (current.phase()) {
                case RAMP_UP -> {
                    if (errorRate >= errorThreshold) {
                        // Errors detected, start ramping down
                        double newTps = Math.max(0, current.currentTps() - rampDecrement);
                        yield transitionPhaseInternal(current, Phase.RAMP_DOWN, elapsedMillis, current.stableTps(), newTps);
                    } else {
                        // No errors, continue ramping up
                        double newTps = Math.min(maxTps, current.currentTps() + rampIncrement);
                        yield new AdaptiveState(
                            current.phase(),
                            newTps,
                            elapsedMillis,
                            current.stableTps(),
                            current.phaseStartTime(),
                            current.stableIntervalsCount(),
                            current.rampDownAttempts(),
                            current.phaseTransitionCount()
                        );
                    }
                }
                case RAMP_DOWN -> {
                    int newAttempts = current.rampDownAttempts() + 1;
                    if (errorRate < errorThreshold) {
                        int newStableCount = current.stableIntervalsCount() + 1;
                        if (newStableCount >= STABLE_INTERVALS_REQUIRED) {
                            // Found stable point
                            double stable = current.currentTps();
                            yield transitionPhaseInternal(current, Phase.SUSTAIN, elapsedMillis, stable, stable);
                        } else {
                            // Not stable yet, keep current TPS
                            yield new AdaptiveState(
                                current.phase(),
                                current.currentTps(),
                                elapsedMillis,
                                current.stableTps(),
                                current.phaseStartTime(),
                                newStableCount,
                                newAttempts,
                                current.phaseTransitionCount()
                            );
                        }
                    } else {
                        // Still errors, continue ramping down
                        double newTps = Math.max(0, current.currentTps() - rampDecrement);
                        yield new AdaptiveState(
                            current.phase(),
                            newTps,
                            elapsedMillis,
                            current.stableTps(),
                            current.phaseStartTime(),
                            0,  // Reset counter
                            newAttempts,
                            current.phaseTransitionCount()
                        );
                    }
                }
                case SUSTAIN -> {
                    // Monitor during sustain, but don't adjust
                    yield new AdaptiveState(
                        current.phase(),
                        current.currentTps(),
                        elapsedMillis,
                        current.stableTps(),
                        current.phaseStartTime(),
                        current.stableIntervalsCount(),
                        current.rampDownAttempts(),
                        current.phaseTransitionCount()
                    );
                }
                case COMPLETE -> {
                    // No adjustments in complete phase
                    yield new AdaptiveState(
                        current.phase(),
                        current.currentTps(),
                        elapsedMillis,
                        current.stableTps(),
                        current.phaseStartTime(),
                        current.stableIntervalsCount(),
                        current.rampDownAttempts(),
                        current.phaseTransitionCount()
                    );
                }
            };
        });
    }
    
    private AdaptiveState transitionPhaseInternal(AdaptiveState current, Phase newPhase, long elapsedMillis, double stableTps, double newTps) {
        if (current.phase() != newPhase) {
            return new AdaptiveState(
                newPhase,
                newTps,
                elapsedMillis,
                stableTps,
                elapsedMillis,  // New phase start time
                newPhase == Phase.SUSTAIN ? 0 : current.stableIntervalsCount(),  // Reset for sustain
                current.rampDownAttempts(),
                current.phaseTransitionCount() + 1
            );
        }
        // Same phase, just update TPS and time
        return new AdaptiveState(
            current.phase(),
            newTps,
            elapsedMillis,
            stableTps,
            current.phaseStartTime(),
            current.stableIntervalsCount(),
            current.rampDownAttempts(),
            current.phaseTransitionCount()
        );
    }
    
    private void transitionPhase(Phase newPhase, long elapsedMillis, double stableTps) {
        state.updateAndGet(current -> transitionPhaseInternal(current, newPhase, elapsedMillis, stableTps, current.currentTps()));
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
        return state.get().phase();
    }
    
    /**
     * Gets the current target TPS.
     * 
     * @return current TPS
     */
    public double getCurrentTps() {
        return state.get().currentTps();
    }
    
    /**
     * Gets the stable TPS found (if any).
     * 
     * @return stable TPS, or -1 if not found yet
     */
    public double getStableTps() {
        double stable = state.get().stableTps();
        return stable >= 0 ? stable : -1.0;
    }
    
    /**
     * Gets the number of phase transitions that have occurred.
     * 
     * @return phase transition count
     */
    public long getPhaseTransitionCount() {
        return state.get().phaseTransitionCount();
    }
}

