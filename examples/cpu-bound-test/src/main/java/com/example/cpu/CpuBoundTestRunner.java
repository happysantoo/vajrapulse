package com.example.cpu;

import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.pattern.StaticLoad;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;

import java.time.Duration;

/**
 * Main runner for CPU-bound load test example.
 * 
 * <p>This example demonstrates:
 * <ul>
 *   <li>CPU-bound load testing with platform threads</li>
 *   <li>Encryption and compression workloads</li>
 *   <li>Thread strategy selection (@PlatformThreads)</li>
 *   <li>Metrics collection for CPU-intensive tasks</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>{@code
 * // Run with default 10 TPS for 30 seconds
 * ./gradlew :examples:cpu-bound-test:run
 * 
 * // Run with custom TPS
 * ./gradlew :examples:cpu-bound-test:run --args "20"
 * }</pre>
 * 
 * <p><strong>Note:</strong> This example uses @PlatformThreads because
 * encryption and compression are CPU-intensive operations. Virtual threads
 * would not provide benefits for this workload.
 * 
 * @since 0.9.10
 */
public class CpuBoundTestRunner {
    
    /**
     * Main entry point for the CPU-bound load test example.
     * 
     * @param args command-line arguments (optional: TPS rate)
     * @throws Exception if test execution fails
     */
    public static void main(String[] args) throws Exception {
        // Parse TPS from args or use default (lower for CPU-bound work)
        double tps = args.length > 0 ? Double.parseDouble(args[0]) : 10.0;
        Duration duration = Duration.ofSeconds(30);
        
        System.out.println("Starting CPU-bound load test:");
        System.out.println("  TPS: " + tps);
        System.out.println("  Duration: " + duration);
        System.out.println("  Thread Strategy: Platform Threads (CPU-bound)");
        System.out.println();
        
        // Create task
        CpuBoundTest task = new CpuBoundTest();
        
        // Create load pattern
        LoadPattern loadPattern = new StaticLoad(tps, duration);
        
        // Create metrics collector and exporter
        try (MetricsCollector metrics = new MetricsCollector()) {
            ConsoleMetricsExporter exporter = new ConsoleMetricsExporter();
            
            // Create and run engine
            ExecutionEngine engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(loadPattern)
                .withMetricsCollector(metrics)
                .build();
            
            try {
                engine.run();
                
                // Print final results
                System.out.println("\n=== Final Results ===");
                exporter.export("Final", metrics.snapshot());
            } finally {
                engine.close();
            }
        }
    }
}
