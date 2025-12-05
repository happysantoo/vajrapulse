package com.vajrapulse.examples;

import com.vajrapulse.api.*;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.ClientMetrics;
import com.vajrapulse.core.metrics.MetricsCollector;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Example demonstrating client-side metrics with HTTP clients.
 * 
 * <p>This example shows how to:
 * <ul>
 *   <li>Track connection pool metrics from HTTP clients</li>
 *   <li>Record client-side errors (timeouts, connection refused)</li>
 *   <li>Monitor queue depth and wait times</li>
 *   <li>Use ClientMetrics builder for easy metric collection</li>
 * </ul>
 * 
 * <p>Run with:
 * <pre>{@code
 * java -cp ... com.vajrapulse.examples.ClientMetricsHttpExample
 * }</pre>
 * 
 * @since 0.9.7
 */
@VirtualThreads
public class ClientMetricsHttpExample implements TaskLifecycle {
    
    private HttpClient client;
    private final MetricsCollector metricsCollector;
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong connectionTimeouts = new AtomicLong(0);
    private final AtomicLong requestTimeouts = new AtomicLong(0);
    
    /**
     * Default constructor.
     */
    public ClientMetricsHttpExample() {
        this.metricsCollector = null; // Will be set via ExecutionEngine
        this.client = null; // Will be initialized in init()
    }
    
    /**
     * Constructor with metrics collector (for testing).
     */
    public ClientMetricsHttpExample(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        this.client = null; // Will be initialized in init()
    }
    
    @Override
    public void init() throws Exception {
        client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        if (metricsCollector == null) {
            // In real usage, metricsCollector comes from ExecutionEngine
            return TaskResult.success();
        }
        
        try {
            // Simulate HTTP request
            var request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://httpbin.org/get"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            
            activeConnections.incrementAndGet();
            
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            activeConnections.decrementAndGet();
            
            // Record client metrics
            recordClientMetrics(0, 0, 0, 0);
            
            if (response.statusCode() == 200) {
                return TaskResult.success(response.body());
            } else {
                return TaskResult.failure(new RuntimeException("HTTP " + response.statusCode()));
            }
            
        } catch (java.net.http.HttpTimeoutException e) {
            requestTimeouts.incrementAndGet();
            recordClientMetrics(0, 0, 0, 0);
            return TaskResult.failure(e);
            
        } catch (java.net.ConnectException e) {
            connectionTimeouts.incrementAndGet();
            recordClientMetrics(0, 0, 0, 0);
            return TaskResult.failure(e);
            
        } catch (Exception e) {
            recordClientMetrics(0, 0, 0, 0);
            return TaskResult.failure(e);
        }
    }
    
    private void recordClientMetrics(long queueDepth, long queueWaitNanos, long queueOpCount, long connectionRefused) {
        if (metricsCollector == null) {
            return;
        }
        
        // Use builder pattern for easy metric collection
        ClientMetrics metrics = ClientMetrics.builder()
            .activeConnections(activeConnections.get())
            .idleConnections(Math.max(0, 10 - activeConnections.get())) // Assume pool size of 10
            .waitingConnections(0)
            .queueDepth(queueDepth)
            .queueWaitTimeNanos(queueWaitNanos)
            .queueOperationCount(queueOpCount)
            .connectionTimeouts(connectionTimeouts.get())
            .requestTimeouts(requestTimeouts.get())
            .connectionRefused(connectionRefused)
            .build();
        
        metricsCollector.recordClientMetrics(metrics);
        
        // Also record individual errors
        if (connectionTimeouts.get() > 0) {
            metricsCollector.recordConnectionTimeout();
        }
        if (requestTimeouts.get() > 0) {
            metricsCollector.recordRequestTimeout();
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
        LoadPattern pattern = new StaticLoad(10.0, Duration.ofSeconds(30));
        
        try (MetricsCollector collector = new MetricsCollector()) {
            ExecutionEngine engine = ExecutionEngine.builder()
                .withTask(new ClientMetricsHttpExample(collector))
                .withLoadPattern(pattern)
                .withMetricsCollector(collector)
                .build();
            
            engine.run();
            
            var metrics = collector.snapshot();
            
            System.out.println("=== Client Metrics Example Results ===");
            System.out.println("Total Executions: " + metrics.totalExecutions());
            System.out.println("Success Rate: " + metrics.successRate() + "%");
            
            var clientMetrics = metrics.clientMetrics();
            System.out.println("\n=== Client-Side Metrics ===");
            System.out.println("Active Connections: " + clientMetrics.activeConnections());
            System.out.println("Idle Connections: " + clientMetrics.idleConnections());
            System.out.println("Connection Timeouts: " + clientMetrics.connectionTimeouts());
            System.out.println("Request Timeouts: " + clientMetrics.requestTimeouts());
        }
    }
}

