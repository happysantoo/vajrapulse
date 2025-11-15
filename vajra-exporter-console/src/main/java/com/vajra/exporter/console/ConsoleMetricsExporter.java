package com.vajra.exporter.console;

import com.vajra.core.metrics.AggregatedMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

/**
 * Exports metrics to the console in a human-readable format.
 * 
 * <p>This exporter formats metrics as a table showing:
 * <ul>
 *   <li>Total/Success/Failure counts and rates</li>
 *   <li>Latency percentiles (P50, P95, P99)</li>
 *   <li>Formatted duration values (ms)</li>
 * </ul>
 * 
 * <p>Example output:
 * <pre>
 * ========================================
 * Load Test Results
 * ========================================
 * Total Executions:    1000
 * Successful:          950 (95.0%)
 * Failed:              50 (5.0%)
 * 
 * Success Latency (ms):
 *   P50:  12.5
 *   P95:  45.2
 *   P99:  78.9
 * 
 * Failure Latency (ms):
 *   P50:  150.3
 *   P95:  250.7
 *   P99:  380.1
 * ========================================
 * </pre>
 */
public final class ConsoleMetricsExporter {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleMetricsExporter.class);
    
    private final PrintStream out;
    
    /**
     * Creates an exporter that writes to System.out.
     */
    public ConsoleMetricsExporter() {
        this(System.out);
    }
    
    /**
     * Creates an exporter that writes to the specified stream.
     * 
     * @param out the output stream
     */
    public ConsoleMetricsExporter(PrintStream out) {
        this.out = out;
    }
    
    /**
     * Exports the metrics in a formatted table.
     * 
     * @param metrics the metrics to export
     */
    public void export(AggregatedMetrics metrics) {
        out.println();
        out.println("========================================");
        out.println("Load Test Results");
        out.println("========================================");
        
        // Summary
        out.printf("Total Executions:    %d%n", metrics.totalExecutions());
        out.printf("Successful:          %d (%.1f%%)%n", 
            metrics.successCount(), metrics.successRate());
        out.printf("Failed:              %d (%.1f%%)%n", 
            metrics.failureCount(), metrics.failureRate());
        out.println();
        
        // Success latencies
        if (metrics.successCount() > 0) {
            out.println("Success Latency (ms):");
            out.printf("  P50:  %.2f%n", nanosToMillis(metrics.successP50()));
            out.printf("  P95:  %.2f%n", nanosToMillis(metrics.successP95()));
            out.printf("  P99:  %.2f%n", nanosToMillis(metrics.successP99()));
            out.println();
        }
        
        // Failure latencies
        if (metrics.failureCount() > 0) {
            out.println("Failure Latency (ms):");
            out.printf("  P50:  %.2f%n", nanosToMillis(metrics.failureP50()));
            out.printf("  P95:  %.2f%n", nanosToMillis(metrics.failureP95()));
            out.printf("  P99:  %.2f%n", nanosToMillis(metrics.failureP99()));
            out.println();
        }
        
        out.println("========================================");
        out.println();
    }
    
    /**
     * Exports metrics with a custom title.
     * 
     * @param title the title to display
     * @param metrics the metrics to export
     */
    public void export(String title, AggregatedMetrics metrics) {
        out.println();
        out.println("========================================");
        out.println(title);
        out.println("========================================");
        
        // Same formatting as above
        out.printf("Total Executions:    %d%n", metrics.totalExecutions());
        out.printf("Successful:          %d (%.1f%%)%n", 
            metrics.successCount(), metrics.successRate());
        out.printf("Failed:              %d (%.1f%%)%n", 
            metrics.failureCount(), metrics.failureRate());
        out.println();
        
        if (metrics.successCount() > 0) {
            out.println("Success Latency (ms):");
            out.printf("  P50:  %.2f%n", nanosToMillis(metrics.successP50()));
            out.printf("  P95:  %.2f%n", nanosToMillis(metrics.successP95()));
            out.printf("  P99:  %.2f%n", nanosToMillis(metrics.successP99()));
            out.println();
        }
        
        if (metrics.failureCount() > 0) {
            out.println("Failure Latency (ms):");
            out.printf("  P50:  %.2f%n", nanosToMillis(metrics.failureP50()));
            out.printf("  P95:  %.2f%n", nanosToMillis(metrics.failureP95()));
            out.printf("  P99:  %.2f%n", nanosToMillis(metrics.failureP99()));
            out.println();
        }
        
        out.println("========================================");
        out.println();
    }
    
    private double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }
}
