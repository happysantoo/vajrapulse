package com.vajrapulse.exporter.console;

import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsExporter;

import java.io.PrintStream;

/**
 * Exports metrics to the console in a human-readable format.
 * 
 * <p>This exporter formats metrics as a table showing:
 * <ul>
 *   <li>Elapsed time and throughput (TPS)</li>
 *   <li>Request counts (total/success/failure) with rates</li>
 *   <li>Response TPS (actual throughput achieved)</li>
 *   <li>Latency percentiles (dynamically displays all configured percentiles)</li>
 *   <li>Formatted duration values (ms)</li>
 * </ul>
 * 
 * <p>Example output:
 * <pre>
 * ========================================
 * Load Test Results
 * ========================================
 * Elapsed Time:        30.5s
 * 
 * Requests:
 *   Total:             3050 (100.0 TPS)
 *   Successful:        2995 (98.2%, 98.2 TPS)
 *   Failed:            55 (1.8%, 1.8 TPS)
 * 
 * Success Latency (ms):
 *   P50:  12.5
 *   P95:  45.2
 *   P99:  78.9
 * 
 * Failure Latency (ms):
 *   P50:  150.3
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
        printMetricsBody(metrics);
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
        printMetricsBody(metrics);
        out.println("========================================");
        out.println();
    }
    
    private void printMetricsBody(AggregatedMetrics metrics) {
        // Elapsed time
        out.printf("Elapsed Time:        %.1fs%n", metrics.elapsedMillis() / 1000.0);
        out.println();
        
        // Request counts and TPS
        out.println("Requests:");
        out.printf("  Total:             %d (%.1f TPS)%n", 
            metrics.totalExecutions(), metrics.responseTps());
        out.printf("  Successful:        %d (%.1f%%, %.1f TPS)%n", 
            metrics.successCount(), metrics.successRate(), metrics.successTps());
        out.printf("  Failed:            %d (%.1f%%, %.1f TPS)%n", 
            metrics.failureCount(), metrics.failureRate(), metrics.failureTps());
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
