package com.vajrapulse.examples;

import com.vajrapulse.api.*;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.engine.MetricsProviderAdapter;

import java.time.Duration;

/**
 * Example demonstrating AdaptiveLoadPattern combined with WarmupCooldownLoadPattern.
 * 
 * <p>This example shows how to:
 * <ul>
 *   <li>Wrap an AdaptiveLoadPattern with warm-up and cool-down phases</li>
 *   <li>Ensure metrics are only recorded during steady-state</li>
 *   <li>Use adaptive patterns with clean baseline measurements</li>
 * </ul>
 * 
 * <p>Run with:
 * <pre>{@code
 * java -cp ... com.vajrapulse.examples.AdaptiveWithWarmupExample
 * }</pre>
 * 
 * @since 0.9.7
 */
@VirtualThreads
public class AdaptiveWithWarmupExample implements TaskLifecycle {
    
    private final java.net.http.HttpClient client;
    
    /**
     * Default constructor.
     */
    public AdaptiveWithWarmupExample() {
        this.client = java.net.http.HttpClient.newHttpClient();
    }
    
    @Override
    public void init() throws Exception {
        // No initialization needed
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://httpbin.org/get"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        
        try {
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return TaskResult.success();
            } else {
                return TaskResult.failure(new RuntimeException("HTTP " + response.statusCode()));
            }
        } catch (Exception e) {
            return TaskResult.failure(e);
        }
    }
    
    @Override
    public void teardown() throws Exception {
        // No cleanup needed
    }
    
    /**
     * Main entry point for the example.
     * 
     * @param args command-line arguments
     * @throws Exception if execution fails
     */
    public static void main(String[] args) throws Exception {
        try (MetricsCollector collector = new MetricsCollector()) {
            var metricsProvider = new MetricsProviderAdapter(collector);
            
            // Create adaptive pattern
            LoadPattern adaptivePattern = new AdaptiveLoadPattern(
                20.0,                    // Initial TPS
                10.0,                    // Ramp increment
                20.0,                    // Ramp decrement
                Duration.ofSeconds(5),   // Ramp interval
                100.0,                   // Max TPS
                Duration.ofSeconds(10),  // Sustain duration
                0.05,                    // Error threshold (5% as ratio)
                metricsProvider
            );
            
            // Wrap with warm-up and cool-down
            LoadPattern pattern = new WarmupCooldownLoadPattern(
                adaptivePattern,
                Duration.ofSeconds(10),  // Warm-up: 10 seconds
                Duration.ofSeconds(5)   // Cool-down: 5 seconds
            );
            
            System.out.println("=== Adaptive Pattern with Warm-up/Cool-down ===");
            System.out.println("Total Duration: " + pattern.getDuration());
            System.out.println("Warm-up: 10 seconds");
            System.out.println("Steady-state: Adaptive pattern");
            System.out.println("Cool-down: 5 seconds");
            System.out.println();
            
            ExecutionEngine engine = ExecutionEngine.builder()
                .withTask(new AdaptiveWithWarmupExample())
                .withLoadPattern(pattern)
                .withMetricsCollector(collector)
                .build();
            
            engine.run();
            
            var metrics = collector.snapshot();
            
            System.out.println("=== Results (Steady-State Only) ===");
            System.out.println("Total Executions: " + metrics.totalExecutions());
            System.out.println("Success Rate: " + metrics.successRate() + "%");
            System.out.println("Response TPS: " + metrics.responseTps());
            
            if (!metrics.successPercentiles().isEmpty()) {
                System.out.println("\nLatency Percentiles:");
                metrics.successPercentiles().forEach((p, latency) -> {
                    System.out.printf("  P%.0f: %.2f ms%n", p * 100, latency / 1_000_000.0);
                });
            }
        }
    }
}

