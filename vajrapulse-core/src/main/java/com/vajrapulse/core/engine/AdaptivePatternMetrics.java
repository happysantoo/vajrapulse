package com.vajrapulse.core.engine;

import com.vajrapulse.api.AdaptiveLoadPattern;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
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
 * @since 0.9.5
 */
public final class AdaptivePatternMetrics {
    
    // Store trackers per pattern instance to persist across gauge polls
    private static final ConcurrentHashMap<AdaptiveLoadPattern, PatternStateTracker> trackers = new ConcurrentHashMap<>();
    
    /**
     * Minimum TPS change threshold to record as an adjustment (avoids noise from floating-point precision).
     */
    private static final double MIN_TPS_ADJUSTMENT_THRESHOLD = 0.001;
    
    // Track previous state to detect transitions and TPS changes
    private static final class PatternStateTracker {
        private final AtomicReference<AdaptiveLoadPattern.Phase> lastPhase = new AtomicReference<>();
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
        
        PatternStateTracker(AdaptiveLoadPattern pattern, MeterRegistry registry, String runId) {
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
            this.rampDownToSustainCounter = rampDownToSustainBuilder.register(registry);
            this.rampDownToRampUpCounter = rampDownToRampUpBuilder.register(registry);
            this.rampUpToSustainCounter = rampUpToSustainBuilder.register(registry);
            
            // Phase duration timers
            var rampUpDurationBuilder = Timer.builder("vajrapulse.adaptive.phase.duration")
                .tag("phase", "RAMP_UP")
                .description("Duration spent in RAMP_UP phase");
            var rampDownDurationBuilder = Timer.builder("vajrapulse.adaptive.phase.duration")
                .tag("phase", "RAMP_DOWN")
                .description("Duration spent in RAMP_DOWN phase");
            var sustainDurationBuilder = Timer.builder("vajrapulse.adaptive.phase.duration")
                .tag("phase", "SUSTAIN")
                .description("Duration spent in SUSTAIN phase");
            if (runId != null && !runId.isBlank()) {
                rampUpDurationBuilder.tag("run_id", runId);
                rampDownDurationBuilder.tag("run_id", runId);
                sustainDurationBuilder.tag("run_id", runId);
            }
            
            this.rampUpDurationTimer = rampUpDurationBuilder.register(registry);
            this.rampDownDurationTimer = rampDownDurationBuilder.register(registry);
            this.sustainDurationTimer = sustainDurationBuilder.register(registry);
            
            // TPS adjustment histogram
            var tpsAdjustmentBuilder = io.micrometer.core.instrument.DistributionSummary.builder("vajrapulse.adaptive.tps_adjustment")
                .description("TPS adjustment magnitude (absolute change)")
                .baseUnit("tps");
            if (runId != null && !runId.isBlank()) {
                tpsAdjustmentBuilder.tag("run_id", runId);
            }
            this.tpsAdjustmentHistogram = tpsAdjustmentBuilder.register(registry);
        }
        
        void update(AdaptiveLoadPattern pattern) {
            AdaptiveLoadPattern.Phase currentPhase = pattern.getCurrentPhase();
            double currentTps = pattern.getCurrentTps();
            
            AdaptiveLoadPattern.Phase previousPhase = lastPhase.getAndSet(currentPhase);
            Double previousTps = lastTps.getAndSet(currentTps);
            
            // Detect phase transitions and record reasons
            if (previousPhase != null && previousPhase != currentPhase) {
                recordPhaseTransition(previousPhase, currentPhase);
                recordPhaseDuration(previousPhase);
            }
            
            // Track TPS adjustments (only if TPS actually changed)
            if (previousTps != null && Math.abs(currentTps - previousTps) > MIN_TPS_ADJUSTMENT_THRESHOLD) {
                double adjustment = Math.abs(currentTps - previousTps);
                tpsAdjustmentHistogram.record(adjustment);
            }
        }
        
        private void recordPhaseTransition(AdaptiveLoadPattern.Phase from, AdaptiveLoadPattern.Phase to) {
            if (from == AdaptiveLoadPattern.Phase.RAMP_UP && to == AdaptiveLoadPattern.Phase.RAMP_DOWN) {
                rampUpToRampDownCounter.increment();
            } else if (from == AdaptiveLoadPattern.Phase.RAMP_DOWN && to == AdaptiveLoadPattern.Phase.SUSTAIN) {
                rampDownToSustainCounter.increment();
            } else if (from == AdaptiveLoadPattern.Phase.RAMP_DOWN && to == AdaptiveLoadPattern.Phase.RAMP_UP) {
                // This includes recovery transitions (RAMP_DOWN at minimum -> RAMP_UP)
                rampDownToRampUpCounter.increment();
            } else if (from == AdaptiveLoadPattern.Phase.RAMP_UP && to == AdaptiveLoadPattern.Phase.SUSTAIN) {
                rampUpToSustainCounter.increment();
            }
        }
        
        private void recordPhaseDuration(AdaptiveLoadPattern.Phase phase) {
            long currentTime = System.currentTimeMillis();
            long phaseStart = lastPhaseStartTime.getAndSet(currentTime);
            if (phaseStart > 0) {
                long duration = currentTime - phaseStart;
                switch (phase) {
                    case RAMP_UP -> rampUpDurationTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
                    case RAMP_DOWN -> rampDownDurationTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
                    case SUSTAIN -> sustainDurationTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
                    default -> { /* Other phases handled above */ }
                }
            }
        }
    }
    
    private AdaptivePatternMetrics() {
        // Utility class
    }
    
    /**
     * Registers metrics for an adaptive load pattern.
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
    public static void register(AdaptiveLoadPattern pattern, MeterRegistry registry, String runId) {
        // Phase gauge (0=RAMP_UP, 1=RAMP_DOWN, 2=SUSTAIN)
        var phaseBuilder = Gauge.builder("vajrapulse.adaptive.phase", pattern, 
                p -> (double) p.getCurrentPhase().ordinal())
            .description("Current adaptive pattern phase (0=RAMP_UP, 1=RAMP_DOWN, 2=SUSTAIN)");
        if (runId != null && !runId.isBlank()) {
            phaseBuilder.tag("run_id", runId);
        }
        phaseBuilder.register(registry);
        
        // Current TPS gauge
        var currentTpsBuilder = Gauge.builder("vajrapulse.adaptive.current_tps", pattern, 
                AdaptiveLoadPattern::getCurrentTps)
            .description("Current target TPS for adaptive pattern");
        if (runId != null && !runId.isBlank()) {
            currentTpsBuilder.tag("run_id", runId);
        }
        currentTpsBuilder.register(registry);
        
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
        stableTpsBuilder.register(registry);
        
        // Phase transition counter (gauge based on pattern's internal counter)
        var phaseTransitionBuilder = Gauge.builder("vajrapulse.adaptive.phase_transitions", pattern, 
                AdaptiveLoadPattern::getPhaseTransitionCount)
            .description("Number of phase transitions in adaptive pattern");
        if (runId != null && !runId.isBlank()) {
            phaseTransitionBuilder.tag("run_id", runId);
        }
        phaseTransitionBuilder.register(registry);
        
        // Create and store state tracker for transition and adjustment metrics
        trackers.computeIfAbsent(pattern, 
            p -> new PatternStateTracker(p, registry, runId));
        
        // Register a gauge that updates the tracker when polled
        // This ensures metrics are updated periodically by Micrometer's polling mechanism
        var trackerUpdateBuilder = Gauge.builder("vajrapulse.adaptive.metrics_update", 
                () -> {
                    PatternStateTracker t = trackers.get(pattern);
                    if (t != null) {
                        t.update(pattern);
                    }
                    return 1.0; // Dummy value, we're using this for side effects
                })
            .description("Internal metric to trigger state tracking updates (value is always 1.0)");
        if (runId != null && !runId.isBlank()) {
            trackerUpdateBuilder.tag("run_id", runId);
        }
        trackerUpdateBuilder.register(registry);
    }
    
    /**
     * Removes the tracker for a pattern (for cleanup/testing).
     * 
     * @param pattern the pattern to remove tracking for
     */
    static void removeTracker(AdaptiveLoadPattern pattern) {
        trackers.remove(pattern);
    }
}

