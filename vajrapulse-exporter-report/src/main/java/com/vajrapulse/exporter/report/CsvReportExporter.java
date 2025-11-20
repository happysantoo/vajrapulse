package com.vajrapulse.exporter.report;

import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV report exporter for spreadsheet analysis.
 * 
 * <p>Exports metrics in CSV format suitable for Excel, LibreOffice, Google Sheets,
 * and other spreadsheet applications.
 * 
 * <p>Example usage:
 * <pre>{@code
 * MetricsPipeline.builder()
 *     .addExporter(new CsvReportExporter(Path.of("reports/test-run.csv")))
 *     .build()
 *     .run(task, loadPattern);
 * }</pre>
 */
public final class CsvReportExporter implements MetricsExporter {
    private static final Logger logger = LoggerFactory.getLogger(CsvReportExporter.class);
    
    private final Path outputPath;
    
    /**
     * Creates a new CSV report exporter.
     * 
     * @param outputPath path to output CSV file
     */
    public CsvReportExporter(Path outputPath) {
        this.outputPath = outputPath;
    }
    
    @Override
    public void export(String title, AggregatedMetrics metrics) {
        try {
            List<String> rows = buildCsvRows(title, metrics);
            String csv = String.join("\n", rows);
            
            // Create parent directories if needed
            var parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            
            Files.writeString(outputPath, csv);
            logger.info("CSV report written to: {}", outputPath);
        } catch (IOException e) {
            logger.error("Failed to write CSV report to {}", outputPath, e);
            throw new RuntimeException("Failed to write CSV report", e);
        }
    }
    
    private List<String> buildCsvRows(String title, AggregatedMetrics metrics) {
        List<String> rows = new ArrayList<>();
        
        // Header section
        rows.add("Metric,Value");
        rows.add("");
        
        // Metadata
        rows.add("Title," + escapeCsv(title));
        rows.add("Timestamp," + escapeCsv(Instant.now().toString()));
        rows.add("Elapsed Seconds," + (metrics.elapsedMillis() / 1000.0));
        rows.add("");
        
        // Summary
        rows.add("Summary,");
        rows.add("Total Executions," + metrics.totalExecutions());
        rows.add("Success Count," + metrics.successCount());
        rows.add("Failure Count," + metrics.failureCount());
        rows.add("Success Rate %," + String.format("%.2f", metrics.successRate()));
        rows.add("Failure Rate %," + String.format("%.2f", metrics.failureRate()));
        rows.add("Response TPS," + String.format("%.2f", metrics.responseTps()));
        rows.add("Success TPS," + String.format("%.2f", metrics.successTps()));
        rows.add("Failure TPS," + String.format("%.2f", metrics.failureTps()));
        rows.add("");
        
        // Queue metrics
        rows.add("Queue,");
        rows.add("Queue Size," + metrics.queueSize());
        if (!metrics.queueWaitPercentiles().isEmpty()) {
            rows.add("Queue Wait Time (ms),");
            metrics.queueWaitPercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    String label = formatPercentileLabel(e.getKey());
                    double value = toDouble(e.getValue());
                    rows.add(label + "," + String.format("%.2f", nanosToMillis(value)));
                });
        }
        rows.add("");
        
        // Success latencies
        if (!metrics.successPercentiles().isEmpty()) {
            rows.add("Success Latency (ms),");
            metrics.successPercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    String label = formatPercentileLabel(e.getKey());
                    double value = toDouble(e.getValue());
                    rows.add(label + "," + String.format("%.2f", nanosToMillis(value)));
                });
            rows.add("");
        }
        
        // Failure latencies
        if (!metrics.failurePercentiles().isEmpty()) {
            rows.add("Failure Latency (ms),");
            metrics.failurePercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    String label = formatPercentileLabel(e.getKey());
                    double value = toDouble(e.getValue());
                    rows.add(label + "," + String.format("%.2f", nanosToMillis(value)));
                });
        }
        
        return rows;
    }
    
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    private String formatPercentileLabel(double percentile) {
        double pct = percentile * 100;
        java.math.BigDecimal bd = new java.math.BigDecimal(pct)
            .setScale(3, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros();
        String num = bd.scale() <= 0 
            ? bd.toBigInteger().toString() 
            : bd.toPlainString();
        return "P" + num;
    }
    
    private double nanosToMillis(double nanos) {
        return nanos / 1_000_000.0;
    }
    
    private double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to double");
    }
}

