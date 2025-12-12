package com.example.http;

import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.TaskResult;
import com.vajrapulse.api.task.VirtualThreads;

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
 *   <li>Init/teardown lifecycle</li>
 *   <li>Proper error handling with TaskResult</li>
 * </ul>
 */
@VirtualThreads
public class HttpLoadTest implements TaskLifecycle {
    
    /**
     * Default constructor for HttpLoadTest.
     * Initializes the task for use with VajraPulse execution engine.
     */
    public HttpLoadTest() {
        // Default constructor - initialization happens in init()
    }
    
    private HttpClient client;
    private HttpRequest request;
    
    @Override
    public void init() throws Exception {
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
        
        System.out.println("HttpLoadTest init completed");
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
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
    public void teardown() throws Exception {
        System.out.println("HttpLoadTest teardown completed");
        // HttpClient doesn't need explicit cleanup
    }
    
   
}
