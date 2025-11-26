package com.vajrapulse.core.engine;

import com.vajrapulse.api.AdaptiveLoadPattern;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers Micrometer metrics for adaptive load patterns.
 * 
 * <p>This class registers gauges and counters to track:
 * <ul>
 *   <li>Current phase (RAMP_UP, RAMP_DOWN, SUSTAIN, COMPLETE)</li>
 *   <li>Current target TPS</li>
 *   <li>Stable TPS found (if any)</li>
 *   <li>Phase transition count</li>
 * </ul>
 * 
 * @since 0.9.5
 */
public final class AdaptivePatternMetrics {
    
    private AdaptivePatternMetrics() {
        // Utility class
    }
    
    /**
     * Registers metrics for an adaptive load pattern.
     * 
     * <p>Registers the following metrics:
     * <ul>
     *   <li>{@code vajrapulse.adaptive.phase} - Current phase (0=RAMP_UP, 1=RAMP_DOWN, 2=SUSTAIN, 3=COMPLETE)</li>
     *   <li>{@code vajrapulse.adaptive.current_tps} - Current target TPS</li>
     *   <li>{@code vajrapulse.adaptive.stable_tps} - Stable TPS found (NaN if not found yet)</li>
     *   <li>{@code vajrapulse.adaptive.phase_transitions} - Number of phase transitions (gauge)</li>
     * </ul>
     * 
     * @param pattern the adaptive load pattern
     * @param registry the meter registry to register metrics in
     * @param runId optional run ID for tagging (can be null)
     */
    public static void register(AdaptiveLoadPattern pattern, MeterRegistry registry, String runId) {
        // Phase gauge (0=RAMP_UP, 1=RAMP_DOWN, 2=SUSTAIN, 3=COMPLETE)
        var phaseBuilder = Gauge.builder("vajrapulse.adaptive.phase", pattern, 
                p -> (double) p.getCurrentPhase().ordinal())
            .description("Current adaptive pattern phase (0=RAMP_UP, 1=RAMP_DOWN, 2=SUSTAIN, 3=COMPLETE)");
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
    }
}

