package com.vajrapulse.worker;

import com.vajrapulse.api.AdaptiveLoadPattern;
import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.MetricsProvider;
import com.vajrapulse.api.RampUpLoad;
import com.vajrapulse.api.RampUpToMaxLoad;
import com.vajrapulse.api.SineWaveLoad;
import com.vajrapulse.api.SpikeLoad;
import com.vajrapulse.api.StaticLoad;
import com.vajrapulse.api.StepLoad;
import com.vajrapulse.core.engine.MetricsProviderAdapter;
import com.vajrapulse.core.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Factory for creating load patterns from configuration.
 * 
 * <p>This class centralizes load pattern creation logic, making it reusable
 * across CLI and programmatic usage.
 * 
 * @since 0.9.5
 */
public final class LoadPatternFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(LoadPatternFactory.class);
    
    private LoadPatternFactory() {
        // Utility class - no instantiation
    }
    
    /**
     * Creates a load pattern based on the specified mode and parameters.
     * 
     * @param mode the load pattern mode (static, ramp, ramp-sustain, step, sine, spike, adaptive)
     * @param tps base TPS for static/ramp/ramp-sustain modes
     * @param duration test duration string (e.g., "30s", "5m", "1h")
     * @param rampDuration ramp-up duration string for ramp/ramp-sustain modes
     * @param stepsSpec comma-separated steps for step mode (e.g., "50@30s,200@1m")
     * @param sineMeanRate mean TPS for sine mode
     * @param sineAmplitude amplitude for sine mode
     * @param sinePeriod period string for sine mode
     * @param spikeBaseRate base TPS for spike mode
     * @param spikeSpikeRate spike TPS for spike mode
     * @param spikeInterval interval string between spikes
     * @param spikeDuration duration string of each spike
     * @param adaptiveInitialTps initial TPS for adaptive mode
     * @param adaptiveRampIncrement TPS increment per interval for adaptive mode
     * @param adaptiveRampDecrement TPS decrement per interval for adaptive mode
     * @param adaptiveRampInterval interval string for adaptive mode
     * @param adaptiveMaxTps maximum TPS for adaptive mode (or "unlimited")
     * @param adaptiveSustainDuration sustain duration string for adaptive mode
     * @param adaptiveErrorThreshold error rate threshold for adaptive mode (0.0-1.0)
     * @param metricsCollector metrics collector (required for adaptive mode)
     * @return the created load pattern
     * @throws IllegalArgumentException if parameters are invalid or mode is unknown
     */
    public static LoadPattern create(
            String mode,
            double tps,
            String duration,
            String rampDuration,
            String stepsSpec,
            Double sineMeanRate,
            Double sineAmplitude,
            String sinePeriod,
            Double spikeBaseRate,
            Double spikeSpikeRate,
            String spikeInterval,
            String spikeDuration,
            double adaptiveInitialTps,
            double adaptiveRampIncrement,
            double adaptiveRampDecrement,
            String adaptiveRampInterval,
            String adaptiveMaxTps,
            String adaptiveSustainDuration,
            double adaptiveErrorThreshold,
            MetricsCollector metricsCollector) {
        
        Duration testDuration = parseDuration(duration);
        
        return switch (mode.toLowerCase()) {
            case "static" -> {
                logger.debug("Creating static load: {} TPS for {}", tps, testDuration);
                yield new StaticLoad(tps, testDuration);
            }
            case "ramp" -> {
                Duration ramp = parseDuration(rampDuration);
                logger.debug("Creating ramp-up load: 0 to {} TPS over {}", tps, ramp);
                yield new RampUpLoad(tps, ramp);
            }
            case "ramp-sustain" -> {
                Duration ramp = parseDuration(rampDuration);
                Duration sustain = testDuration.minus(ramp);
                logger.debug("Creating ramp-sustain load: 0 to {} TPS over {}, sustain for {}", 
                    tps, ramp, sustain);
                yield new RampUpToMaxLoad(tps, ramp, sustain);
            }
            case "step" -> {
                if (stepsSpec == null || stepsSpec.isBlank()) {
                    throw new IllegalArgumentException("--steps required for step mode");
                }
                List<StepLoad.Step> steps = parseSteps(stepsSpec);
                logger.debug("Creating step load with {} steps totalDuration={}", 
                    steps.size(), new StepLoad(steps).getDuration());
                yield new StepLoad(List.copyOf(steps));
            }
            case "sine" -> {
                if (sineMeanRate == null || sineAmplitude == null || sinePeriod == null) {
                    throw new IllegalArgumentException("--mean-rate, --amplitude, --period required for sine mode");
                }
                Duration period = parseDuration(sinePeriod);
                logger.debug("Creating sine load mean={} amplitude={} period={} totalDuration={}", 
                    sineMeanRate, sineAmplitude, period, testDuration);
                yield new SineWaveLoad(sineMeanRate, sineAmplitude, testDuration, period);
            }
            case "spike" -> {
                if (spikeBaseRate == null || spikeSpikeRate == null || spikeInterval == null || spikeDuration == null) {
                    throw new IllegalArgumentException("--base-rate, --spike-rate, --spike-interval, --spike-duration required for spike mode");
                }
                Duration interval = parseDuration(spikeInterval);
                Duration spikeDur = parseDuration(spikeDuration);
                logger.debug("Creating spike load baseRate={} spikeRate={} interval={} spikeDuration={} totalDuration={}", 
                    spikeBaseRate, spikeSpikeRate, interval, spikeDur, testDuration);
                yield new SpikeLoad(spikeBaseRate, spikeSpikeRate, testDuration, interval, spikeDur);
            }
            case "adaptive" -> {
                Duration rampInterval = parseDuration(adaptiveRampInterval);
                Duration sustainDuration = parseDuration(adaptiveSustainDuration);
                
                double maxTps;
                if ("unlimited".equalsIgnoreCase(adaptiveMaxTps)) {
                    maxTps = Double.POSITIVE_INFINITY;
                } else {
                    maxTps = Double.parseDouble(adaptiveMaxTps);
                }
                
                MetricsProvider provider = new MetricsProviderAdapter(metricsCollector);
                
                logger.debug("Creating adaptive load initialTps={} rampIncrement={} rampDecrement={} rampInterval={} maxTps={} sustainDuration={} errorThreshold={}",
                    adaptiveInitialTps, adaptiveRampIncrement, adaptiveRampDecrement, rampInterval, maxTps, sustainDuration, adaptiveErrorThreshold);
                yield AdaptiveLoadPattern.builder()
                    .initialTps(adaptiveInitialTps)
                    .rampIncrement(adaptiveRampIncrement)
                    .rampDecrement(adaptiveRampDecrement)
                    .rampInterval(rampInterval)
                    .maxTps(maxTps)
                    .sustainDuration(sustainDuration)
                    .errorThreshold(adaptiveErrorThreshold)
                    .metricsProvider(provider)
                    .build();
            }
            default -> throw new IllegalArgumentException(
                "Unknown mode: " + mode + ". Valid modes: static, ramp, ramp-sustain, step, sine, spike, adaptive"
            );
        };
    }
    
    /**
     * Parses a duration string (e.g., "30s", "5m", "1h").
     * 
     * @param durationStr duration string
     * @return parsed duration
     * @throws IllegalArgumentException if duration string is invalid
     */
    public static Duration parseDuration(String durationStr) {
        durationStr = durationStr.toLowerCase().trim();
        
        if (durationStr.endsWith("ms")) {
            long millis = Long.parseLong(durationStr.substring(0, durationStr.length() - 2));
            return Duration.ofMillis(millis);
        } else if (durationStr.endsWith("s")) {
            long seconds = Long.parseLong(durationStr.substring(0, durationStr.length() - 1));
            return Duration.ofSeconds(seconds);
        } else if (durationStr.endsWith("m")) {
            long minutes = Long.parseLong(durationStr.substring(0, durationStr.length() - 1));
            return Duration.ofMinutes(minutes);
        } else if (durationStr.endsWith("h")) {
            long hours = Long.parseLong(durationStr.substring(0, durationStr.length() - 1));
            return Duration.ofHours(hours);
        } else {
            // Assume seconds if no unit
            long seconds = Long.parseLong(durationStr);
            return Duration.ofSeconds(seconds);
        }
    }
    
    /**
     * Parses step specification string (e.g., "50@30s,200@1m").
     * 
     * @param stepsSpec comma-separated steps
     * @return list of steps
     * @throws IllegalArgumentException if steps specification is invalid
     */
    private static List<StepLoad.Step> parseSteps(String stepsSpec) {
        List<StepLoad.Step> steps = new java.util.ArrayList<>();
        for (String part : stepsSpec.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            String[] pieces = trimmed.split(":");
            if (pieces.length != 2) {
                throw new IllegalArgumentException("Invalid step segment: " + trimmed + " (expected rate:duration)");
            }
            double rate = Double.parseDouble(pieces[0]);
            Duration d = parseDuration(pieces[1]);
            steps.add(new StepLoad.Step(rate, d));
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("No valid steps parsed for step mode");
        }
        return steps;
    }
}

