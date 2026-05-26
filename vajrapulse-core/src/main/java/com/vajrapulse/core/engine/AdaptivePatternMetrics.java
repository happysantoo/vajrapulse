package com.vajrapulse.core.engine;

import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern;
import com.vajrapulse.api.pattern.adaptive.AdaptivePhase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registers Micrometer metrics for adaptive load patterns.
 *
 * <p>This class registers gauges, counters, and timers to track:
 * <ul>
 *   <li>Current phase (RAMP_UP, RAMP_DOWN, SUSTAIN)</li>
 *   <li>Current target TPS</li>
 *   <li>Stable TPS found (if any)</li>
 *   <li>Phase transition count and reasons</li>
 *   <li>Phase duration timers</li>
 *   <li>TPS adjustment histogram</li>
 * </ul>
 *
 * <p>Each instance tracks its own registered meters so that {@link #unregister()}
 * removes only the meters belonging to this specific pattern, avoiding
 * cross-contamination when multiple adaptive patterns share a registry.
 *
 * @since 0.9.5
 */
public final class AdaptivePatternMetrics {
    
    /**
     * Minimum TPS change threshold to record as an adjustment (avoids noise from floating-point precision).
     */
    private static final double MIN_TPS_ADJUSTMENT_THRESHOLD = 0.001;

    private final AdaptiveLoadPattern pattern;
    private final MeterRegistry registry;
    private final String runId;
    private final PatternStateTracker tracker;
    private final List<Meter> registeredMeters;
    
    // Track previous state to detect transitions and TPS changes
    private static final class PatternStateTracker {
        private final AtomicReference<AdaptivePhase> lastPhase = new AtomicReference<>();
        private final AtomicReference<Double> lastTps = new AtomicReference<>();
        private final AtomicLong lastPhaseStartTime = new AtomicLong(0);
        
        // Counters for transition reasons
        private final Counter rampUpToRampDownCounter;
        private final Counter rampDownToSustainCounter;
        private final Counter rampDownToRampUpCounter; // Recovery: RAMP_DOWN at minimum -> RAMP_UP
        private final Counter rampUpToSustainCounter;
        
        // Timers for phase durations
        private final Timer rampUpDurationTimer;
        private final Timer rampDownDurationTimer;
        private final Timer sustainDurationTimer;
        
        // Histogram for TPS adjustments
        private final io.micrometer.core.instrument.DistributionSummary tpsAdjustmentHistogram;
        
        // Track all meters registered by this tracker for clean removal
        private final List<Meter> trackerMeters;

        PatternStateTracker(AdaptiveLoadPattern pattern, MeterRegistry registry, String runId) {
            List<Meter> localMeters = new ArrayList<>();

            // Transition reason counters
            var rampUpToRampDownBuilder = Counter.builder("vajrapulse.adaptive.transitions")
                .tag("from_phase", "RAMP_UP")
                .tag("to_phase", "RAMP_DOWN")
                .tag("reason", "error_threshold_exceeded")
                .description("Transitions from RAMP_UP to RAMP_DOWN due to error threshold");
            var rampDownToSustainBuilder = Counter.builder("vajrapulse.adaptive.transitions")
                .tag("from_phase", "RAMP_DOWN")
                .tag("to_phase", "SUSTAIN")
                .tag("reason", "stable_point_found")
                .description("Transitions from RAMP_DOWN to SUSTAIN when stable point found");
            var rampDownToRampUpBuilder = Counter.builder("vajrapulse.adaptive.transitions")
                .tag("from_phase", "RAMP_DOWN")
                .tag("to_phase", "RAMP_UP")
                .tag("reason", "recovery_from_minimum")
                .description("Transitions from RAMP_DOWN (at minimum) to RAMP_UP when conditions improved (recovery)");
            var rampUpToSustainBuilder = Counter.builder("vajrapulse.adaptive.transitions")
                .tag("from_phase", "RAMP_UP")
                .tag("to_phase", "SUSTAIN")
                .tag("reason", "max_tps_reached")
                .description("Transitions from RAMP_UP to SUSTAIN when max TPS reached");
            
            if (runId != null && !runId.isBlank()) {
                rampUpToRampDownBuilder.tag("run_id", runId);
                rampDownToSustainBuilder.tag("run_id", runId);
                rampDownToRampUpBuilder.tag("run_id", runId);
                rampUpToSustainBuilder.tag("run_id", runId);
            }
            
            this.rampUpToRampDownCounter = rampUpToRampDownBuilder.register(registry);
            localMeters.add(this.rampUpToRampDownCounter);
            this.rampDownToSustainCounter = rampDownToSustainBuilder.register(registry);
            localMeters.add(this.rampDownToSustainCounter);
            this.rampDownToRampUpCounter = rampDownToRampUpBuilder.register(registry);
            localMeters.add(this.rampDownToRampUpCounter);
            this.rampUpToSustainCounter = rampUpToSustainBuilder.register(registry);
            localMeters.add(this.rampUpToSustainCounter);
            
            // Phase duration timers
            var rampUpDurationBuilder = Timer.builder("vajrapulse.adaptive.phase.duration")
                .tag("phase", "ramp_up")
                .description("Duration of RAMP_UP phases");
            var rampDownDurationBuilder = Timer.builder("vajrapulse.adaptive.phase.duration")
                .tag("phase", "ramp_down")
                .description("Duration of RAMP_DOWN phases");
            var sustainDurationBuilder = Timer.builder("vajrapulse.adaptive.phase.duration")
                .tag("phase", "sustain")
                .description("Duration of SUSTAIN phases");
            
            if (runId != null && !runId.isBlank()) {
                rampUpDurationBuilder.tag("run_id", runId);
                rampDownDurationBuilder.tag("run_id", runId);
                sustainDurationBuilder.tag("run_id", runId);
            }
            this.rampUpDurationTimer = rampUpDurationBuilder.register(registry);
            localMeters.add(this.rampUpDurationTimer);
            this.rampDownDurationTimer = rampDownDurationBuilder.register(registry);
            localMeters.add(this.rampDownDurationTimer);
            this.sustainDurationTimer = sustainDurationBuilder.register(registry);
            localMeters.add(this.sustainDurationTimer);
            
            var tpsAdjustmentBuilder = io.micrometer.core.instrument.DistributionSummary.builder("vajrapulse.adaptive.tps_adjustment")
                .description("Magnitude of TPS adjustments in adaptive pattern");
            if (runId != null && !runId.isBlank()) {
                tpsAdjustmentBuilder.tag("run_id", runId);
            }
            this.tpsAdjustmentHistogram = tpsAdjustmentBuilder.register(registry);
            localMeters.add(this.tpsAdjustmentHistogram);
            
            this.trackerMeters = List.copyOf(localMeters);
        }
        
        /**
         * Updates the tracker based on the current pattern state.
         * Called from gauge polling to detect transitions and adjustments.
         */
        void update(AdaptiveLoadPattern pattern) {
            // Detect phase transitions
            AdaptivePhase previousPhase = lastPhase.getAndSet(pattern.getCurrentPhase());
            if (previousPhase != null && previousPhase != pattern.getCurrentPhase()) {
                recordPhaseTransition(previousPhase, pattern.getCurrentPhase(), pattern, System.currentTimeMillis());
            }
            
            // Detect TPS adjustments
            Double previousTps = lastTps.getAndSet(pattern.getCurrentTps());
            if (previousTps != null) {
                double adjustment = Math.abs(pattern.getCurrentTps() - previousTps);
                if (adjustment > MIN_TPS_ADJUSTMENT_THRESHOLD) {
                    tpsAdjustmentHistogram.record(adjustment);
                }
            }
            
            // If first observation, just record the start time
            if (lastPhaseStartTime.get() == 0) {
                lastPhaseStartTime.set(System.currentTimeMillis());
            }
        }
        
        /**
         * Records a phase transition and updates the appropriate timer.
         *
         * @param fromPhase previous phase
         * @param toPhase new phase
         * @param pattern adaptive load pattern
         * @param currentTime current time in milliseconds
         */
        private void recordPhaseTransition(AdaptivePhase fromPhase, AdaptivePhase toPhase, 
                AdaptiveLoadPattern pattern, long currentTime) {
            
            // Increment appropriate transition counter
            if (fromPhase == AdaptivePhase.RAMP_UP && toPhase == AdaptivePhase.RAMP_DOWN) {
                rampUpToRampDownCounter.increment();
            } else if (fromPhase == AdaptivePhase.RAMP_DOWN && toPhase == AdaptivePhase.SUSTAIN) {
                rampDownToSustainCounter.increment();
            } else if (fromPhase == AdaptivePhase.RAMP_DOWN && toPhase == AdaptivePhase.RAMP_UP) {
                rampDownToRampUpCounter.increment();
            } else if (fromPhase == AdaptivePhase.RAMP_UP && toPhase == AdaptivePhase.SUSTAIN) {
                rampUpToSustainCounter.increment();
            }
            
            // Only record if we have a valid start time
            long lastStart = lastPhaseStartTime.get();
            if (lastStart > 0) {
                long duration = currentTime - lastStart;

                switch (fromPhase) {
                    case RAMP_UP -> rampUpDurationTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
                    case RAMP_DOWN -> rampDownDurationTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
                    case SUSTAIN -> sustainDurationTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
                    default -> { /* Other phases handled above */ }
                }
            }
            lastPhaseStartTime.set(currentTime);
        }
        
        /**
         * Returns the list of meters registered by this tracker.
         * Used for clean removal during unregister.
         */
        List<Meter> getRegisteredMeters() {
            return trackerMeters;
        }
    }
    
    /**
     * Creates and registers metrics for an adaptive load pattern.
     *
     * <p>Registers the following metrics:
     * <ul>
     *   <li>{@code vajrapulse.adaptive.phase} - Current phase (0=RAMP_UP, 1=RAMP_DOWN, 2=SUSTAIN)</li>
     *   <li>{@code vajrapulse.adaptive.current_tps} - Current target TPS</li>
     *   <li>{@code vajrapulse.adaptive.stable_tps} - Stable TPS found (NaN if not found yet)</li>
     *   <li>{@code vajrapulse.adaptive.phase_transitions} - Number of phase transitions (gauge)</li>
     *   <li>{@code vajrapulse.adaptive.transitions} - Phase transition counters with reason tags</li>
     *   <li>{@code vajrapulse.adaptive.phase.duration} - Timer for each phase duration</li>
     *   <li>{@code vajrapulse.adaptive.tps_adjustment} - Histogram of TPS adjustment magnitudes</li>
     * </ul>
     *
     * <p>The metrics are updated periodically by observing pattern state changes.
     *
     * @param pattern the adaptive load pattern
     * @param registry the meter registry to register metrics in
     * @param runId optional run ID for tagging (can be null)
     */
    public AdaptivePatternMetrics(AdaptiveLoadPattern pattern, MeterRegistry registry, String runId) {
        this.pattern = pattern;
        this.registry = registry;
        this.runId = runId;

        // Create state tracker first so gauge lambdas can reference it
        this.tracker = new PatternStateTracker(pattern, registry, runId);

        // Phase gauge (0=RAMP_UP, 1=RAMP_DOWN, 2=SUSTAIN) — also drives tracker updates
        var phaseBuilder = Gauge.builder("vajrapulse.adaptive.phase", pattern,
                p -> {
                    tracker.update(p);
                    return (double) p.getCurrentPhase().ordinal();
                })
            .description("Current adaptive pattern phase (0=RAMP_UP, 1=RAMP_DOWN, 2=SUSTAIN)");
        if (runId != null && !runId.isBlank()) {
            phaseBuilder.tag("run_id", runId);
        }
        Gauge phaseGauge = phaseBuilder.register(registry);
        
        // Current TPS gauge
        var currentTpsBuilder = Gauge.builder("vajrapulse.adaptive.current_tps", pattern, 
                AdaptiveLoadPattern::getCurrentTps)
            .description("Current target TPS for adaptive pattern");
        if (runId != null && !runId.isBlank()) {
            currentTpsBuilder.tag("run_id", runId);
        }
        Gauge currentTpsGauge = currentTpsBuilder.register(registry);
        
        // Stable TPS gauge (NaN if not found yet)
        var stableTpsBuilder = Gauge.builder("vajrapulse.adaptive.stable_tps", pattern, 
                p -> {
                    double stable = p.getStableTps();
                    return stable >= 0 ? stable : Double.NaN;
                })
            .description("Stable TPS found by adaptive pattern (NaN if not found yet)");
        if (runId != null && !runId.isBlank()) {
            stableTpsBuilder.tag("run_id", runId);
        }
        Gauge stableTpsGauge = stableTpsBuilder.register(registry);
        
        // Phase transition counter (gauge based on pattern's internal counter)
        var phaseTransitionBuilder = Gauge.builder("vajrapulse.adaptive.phase_transitions", pattern, 
                AdaptiveLoadPattern::getPhaseTransitionCount)
            .description("Number of phase transitions in adaptive pattern");
        if (runId != null && !runId.isBlank()) {
            phaseTransitionBuilder.tag("run_id", runId);
        }
        Gauge phaseTransitionsGauge = phaseTransitionBuilder.register(registry);
        
        // Collect all registered meters for clean removal
        List<Meter> allMeters = new ArrayList<>();
        allMeters.add(phaseGauge);
        allMeters.add(currentTpsGauge);
        allMeters.add(stableTpsGauge);
        allMeters.add(phaseTransitionsGauge);
        this.registeredMeters = List.copyOf(allMeters);
    }
    
    /**
     * Unregisters all metrics registered by this instance.
     * 
     * <p>Removes only the meters that were registered by this specific instance,
     * avoiding cross-contamination when multiple adaptive patterns share the same
     * registry. This fixes the previous bug where unregister removed all
     * {@code vajrapulse.adaptive.*} meters from a shared registry.
     */
    public void unregister() {
        // Remove gauges registered in the constructor
        for (Meter meter : registeredMeters) {
            registry.remove(meter);
        }
        // Remove meters registered by the tracker (counters, timers, histogram)
        for (Meter meter : tracker.getRegisteredMeters()) {
            registry.remove(meter);
        }
    }
}
