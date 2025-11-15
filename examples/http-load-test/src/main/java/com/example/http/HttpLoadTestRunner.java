package com.example.http;

import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.StaticLoad;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;

import java.time.Duration;

/**
 * Runner application for HTTP load test example.
 * 
 * <p>This demonstrates how to programmatically configure and run
 * a load test without using the CLI.
 * 
 * <p>Usage:
 * <pre>
 * ./gradlew :examples:http-load-test:run
 * </pre>
 */
public final class HttpLoadTestRunner {
    
    public static void main(String[] args) throws Exception {
        // Create task instance
        HttpLoadTest task = new HttpLoadTest();
        
        // Configure load pattern: 100 TPS for 30 seconds
        LoadPattern loadPattern = new StaticLoad(100.0, Duration.ofSeconds(30));
        
        // Create metrics collector
        MetricsCollector metricsCollector = new MetricsCollector();
        
        // Display test configuration
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║        VajraPulse HTTP Load Test Example              ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Task:     " + task.getClass().getSimpleName());
        System.out.println("  Pattern:  Static Load");
        System.out.println("  TPS:      100");
        System.out.println("  Duration: 30 seconds");
        System.out.println("  Endpoint: https://httpbin.org/delay/0");
        System.out.println();
        System.out.println("Starting load test...");
        System.out.println();
        
        // Run load test
        try (ExecutionEngine engine = new ExecutionEngine(task, loadPattern, metricsCollector)) {
            engine.run();
        }
        
        System.out.println();
        System.out.println("Load test completed!");
        System.out.println();
        
        // Export results to console
        AggregatedMetrics metrics = metricsCollector.snapshot();
        ConsoleMetricsExporter exporter = new ConsoleMetricsExporter();
        exporter.export("HTTP Load Test Results", metrics);
    }
}
