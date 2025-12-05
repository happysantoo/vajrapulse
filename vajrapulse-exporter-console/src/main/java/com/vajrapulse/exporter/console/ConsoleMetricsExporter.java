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
 *   Total:             3050
 *   Successful:        2995 (98.2%)
 *   Failed:            55 (1.8%)
 * 
 * Request TPS:         100.0
 * 
 * Response TPS:
 *   Total:             100.0
 *   Successful:        98.2
 *   Failed:            1.8
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
        
        // Request counts
        out.println("Requests:");
        out.printf("  Total:             %d%n", metrics.totalExecutions());
        out.printf("  Successful:        %d (%.1f%%)%n", 
            metrics.successCount(), metrics.successRate());
        out.printf("  Failed:            %d (%.1f%%)%n", 
            metrics.failureCount(), metrics.failureRate());
        out.println();
        
        // Request TPS (target/intended rate)
        double requestTps = metrics.responseTps();
        out.printf("Request TPS:         %.1f%n", requestTps);
        out.println();
        
        // Response TPS (actual achieved throughput)
        out.println("Response TPS:");
        out.printf("  Total:             %.1f%n", metrics.responseTps());
        out.printf("  Successful:        %.1f%n", metrics.successTps());
        out.printf("  Failed:            %.1f%n", metrics.failureTps());
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
        
        // Queue metrics
        out.println("Queue:");
        out.printf("  Size:              %d%n", metrics.queueSize());
        if (!metrics.queueWaitPercentiles().isEmpty()) {
            out.println("  Wait Time (ms):");
            metrics.queueWaitPercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    String label = formatPercentileLabel(e.getKey());
                    out.printf("    %s:  %.2f%n", label, nanosToMillis(e.getValue()));
                });
        }
        out.println();
        
        // Client-side metrics
        var clientMetrics = metrics.clientMetrics();
        if (clientMetrics != null && (clientMetrics.totalConnections() > 0 || 
            clientMetrics.queueDepth() > 0 || 
            clientMetrics.connectionTimeouts() > 0 || 
            clientMetrics.requestTimeouts() > 0 || 
            clientMetrics.connectionRefused() > 0)) {
            out.println("Client Metrics:");
            
            // Connection pool metrics
            if (clientMetrics.totalConnections() > 0) {
                out.println("  Connection Pool:");
                out.printf("    Active:           %d%n", clientMetrics.activeConnections());
                out.printf("    Idle:             %d%n", clientMetrics.idleConnections());
                out.printf("    Waiting:          %d%n", clientMetrics.waitingConnections());
                out.printf("    Total:            %d%n", clientMetrics.totalConnections());
                out.printf("    Utilization:      %.1f%%%n", clientMetrics.connectionPoolUtilization() * 100.0);
            }
            
            // Client queue metrics
            if (clientMetrics.queueDepth() > 0) {
                out.println("  Client Queue:");
                out.printf("    Depth:            %d%n", clientMetrics.queueDepth());
                if (clientMetrics.queueWaitTimeNanos() > 0) {
                    out.printf("    Avg Wait Time:    %.2f ms%n", clientMetrics.averageQueueWaitTimeMs());
                }
            }
            
            // Client-side errors
            long totalClientErrors = clientMetrics.connectionTimeouts() + 
                                    clientMetrics.requestTimeouts() + 
                                    clientMetrics.connectionRefused();
            if (totalClientErrors > 0) {
                out.println("  Client Errors:");
                if (clientMetrics.connectionTimeouts() > 0) {
                    out.printf("    Connection Timeouts:  %d%n", clientMetrics.connectionTimeouts());
                }
                if (clientMetrics.requestTimeouts() > 0) {
                    out.printf("    Request Timeouts:     %d%n", clientMetrics.requestTimeouts());
                }
                if (clientMetrics.connectionRefused() > 0) {
                    out.printf("    Connection Refused:   %d%n", clientMetrics.connectionRefused());
                }
            }
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
