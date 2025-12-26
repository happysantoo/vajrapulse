package com.example.grpc;

import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.pattern.StaticLoad;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;

import java.time.Duration;

/**
 * Main runner for gRPC load test example.
 * 
 * <p>This example demonstrates:
 * <ul>
 *   <li>gRPC load testing with virtual threads</li>
 *   <li>Unary and streaming RPC calls</li>
 *   <li>Channel management and lifecycle</li>
 *   <li>Metrics collection for gRPC operations</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>{@code
 * // Set gRPC server address (default: localhost:50051)
 * export GRPC_SERVER_ADDRESS="localhost:50051"
 * 
 * // Run the test
 * ./gradlew :examples:grpc-load-test:run
 * 
 * // Or use system property
 * ./gradlew :examples:grpc-load-test:run -Dgrpc.server.address=localhost:50051
 * }</pre>
 * 
 * <p><strong>Note:</strong> This example requires a running gRPC server.
 * For testing without a server, you can use a mock gRPC server or
 * modify the code to handle connection failures gracefully.
 * 
 * @since 0.9.10
 */
public class GrpcLoadTestRunner {
    
    /**
     * Main entry point for the gRPC load test example.
     * 
     * @param args command-line arguments (optional: TPS rate)
     * @throws Exception if test execution fails
     */
    public static void main(String[] args) throws Exception {
        // Parse TPS from args or use default
        double tps = args.length > 0 ? Double.parseDouble(args[0]) : 50.0;
        Duration duration = Duration.ofSeconds(30);
        
        String serverAddress = System.getenv("GRPC_SERVER_ADDRESS");
        if (serverAddress == null || serverAddress.isBlank()) {
            serverAddress = System.getProperty("grpc.server.address", "localhost:50051");
        }
        
        System.out.println("Starting gRPC load test:");
        System.out.println("  TPS: " + tps);
        System.out.println("  Duration: " + duration);
        System.out.println("  Server: " + serverAddress);
        System.out.println("  Thread Strategy: Virtual Threads (I/O-bound)");
        System.out.println();
        
        // Create task
        GrpcLoadTest task = new GrpcLoadTest();
        
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
