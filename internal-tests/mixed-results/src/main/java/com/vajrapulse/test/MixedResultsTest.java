package com.vajrapulse.test;

import com.vajrapulse.api.*;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import com.vajrapulse.worker.pipeline.MetricsPipeline;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mixed results test task for integration testing.
 * 
 * <p>This test verifies metrics collection for both
 * successful and failed executions.
 */
@VirtualThreads
public class MixedResultsTest implements Task {
    
    private final AtomicInteger counter = new AtomicInteger(0);
    
    /**
     * Default constructor for MixedResultsTest.
     */
    public MixedResultsTest() {
    }
    
    @Override
    public void setup() throws Exception {
        // No setup needed
    }
    
    @Override
    public TaskResult execute() throws Exception {
        Thread.sleep(10);
        int count = counter.incrementAndGet();
        
        // Fail every 5th request
        if (count % 5 == 0) {
            return TaskResult.failure(new RuntimeException("Simulated failure #" + count));
        }
        
        return TaskResult.success("ok");
    }
    
    @Override
    public void cleanup() throws Exception {
        // No cleanup needed
    }
    
    /**
     * Main entry point for the mixed results test.
     * 
     * @param args command-line arguments (optional)
     * @throws Exception if test execution fails
     */
    public static void main(String[] args) throws Exception {
        Task task = new MixedResultsTest();
        LoadPattern loadPattern = new StaticLoad(40.0, Duration.ofSeconds(10));
        
        try (MetricsPipeline pipeline = MetricsPipeline.builder()
                .addExporter(new ConsoleMetricsExporter())
                .withPeriodic(Duration.ofSeconds(2))
                .build()) {
            var metrics = pipeline.run(task, loadPattern);
            
            System.out.println("\n=== Test Summary ===");
            System.out.println("Total Executions: " + metrics.totalExecutions());
            System.out.println("Success Count: " + metrics.successCount());
            System.out.println("Failure Count: " + metrics.failureCount());
            System.out.println("Success Rate: " + String.format("%.2f%%", metrics.successRate()));
            System.out.println("Failure Rate: " + String.format("%.2f%%", metrics.failureRate()));
        }
    }
}

