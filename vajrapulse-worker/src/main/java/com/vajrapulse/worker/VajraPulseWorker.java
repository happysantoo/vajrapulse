package com.vajrapulse.worker;

import com.vajrapulse.api.AdaptiveLoadPattern;
import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.MetricsProvider;
import com.vajrapulse.api.RampUpLoad;
import com.vajrapulse.api.RampUpToMaxLoad;
import com.vajrapulse.api.StaticLoad;
import com.vajrapulse.api.StepLoad;
import com.vajrapulse.api.SineWaveLoad;
import com.vajrapulse.api.SpikeLoad;
import com.vajrapulse.api.Task;
import com.vajrapulse.core.engine.MetricsProviderAdapter;
import com.vajrapulse.core.config.ConfigLoader;
import com.vajrapulse.core.config.VajraPulseConfig;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.tracing.Tracing;
import com.vajrapulse.core.logging.StructuredLogger;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Main entry point for VajraPulse load testing worker.
 * 
 * <p>This CLI application:
 * <ul>
 *   <li>Loads the specified task class</li>
 *   <li>Configures the load pattern (static, ramp, ramp-sustain)</li>
 *   <li>Executes the load test</li>
 *   <li>Displays results to console</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * java -jar vajrapulse-worker.jar \
 *   --task com.example.MyTask \
 *   --mode static \
 *   --tps 100 \
 *   --duration 5m
 * </pre>
 */
@Command(
    name = "vajrapulse",
    description = "VajraPulse Load Testing Framework",
    mixinStandardHelpOptions = true,
    version = "1.0.0-SNAPSHOT"
)
public final class VajraPulseWorker implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(VajraPulseWorker.class);
    
    @Parameters(
        index = "0",
        description = "Fully qualified task class name (e.g., com.example.MyTask)"
    )
    private String taskClassName;
    
    @Option(
        names = {"-m", "--mode"},
        description = "Load pattern mode: static, ramp, ramp-sustain, step, sine, spike, adaptive (default: ${DEFAULT-VALUE})",
        defaultValue = "static"
    )
    private String mode;
    
    @Option(
        names = {"-t", "--tps"},
        description = "Target transactions per second (static/ramp/ramp-sustain base TPS) (default: ${DEFAULT-VALUE})",
        defaultValue = "100"
    )
    private double tps;
    
    @Option(
        names = {"-d", "--duration"},
        description = "Test duration (static/ramp-sustain total, sine totalDuration, spike totalDuration) e.g. 30s, 5m, 1h (default: ${DEFAULT-VALUE})",
        defaultValue = "1m"
    )
    private String duration;
    
    @Option(
        names = {"-r", "--ramp-duration"},
        description = "Ramp-up duration for ramp / ramp-sustain (default: ${DEFAULT-VALUE})",
        defaultValue = "30s"
    )
    private String rampDuration;

    // Step pattern specific: steps formatted as rate:duration,rate:duration
    @Option(
        names = {"--steps"},
        description = "Comma separated steps for step mode: rate:duration,... (duration units: ms,s,m,h)"
    )
    private String stepsSpec;

    // Sine pattern specific
    @Option(
        names = {"--mean-rate"},
        description = "Mean TPS for sine mode"
    )
    private Double sineMeanRate;
    @Option(
        names = {"--amplitude"},
        description = "Amplitude (positive) for sine mode"
    )
    private Double sineAmplitude;
    @Option(
        names = {"--period"},
        description = "Period for sine mode (e.g. 10s)"
    )
    private String sinePeriod;

    // Spike pattern specific
    @Option(
        names = {"--base-rate"},
        description = "Base TPS for spike mode"
    )
    private Double spikeBaseRate;
    @Option(
        names = {"--spike-rate"},
        description = "Spike TPS for spike mode"
    )
    private Double spikeSpikeRate;
    @Option(
        names = {"--spike-interval"},
        description = "Interval between spikes (e.g. 30s)"
    )
    private String spikeInterval;
    @Option(
        names = {"--spike-duration"},
        description = "Duration of each spike (e.g. 2s)"
    )
    private String spikeDuration;

    // Adaptive pattern specific
    @Option(
        names = {"--initial-tps"},
        description = "Initial TPS for adaptive mode (default: ${DEFAULT-VALUE})",
        defaultValue = "100"
    )
    private double adaptiveInitialTps;
    
    @Option(
        names = {"--ramp-increment"},
        description = "TPS increment per interval for adaptive mode (default: ${DEFAULT-VALUE})",
        defaultValue = "50"
    )
    private double adaptiveRampIncrement;
    
    @Option(
        names = {"--ramp-decrement"},
        description = "TPS decrement per interval for adaptive mode (default: ${DEFAULT-VALUE})",
        defaultValue = "100"
    )
    private double adaptiveRampDecrement;
    
    @Option(
        names = {"--ramp-interval"},
        description = "Time between adjustments for adaptive mode (e.g. 1m) (default: ${DEFAULT-VALUE})",
        defaultValue = "1m"
    )
    private String adaptiveRampInterval;
    
    @Option(
        names = {"--max-tps"},
        description = "Maximum TPS limit for adaptive mode (use 'unlimited' for no limit) (default: ${DEFAULT-VALUE})",
        defaultValue = "5000"
    )
    private String adaptiveMaxTps;
    
    @Option(
        names = {"--sustain-duration"},
        description = "Duration to sustain at stable point for adaptive mode (e.g. 10m) (default: ${DEFAULT-VALUE})",
        defaultValue = "10m"
    )
    private String adaptiveSustainDuration;
    
    @Option(
        names = {"--error-threshold"},
        description = "Error rate threshold for adaptive mode (0.0 to 1.0, e.g. 0.01 = 1%%) (default: ${DEFAULT-VALUE})",
        defaultValue = "0.01"
    )
    private double adaptiveErrorThreshold;

    // Run ID override
    @Option(
        names = {"--run-id"},
        description = "Explicit run id to use (UUID or custom string). If omitted a UUID is generated."
    )
    private String runIdOverride;

    // External configuration file
    @Option(
        names = {"--config"},
        description = "Path to configuration file (yaml/json). Overrides defaults + env vars."
    )
    private String configPath;
    
    @Override
    public Integer call() throws Exception {
        logger.info("VajraPulse Load Testing Framework starting...");
        logger.info("Task: {}", taskClassName);
        logger.info("Mode: {}", mode);
        logger.info("TPS: {}", tps);
        logger.info("Duration: {}", duration);
        
        // Load task class
        Task task = loadTask(taskClassName);
        logger.info("Task loaded successfully: {}", task.getClass().getName());
        
        // Create runId and metrics collector tagged with it
        String runId = (runIdOverride != null && !runIdOverride.isBlank())
            ? runIdOverride
            : java.util.UUID.randomUUID().toString();
        MetricsCollector metricsCollector = MetricsCollector.createWithRunId(runId, new double[]{0.50, 0.95, 0.99});
        logger.info("Run initialized runId={}", runId);
        
        // Create load pattern (adaptive mode needs metrics collector)
        LoadPattern loadPattern = createLoadPattern(metricsCollector);
        logger.info("Load pattern created: {}", loadPattern.getClass().getSimpleName());
        // Initialize tracing if enabled
        Tracing.initIfEnabled(runId);
        StructuredLogger.info(VajraPulseWorker.class, "start", java.util.Map.of(
            "run_id", runId,
            "task", taskClassName,
            "mode", mode,
            "tps", tps,
            "duration", duration
        ));
        
        // Run load test
        VajraPulseConfig config = (configPath != null && !configPath.isBlank())
            ? ConfigLoader.load(java.nio.file.Paths.get(configPath))
            : ConfigLoader.load();

        try (ExecutionEngine engine = new ExecutionEngine(task, loadPattern, metricsCollector, runId, config)) {
            engine.run();
            logger.info("Load test completed");
        }
        
        // Export results
        AggregatedMetrics metrics = metricsCollector.snapshot();
        ConsoleMetricsExporter exporter = new ConsoleMetricsExporter();
        exporter.export("Load Test Results (runId=" + runId + ")", metrics);
        StructuredLogger.info(VajraPulseWorker.class, "finished", java.util.Map.of(
            "run_id", runId,
            "total", metrics.totalExecutions(),
            "success", metrics.successCount(),
            "failure", metrics.failureCount()
        ));
        
        return 0;
    }
    
    private Task loadTask(String className) throws Exception {
        try {
            Class<?> taskClass = Class.forName(className);
            if (!Task.class.isAssignableFrom(taskClass)) {
                throw new IllegalArgumentException(
                    "Class " + className + " does not implement Task interface"
                );
            }
            return (Task) taskClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            logger.error("Task class not found: {}", className);
            throw new IllegalArgumentException("Task class not found: " + className, e);
        } catch (Exception e) {
            logger.error("Failed to instantiate task: {}", className, e);
            throw new IllegalArgumentException("Failed to instantiate task: " + className, e);
        }
    }
    
    private LoadPattern createLoadPattern(MetricsCollector metricsCollector) {
        Duration testDuration = parseDuration(duration);
        
        switch (mode.toLowerCase()) {
            case "static" -> {
                logger.debug("Creating static load: {} TPS for {}", tps, testDuration);
                return new StaticLoad(tps, testDuration);
            }
            case "ramp" -> {
                Duration ramp = parseDuration(rampDuration);
                logger.debug("Creating ramp-up load: 0 to {} TPS over {}", tps, ramp);
                return new RampUpLoad(tps, ramp);
            }
            case "ramp-sustain" -> {
                Duration ramp = parseDuration(rampDuration);
                Duration sustain = testDuration.minus(ramp);
                logger.debug("Creating ramp-sustain load: 0 to {} TPS over {}, sustain for {}", 
                    tps, ramp, sustain);
                return new RampUpToMaxLoad(tps, ramp, sustain);
            }
            case "step" -> {
                if (stepsSpec == null || stepsSpec.isBlank()) {
                    throw new IllegalArgumentException("--steps required for step mode");
                }
                java.util.List<StepLoad.Step> steps = new java.util.ArrayList<>();
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
                logger.debug("Creating step load with {} steps totalDuration={}", steps.size(), new StepLoad(steps).getDuration());
                return new StepLoad(java.util.List.copyOf(steps));
            }
            case "sine" -> {
                if (sineMeanRate == null || sineAmplitude == null || sinePeriod == null) {
                    throw new IllegalArgumentException("--mean-rate, --amplitude, --period required for sine mode");
                }
                Duration period = parseDuration(sinePeriod);
                logger.debug("Creating sine load mean={} amplitude={} period={} totalDuration={}", sineMeanRate, sineAmplitude, period, testDuration);
                return new SineWaveLoad(sineMeanRate, sineAmplitude, testDuration, period);
            }
            case "spike" -> {
                if (spikeBaseRate == null || spikeSpikeRate == null || spikeInterval == null || spikeDuration == null) {
                    throw new IllegalArgumentException("--base-rate, --spike-rate, --spike-interval, --spike-duration required for spike mode");
                }
                Duration interval = parseDuration(spikeInterval);
                Duration spikeDur = parseDuration(spikeDuration);
                logger.debug("Creating spike load baseRate={} spikeRate={} interval={} spikeDuration={} totalDuration={}", spikeBaseRate, spikeSpikeRate, interval, spikeDur, testDuration);
                return new SpikeLoad(spikeBaseRate, spikeSpikeRate, testDuration, interval, spikeDur);
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
                return new AdaptiveLoadPattern(
                    adaptiveInitialTps,
                    adaptiveRampIncrement,
                    adaptiveRampDecrement,
                    rampInterval,
                    maxTps,
                    sustainDuration,
                    adaptiveErrorThreshold,
                    provider
                );
            }
            default -> throw new IllegalArgumentException(
                "Unknown mode: " + mode + ". Valid modes: static, ramp, ramp-sustain, step, sine, spike, adaptive"
            );
        }
    }
    
    private Duration parseDuration(String durationStr) {
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
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new VajraPulseWorker()).execute(args);
        System.exit(exitCode);
    }
}
