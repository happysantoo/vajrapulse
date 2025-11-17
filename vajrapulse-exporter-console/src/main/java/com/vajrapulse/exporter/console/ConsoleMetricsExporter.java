package com.vajrapulse.exporter.console;

import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsExporter;

import java.io.PrintStream;

/**
 * Exports metrics to the console in a human-readable format.
 * 
 * <p>This exporter formats metrics as a table showing:
 * <ul>
 *   <li>Total/Success/Failure counts and rates</li>
 *   <li>Latency percentiles (dynamically displays all configured percentiles)</li>
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
 *   P75:  23.8
 *   P90:  38.4
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
public final class ConsoleMetricsExporter implements MetricsExporter {
    
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
        
        // Success latencies - display all configured percentiles
        if (metrics.successCount() > 0 && !metrics.successPercentiles().isEmpty()) {
            out.println("Success Latency (ms):");
            metrics.successPercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    String label = formatPercentileLabel(e.getKey());
                    out.printf("  %s:  %.2f%n", label, nanosToMillis(e.getValue()));
                });
            out.println();
        }
        
        // Failure latencies - display all configured percentiles
        if (metrics.failureCount() > 0 && !metrics.failurePercentiles().isEmpty()) {
            out.println("Failure Latency (ms):");
            metrics.failurePercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    String label = formatPercentileLabel(e.getKey());
                    out.printf("  %s:  %.2f%n", label, nanosToMillis(e.getValue()));
                });
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
    @Override
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
        
        if (metrics.successCount() > 0 && !metrics.successPercentiles().isEmpty()) {
            out.println("Success Latency (ms):");
            metrics.successPercentiles().forEach((percentile, nanos) -> {
                String label = formatPercentileLabel(percentile);
                out.printf("  %s:  %.2f%n", label, nanosToMillis(nanos));
            });
            out.println();
        }
        
        if (metrics.failureCount() > 0 && !metrics.failurePercentiles().isEmpty()) {
            out.println("Failure Latency (ms):");
            metrics.failurePercentiles().forEach((percentile, nanos) -> {
                String label = formatPercentileLabel(percentile);
                out.printf("  %s:  %.2f%n", label, nanosToMillis(nanos));
            });
            out.println();
        }
        
        out.println("========================================");
        out.println();
    }
    
    private double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }
    
    private String formatPercentileLabel(double percentile) {
        double pct = percentile * 100;
        java.math.BigDecimal bd = new java.math.BigDecimal(pct).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        String num = bd.scale() <= 0 ? bd.toBigInteger().toString() : bd.toPlainString();
        return "P" + num;
    }
}
