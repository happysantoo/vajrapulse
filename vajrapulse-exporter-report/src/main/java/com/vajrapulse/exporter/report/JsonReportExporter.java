package com.vajrapulse.exporter.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON report exporter for programmatic analysis.
 * 
 * <p>Exports metrics in JSON format suitable for parsing by analysis tools,
 * CI/CD pipelines, and custom reporting systems.
 * 
 * <p>Example usage:
 * <pre>{@code
 * MetricsPipeline.builder()
 *     .addExporter(new JsonReportExporter(Path.of("reports/test-run.json")))
 *     .build()
 *     .run(task, loadPattern);
 * }</pre>
 */
public final class JsonReportExporter implements MetricsExporter {
    private static final Logger logger = LoggerFactory.getLogger(JsonReportExporter.class);
    
    private final Path outputPath;
    private final ObjectMapper mapper;
    
    /**
     * Creates a new JSON report exporter.
     * 
     * @param outputPath path to output JSON file
     */
    public JsonReportExporter(Path outputPath) {
        this.outputPath = outputPath;
        this.mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @Override
    public void export(String title, AggregatedMetrics metrics) {
        try {
            Map<String, Object> report = buildReport(title, metrics);
            String json = mapper.writeValueAsString(report);
            
            // Create parent directories if needed
            var parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            
            Files.writeString(outputPath, json);
            logger.info("JSON report written to: {}", outputPath);
        } catch (IOException e) {
            logger.error("Failed to write JSON report to {}", outputPath, e);
            throw new RuntimeException("Failed to write JSON report", e);
        }
    }
    
    private Map<String, Object> buildReport(String title, AggregatedMetrics metrics) {
        Map<String, Object> report = new LinkedHashMap<>();
        
        // Metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", title);
        metadata.put("timestamp", Instant.now().toString());
        metadata.put("elapsedSeconds", metrics.elapsedMillis() / 1000.0);
        report.put("metadata", metadata);
        
        // Summary
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalExecutions", metrics.totalExecutions());
        summary.put("successCount", metrics.successCount());
        summary.put("failureCount", metrics.failureCount());
        summary.put("successRate", metrics.successRate());
        summary.put("failureRate", metrics.failureRate());
        summary.put("responseTps", metrics.responseTps());
        summary.put("successTps", metrics.successTps());
        summary.put("failureTps", metrics.failureTps());
        report.put("summary", summary);
        
        // Queue metrics
        Map<String, Object> queue = new LinkedHashMap<>();
        queue.put("size", metrics.queueSize());
        if (!metrics.queueWaitPercentiles().isEmpty()) {
            Map<String, Double> waitTime = new LinkedHashMap<>();
            metrics.queueWaitPercentiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    double value = toDouble(e.getValue());
                    waitTime.put(formatPercentile(e.getKey()), nanosToMillis(value));
                });
            queue.put("waitTimeMs", waitTime);
        }
        report.put("queue", queue);
        
        // Success latencies (convert nanos to millis)
        if (!metrics.successPercentiles().isEmpty()) {
            Map<String, Double> successLatency = new LinkedHashMap<>();
            metrics.successPercentiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    double value = toDouble(e.getValue());
                    successLatency.put(formatPercentile(e.getKey()), nanosToMillis(value));
                });
            report.put("successLatencyMs", successLatency);
        }
        
        // Failure latencies (convert nanos to millis)
        if (!metrics.failurePercentiles().isEmpty()) {
            Map<String, Double> failureLatency = new LinkedHashMap<>();
            metrics.failurePercentiles().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    double value = toDouble(e.getValue());
                    failureLatency.put(formatPercentile(e.getKey()), nanosToMillis(value));
                });
            report.put("failureLatencyMs", failureLatency);
        }
        
        return report;
    }
    
    private String formatPercentile(double percentile) {
        double pct = percentile * 100;
        java.math.BigDecimal bd = new java.math.BigDecimal(pct)
            .setScale(3, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros();
        String num = bd.scale() <= 0 
            ? bd.toBigInteger().toString() 
            : bd.toPlainString();
        return "p" + num;
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

