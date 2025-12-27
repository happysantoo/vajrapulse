package com.vajrapulse.exporter.console;

import com.vajrapulse.api.metrics.RunContext;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.LatencyStats;
import com.vajrapulse.core.metrics.MetricsExporter;

import java.io.PrintStream;

/**
 * Exports metrics to the console in a human-readable format.
 * 
 * <p>This exporter formats metrics as a table showing:
 * <ul>
 *   <li>Run metadata (runId, task class, pattern type)</li>
 *   <li>Elapsed time and throughput (TPS)</li>
 *   <li>Request counts (total/success/failure) with rates</li>
 *   <li>Response TPS (actual throughput achieved)</li>
 *   <li>Statistical summary (mean, stddev, min, max)</li>
 *   <li>Latency percentiles (dynamically displays all configured percentiles)</li>
 *   <li>Formatted duration values (ms)</li>
 * </ul>
 * 
 * <p>Example output:
 * <pre>
 * ========================================
 * Load Test Results
 * ========================================
 * Run ID:           abc123
 * Task:             HttpLoadTest
 * Pattern:          StaticLoad
 * Elapsed Time:     30.5s
 * 
 * Requests:
 *   Total:          3050
 *   Successful:     2995 (98.2%)
 *   Failed:         55 (1.8%)
 * 
 * Response TPS:
 *   Total:          100.0
 *   Successful:     98.2
 *   Failed:         1.8
 * 
 * Success Latency Statistics:
 *   Mean:           12.5 ms
 *   Std Dev:        5.2 ms
 *   Min:            2.1 ms
 *   Max:            150.3 ms
 * 
 * Success Latency Percentiles (ms):
 *   P50:            12.5
 *   P95:            45.2
 *   P99:            78.9
 * ========================================
 * </pre>
 * 
 * @since 0.9.0
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
        printMetricsBody(metrics, null);
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
        export(title, metrics, RunContext.empty());
    }
    
    /**
     * Exports metrics with a custom title and run context.
     * 
     * @param title the title to display
     * @param metrics the metrics to export
     * @param context the run context with metadata
     * @since 0.9.12
     */
    @Override
    public void export(String title, AggregatedMetrics metrics, RunContext context) {
        out.println();
        out.println("========================================");
        out.println(title);
        out.println("========================================");
        printMetricsBody(metrics, context);
        out.println("========================================");
        out.println();
    }
    
    private void printMetricsBody(AggregatedMetrics metrics, RunContext context) {
        // Run metadata (if available)
        if (context != null && !"unknown".equals(context.runId())) {
            out.printf("Run ID:              %s%n", context.runId());
        }
        if (context != null && !"unknown".equals(context.taskClass())) {
            out.printf("Task:                %s%n", context.taskClass());
        }
        if (context != null && !"unknown".equals(context.loadPatternType())) {
            out.printf("Pattern:             %s%n", context.loadPatternType());
        }
        
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
        
        // Response TPS (actual achieved throughput)
        out.println("Response TPS:");
        out.printf("  Total:             %.1f%n", metrics.responseTps());
        out.printf("  Successful:        %.1f%n", metrics.successTps());
        out.printf("  Failed:            %.1f%n", metrics.failureTps());
        out.println();
        
        // Success latency statistics
        LatencyStats successStats = metrics.successStats();
        if (successStats != null && successStats.hasData()) {
            out.println("Success Latency Statistics:");
            out.printf("  Mean:              %.2f ms%n", successStats.meanMillis());
            out.printf("  Std Dev:           %.2f ms%n", successStats.stdDevMillis());
            out.printf("  Min:               %.2f ms%n", successStats.minMillis());
            out.printf("  Max:               %.2f ms%n", successStats.maxMillis());
            out.printf("  CV:                %.1f%%%n", successStats.coefficientOfVariation());
            out.println();
        }
        
        // Success latencies - display all configured percentiles
        if (metrics.successCount() > 0 && !metrics.successPercentiles().isEmpty()) {
            out.println("Success Latency Percentiles (ms):");
            metrics.successPercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    String label = formatPercentileLabel(e.getKey());
                    out.printf("  %s:  %.2f%n", label, nanosToMillis(e.getValue()));
                });
            out.println();
        }
        
        // Failure latency statistics
        LatencyStats failureStats = metrics.failureStats();
        if (failureStats != null && failureStats.hasData()) {
            out.println("Failure Latency Statistics:");
            out.printf("  Mean:              %.2f ms%n", failureStats.meanMillis());
            out.printf("  Std Dev:           %.2f ms%n", failureStats.stdDevMillis());
            out.printf("  Min:               %.2f ms%n", failureStats.minMillis());
            out.printf("  Max:               %.2f ms%n", failureStats.maxMillis());
            out.printf("  CV:                %.1f%%%n", failureStats.coefficientOfVariation());
            out.println();
        }
        
        // Failure latencies - display all configured percentiles
        if (metrics.failureCount() > 0 && !metrics.failurePercentiles().isEmpty()) {
            out.println("Failure Latency Percentiles (ms):");
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
        
        // System info (if available)
        if (context != null && context.systemInfo() != null && !"unknown".equals(context.systemInfo().javaVersion())) {
            out.println("System:");
            out.printf("  Java:              %s%n", context.systemInfo().javaVersion());
            out.printf("  OS:                %s %s%n", context.systemInfo().osName(), context.systemInfo().osArch());
            out.printf("  Host:              %s%n", context.systemInfo().hostname());
            out.printf("  CPUs:              %d%n", context.systemInfo().availableProcessors());
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
