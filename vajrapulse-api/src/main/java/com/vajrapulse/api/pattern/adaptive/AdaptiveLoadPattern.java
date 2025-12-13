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
        this.state = new AtomicReference<>(new AdaptiveState(
            AdaptivePhase.RAMP_UP,
            config.initialTps(),
            -1L,  // Will be set on first call
            -1L,  // Will be set on first call
            -1.0,  // No stable TPS yet
            0,     // No stable intervals
            config.initialTps(),  // Last known good = initial
            false,  // Not in recovery
            0L     // No transitions yet
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
        AdaptiveState initialized = new AdaptiveState(
            AdaptivePhase.RAMP_UP,
            config.initialTps(),
            elapsedMillis,
            elapsedMillis,
            -1.0,  // No stable TPS yet
            0,     // No stable intervals
            config.initialTps(),  // Last known good = initial
            false,  // Not in recovery
            0L     // No transitions yet
        );
        state.set(initialized);
        return initialized;
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
            double newTps = Math.max(config.minTps(), 
                current.currentTps() - config.rampDecrement());
            return new AdjustmentDecision(
                AdaptivePhase.RAMP_DOWN,
                newTps,
                "Errors/backpressure detected"
            );
        }
        
        // Check if max TPS reached
        if (current.currentTps() >= config.maxTps()) {
            return new AdjustmentDecision(
                AdaptivePhase.SUSTAIN,
                current.currentTps(),
                "Max TPS reached"
            );
        }
        
        // Check for stability (intermediate)
        if (decisionPolicy.shouldRampUp(metrics)) {
            // Conditions are good, check if we'll have enough stable intervals after incrementing
            int newStableCount = current.stableIntervalsCount() + 1;
            if (decisionPolicy.shouldSustain(newStableCount, config.stableIntervalsRequired())) {
                return new AdjustmentDecision(
                    AdaptivePhase.SUSTAIN,
                    current.currentTps(),
                    "Stability detected"
                );
            }
        }
        
        // Continue ramping up
        if (decisionPolicy.shouldRampUp(metrics)) {
            double newTps = Math.min(config.maxTps(),
                current.currentTps() + config.rampIncrement());
            // If new TPS reaches max, transition to SUSTAIN
            if (newTps >= config.maxTps()) {
                return new AdjustmentDecision(
                    AdaptivePhase.SUSTAIN,
                    newTps,
                    "Max TPS reached"
                );
            }
            return new AdjustmentDecision(
                AdaptivePhase.RAMP_UP,
                newTps,
                "Conditions good, ramping up"
            );
        }
        
        // Hold current TPS
        return new AdjustmentDecision(
            AdaptivePhase.RAMP_UP,
            current.currentTps(),
            "Moderate backpressure, holding"
        );
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
            if (decisionPolicy.canRecoverFromMinimum(metrics)) {
                double recoveryTps = Math.max(config.minTps(),
                    current.lastKnownGoodTps() * RECOVERY_TPS_RATIO);
                return new AdjustmentDecision(
                    AdaptivePhase.RAMP_UP,
                    recoveryTps,
                    "Recovery: conditions improved"
                );
            }
            // Stay at minimum
            return new AdjustmentDecision(
                AdaptivePhase.RAMP_DOWN,
                config.minTps(),
                "Recovery: waiting for conditions to improve"
            );
        }
        
        // Check for stability
        if (!decisionPolicy.shouldRampDown(metrics)) {
            // Check if conditions are good (needed for stability)
            if (decisionPolicy.shouldRampUp(metrics)) {
                // Conditions are good, check if we'll have enough stable intervals after incrementing
                int newStableCount = current.stableIntervalsCount() + 1;
                if (decisionPolicy.shouldSustain(newStableCount, config.stableIntervalsRequired())) {
                    return new AdjustmentDecision(
                        AdaptivePhase.SUSTAIN,
                        current.currentTps(),
                        "Stability detected during ramp down"
                    );
                }
            }
            // Hold current TPS, continue tracking stability
            return new AdjustmentDecision(
                AdaptivePhase.RAMP_DOWN,
                current.currentTps(),
                "Conditions improved, checking stability"
            );
        }
        
        // Continue ramping down
        double newTps = Math.max(config.minTps(),
            current.currentTps() - config.rampDecrement());
        return new AdjustmentDecision(
            AdaptivePhase.RAMP_DOWN,
            newTps,
            "Errors/backpressure persist, ramping down"
        );
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
            double newTps = Math.max(config.minTps(),
                current.currentTps() - config.rampDecrement());
            return new AdjustmentDecision(
                AdaptivePhase.RAMP_DOWN,
                newTps,
                "Conditions worsened during sustain"
            );
        }
        
        // Check if sustain duration elapsed
        long phaseDuration = elapsedMillis - current.phaseStartTime();
        if (phaseDuration >= config.sustainDuration().toMillis()) {
            // Try to ramp up
            if (decisionPolicy.shouldRampUp(metrics) && 
                current.currentTps() < config.maxTps()) {
                double newTps = Math.min(config.maxTps(),
                    current.currentTps() + config.rampIncrement());
                return new AdjustmentDecision(
                    AdaptivePhase.RAMP_UP,
                    newTps,
                    "Sustain duration elapsed, ramping up"
                );
            }
        }
        
        // Continue sustaining
        return new AdjustmentDecision(
            AdaptivePhase.SUSTAIN,
            current.currentTps(),
            "Continuing to sustain"
        );
    }
    
    /**
     * Checks if current TPS is stable.
     * 
     * <p>Stability means:
     * - Conditions are good (error rate low, backpressure low)
     * - Been stable for required number of intervals
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
        
        // Check if stable intervals count meets requirement
        return decisionPolicy.shouldSustain(
            current.stableIntervalsCount(),
            config.stableIntervalsRequired()
        );
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
            return switch (decision.newPhase()) {
                case RAMP_UP -> transitionToRampUp(current, elapsedMillis, decision.newTps());
                case RAMP_DOWN -> transitionToRampDown(current, elapsedMillis, decision.newTps());
                case SUSTAIN -> transitionToSustain(current, elapsedMillis, decision.newTps());
            };
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
        int newStableCount = 0;
        if (decisionPolicy.shouldRampUp(captureMetricsSnapshot(elapsedMillis))) {
            // Conditions are good, increment stability count
            newStableCount = current.stableIntervalsCount() + 1;
        }
        // If conditions not good, stability count resets to 0
        
        // Check if at minimum TPS (recovery mode)
        boolean inRecovery = decision.newTps() <= config.minTps();
        
        // Update last known good TPS - only increase, never decrease
        // This captures the highest TPS we've achieved
        // During ramp-down, preserve the maximum TPS we had before ramping down
        // Only update if we're in RAMP_UP phase or if current TPS is higher than previous max
        double lastKnownGood = current.lastKnownGoodTps();
        if (current.phase() == AdaptivePhase.RAMP_UP) {
            // During ramp-up, update if new TPS is higher
            lastKnownGood = Math.max(
                Math.max(current.currentTps(), decision.newTps()),
                current.lastKnownGoodTps()
            );
        } else if (current.phase() == AdaptivePhase.RAMP_DOWN) {
            // During ramp-down, preserve the peak - never decrease lastKnownGoodTps
            // We only update if current TPS (before decrement) is higher than previous max
            // This ensures we capture the TPS we're ramping down FROM, not the lower TPS we're going TO
            lastKnownGood = Math.max(current.currentTps(), current.lastKnownGoodTps());
        }
        // For SUSTAIN phase, keep existing lastKnownGood (no change needed)
        
        return new AdaptiveState(
            current.phase(),
            decision.newTps(),
            elapsedMillis,
            current.phaseStartTime(),  // Keep phase start time
            current.stableTps(),  // Keep stable TPS if found
            newStableCount,
            lastKnownGood,
            inRecovery,
            current.phaseTransitionCount()
        );
    }
    
    /**
     * Transitions to RAMP_UP phase.
     * 
     * @param current current state
     * @param elapsedMillis current time
     * @param newTps new TPS (after increment or recovery)
     * @return new state
     */
    private AdaptiveState transitionToRampUp(
            AdaptiveState current,
            long elapsedMillis,
            double newTps) {
        
        // Update last known good TPS if new TPS is higher
        // During recovery from minimum, preserve the lastKnownGoodTps (don't update with recovery TPS)
        double lastKnownGood = current.lastKnownGoodTps();
        if (!current.inRecovery()) {
            // Normal ramp-up: update if new TPS is higher
            lastKnownGood = Math.max(
                Math.max(current.currentTps(), newTps),
                current.lastKnownGoodTps()
            );
        }
        // If in recovery, keep existing lastKnownGoodTps (it should be the peak before ramping down)
        
        return new AdaptiveState(
            AdaptivePhase.RAMP_UP,
            newTps,
            elapsedMillis,
            elapsedMillis,  // New phase start time
            current.stableTps(),  // Preserve stable TPS if found
            0,  // Reset stability count
            lastKnownGood,
            false,  // Not in recovery
            current.phaseTransitionCount() + 1
        );
    }
    
    /**
     * Transitions to RAMP_DOWN phase.
     * 
     * @param current current state
     * @param elapsedMillis current time
     * @param newTps new TPS (after decrement)
     * @return new state
     */
    private AdaptiveState transitionToRampDown(
            AdaptiveState current,
            long elapsedMillis,
            double newTps) {
        
        // Update last known good TPS - preserve the maximum TPS we've achieved
        // When transitioning to RAMP_DOWN, current.currentTps() is the TPS we're ramping down from
        // This should be captured as lastKnownGood if it's higher than previous
        double lastKnownGood = Math.max(
            current.currentTps(),  // The TPS we're ramping down from
            current.lastKnownGoodTps()  // Previous maximum
        );
        
        // Check if at minimum (recovery mode)
        boolean inRecovery = newTps <= config.minTps();
        
        return new AdaptiveState(
            AdaptivePhase.RAMP_DOWN,
            newTps,
            elapsedMillis,
            elapsedMillis,  // New phase start time
            current.stableTps(),  // Preserve stable TPS if found
            0,  // Reset stability count
            lastKnownGood,
            inRecovery,
            current.phaseTransitionCount() + 1
        );
    }
    
    /**
     * Transitions to SUSTAIN phase.
     * 
     * @param current current state
     * @param elapsedMillis current time
     * @param stableTps the stable TPS to sustain at
     * @return new state
     */
    private AdaptiveState transitionToSustain(
            AdaptiveState current,
            long elapsedMillis,
            double stableTps) {
        
        // Update last known good TPS if stable TPS is higher
        double lastKnownGood = Math.max(
            stableTps,
            current.lastKnownGoodTps()
        );
        
        return new AdaptiveState(
            AdaptivePhase.SUSTAIN,
            stableTps,
            elapsedMillis,
            elapsedMillis,  // New phase start time
            stableTps,  // Set stable TPS
            0,  // Reset stability count
            lastKnownGood,
            false,  // Not in recovery
            current.phaseTransitionCount() + 1
        );
    }
    
    /**
     * Captures a metrics snapshot.
     * 
     * @param elapsedMillis current elapsed time
     * @return metrics snapshot
     */
    private MetricsSnapshot captureMetricsSnapshot(long elapsedMillis) {
        double failureRate = metricsProvider.getFailureRate() / PERCENTAGE_TO_RATIO;
        double recentFailureRate = metricsProvider.getRecentFailureRate(10) / PERCENTAGE_TO_RATIO;
        double backpressure = getBackpressureLevel();
        long totalExecutions = metricsProvider.getTotalExecutions();
        
        return new MetricsSnapshot(
            failureRate,
            recentFailureRate,
            backpressure,
            totalExecutions
        );
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
            PhaseTransitionEvent event = new PhaseTransitionEvent(
                oldState.phase(),
                newState.phase(),
                newState.currentTps(),
                System.currentTimeMillis()
            );
            for (AdaptivePatternListener listener : listeners) {
                try {
                    listener.onPhaseTransition(event);
                } catch (Exception e) {
                    System.err.println("Listener error in onPhaseTransition: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        // TPS change
        if (Math.abs(newState.currentTps() - oldState.currentTps()) > 0.001) {
            TpsChangeEvent event = new TpsChangeEvent(
                oldState.currentTps(),
                newState.currentTps(),
                newState.phase(),
                System.currentTimeMillis()
            );
            for (AdaptivePatternListener listener : listeners) {
                try {
                    listener.onTpsChange(event);
                } catch (Exception e) {
                    System.err.println("Listener error in onTpsChange: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        // Stability detected
        if (newState.phase() == AdaptivePhase.SUSTAIN && 
            newState.hasStableTps() && 
            !oldState.hasStableTps()) {
            StabilityDetectedEvent event = new StabilityDetectedEvent(
                newState.stableTps(),
                System.currentTimeMillis()
            );
            for (AdaptivePatternListener listener : listeners) {
                try {
                    listener.onStabilityDetected(event);
                } catch (Exception e) {
                    System.err.println("Listener error in onStabilityDetected: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        // Recovery entry
        if (newState.inRecovery() && !oldState.inRecovery()) {
            RecoveryEvent event = new RecoveryEvent(
                newState.lastKnownGoodTps(),
                newState.currentTps(),
                System.currentTimeMillis()
            );
            for (AdaptivePatternListener listener : listeners) {
                try {
                    listener.onRecovery(event);
                } catch (Exception e) {
                    System.err.println("Listener error in onRecovery: " + e.getMessage());
                    e.printStackTrace();
                }
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
        private AdaptiveConfig config = AdaptiveConfig.defaults();
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
                config.maxTps(),
                config.minTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.sustainDuration(),
                config.stableIntervalsRequired()
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
                config.maxTps(),
                config.minTps(),
                increment,
                config.rampDecrement(),
                config.rampInterval(),
                config.sustainDuration(),
                config.stableIntervalsRequired()
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
                config.maxTps(),
                config.minTps(),
                config.rampIncrement(),
                decrement,
                config.rampInterval(),
                config.sustainDuration(),
                config.stableIntervalsRequired()
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
                config.maxTps(),
                config.minTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                interval,
                config.sustainDuration(),
                config.stableIntervalsRequired()
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
                maxTps,
                config.minTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.sustainDuration(),
                config.stableIntervalsRequired()
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
                config.maxTps(),
                minTps,
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.sustainDuration(),
                config.stableIntervalsRequired()
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
                config.maxTps(),
                config.minTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                duration,
                config.stableIntervalsRequired()
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
                config.maxTps(),
                config.minTps(),
                config.rampIncrement(),
                config.rampDecrement(),
                config.rampInterval(),
                config.sustainDuration(),
                count
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
            if (metricsProvider == null) {
                throw new IllegalStateException("Metrics provider must be set");
            }
            if (decisionPolicy == null) {
                // Default policy with 1% error threshold
                decisionPolicy = new DefaultRampDecisionPolicy(0.01);
            }
            return new AdaptiveLoadPattern(
                config,
                metricsProvider,
                backpressureProvider,
                decisionPolicy,
                listeners
            );
        }
    }
}
