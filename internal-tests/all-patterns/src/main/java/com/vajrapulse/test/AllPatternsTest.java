package com.vajrapulse.test;

import com.vajrapulse.api.*;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import com.vajrapulse.worker.pipeline.LoadTestRunner;

import java.time.Duration;
import java.util.List;

/**
 * Comprehensive test that exercises all load patterns.
 * 
 * <p>This test runs through all available load patterns
 * to verify they work correctly with the execution engine.
 */
@VirtualThreads
public class AllPatternsTest implements Task {
    
    /**
     * Default constructor for AllPatternsTest.
     */
    public AllPatternsTest() {
    }
    
    @Override
    public void setup() throws Exception {
        System.out.println("Initializing AllPatternsTest...");
    }
    
    @Override
    public TaskResult execute() throws Exception {
        Thread.sleep(10);
        return TaskResult.success("ok");
    }
    
    @Override
    public void cleanup() throws Exception {
        System.out.println("Cleaning up AllPatternsTest...");
    }
    
    /**
     * Main entry point that tests all load patterns.
     * 
     * @param args command-line arguments (optional)
     * @throws Exception if test execution fails
     */
    public static void main(String[] args) throws Exception {
        Task task = new AllPatternsTest();
        
        List<LoadPattern> patterns = List.of(
            new StaticLoad(50.0, Duration.ofSeconds(5)),
            new RampUpLoad(100.0, Duration.ofSeconds(5)),
            new RampUpToMaxLoad(80.0, Duration.ofSeconds(3), Duration.ofSeconds(2)),
            new StepLoad(List.of(
                new StepLoad.Step(25.0, Duration.ofSeconds(2)),
                new StepLoad.Step(50.0, Duration.ofSeconds(2)),
                new StepLoad.Step(75.0, Duration.ofSeconds(1))
            )),
            new SineWaveLoad(40.0, 20.0, Duration.ofSeconds(5), Duration.ofSeconds(2)),
            new SpikeLoad(30.0, 90.0, Duration.ofSeconds(5), Duration.ofSeconds(2), Duration.ofMillis(500))
        );
        
        String[] patternNames = {
            "StaticLoad",
            "RampUpLoad",
            "RampUpToMaxLoad",
            "StepLoad",
            "SineWaveLoad",
            "SpikeLoad"
        };
        
        for (int i = 0; i < patterns.size(); i++) {
            LoadPattern pattern = patterns.get(i);
            String name = patternNames[i];
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("Testing: " + name);
            System.out.println("=".repeat(60));
            
            try (LoadTestRunner pipeline = LoadTestRunner.builder()
                    .addExporter(new ConsoleMetricsExporter())
                    .build()) {
                var metrics = pipeline.run(task, pattern);
                
                System.out.println("\n" + name + " Results:");
                System.out.println("  Total Executions: " + metrics.totalExecutions());
                System.out.println("  Success Rate: " + String.format("%.2f%%", metrics.successRate()));
                System.out.println("  Response TPS: " + String.format("%.2f", metrics.responseTps()));
            }
            
            // Brief pause between patterns
            Thread.sleep(500);
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("All patterns tested successfully!");
        System.out.println("=".repeat(60));
    }
}

