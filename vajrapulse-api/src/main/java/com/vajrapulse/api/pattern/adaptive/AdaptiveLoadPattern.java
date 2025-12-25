package com.vajrapulse.api.pattern.adaptive;

import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.metrics.MetricsProvider;
import com.vajrapulse.api.backpressure.BackpressureProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Adaptive load pattern that automatically finds the maximum sustainable TPS.
 * 
 * <p>This pattern:
 * <ol>
 *   <li>Starts at initial TPS</li>
 *   <li>Ramps up by increment amount at fixed intervals until errors occur</li>
 *   <li>Ramps down by decrement amount when errors exceed threshold</li>
 *   <li>Finds stable point (consecutive intervals with low error rate)</li>
 *   <li>Sustains at stable point for configured duration</li>
 *   <li>Continues at stable TPS indefinitely after sustain duration</li>
 * </ol>
 * 
 * <p>Example:
 * <pre>{@code
 * MetricsProvider metrics = ...; // From MetricsCollector
 * AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
 *     .initialTps(100.0)
 *     .rampIncrement(50.0)
 *     .rampDecrement(100.0)
 *     .rampInterval(Duration.ofMinutes(1))
 *     .maxTps(5000.0)
 *     .minTps(10.0)
 *     .sustainDuration(Duration.ofMinutes(10))
 *     .stableIntervalsRequired(3)
 *     .metricsProvider(metrics)
 *     .build();
 * }</pre>
 * 
 * <p><strong>Thread Safety:</strong> This class is thread-safe for concurrent
 * access from multiple threads calling {@link #calculateTps(long)}.
 * 
 * <p><strong>Memory Ordering Guarantees:</strong>
 * <ul>
 *   <li>State updates use {@link AtomicReference#set(Object)} which provides
 *       volatile write semantics</li>
 *   <li>State reads use {@link AtomicReference#get()} which provides volatile read semantics</li>
 *   <li>All state transitions are atomic and visible to all threads immediately</li>
 *   <li>The immutable {@code AdaptiveState} record ensures no partial state visibility</li>
 * </ul>
 * 
 * @since 0.9.9
 */
public final class AdaptiveLoadPattern implements LoadPattern {
    
    private final AdaptiveConfig config;
    private final MetricsProvider metricsProvider;
    private final BackpressureProvider backpressureProvider;
    private final RampDecisionPolicy decisionPolicy;
    private final List<AdaptivePatternListener> listeners;
    
    // Immutable state stored atomically
    private final AtomicReference<AdaptiveState> state;
    
    /**
     * Conversion factor from percentage to ratio (100.0% = 1.0).
     */
    private static final double PERCENTAGE_TO_RATIO = 100.0;
    
    /**
     * Recovery TPS ratio (hardcoded to 50% of last known good TPS).
     */
    private static final double RECOVERY_TPS_RATIO = 0.5;
    
    /**
     * Creates a new adaptive load pattern with configuration.
     * 
     * @param config configuration for the adaptive pattern (must not be null)
     * @param metricsProvider provides execution metrics (must not be null)
     * @param backpressureProvider optional backpressure provider for additional signals
     * @param decisionPolicy decision policy for ramp decisions (must not be null)
     * @param listeners list of event listeners (must not be null, can be empty)
     * @throws IllegalArgumentException if any parameter is invalid
     * @since 0.9.9
     */
    public AdaptiveLoadPattern(
            AdaptiveConfig config,
            MetricsProvider metricsProvider,
            BackpressureProvider backpressureProvider,
            RampDecisionPolicy decisionPolicy,
            List<AdaptivePatternListener> listeners) {
        this.config = Objects.requireNonNull(config, "Config must not be null");
        this.metricsProvider = Objects.requireNonNull(metricsProvider, "Metrics provider must not be null");
        this.backpressureProvider = backpressureProvider; // Can be null
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "Decision policy must not be null");
        this.listeners = new CopyOnWriteArrayList<>(
            Objects.requireNonNull(listeners, "Listeners must not be null")
        );
        
        // Initialize state (will be set on first calculateTps call)
        this.state = new AtomicReference<>(createInitialState(-1L));
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        if (elapsedMillis < 0) {
            return 0.0;
        }
        
        AdaptiveState current = state.get();
        
        // Initialize on first call
        if (current.lastAdjustmentTime() < 0) {
            current = initializeState(elapsedMillis);
        }
        
        // Check if it's time to adjust
        long timeSinceLastAdjustment = elapsedMillis - current.lastAdjustmentTime();
        if (timeSinceLastAdjustment < config.rampInterval().toMillis()) {
            return current.currentTps();  // Not time yet, return current
        }
        
        // Time for adjustment - make decision and update state
        MetricsSnapshot metrics = captureMetricsSnapshot(elapsedMillis);
        AdjustmentDecision decision = makeDecision(current, metrics, elapsedMillis);
        
        // Apply decision
        AdaptiveState newState = applyDecision(current, decision, elapsedMillis);
        state.set(newState);
        
        // Notify listeners
        notifyListeners(current, newState, decision);
        
        return newState.currentTps();
    }
    
    /**
     * Initializes state on first call.
     * 
     * @param elapsedMillis current elapsed time
     * @return initialized state
     */
    private AdaptiveState initializeState(long elapsedMillis) {
        AdaptiveState initialized = createInitialState(elapsedMillis);
        state.set(initialized);
        return initialized;
    }
    
    /**
     * Creates the initial state for the pattern.
     * 
     * @param elapsedMillis current elapsed time
     * @return initial state
     */
    private AdaptiveState createInitialState(long elapsedMillis) {
        return new AdaptiveState(
            AdaptivePhase.RAMP_UP, config.initialTps(), elapsedMillis, elapsedMillis,
            -1.0, 0, config.initialTps(), false, 0L
        );
    }
    
    /**
     * Makes adjustment decision based on current state and metrics.
     * 
     * @param current current state
     * @param metrics current metrics
     * @param elapsedMillis current time
     * @return adjustment decision
     */
    private AdjustmentDecision makeDecision(
            AdaptiveState current,
            MetricsSnapshot metrics,
            long elapsedMillis) {
        
        return switch (current.phase()) {
            case RAMP_UP -> decideRampUp(current, metrics, elapsedMillis);
            case RAMP_DOWN -> decideRampDown(current, metrics, elapsedMillis);
            case SUSTAIN -> decideSustain(current, metrics, elapsedMillis);
        };
    }
    
    /**
     * Decision result.
     */
    private record AdjustmentDecision(
        AdaptivePhase newPhase,
        double newTps,
        String reason  // For logging/debugging
    ) {}
    
    /**
     * Decides what to do in RAMP_UP phase.
     */
    private AdjustmentDecision decideRampUp(
            AdaptiveState current,
            MetricsSnapshot metrics,
            long elapsedMillis) {
        
        // Check for errors/backpressure
        if (decisionPolicy.shouldRampDown(metrics)) {
            return decision(AdaptivePhase.RAMP_DOWN, calculateRampDownTps(current.currentTps()),
                "Errors/backpressure detected");
        }
        
        // Check if max TPS reached
        AdjustmentDecision maxTpsDecision = checkMaxTpsReached(current.currentTps());
        if (maxTpsDecision != null) {
            return maxTpsDecision;
        }
        
        // Check for stability
        if (isStable(current, metrics)) {
            return decision(AdaptivePhase.SUSTAIN, current.currentTps(), "Stability detected");
        }
        
        // Continue ramping up or hold
        return decideRampUpContinuation(current, metrics);
    }
    
    /**
     * Decides whether to continue ramping up or hold current TPS.
     * 
     * @param current current state
     * @param metrics current metrics
     * @return decision to ramp up or hold
     */
    private AdjustmentDecision decideRampUpContinuation(
            AdaptiveState current,
            MetricsSnapshot metrics) {
        
        if (decisionPolicy.shouldRampUp(metrics)) {
            double newTps = calculateRampUpTps(current.currentTps());
            AdjustmentDecision maxTpsDecision = checkMaxTpsReached(newTps);
            if (maxTpsDecision != null) {
                return maxTpsDecision;
            }
            return decision(AdaptivePhase.RAMP_UP, newTps, "Conditions good, ramping up");
        }
        
        return decision(AdaptivePhase.RAMP_UP, current.currentTps(), "Moderate backpressure, holding");
    }
    
    /**
     * Checks if TPS has reached maximum and returns transition decision if so.
     * 
     * @param tps the TPS to check
     * @return transition decision to SUSTAIN if max reached, null otherwise
     */
    private AdjustmentDecision checkMaxTpsReached(double tps) {
        if (tps >= config.maxTps()) {
            return decision(AdaptivePhase.SUSTAIN, tps, "Max TPS reached");
        }
        return null;
    }
    
    /**
     * Decides what to do in RAMP_DOWN phase.
     */
    private AdjustmentDecision decideRampDown(
            AdaptiveState current,
            MetricsSnapshot metrics,
            long elapsedMillis) {
        
        // Check if at minimum (recovery mode)
        if (current.inRecovery()) {
            return decideRecovery(current, metrics);
        }
        
        // Check for stability when conditions improve
        if (!decisionPolicy.shouldRampDown(metrics)) {
            return decideStabilityDuringRampDown(current, metrics);
        }
        
        // Continue ramping down
        return decision(AdaptivePhase.RAMP_DOWN, calculateRampDownTps(current.currentTps()),
            "Errors/backpressure persist, ramping down");
    }
    
    /**
     * Decides recovery action when at minimum TPS.
     * 
     * @param current current state
     * @param metrics current metrics
     * @return recovery decision
     */
    private AdjustmentDecision decideRecovery(AdaptiveState current, MetricsSnapshot metrics) {
        if (decisionPolicy.canRecoverFromMinimum(metrics)) {
            return decision(AdaptivePhase.RAMP_UP, 
                calculateRecoveryTps(current.lastKnownGoodTps()),
                "Recovery: conditions improved");
        }
        return decision(AdaptivePhase.RAMP_DOWN, config.minTps(),
            "Recovery: waiting for conditions to improve");
    }
    
    /**
     * Decides action when conditions improve during ramp down.
     * 
     * @param current current state
     * @param metrics current metrics
     * @return decision (either SUSTAIN if stable, or continue RAMP_DOWN)
     */
    private AdjustmentDecision decideStabilityDuringRampDown(AdaptiveState current, MetricsSnapshot metrics) {
        if (isStable(current, metrics)) {
            return decision(AdaptivePhase.SUSTAIN, current.currentTps(),
                "Stability detected during ramp down");
        }
        return decision(AdaptivePhase.RAMP_DOWN, current.currentTps(),
            "Conditions improved, checking stability");
    }
    
    /**
     * Decides what to do in SUSTAIN phase.
     */
    private AdjustmentDecision decideSustain(
            AdaptiveState current,
            MetricsSnapshot metrics,
            long elapsedMillis) {
        
        // Check if conditions worsened
        if (decisionPolicy.shouldRampDown(metrics)) {
            return decision(AdaptivePhase.RAMP_DOWN, calculateRampDownTps(current.currentTps()),
                "Conditions worsened during sustain");
        }
        
        // Check if sustain duration elapsed and can ramp up
        AdjustmentDecision afterSustainDecision = checkAfterSustainDuration(current, metrics, elapsedMillis);
        if (afterSustainDecision != null) {
            return afterSustainDecision;
        }
        
        // Continue sustaining
        return decision(AdaptivePhase.SUSTAIN, current.currentTps(), "Continuing to sustain");
    }
    
    /**
     * Checks if sustain duration has elapsed and returns decision to ramp up if conditions allow.
     * 
     * @param current current state
     * @param metrics current metrics
     * @param elapsedMillis current elapsed time
     * @return decision to ramp up if duration elapsed and conditions allow, null otherwise
     */
    private AdjustmentDecision checkAfterSustainDuration(
            AdaptiveState current,
            MetricsSnapshot metrics,
            long elapsedMillis) {
        
        long phaseDuration = elapsedMillis - current.phaseStartTime();
        if (phaseDuration >= config.sustainDuration().toMillis()) {
            if (decisionPolicy.shouldRampUp(metrics) && current.currentTps() < config.maxTps()) {
                return decision(AdaptivePhase.RAMP_UP, calculateRampUpTps(current.currentTps()),
                    "Sustain duration elapsed, ramping up");
            }
        }
        return null;
    }
    
    /**
     * Checks if current TPS is stable.
     * 
     * <p>Stability means:
     * - Conditions are good (error rate low, backpressure low)
     * - Been stable for required number of intervals (after incrementing)
     * 
     * @param current current state
     * @param metrics current metrics
     * @return true if stable
     */
    private boolean isStable(AdaptiveState current, MetricsSnapshot metrics) {
        // Conditions must be good
        if (!decisionPolicy.shouldRampUp(metrics)) {
            return false;
        }
        
        // Check if we'll have enough stable intervals after incrementing
        int newStableCount = current.stableIntervalsCount() + 1;
        return decisionPolicy.shouldSustain(newStableCount, config.stableIntervalsRequired());
    }
    
    /**
     * Applies adjustment decision to state.
     * 
     * @param current current state
     * @param decision adjustment decision
     * @param elapsedMillis current time
     * @return new state
     */
    private AdaptiveState applyDecision(
            AdaptiveState current,
            AdjustmentDecision decision,
            long elapsedMillis) {
        
        if (decision.newPhase() != current.phase()) {
            // Phase transition
            return transitionToPhase(current, decision.newPhase(), elapsedMillis, decision.newTps());
        } else {
            // Same phase, just update TPS and timing
            return updateStateInPhase(current, decision, elapsedMillis);
        }
    }
    
    /**
     * Updates state within the same phase.
     */
    private AdaptiveState updateStateInPhase(
            AdaptiveState current,
            AdjustmentDecision decision,
            long elapsedMillis) {
        
        // Update stability count if conditions are good
        int newStableCount = calculateStableCount(current, elapsedMillis);
        
        return new AdaptiveState(
            current.phase(),
            decision.newTps(),
            elapsedMillis,
            current.phaseStartTime(),
            current.stableTps(),
            newStableCount,
            updateLastKnownGoodTps(current, current.currentTps(), decision.newTps()),
            decision.newTps() <= config.minTps(),
            current.phaseTransitionCount()
        );
    }
    
    /**
     * Calculates the new stable intervals count based on current conditions.
     * 
     * @param current current state
     * @param elapsedMillis current elapsed time
     * @return new stable count (incremented if conditions good, reset to 0 otherwise)
     */
    private int calculateStableCount(AdaptiveState current, long elapsedMillis) {
        MetricsSnapshot metrics = captureMetricsSnapshot(elapsedMillis);
        return decisionPolicy.shouldRampUp(metrics)
            ? current.stableIntervalsCount() + 1
            : 0;
    }
    
    /**
     * Transitions to a new phase.
     * 
     * @param current current state
     * @param newPhase phase to transition to
     * @param elapsedMillis current time
     * @param newTps new TPS for the phase
     * @return new state after transition
     */
    private AdaptiveState transitionToPhase(
            AdaptiveState current,
            AdaptivePhase newPhase,
            long elapsedMillis,
            double newTps) {
        
        return switch (newPhase) {
            case RAMP_UP -> {
                // Special handling for recovery: preserve lastKnownGoodTps
                double lastKnownGood = current.inRecovery()
                    ? current.lastKnownGoodTps()
                    : updateLastKnownGoodTps(current, current.currentTps(), newTps);
                yield new AdaptiveState(
                    AdaptivePhase.RAMP_UP, newTps, elapsedMillis, elapsedMillis,
                    current.stableTps(), 0, lastKnownGood, false,
                    current.phaseTransitionCount() + 1
                );
            }
            case RAMP_DOWN -> new AdaptiveState(
                AdaptivePhase.RAMP_DOWN, newTps, elapsedMillis, elapsedMillis,
                current.stableTps(), 0,
                updateLastKnownGoodTps(current, current.currentTps(), newTps),
                newTps <= config.minTps(),
                current.phaseTransitionCount() + 1
            );
            case SUSTAIN -> new AdaptiveState(
                AdaptivePhase.SUSTAIN, newTps, elapsedMillis, elapsedMillis,
                newTps, 0, Math.max(newTps, current.lastKnownGoodTps()),
                false, current.phaseTransitionCount() + 1
            );
        };
    }
    
    /**
     * Updates last known good TPS based on phase and current/new TPS values.
     * 
     * <p>Last known good TPS represents the highest TPS achieved before problems.
     * It only increases, never decreases, and is used for recovery calculations.
     * 
     * @param current current state
     * @param currentTps current TPS value
     * @param newTps new TPS value
     * @return updated last known good TPS
     */
    private double updateLastKnownGoodTps(
            AdaptiveState current,
            double currentTps,
            double newTps) {
        
        return switch (current.phase()) {
            case RAMP_UP -> Math.max(Math.max(currentTps, newTps), current.lastKnownGoodTps());
            case RAMP_DOWN -> Math.max(currentTps, current.lastKnownGoodTps());
            case SUSTAIN -> current.lastKnownGoodTps();
        };
    }
    
    /**
     * Calculates new TPS after ramping up, clamped to max TPS.
     * 
     * @param currentTps current TPS
     * @return new TPS after increment, clamped to max
     */
    private double calculateRampUpTps(double currentTps) {
        return Math.min(config.maxTps(), currentTps + config.rampIncrement());
    }
    
    /**
     * Calculates new TPS after ramping down, clamped to min TPS.
     * 
     * @param currentTps current TPS
     * @return new TPS after decrement, clamped to min
     */
    private double calculateRampDownTps(double currentTps) {
        return Math.max(config.minTps(), currentTps - config.rampDecrement());
    }
    
    /**
     * Calculates recovery TPS from last known good TPS.
     * 
     * @param lastKnownGoodTps last known good TPS
     * @return recovery TPS (50% of last known good, clamped to min)
     */
    private double calculateRecoveryTps(double lastKnownGoodTps) {
        return Math.max(config.minTps(), lastKnownGoodTps * RECOVERY_TPS_RATIO);
    }
    
    /**
     * Creates an adjustment decision.
     * 
     * @param newPhase new phase
     * @param newTps new TPS
     * @param reason reason for the decision
     * @return adjustment decision
     */
    private AdjustmentDecision decision(AdaptivePhase newPhase, double newTps, String reason) {
        return new AdjustmentDecision(newPhase, newTps, reason);
    }
    
    /**
     * Captures a metrics snapshot.
     * 
     * @param elapsedMillis current elapsed time
     * @return metrics snapshot
     */
    private MetricsSnapshot captureMetricsSnapshot(long elapsedMillis) {
        return new MetricsSnapshot(
            metricsProvider.getFailureRate() / PERCENTAGE_TO_RATIO,
            metricsProvider.getRecentFailureRate(10) / PERCENTAGE_TO_RATIO,
            getBackpressureLevel(),
            metricsProvider.getTotalExecutions()
        );
    }
    
    /**
     * Gets the current backpressure level from the provider.
     * 
     * @return backpressure level (0.0 if no provider)
     */
    public double getBackpressureLevel() {
        return backpressureProvider == null ? 0.0 : backpressureProvider.getBackpressureLevel();
    }
    
    /**
     * Notifies listeners of state changes.
     * 
     * @param oldState previous state
     * @param newState new state
     * @param decision adjustment decision
     */
    private void notifyListeners(
            AdaptiveState oldState,
            AdaptiveState newState,
            AdjustmentDecision decision) {
        
        if (listeners.isEmpty()) {
            return;
        }
        
        // Phase transition
        if (oldState.phase() != newState.phase()) {
            notifyPhaseTransition(oldState.phase(), newState.phase(), newState.currentTps());
        }
        
        // TPS change
        if (Math.abs(newState.currentTps() - oldState.currentTps()) > 0.001) {
            notifyTpsChange(oldState.currentTps(), newState.currentTps(), newState.phase());
        }
        
        // Stability detected
        if (newState.phase() == AdaptivePhase.SUSTAIN && 
            newState.hasStableTps() && 
            !oldState.hasStableTps()) {
            notifyStabilityDetected(newState.stableTps());
        }
        
        // Recovery entry
        if (newState.inRecovery() && !oldState.inRecovery()) {
            notifyRecovery(newState.lastKnownGoodTps(), newState.currentTps());
        }
    }
    
    /**
     * Notifies listeners of phase transition.
     */
    private void notifyPhaseTransition(AdaptivePhase oldPhase, AdaptivePhase newPhase, double tps) {
        PhaseTransitionEvent event = new PhaseTransitionEvent(
            oldPhase, newPhase, tps, System.currentTimeMillis()
        );
        notifyListeners(listener -> listener.onPhaseTransition(event), "onPhaseTransition");
    }
    
    /**
     * Notifies listeners of TPS change.
     */
    private void notifyTpsChange(double oldTps, double newTps, AdaptivePhase phase) {
        TpsChangeEvent event = new TpsChangeEvent(
            oldTps, newTps, phase, System.currentTimeMillis()
        );
        notifyListeners(listener -> listener.onTpsChange(event), "onTpsChange");
    }
    
    /**
     * Notifies listeners of stability detection.
     */
    private void notifyStabilityDetected(double stableTps) {
        StabilityDetectedEvent event = new StabilityDetectedEvent(
            stableTps, System.currentTimeMillis()
        );
        notifyListeners(listener -> listener.onStabilityDetected(event), "onStabilityDetected");
    }
    
    /**
     * Notifies listeners of recovery entry.
     */
    private void notifyRecovery(double lastKnownGoodTps, double currentTps) {
        RecoveryEvent event = new RecoveryEvent(
            lastKnownGoodTps, currentTps, System.currentTimeMillis()
        );
        notifyListeners(listener -> listener.onRecovery(event), "onRecovery");
    }
    
    /**
     * Helper method to notify all listeners with error handling.
     */
    private void notifyListeners(
            java.util.function.Consumer<AdaptivePatternListener> notification,
            String methodName) {
        for (AdaptivePatternListener listener : listeners) {
            try {
                notification.accept(listener);
            } catch (Exception e) {
                System.err.println("Listener error in " + methodName + ": " + e.getMessage());
                e.printStackTrace();
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
     *     .stableIntervalsRequired(3)
     *     .metricsProvider(metrics)
     *     .decisionPolicy(policy)
     *     .build();
     * }</pre>
     * 
     * @since 0.9.9
     */
    public static final class Builder {
        // Mutable fields for configuration (create AdaptiveConfig only in build())
        private double initialTps = 100.0;
        private double maxTps = 5000.0;
        private double minTps = 10.0;
        private double rampIncrement = 50.0;
        private double rampDecrement = 100.0;
        private Duration rampInterval = Duration.ofMinutes(1);
        private Duration sustainDuration = Duration.ofMinutes(10);
        private int stableIntervalsRequired = 3;
        
        private MetricsProvider metricsProvider;
        private BackpressureProvider backpressureProvider;
        private RampDecisionPolicy decisionPolicy;
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
            Objects.requireNonNull(config, "Config must not be null");
            return initialTps(config.initialTps())
                .maxTps(config.maxTps())
                .minTps(config.minTps())
                .rampIncrement(config.rampIncrement())
                .rampDecrement(config.rampDecrement())
                .rampInterval(config.rampInterval())
                .sustainDuration(config.sustainDuration())
                .stableIntervalsRequired(config.stableIntervalsRequired());
        }
        
        /**
         * Sets the initial TPS.
         * 
         * @param tps initial TPS (must be positive)
         * @return this builder
         */
        public Builder initialTps(double tps) {
            this.initialTps = tps;
            return this;
        }
        
        /**
         * Sets the ramp increment.
         * 
         * @param increment TPS increase per interval (must be positive)
         * @return this builder
         */
        public Builder rampIncrement(double increment) {
            this.rampIncrement = increment;
            return this;
        }
        
        /**
         * Sets the ramp decrement.
         * 
         * @param decrement TPS decrease per interval (must be positive)
         * @return this builder
         */
        public Builder rampDecrement(double decrement) {
            this.rampDecrement = decrement;
            return this;
        }
        
        /**
         * Sets the ramp interval.
         * 
         * @param interval time between adjustments (must be positive)
         * @return this builder
         */
        public Builder rampInterval(Duration interval) {
            this.rampInterval = interval;
            return this;
        }
        
        /**
         * Sets the maximum TPS.
         * 
         * @param maxTps maximum TPS (must be positive or Double.POSITIVE_INFINITY)
         * @return this builder
         */
        public Builder maxTps(double maxTps) {
            this.maxTps = maxTps;
            return this;
        }
        
        /**
         * Sets the minimum TPS.
         * 
         * @param minTps minimum TPS (must be non-negative)
         * @return this builder
         */
        public Builder minTps(double minTps) {
            this.minTps = minTps;
            return this;
        }
        
        /**
         * Sets the sustain duration.
         * 
         * @param duration duration to sustain at stable point (must be positive)
         * @return this builder
         */
        public Builder sustainDuration(Duration duration) {
            this.sustainDuration = duration;
            return this;
        }
        
        /**
         * Sets the number of stable intervals required.
         * 
         * @param count number of stable intervals required (must be at least 1)
         * @return this builder
         */
        public Builder stableIntervalsRequired(int count) {
            this.stableIntervalsRequired = count;
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
         * Sets the decision policy.
         * 
         * @param policy decision policy (must not be null)
         * @return this builder
         */
        public Builder decisionPolicy(RampDecisionPolicy policy) {
            this.decisionPolicy = Objects.requireNonNull(policy, "Decision policy must not be null");
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
            validateBuilder();
            AdaptiveConfig config = createConfig();
            RampDecisionPolicy policy = decisionPolicy != null 
                ? decisionPolicy 
                : new DefaultRampDecisionPolicy(0.01);
            
            return new AdaptiveLoadPattern(
                config,
                metricsProvider,
                backpressureProvider,
                policy,
                listeners
            );
        }
        
        /**
         * Validates that required builder fields are set.
         * 
         * @throws IllegalStateException if metrics provider is not set
         */
        private void validateBuilder() {
            if (metricsProvider == null) {
                throw new IllegalStateException("Metrics provider must be set");
            }
        }
        
        /**
         * Creates AdaptiveConfig from builder fields.
         * 
         * @return new AdaptiveConfig instance
         */
        private AdaptiveConfig createConfig() {
            return new AdaptiveConfig(
                initialTps,
                maxTps,
                minTps,
                rampIncrement,
                rampDecrement,
                rampInterval,
                sustainDuration,
                stableIntervalsRequired
            );
        }
    }
}
