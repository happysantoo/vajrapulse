package com.example.multi;

import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.pattern.StaticLoad;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.metrics.MetricsExporter;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import com.vajrapulse.exporter.otel.OpenTelemetryExporter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Example demonstrating multiple exporters working together.
 * 
 * <p>This example shows how to export metrics to multiple destinations
 * simultaneously:
 * <ul>
 *   <li>Console exporter - for immediate feedback</li>
 *   <li>OpenTelemetry exporter - for observability platforms</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>{@code
 * // Set OTLP endpoint (optional, uses default if not set)
 * export OTLP_ENDPOINT="http://localhost:4318"
 * 
 * // Run the test
 * ./gradlew :examples:multi-exporter:run
 * }</pre>
 * 
 * @since 0.9.10
 */
public class MultiExporterRunner {
    
    /**
     * Composite exporter that forwards to multiple exporters.
     */
    private static class CompositeExporter implements MetricsExporter {
        private final List<MetricsExporter> exporters;
        
        CompositeExporter(List<MetricsExporter> exporters) {
            this.exporters = new ArrayList<>(exporters);
        }
        
        @Override
        public void export(String title, AggregatedMetrics metrics) {
            // Export to all exporters, handling failures gracefully
            for (MetricsExporter exporter : exporters) {
                try {
                    exporter.export(title, metrics);
                } catch (Exception e) {
                    System.err.println("Exporter failed: " + exporter.getClass().getSimpleName() + 
                        " - " + e.getMessage());
                    // Continue with other exporters
                }
            }
        }
    }
    
    /**
     * Main entry point for the multi-exporter example.
     * 
     * @param args command-line arguments (optional: TPS rate)
     * @throws Exception if test execution fails
     */
    public static void main(String[] args) throws Exception {
        // Parse TPS from args or use default
        double tps = args.length > 0 ? Double.parseDouble(args[0]) : 50.0;
        Duration duration = Duration.ofSeconds(30);
        
        System.out.println("Starting multi-exporter load test:");
        System.out.println("  TPS: " + tps);
        System.out.println("  Duration: " + duration);
        System.out.println("  Exporters: Console + OpenTelemetry");
        System.out.println();
        
        // Create task
        MultiExporterTest task = new MultiExporterTest();
        
        // Create load pattern
        LoadPattern loadPattern = new StaticLoad(tps, duration);
        
        // Create multiple exporters
        ConsoleMetricsExporter consoleExporter = new ConsoleMetricsExporter();
        
        // OpenTelemetry exporter (optional - only if endpoint is configured)
        String otlpEndpoint = System.getenv("OTLP_ENDPOINT");
        if (otlpEndpoint == null || otlpEndpoint.isBlank()) {
            otlpEndpoint = System.getProperty("otlp.endpoint", "http://localhost:4318");
        }
        
        List<MetricsExporter> exporters = new ArrayList<>();
        exporters.add(consoleExporter);
        
        // Add OpenTelemetry exporter (will fail gracefully if endpoint is unreachable)
        try (OpenTelemetryExporter otelExporter = OpenTelemetryExporter.builder()
                .endpoint(otlpEndpoint)
                .resourceAttributes(java.util.Map.of(
                    "service.name", "multi-exporter-example",
                    "service.version", "0.9.10"
                ))
                .build()) {
            
            exporters.add(otelExporter);
            
            // Create composite exporter
            CompositeExporter compositeExporter = new CompositeExporter(exporters);
            
            // Create metrics collector with periodic export
            try (MetricsCollector metrics = new MetricsCollector()) {
                // Create and run engine
                ExecutionEngine engine = ExecutionEngine.builder()
                    .withTask(task)
                    .withLoadPattern(loadPattern)
                    .withMetricsCollector(metrics)
                    .build();
                
                try {
                    // Start periodic export thread
                    Thread exportThread = new Thread(() -> {
                        try {
                            while (!Thread.currentThread().isInterrupted()) {
                                Thread.sleep(5000); // Export every 5 seconds
                                compositeExporter.export("Periodic", metrics.snapshot());
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                    exportThread.setDaemon(true);
                    exportThread.start();
                    
                    engine.run();
                    
                    // Final export
                    System.out.println("\n=== Final Results ===");
                    compositeExporter.export("Final", metrics.snapshot());
                    
                    exportThread.interrupt();
                    exportThread.join(1000);
                } finally {
                    engine.close();
                }
            }
        }
    }
}
