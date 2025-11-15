package com.vajrapulse.worker;

import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.RampUpLoad;
import com.vajrapulse.api.RampUpToMaxLoad;
import com.vajrapulse.api.StaticLoad;
import com.vajrapulse.api.Task;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsCollector;
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
        description = "Load pattern mode: static, ramp, ramp-sustain (default: ${DEFAULT-VALUE})",
        defaultValue = "static"
    )
    private String mode;
    
    @Option(
        names = {"-t", "--tps"},
        description = "Target transactions per second (default: ${DEFAULT-VALUE})",
        defaultValue = "100"
    )
    private double tps;
    
    @Option(
        names = {"-d", "--duration"},
        description = "Test duration (e.g., 30s, 5m, 1h) (default: ${DEFAULT-VALUE})",
        defaultValue = "1m"
    )
    private String duration;
    
    @Option(
        names = {"-r", "--ramp-duration"},
        description = "Ramp-up duration for ramp modes (e.g., 30s, 1m) (default: ${DEFAULT-VALUE})",
        defaultValue = "30s"
    )
    private String rampDuration;
    
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
        
        // Create load pattern
        LoadPattern loadPattern = createLoadPattern();
        logger.info("Load pattern created: {}", loadPattern.getClass().getSimpleName());
        
        // Create metrics collector
        MetricsCollector metricsCollector = new MetricsCollector();
        
        // Run load test
        try (ExecutionEngine engine = new ExecutionEngine(task, loadPattern, metricsCollector)) {
            engine.run();
            logger.info("Load test completed");
        }
        
        // Export results
        AggregatedMetrics metrics = metricsCollector.snapshot();
        ConsoleMetricsExporter exporter = new ConsoleMetricsExporter();
        exporter.export("Load Test Results", metrics);
        
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
    
    private LoadPattern createLoadPattern() {
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
            default -> throw new IllegalArgumentException(
                "Unknown mode: " + mode + ". Valid modes: static, ramp, ramp-sustain"
            );
        };
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
