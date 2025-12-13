package com.vajrapulse.test;

import com.vajrapulse.api.*;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import com.vajrapulse.worker.pipeline.LoadTestRunner;

import java.time.Duration;

/**
 * Simple success-only test task for integration testing.
 * 
 * <p>This test verifies basic execution engine functionality
 * with a task that always succeeds.
 */
@VirtualThreads
public class SimpleSuccessTest implements Task {
    
    /**
     * Default constructor for SimpleSuccessTest.
     */
    public SimpleSuccessTest() {
    }
    
    @Override
    public void setup() throws Exception {
        // No setup needed
    }
    
    @Override
    public TaskResult execute() throws Exception {
        // Simulate I/O-bound operation
        Thread.sleep(10);
        return TaskResult.success("ok");
    }
    
    @Override
    public void cleanup() throws Exception {
        // No cleanup needed
    }
    
    /**
     * Main entry point for the simple success test.
     * 
     * @param args command-line arguments (optional: pattern type)
     * @throws Exception if test execution fails
     */
    public static void main(String[] args) throws Exception {
        Task task = new SimpleSuccessTest();
        
        // Default to static load, but allow override via args
        String patternType = args.length > 0 ? args[0].toLowerCase() : "static";
        LoadPattern loadPattern = switch (patternType) {
            case "static" -> new StaticLoad(50.0, Duration.ofSeconds(10));
            case "ramp" -> new RampUpLoad(100.0, Duration.ofSeconds(10));
            case "ramp-sustain" -> new RampUpToMaxLoad(100.0, Duration.ofSeconds(5), Duration.ofSeconds(5));
            case "step" -> new StepLoad(java.util.List.of(
                new StepLoad.Step(25.0, Duration.ofSeconds(3)),
                new StepLoad.Step(50.0, Duration.ofSeconds(3)),
                new StepLoad.Step(75.0, Duration.ofSeconds(4))
            ));
            case "sine" -> new SineWaveLoad(50.0, 25.0, Duration.ofSeconds(10), Duration.ofSeconds(5));
            case "spike" -> new SpikeLoad(30.0, 100.0, Duration.ofSeconds(10), Duration.ofSeconds(3), Duration.ofSeconds(1));
            default -> throw new IllegalArgumentException("Unknown pattern: " + patternType);
        };
        
        try (LoadTestRunner pipeline = LoadTestRunner.builder()
                .addExporter(new ConsoleMetricsExporter())
                .withPeriodic(Duration.ofSeconds(2))
                .build()) {
            var metrics = pipeline.run(task, loadPattern);
            
            System.out.println("\n=== Test Summary ===");
            System.out.println("Total Executions: " + metrics.totalExecutions());
            System.out.println("Success Rate: " + String.format("%.2f%%", metrics.successRate()));
            System.out.println("Response TPS: " + String.format("%.2f", metrics.responseTps()));
        }
    }
}

