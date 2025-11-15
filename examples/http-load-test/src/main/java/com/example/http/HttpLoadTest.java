package com.example.http;

import com.vajrapulse.api.Task;
import com.vajrapulse.api.TaskResult;
import com.vajrapulse.api.VirtualThreads;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Example HTTP load test using Java 21 HttpClient with virtual threads.
 * 
 * <p>This task sends HTTP GET requests to httpbin.org and validates responses.
 * It demonstrates:
 * <ul>
 *   <li>Using @VirtualThreads for I/O-bound operations</li>
 *   <li>Setup/cleanup lifecycle</li>
 *   <li>Proper error handling with TaskResult</li>
 * </ul>
 */
@VirtualThreads
public class HttpLoadTest implements Task {
    
    private HttpClient client;
    private HttpRequest request;
    
    @Override
    public void setup() throws Exception {
        // Create HTTP client with virtual thread executor
        client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        // Prepare request (reuse for all executions)
        request = HttpRequest.newBuilder()
            .uri(URI.create("https://httpbin.org/delay/0"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        
        System.out.println("HttpLoadTest setup completed");
    }
    
    @Override
    public TaskResult execute() throws Exception {
        HttpResponse<String> response = client.send(
            request, 
            HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() == 200) {
            return TaskResult.success(response.body());
        } else {
            return TaskResult.failure(
                new RuntimeException("HTTP " + response.statusCode())
            );
        }
    }
    
    @Override
    public void cleanup() throws Exception {
        System.out.println("HttpLoadTest cleanup completed");
        // HttpClient doesn't need explicit cleanup
    }
    
    /**
     * Main entry point to run this load test directly.
     * 
     * <p>Usage:
     * <pre>
     * ./gradlew run
     * </pre>
     */
    public static void main(String[] args) throws Exception {
        // Create task instance
        HttpLoadTest task = new HttpLoadTest();
        
        // Configure load pattern: 100 TPS for 30 seconds
        com.vajrapulse.api.LoadPattern loadPattern = 
            new com.vajrapulse.api.StaticLoad(100.0, Duration.ofSeconds(30));
        
        // Create metrics collector
        com.vajrapulse.core.metrics.MetricsCollector metricsCollector = 
            new com.vajrapulse.core.metrics.MetricsCollector();
        
        // Run load test
        System.out.println("Starting HTTP load test...");
        System.out.println("Target: 100 TPS for 30 seconds");
        System.out.println("Endpoint: https://httpbin.org/delay/0");
        System.out.println();
        
        try (com.vajrapulse.core.engine.ExecutionEngine engine = 
                new com.vajrapulse.core.engine.ExecutionEngine(task, loadPattern, metricsCollector)) {
            engine.run();
        }
        
        // Export results to console
        com.vajrapulse.core.metrics.AggregatedMetrics metrics = metricsCollector.snapshot();
        com.vajrapulse.exporter.console.ConsoleMetricsExporter exporter = 
            new com.vajrapulse.exporter.console.ConsoleMetricsExporter();
        exporter.export("HTTP Load Test Results", metrics);
    }
}
