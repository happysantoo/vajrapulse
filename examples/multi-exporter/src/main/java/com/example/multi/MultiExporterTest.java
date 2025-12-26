package com.example.multi;

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
 * Example task for multi-exporter demonstration.
 * 
 * <p>This is a simple HTTP task that will export metrics to multiple
 * exporters simultaneously (Console and OpenTelemetry).
 * 
 * @since 0.9.10
 */
@VirtualThreads
public class MultiExporterTest implements TaskLifecycle {
    
    private HttpClient client;
    private HttpRequest request;
    
    /**
     * Default constructor for MultiExporterTest.
     */
    public MultiExporterTest() {
        // Default constructor
    }
    
    @Override
    public void init() throws Exception {
        client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        
        request = HttpRequest.newBuilder()
            .uri(URI.create("https://httpbin.org/delay/0"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        
        System.out.println("MultiExporterTest init completed");
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        HttpResponse<String> response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
        
        if (response.statusCode() == 200) {
            return TaskResult.success("OK");
        } else {
            return TaskResult.failure(
                new RuntimeException("HTTP " + response.statusCode())
            );
        }
    }
    
    @Override
    public void teardown() throws Exception {
        System.out.println("MultiExporterTest teardown completed");
    }
}
