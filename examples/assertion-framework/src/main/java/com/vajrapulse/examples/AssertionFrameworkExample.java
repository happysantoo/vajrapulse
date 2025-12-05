package com.vajrapulse.examples;

import com.vajrapulse.api.*;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.MetricsCollector;

import java.time.Duration;

/**
 * Example demonstrating the Assertion Framework in a CI/CD-like scenario.
 * 
 * <p>This example shows how to:
 * <ul>
 *   <li>Define assertions for SLOs (Service Level Objectives)</li>
 *   <li>Evaluate assertions after test execution</li>
 *   <li>Use composite assertions for complex validation</li>
 *   <li>Fail builds if assertions don't pass (CI/CD integration)</li>
 * </ul>
 * 
 * <p>Run with:
 * <pre>{@code
 * java -cp ... com.vajrapulse.examples.AssertionFrameworkExample
 * }</pre>
 * 
 * @since 0.9.7
 */
@VirtualThreads
public class AssertionFrameworkExample implements TaskLifecycle {
    
    private final java.net.http.HttpClient client;
    
    /**
     * Default constructor.
     */
    public AssertionFrameworkExample() {
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
        // Define SLOs as assertions
        Assertion p95Latency = Assertions.latency(0.95, Duration.ofMillis(500));
        Assertion p99Latency = Assertions.latency(0.99, Duration.ofMillis(1000));
        Assertion errorRate = Assertions.errorRate(1.0); // 1% max error rate
        Assertion successRate = Assertions.successRate(99.0); // 99% min success rate
        Assertion throughput = Assertions.throughput(10.0); // At least 10 TPS
        Assertion executionCount = Assertions.executionCount(100); // At least 100 executions
        
        // Composite assertion: all must pass
        Assertion allAssertions = Assertions.all(
            p95Latency,
            p99Latency,
            errorRate,
            successRate,
            throughput,
            executionCount
        );
        
        LoadPattern pattern = new StaticLoad(20.0, Duration.ofSeconds(30));
        
        try (MetricsCollector collector = new MetricsCollector()) {
            ExecutionEngine engine = ExecutionEngine.builder()
                .withTask(new AssertionFrameworkExample())
                .withLoadPattern(pattern)
                .withMetricsCollector(collector)
                .build();
            
            System.out.println("=== Running Load Test ===");
            System.out.println("Pattern: Static 20 TPS for 30 seconds");
            System.out.println();
            
            engine.run();
            
            var metrics = collector.snapshot();
            
            System.out.println("=== Test Results ===");
            System.out.println("Total Executions: " + metrics.totalExecutions());
            System.out.println("Success Rate: " + metrics.successRate() + "%");
            System.out.println("Error Rate: " + metrics.failureRate() + "%");
            System.out.println("Response TPS: " + metrics.responseTps());
            
            if (!metrics.successPercentiles().isEmpty()) {
                System.out.println("\nLatency Percentiles:");
                metrics.successPercentiles().forEach((p, latency) -> {
                    System.out.printf("  P%.0f: %.2f ms%n", p * 100, latency / 1_000_000.0);
                });
            }
            
            System.out.println("\n=== Evaluating Assertions ===");
            
            // Evaluate assertions
            AssertionResult result = allAssertions.evaluate(metrics);
            
            if (result.passed()) {
                System.out.println("✅ All assertions passed!");
                System.out.println("Result: " + (result.message() != null ? result.message() : "Success"));
                System.exit(0); // Success in CI/CD
            } else {
                System.err.println("❌ Assertions failed!");
                System.err.println("Failure: " + result.message());
                System.exit(1); // Fail build in CI/CD
            }
        }
    }
}

