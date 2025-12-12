package com.vajrapulse.api.pattern.adaptive;

import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.metrics.MetricsProvider;
import com.vajrapulse.api.metrics.BackpressureProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
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
    
    private final AdaptiveConfig config;
    private final MetricsProvider metricsProvider;
    private final BackpressureProvider backpressureProvider; // Optional
    private final RampDecisionPolicy decisionPolicy;
    private final List<AdaptivePatternListener> listeners;
    
    // Phase strategies
    private final Map<AdaptivePhase, PhaseStrategy> strategies;
    
    // Immutable state stored atomically
    private final AtomicReference<AdaptiveState> state;
    
    /**
     * Conversion factor from percentage to ratio (100.0% = 1.0).
     */
    private static final double PERCENTAGE_TO_RATIO = 100.0;
    
    /**
     * Creates a new adaptive load pattern with configuration.
     * 
     * @param config configuration for the adaptive pattern (must not be null)
     * @param metricsProvider provides execution metrics (must not be null)
     * @throws IllegalArgumentException if any parameter is invalid
     * @since 0.9.9
     */
    public AdaptiveLoadPattern(
            AdaptiveConfig config,
            MetricsProvider metricsProvider) {
        this(config, metricsProvider, null, List.of());
    }
    
    /**
     * Creates a new adaptive load pattern with configuration and backpressure provider.
     * 
     * @param config configuration for the adaptive pattern (must not be null)
     * @param metricsProvider provides execution metrics (must not be null)
     * @param backpressureProvider optional backpressure provider for additional signals
     * @throws IllegalArgumentException if any parameter is invalid
     * @since 0.9.9
     */
    public AdaptiveLoadPattern(
            AdaptiveConfig config,
            MetricsProvider metricsProvider,
            BackpressureProvider backpressureProvider) {
        this(config, metricsProvider, backpressureProvider, List.of());
    }
    
    /**
     * Creates a new adaptive load pattern with configuration, backpressure provider, and listeners.
     * 
     * @param config configuration for the adaptive pattern (must not be null)
     * @param metricsProvider provides execution metrics (must not be null)
     * @param backpressureProvider optional backpressure provider for additional signals
     * @param listeners list of event listeners (must not be null, can be empty)
     * @throws IllegalArgumentException if any parameter is invalid
     * @since 0.9.9
     */
    public AdaptiveLoadPattern(
            AdaptiveConfig config,
            MetricsProvider metricsProvider,
            BackpressureProvider backpressureProvider,
            List<AdaptivePatternListener> listeners) {
        this.config = java.util.Objects.requireNonNull(config, "Config must not be null");
        this.metricsProvider = java.util.Objects.requireNonNull(metricsProvider, "Metrics provider must not be null");
        this.backpressureProvider = backpressureProvider; // Can be null
        this.listeners = new CopyOnWriteArrayList<>(
            java.util.Objects.requireNonNull(listeners, "Listeners must not be null")
        );
        this.decisionPolicy = new DefaultRampDecisionPolicy(
            config.errorThreshold(),
            config.backpressureRampUpThreshold(),
            config.backpressureRampDownThreshold(),
            config.stableIntervalsRequired()
        );
        
        // Initialize phase strategies
        this.strategies = new EnumMap<>(AdaptivePhase.class);
        this.strategies.put(AdaptivePhase.RAMP_UP, new RampUpStrategy());
        this.strategies.put(AdaptivePhase.RAMP_DOWN, new RampDownStrategy());
        this.strategies.put(AdaptivePhase.SUSTAIN, new SustainStrategy());
        
        // Initialize state atomically
        AdaptiveCoreState core = new AdaptiveCoreState(
            AdaptivePhase.RAMP_UP,
            config.initialTps(),
            -1L,  // Will be set on first call
            -1L,  // Will be set on first call
            0,    // rampDownAttempts
            0L    // phaseTransitionCount
        );
        this.state = new AtomicReference<>(new AdaptiveState(
            core,
            AdaptiveStabilityTracking.empty(),
            new AdaptiveRecoveryTracking(config.initialTps(), -1)  // Initialize lastKnownGoodTps to initialTps
        ));
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
     * @deprecated Use {@link #AdaptiveLoadPattern(AdaptiveConfig, MetricsProvider)} instead
     */
    @Deprecated
    public AdaptiveLoadPattern(
            double initialTps,
            double rampIncrement,
            double rampDecrement,
            Duration rampInterval,
            double maxTps,
            Duration sustainDuration,
            double errorThreshold,
            MetricsProvider metricsProvider) {
        this(createConfigFromParams(initialTps, rampIncrement, rampDecrement, rampInterval, maxTps, 0.0, sustainDuration, errorThreshold), metricsProvider, null, List.of());
    }
    
    /**
     * Helper method to create config from constructor parameters.
     */
    private static AdaptiveConfig createConfigFromParams(
            double initialTps,
            double rampIncrement,
            double rampDecrement,
            Duration rampInterval,
            double maxTps,
            double minTps,
            Duration sustainDuration,
            double errorThreshold) {
        return new AdaptiveConfig(
            initialTps,
            rampIncrement,
            rampDecrement,
            rampInterval,
            maxTps,
            minTps,
            sustainDuration,
            errorThreshold,
            0.3,  // Default backpressure thresholds
            0.7,
            3,    // Default stable intervals
            50.0, // Default TPS tolerance
            0.5   // Default recovery ratio
        );
    }
    
    /**
     * Creates a new adaptive load pattern with backpressure provider.
     * 
     * @param initialTps starting TPS (must be positive)
     * @param rampIncrement TPS increase per interval (must be positive)
     * @param rampDecrement TPS decrease per interval (must be positive)
     * @param rampInterval time between adjustments (must be positive)
     * @param maxTps maximum TPS limit (must be positive or Double.POSITIVE_INFINITY)
     * @param sustainDuration duration to sustain at stable point (must be positive)
     * @param errorThreshold error rate threshold 0.0-1.0 (must be between 0 and 1)
     * @param metricsProvider provides execution metrics (must not be null)
     * @param backpressureProvider optional backpressure provider for additional signals
     * @throws IllegalArgumentException if any parameter is invalid
     * @deprecated Use {@link #AdaptiveLoadPattern(AdaptiveConfig, MetricsProvider, BackpressureProvider)} instead
     * @since 0.9.6
     */
    @Deprecated
    public AdaptiveLoadPattern(
            double initialTps,
            double rampIncrement,
            double rampDecrement,
            Duration rampInterval,
            double maxTps,
            Duration sustainDuration,
            double errorThreshold,
            MetricsProvider metricsProvider,
            BackpressureProvider backpressureProvider) {
        this(createConfigFromParams(initialTps, rampIncrement, rampDecrement, rampInterval, maxTps, 0.0, sustainDuration, errorThreshold), metricsProvider, backpressureProvider, List.of());
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        if (elapsedMillis < 0) {
            return 0.0;
        }
        
        AdaptiveState current = state.get();
        
        // Initialize on first call
        if (current.lastAdjustmentTime() < 0) {
            state.updateAndGet(s -> {
                AdaptiveCoreState newCore = new AdaptiveCoreState(
                    s.core().phase(),
                    s.core().currentTps(),
                    0L,
                    0L,
                    s.core().rampDownAttempts(),
                    s.core().phaseTransitionCount()
                );
                return new AdaptiveState(newCore, s.stability(), s.recovery());
            });
            current = state.get();
        }
        
        // Check if it's time to adjust
        long timeSinceLastAdjustment = elapsedMillis - current.lastAdjustmentTime();
        if (timeSinceLastAdjustment >= config.rampInterval().toMillis()) {
            checkAndAdjust(elapsedMillis);
            current = state.get(); // Refresh after adjustment
        }
        
        // Handle phase-specific logic using strategy pattern
        PhaseStrategy strategy = strategies.get(current.phase());
        if (strategy == null) {
            // Fallback to current TPS if strategy not found (should never happen)
            return current.currentTps();
        }
        
        // Create context for strategy
        MetricsSnapshot metrics = captureMetricsSnapshot(elapsedMillis);
        PhaseContext context = new PhaseContext(
            current, config, metrics, decisionPolicy, this
        );
        
        return strategy.handle(context, elapsedMillis);
    }
    
    /**
     * Gets the current backpressure level from the provider.
     * 
     * @return backpressure level (0.0 if no provider)
     */
    public double getBackpressureLevel() {
        if (backpressureProvider == null) {
            return 0.0;
        }
        return backpressureProvider.getBackpressureLevel();
    }
    
    /**
     * Checks if the current TPS level is stable.
     * 
     * <p>A TPS level is considered stable if:
     * <ul>
     *   <li>Error rate &lt; config.errorThreshold()</li>
     *   <li>Backpressure &lt; 0.3</li>
     *   <li>TPS hasn't changed significantly (within tolerance)</li>
     *   <li>Stable conditions maintained for required duration (3 intervals)</li>
     * </ul>
     * 
     * <p>This method enables intermediate stability detection, allowing the pattern
     * to sustain at optimal TPS levels (not just MAX_TPS). Stability can be detected
     * during both RAMP_UP and RAMP_DOWN phases.
     * 
     * @param currentTps the current TPS
     * @param elapsedMillis elapsed time since start
     * @return true if stable, false otherwise
     * @since 0.9.8
     */
    boolean isStableAtCurrentTps(double currentTps, long elapsedMillis) {
        // Capture metrics snapshot for policy
        // Use elapsedMillis directly (should always be positive in normal operation)
        MetricsSnapshot metrics = captureMetricsSnapshot(elapsedMillis);
        
        // Check if conditions are good using policy
        boolean conditionsGood = decisionPolicy.shouldRampUp(metrics);
        
        // Get current state to access stability tracking fields
        AdaptiveState currentState = state.get();
        AdaptiveStabilityTracking stability = currentState.stability();
        double candidate = stability != null && stability.isTracking() ? stability.candidateTps() : -1;
        long startTime = stability != null && stability.isTracking() ? stability.candidateStartTime() : -1;
        
        if (!conditionsGood) {
            // Conditions not good - reset stability tracking atomically
            state.updateAndGet(s -> {
                AdaptiveStabilityTracking resetStability = AdaptiveStabilityTracking.empty();
                return new AdaptiveState(s.core(), resetStability, s.recovery());
            });
            return false;
        }
        
        // Check if TPS is within tolerance of candidate
        if (candidate < 0 || Math.abs(currentTps - candidate) > config.tpsTolerance()) {
            // New candidate or TPS changed significantly - update atomically
            state.updateAndGet(s -> {
                AdaptiveStabilityTracking newStability = new AdaptiveStabilityTracking(
                    s.stability() != null ? s.stability().stableTps() : -1,
                    currentTps,  // New candidate
                    elapsedMillis,  // New start time
                    s.stability() != null ? s.stability().stableIntervalsCount() : 0
                );
                return new AdaptiveState(s.core(), newStability, s.recovery());
            });
            return false;
        }
        
        // Check if stable for required duration
        // Use 3 intervals worth of time for stability detection (same as old logic)
        long stabilityDuration = elapsedMillis - startTime;
        long requiredStabilityDuration = config.rampInterval().toMillis() * config.stableIntervalsRequired();
        return stabilityDuration >= requiredStabilityDuration;
    }
    
    /**
     * Interval for batching metrics queries to reduce overhead.
     * Metrics are queried at most once per 100ms to balance responsiveness and performance.
     */
    private static final long METRICS_QUERY_BATCH_INTERVAL_MS = 100L;
    
    // Cached metrics to reduce query frequency
    private volatile MetricsSnapshot cachedMetricsSnapshot;
    private volatile long cachedMetricsTimeMillis = -1L;
    
    /**
     * Captures a metrics snapshot, using cache if available.
     * 
     * @param elapsedMillis current elapsed time
     * @return metrics snapshot
     */
    private MetricsSnapshot captureMetricsSnapshot(long elapsedMillis) {
        // Batch metrics queries to reduce overhead
        // Query metrics at most once per METRICS_QUERY_BATCH_INTERVAL_MS
        if (cachedMetricsTimeMillis < 0 || (elapsedMillis - cachedMetricsTimeMillis) >= METRICS_QUERY_BATCH_INTERVAL_MS) {
            double failureRate = metricsProvider.getFailureRate() / PERCENTAGE_TO_RATIO;  // Convert percentage to ratio
            double recentFailureRate = metricsProvider.getRecentFailureRate(10) / PERCENTAGE_TO_RATIO;  // 10 second window
            double backpressure = getBackpressureLevel();
            long totalExecutions = metricsProvider.getTotalExecutions();
            
            cachedMetricsSnapshot = new MetricsSnapshot(
                failureRate,
                recentFailureRate,
                backpressure,
                totalExecutions
            );
            cachedMetricsTimeMillis = elapsedMillis;
        }
        return cachedMetricsSnapshot;
    }
    
    private void checkAndAdjust(long elapsedMillis) {
        // Capture metrics snapshot (cached for performance)
        MetricsSnapshot metrics = captureMetricsSnapshot(elapsedMillis);
        
        // Use policy for decisions
        boolean shouldRampDown = decisionPolicy.shouldRampDown(metrics);
        boolean canRampUp = decisionPolicy.shouldRampUp(metrics);
        
        state.updateAndGet(current -> {
            return switch (current.phase()) {
                case RAMP_UP -> {
                    if (shouldRampDown) {
                        // Errors or backpressure detected, start ramping down
                        double newTps = Math.max(config.minTps(), current.currentTps() - config.rampDecrement());
                        yield transitionPhaseInternal(current, AdaptivePhase.RAMP_DOWN, elapsedMillis, current.stableTps(), newTps);
                    } else if (canRampUp) {
                        // No errors, no backpressure - continue ramping up
                        double newTps = Math.min(config.maxTps(), current.currentTps() + config.rampIncrement());
                        AdaptiveCoreState newCore = new AdaptiveCoreState(
                            current.core().phase(),
                            newTps,
                            elapsedMillis,
                            current.core().phaseStartTime(),
                            current.core().rampDownAttempts(),
                            current.core().phaseTransitionCount()
                        );
                        yield new AdaptiveState(newCore, current.stability(), current.recovery());
                    } else {
                        // Moderate backpressure - hold current TPS
                        AdaptiveCoreState newCore = new AdaptiveCoreState(
                            current.core().phase(),
                            current.currentTps(),
                            elapsedMillis,
                            current.core().phaseStartTime(),
                            current.core().rampDownAttempts(),
                            current.core().phaseTransitionCount()
                        );
                        yield new AdaptiveState(newCore, current.stability(), current.recovery());
                    }
                }
                case RAMP_DOWN -> {
                    int newAttempts = current.core().rampDownAttempts() + 1;
                    
                    // Check if we're at minimum TPS - handle recovery behavior
                    boolean atMinimum = current.currentTps() <= config.minTps();
                    
                    if (atMinimum) {
                        // At minimum TPS - check if we can recover using policy
                        boolean canRecover = decisionPolicy.canRecoverFromMinimum(metrics);
                        
                        if (canRecover) {
                            // Conditions improved - transition to RAMP_UP
                            // Calculate recovery TPS: 50% of last known good, but at least minimum
                            double lastKnownGoodTps = current.lastKnownGoodTps() > 0 
                                ? current.lastKnownGoodTps() 
                                : config.initialTps();
                            double recoveryTps = Math.max(config.minTps(), lastKnownGoodTps * config.recoveryTpsRatio());
                            yield transitionPhaseInternal(current, AdaptivePhase.RAMP_UP, elapsedMillis, current.stableTps(), recoveryTps);
                        } else {
                            // Stay at minimum in RAMP_DOWN (recovery mode)
                            AdaptiveCoreState newCore = new AdaptiveCoreState(
                                current.core().phase(),
                                config.minTps(),
                                elapsedMillis,
                                current.core().phaseStartTime(),
                                newAttempts,
                                current.core().phaseTransitionCount()
                            );
                            // Update recovery tracking to mark we're in recovery
                            AdaptiveRecoveryTracking updatedRecovery = current.recovery();
                            if (updatedRecovery == null || !updatedRecovery.isInRecovery()) {
                                // Entering recovery for the first time
                                double lastKnownGood = current.lastKnownGoodTps() > 0 ? current.lastKnownGoodTps() : config.initialTps();
                                updatedRecovery = new AdaptiveRecoveryTracking(lastKnownGood, elapsedMillis);
                                
                                // Notify recovery entry
                                notifyRecovery(lastKnownGood, config.minTps());
                            }
                            // Reset stability counter
                            AdaptiveStabilityTracking resetStability = AdaptiveStabilityTracking.empty();
                            yield new AdaptiveState(newCore, resetStability, updatedRecovery);
                        }
                    } else if (!shouldRampDown) {
                        // Not at minimum, and errors/backpressure cleared - check for stability
                        double currentTps = current.currentTps();
                        AdaptiveStabilityTracking currentStability = current.stability();
                        int currentStableCount = current.stableIntervalsCount();
                        
                        // Check if candidate TPS matches current TPS (within tolerance)
                        boolean candidateMatches = currentStability != null 
                            && currentStability.isTracking()
                            && Math.abs(currentStability.candidateTps() - currentTps) <= config.tpsTolerance();
                        
                        // If candidate doesn't match, reset tracking with new candidate
                        // Otherwise, increment stable count
                        AdaptiveStabilityTracking updatedStability;
                        if (!candidateMatches) {
                            // New candidate - reset tracking
                            updatedStability = new AdaptiveStabilityTracking(
                                currentStability != null ? currentStability.stableTps() : -1,
                                currentTps,
                                elapsedMillis,
                                1  // First stable interval
                            );
                        } else {
                            // Same candidate - increment count
                            int newStableCount = currentStableCount + 1;
                            updatedStability = new AdaptiveStabilityTracking(
                                currentStability.stableTps(),
                                currentStability.candidateTps(),
                                currentStability.candidateStartTime(),
                                newStableCount
                            );
                        }
                        
                        if (decisionPolicy.shouldSustain(metrics, updatedStability)) {
                            // Found stable point at current TPS
                            double stable = current.currentTps();
                            yield transitionPhaseInternal(current, AdaptivePhase.SUSTAIN, elapsedMillis, stable, stable);
                        } else {
                            // Not stable yet, keep current TPS and continue in RAMP_DOWN
                            // Update state with incremented stable count
                            AdaptiveCoreState newCore = new AdaptiveCoreState(
                                current.core().phase(),
                                current.currentTps(),
                                elapsedMillis,
                                current.core().phaseStartTime(),
                                newAttempts,
                                current.core().phaseTransitionCount()
                            );
                            yield new AdaptiveState(newCore, updatedStability, current.recovery());
                        }
                    } else {
                        // Still errors or backpressure, continue ramping down
                        double newTps = Math.max(config.minTps(), current.currentTps() - config.rampDecrement());
                        AdaptiveCoreState newCore = new AdaptiveCoreState(
                            current.core().phase(),
                            newTps,
                            elapsedMillis,
                            current.core().phaseStartTime(),
                            newAttempts,
                            current.core().phaseTransitionCount()
                        );
                        // Reset stability counter
                        AdaptiveStabilityTracking resetStability = AdaptiveStabilityTracking.empty();
                        yield new AdaptiveState(newCore, resetStability, current.recovery());
                    }
                }
                case SUSTAIN -> {
                    // Check if conditions changed during sustain
                    if (shouldRampDown) {
                        // Conditions worsened - ramp down immediately
                        double newTps = Math.max(config.minTps(), current.currentTps() - config.rampDecrement());
                        yield transitionPhaseInternal(current, AdaptivePhase.RAMP_DOWN, elapsedMillis, current.stableTps(), newTps);
                    } else if (canRampUp && current.currentTps() < config.maxTps()) {
                        // Conditions good and below max - ramp up
                        yield transitionPhaseInternal(current, AdaptivePhase.RAMP_UP, elapsedMillis, current.stableTps(), current.currentTps());
                    } else {
                        // Stay in SUSTAIN
                        AdaptiveCoreState newCore = new AdaptiveCoreState(
                            current.core().phase(),
                            current.currentTps(),
                            elapsedMillis,
                            current.core().phaseStartTime(),
                            current.core().rampDownAttempts(),
                            current.core().phaseTransitionCount()
                        );
                        yield new AdaptiveState(newCore, current.stability(), current.recovery());
                    }
                }
            };
        });
    }
    
    AdaptiveState transitionPhaseInternal(AdaptiveState current, AdaptivePhase newPhase, long elapsedMillis, double stableTps, double newTps) {
        AdaptivePhase oldPhase = current.phase();
        double currentTps = current.currentTps();
        
        // Notify phase transition if phase changed
        if (oldPhase != newPhase) {
            notifyPhaseTransition(oldPhase, newPhase, currentTps);
        }
        
        // Notify TPS change if significant
        if (Math.abs(newTps - currentTps) > config.tpsTolerance()) {
            notifyTpsChange(currentTps, newTps);
        }
        
        // Update lastKnownGoodTps when transitioning to RAMP_DOWN
        AdaptiveRecoveryTracking updatedRecovery = current.recovery();
        double lastKnownGood = current.lastKnownGoodTps();
        
        if (newPhase == AdaptivePhase.RAMP_DOWN) {
            // Update last known good TPS before transitioning to RAMP_DOWN
            if (currentTps > lastKnownGood) {
                // Update last known good, preserve recovery start time if already in recovery
                long recoveryStartTime = updatedRecovery != null ? updatedRecovery.recoveryStartTime() : -1;
                updatedRecovery = new AdaptiveRecoveryTracking(currentTps, recoveryStartTime);
            } else if (updatedRecovery != null) {
                // Ramping down - preserve last known good but clear recovery start time if not at minimum
                // (Recovery start time is set in checkAndAdjust when TPS reaches minimum)
                updatedRecovery = new AdaptiveRecoveryTracking(updatedRecovery.lastKnownGoodTps(), -1);
            }
        } else if (newPhase == AdaptivePhase.RAMP_UP && updatedRecovery != null && updatedRecovery.isInRecovery()) {
            // Exiting recovery (transitioning to RAMP_UP) - clear recovery start time
            updatedRecovery = new AdaptiveRecoveryTracking(updatedRecovery.lastKnownGoodTps(), -1);
        }
        
        // Notify stability detection if transitioning to SUSTAIN with a stable TPS
        if (newPhase == AdaptivePhase.SUSTAIN && stableTps >= 0) {
            notifyStabilityDetected(stableTps);
        }
        
        // Update stability tracking
        AdaptiveStabilityTracking updatedStability = current.stability();
        if (newPhase == AdaptivePhase.SUSTAIN && stableTps >= 0) {
            // Found stable TPS
            updatedStability = new AdaptiveStabilityTracking(stableTps, -1, -1, 0);
        } else if (newPhase == AdaptivePhase.SUSTAIN) {
            // Reset stable intervals count for sustain
            updatedStability = updatedStability != null 
                ? new AdaptiveStabilityTracking(updatedStability.stableTps(), updatedStability.candidateTps(), updatedStability.candidateStartTime(), 0)
                : AdaptiveStabilityTracking.empty();
        }
        
        if (current.phase() != newPhase) {
            // Phase transition
            AdaptiveCoreState newCore = new AdaptiveCoreState(
                newPhase,
                newTps,
                elapsedMillis,
                elapsedMillis,  // New phase start time
                current.core().rampDownAttempts(),
                current.core().phaseTransitionCount() + 1
            );
            return new AdaptiveState(newCore, updatedStability, updatedRecovery);
        }
        
        // Same phase, just update TPS and time
        AdaptiveCoreState newCore = new AdaptiveCoreState(
            current.core().phase(),
            newTps,
            elapsedMillis,
            current.core().phaseStartTime(),
            current.core().rampDownAttempts(),
            current.core().phaseTransitionCount()
        );
        return new AdaptiveState(newCore, updatedStability, updatedRecovery);
    }
    
    void transitionPhase(AdaptivePhase newPhase, long elapsedMillis, double stableTps) {
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
    public AdaptivePhase getCurrentPhase() {
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
    
    /**
     * Notifies listeners of a phase transition.
     * 
     * @param from previous phase
     * @param to new phase
     * @param tps TPS at time of transition
     */
    private void notifyPhaseTransition(AdaptivePhase from, AdaptivePhase to, double tps) {
        if (listeners.isEmpty()) {
            return;
        }
        
        PhaseTransitionEvent event = new PhaseTransitionEvent(
            from, to, tps, System.currentTimeMillis()
        );
        
        for (AdaptivePatternListener listener : listeners) {
            try {
                listener.onPhaseTransition(event);
            } catch (Exception e) {
                // Log but don't fail - listener errors shouldn't break the pattern
                System.err.println("Listener error in onPhaseTransition: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Notifies listeners of a significant TPS change.
     * 
     * @param previousTps previous TPS value
     * @param newTps new TPS value
     */
    private void notifyTpsChange(double previousTps, double newTps) {
        if (listeners.isEmpty()) {
            return;
        }
        
        // Only notify if change is significant (more than tolerance)
        if (Math.abs(newTps - previousTps) <= config.tpsTolerance()) {
            return;
        }
        
        TpsChangeEvent event = new TpsChangeEvent(
            previousTps, newTps, System.currentTimeMillis()
        );
        
        for (AdaptivePatternListener listener : listeners) {
            try {
                listener.onTpsChange(event);
            } catch (Exception e) {
                // Log but don't fail
                System.err.println("Listener error in onTpsChange: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Notifies listeners that stability was detected.
     * 
     * @param stableTps the detected stable TPS value
     */
    private void notifyStabilityDetected(double stableTps) {
        if (listeners.isEmpty()) {
            return;
        }
        
        StabilityDetectedEvent event = new StabilityDetectedEvent(
            stableTps, System.currentTimeMillis()
        );
        
        for (AdaptivePatternListener listener : listeners) {
            try {
                listener.onStabilityDetected(event);
            } catch (Exception e) {
                // Log but don't fail
                System.err.println("Listener error in onStabilityDetected: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Notifies listeners that recovery mode was entered.
     * 
     * @param lastKnownGoodTps last known good TPS before recovery
     * @param recoveryTps TPS used for recovery
     */
    private void notifyRecovery(double lastKnownGoodTps, double recoveryTps) {
        if (listeners.isEmpty()) {
            return;
        }
        
        RecoveryEvent event = new RecoveryEvent(
            lastKnownGoodTps, recoveryTps, System.currentTimeMillis()
        );
        
        for (AdaptivePatternListener listener : listeners) {
            try {
                listener.onRecovery(event);
            } catch (Exception e) {
                // Log but don't fail
                System.err.println("Listener error in onRecovery: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Creates a new builder for constructing an AdaptiveLoadPattern.
     * 
     * <p>Example:
     * <pre>{@code
     * AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
     *     .initialTps(100.0)
     *     .rampIncrement(50.0)
     *     .errorThreshold(0.01)
     *     .metricsProvider(metrics)
     *     .build();
     * }</pre>
     * 
     * @return new builder instance
     * @since 0.9.9
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for constructing AdaptiveLoadPattern instances.
     * 
     * <p>This builder provides a fluent API for configuring all aspects
     * of the adaptive load pattern. Configuration starts with sensible
     * defaults from {@link AdaptiveConfig#defaults()}.
     * 
     * <p>Example:
     * <pre>{@code
     * AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
     *     .initialTps(100.0)
     *     .rampIncrement(50.0)
     *     .rampDecrement(100.0)
     *     .rampInterval(Duration.ofMinutes(1))
     *     .maxTps(5000.0)
     *     .minTps(10.0)
     *     .sustainDuration(Duration.ofMinutes(10))
     *     .errorThreshold(0.01)
     *     .backpressureRampUpThreshold(0.3)
     *     .backpressureRampDownThreshold(0.7)
     *     .stableIntervalsRequired(3)
     *     .tpsTolerance(50.0)
     *     .recoveryTpsRatio(0.5)
     *     .metricsProvider(metrics)
     *     .backpressureProvider(backpressure)
     *     .build();
     * }</pre>
     * 
     * @since 0.9.9
     */
    public static final class Builder {
        private AdaptiveConfig config = AdaptiveConfig.defaults();
        private MetricsProvider metricsProvider;
        private BackpressureProvider backpressureProvider;
        private List<AdaptivePatternListener> listeners = new ArrayList<>();
        
        private Builder() {
            // Private constructor - use AdaptiveLoadPattern.builder()
        }
        
        /**
         * Sets the complete configuration.
         * 
         * @param config configuration (must not be null)
         * @return this builder
         */
        public Builder config(AdaptiveConfig config) {
            this.config = Objects.requireNonNull(config, "Config must not be null");
            return this;
        }
        
        /**
         * Sets the initial TPS.
         * 
         * @param tps initial TPS (must be positive)
         * @return this builder
         */
        public Builder initialTps(double tps) {
            this.config = new AdaptiveConfig(
                tps,
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.maxTps(),
                config.minTps(),
                config.sustainDuration(),
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the ramp increment.
         * 
         * @param increment TPS increase per interval (must be positive)
         * @return this builder
         */
        public Builder rampIncrement(double increment) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                increment,
                config.rampDecrement(),
                config.rampInterval(),
                config.maxTps(),
                config.minTps(),
                config.sustainDuration(),
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the ramp decrement.
         * 
         * @param decrement TPS decrease per interval (must be positive)
         * @return this builder
         */
        public Builder rampDecrement(double decrement) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                decrement,
                config.rampInterval(),
                config.maxTps(),
                config.minTps(),
                config.sustainDuration(),
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the ramp interval.
         * 
         * @param interval time between adjustments (must be positive)
         * @return this builder
         */
        public Builder rampInterval(Duration interval) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                interval,
                config.maxTps(),
                config.minTps(),
                config.sustainDuration(),
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the maximum TPS.
         * 
         * @param maxTps maximum TPS (must be positive or Double.POSITIVE_INFINITY)
         * @return this builder
         */
        public Builder maxTps(double maxTps) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                maxTps,
                config.minTps(),
                config.sustainDuration(),
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the minimum TPS.
         * 
         * @param minTps minimum TPS (must be non-negative)
         * @return this builder
         */
        public Builder minTps(double minTps) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.maxTps(),
                minTps,
                config.sustainDuration(),
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the sustain duration.
         * 
         * @param duration duration to sustain at stable point (must be positive)
         * @return this builder
         */
        public Builder sustainDuration(Duration duration) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.maxTps(),
                config.minTps(),
                duration,
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the error threshold.
         * 
         * @param threshold error rate threshold 0.0-1.0 (must be between 0 and 1)
         * @return this builder
         */
        public Builder errorThreshold(double threshold) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.maxTps(),
                config.minTps(),
                config.sustainDuration(),
                threshold,
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the backpressure ramp up threshold.
         * 
         * @param threshold backpressure threshold for ramping up 0.0-1.0
         * @return this builder
         */
        public Builder backpressureRampUpThreshold(double threshold) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.maxTps(),
                config.minTps(),
                config.sustainDuration(),
                config.errorThreshold(),
                threshold,
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the backpressure ramp down threshold.
         * 
         * @param threshold backpressure threshold for ramping down 0.0-1.0
         * @return this builder
         */
        public Builder backpressureRampDownThreshold(double threshold) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.maxTps(),
                config.minTps(),
                config.sustainDuration(),
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                threshold,
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the number of stable intervals required.
         * 
         * @param count number of stable intervals required (must be at least 1)
         * @return this builder
         */
        public Builder stableIntervalsRequired(int count) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.maxTps(),
                config.minTps(),
                config.sustainDuration(),
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                count,
                config.tpsTolerance(),
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the TPS tolerance for stability detection.
         * 
         * @param tolerance TPS tolerance (must be non-negative)
         * @return this builder
         */
        public Builder tpsTolerance(double tolerance) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.maxTps(),
                config.minTps(),
                config.sustainDuration(),
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                tolerance,
                config.recoveryTpsRatio()
            );
            return this;
        }
        
        /**
         * Sets the recovery TPS ratio.
         * 
         * @param ratio ratio of last known good TPS to use for recovery 0.0-1.0
         * @return this builder
         */
        public Builder recoveryTpsRatio(double ratio) {
            this.config = new AdaptiveConfig(
                config.initialTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.maxTps(),
                config.minTps(),
                config.sustainDuration(),
                config.errorThreshold(),
                config.backpressureRampUpThreshold(),
                config.backpressureRampDownThreshold(),
                config.stableIntervalsRequired(),
                config.tpsTolerance(),
                ratio
            );
            return this;
        }
        
        /**
         * Sets the metrics provider.
         * 
         * @param provider metrics provider (must not be null)
         * @return this builder
         */
        public Builder metricsProvider(MetricsProvider provider) {
            this.metricsProvider = Objects.requireNonNull(provider, "Metrics provider must not be null");
            return this;
        }
        
        /**
         * Sets the backpressure provider.
         * 
         * @param provider backpressure provider (can be null)
         * @return this builder
         */
        public Builder backpressureProvider(BackpressureProvider provider) {
            this.backpressureProvider = provider;
            return this;
        }
        
        /**
         * Adds an event listener.
         * 
         * <p>Multiple listeners can be added. They will all receive
         * notifications about pattern events.
         * 
         * @param listener event listener (must not be null)
         * @return this builder
         */
        public Builder listener(AdaptivePatternListener listener) {
            this.listeners.add(Objects.requireNonNull(listener, "Listener must not be null"));
            return this;
        }
        
        /**
         * Builds the AdaptiveLoadPattern instance.
         * 
         * <p>Validates that required fields are set and creates
         * the pattern with the configured values.
         * 
         * @return new AdaptiveLoadPattern instance
         * @throws IllegalStateException if metrics provider is not set
         * @throws IllegalArgumentException if configuration is invalid
         */
        public AdaptiveLoadPattern build() {
            if (metricsProvider == null) {
                throw new IllegalStateException("Metrics provider must be set");
            }
            // Config validation happens in AdaptiveConfig constructor
            return new AdaptiveLoadPattern(config, metricsProvider, backpressureProvider, listeners);
        }
    }
}

