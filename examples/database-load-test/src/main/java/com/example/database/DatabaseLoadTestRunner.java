package com.example.database;

import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.pattern.StaticLoad;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;

import java.time.Duration;

/**
 * Main runner for database load test example.
 * 
 * <p>This example demonstrates:
 * <ul>
 *   <li>Database load testing with JDBC</li>
 *   <li>Connection pooling with HikariCP</li>
 *   <li>Virtual threads for I/O-bound database operations</li>
 *   <li>Metrics collection and console export</li>
 * </ul>
 * 
 * <p>Usage:
 * <pre>{@code
 * // Run with default H2 in-memory database
 * ./gradlew :examples:database-load-test:run
 * 
 * // Run with PostgreSQL (set environment variables)
 * export DATABASE_URL="jdbc:postgresql://localhost:5432/testdb"
 * export DATABASE_USER="postgres"
 * export DATABASE_PASSWORD="password"
 * ./gradlew :examples:database-load-test:run
 * }</pre>
 * 
 * @since 0.9.10
 */
public class DatabaseLoadTestRunner {
    
    /**
     * Main entry point for the database load test example.
     * 
     * @param args command-line arguments (optional: TPS rate)
     * @throws Exception if test execution fails
     */
    public static void main(String[] args) throws Exception {
        // Parse TPS from args or use default
        double tps = args.length > 0 ? Double.parseDouble(args[0]) : 50.0;
        Duration duration = Duration.ofSeconds(30);
        
        System.out.println("Starting database load test:");
        System.out.println("  TPS: " + tps);
        System.out.println("  Duration: " + duration);
        System.out.println("  Database: " + getDatabaseInfo());
        System.out.println();
        
        // Create task
        DatabaseLoadTest task = new DatabaseLoadTest();
        
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
    
    /**
     * Gets database connection info for display.
     */
    private static String getDatabaseInfo() {
        String url = System.getenv("DATABASE_URL");
        if (url == null || url.isBlank()) {
            url = System.getProperty("database.url");
        }
        if (url == null || url.isBlank()) {
            return "H2 (in-memory)";
        }
        if (url.contains("postgresql")) {
            return "PostgreSQL";
        }
        if (url.contains("h2")) {
            return "H2";
        }
        return "Unknown";
    }
}
