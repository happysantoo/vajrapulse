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
import java.util.stream.Collectors;

/**
 * HTML report exporter with charts and visualizations.
 * 
 * <p>Generates beautiful HTML reports with interactive charts using Chart.js.
 * Reports include summary tables, percentile graphs, and run metadata.
 * 
 * <p>Example usage:
 * <pre>{@code
 * MetricsPipeline.builder()
 *     .addExporter(new HtmlReportExporter(Path.of("reports/test-run.html")))
 *     .build()
 *     .run(task, loadPattern);
 * }</pre>
 */
public final class HtmlReportExporter implements MetricsExporter {
    private static final Logger logger = LoggerFactory.getLogger(HtmlReportExporter.class);
    
    private final Path outputPath;
    
    /**
     * Creates a new HTML report exporter.
     * 
     * @param outputPath path to output HTML file
     */
    public HtmlReportExporter(Path outputPath) {
        this.outputPath = outputPath;
    }
    
    @Override
    public void export(String title, AggregatedMetrics metrics) {
        try {
            String html = generateHtml(title, metrics);
            
            // Create parent directories if needed
            var parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            
            Files.writeString(outputPath, html);
            logger.info("HTML report written to: {}", outputPath);
        } catch (IOException e) {
            logger.error("Failed to write HTML report to {}", outputPath, e);
            throw new RuntimeException("Failed to write HTML report", e);
        }
    }
    
    private String generateHtml(String title, AggregatedMetrics metrics) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>").append(escapeHtml(title)).append(" - VajraPulse Report</title>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n");
        html.append("  <style>\n");
        html.append(getCssStyles());
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        
        // Header
        html.append("  <div class=\"container\">\n");
        html.append("    <h1>").append(escapeHtml(title)).append("</h1>\n");
        html.append("    <div class=\"metadata\">\n");
        html.append("      <p><strong>Generated:</strong> ").append(Instant.now().toString()).append("</p>\n");
        html.append("      <p><strong>Elapsed Time:</strong> ").append(formatDuration(metrics.elapsedMillis())).append("</p>\n");
        html.append("    </div>\n");
        
        // Summary Cards
        html.append("    <div class=\"summary-grid\">\n");
        html.append("      <div class=\"card\">\n");
        html.append("        <h3>Total Executions</h3>\n");
        html.append("        <p class=\"value\">").append(metrics.totalExecutions()).append("</p>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"card\">\n");
        html.append("        <h3>Success Rate</h3>\n");
        html.append("        <p class=\"value\">").append(String.format("%.2f%%", metrics.successRate())).append("</p>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"card\">\n");
        html.append("        <h3>Response TPS</h3>\n");
        html.append("        <p class=\"value\">").append(String.format("%.2f", metrics.responseTps())).append("</p>\n");
        html.append("      </div>\n");
        html.append("      <div class=\"card\">\n");
        html.append("        <h3>Failure Rate</h3>\n");
        html.append("        <p class=\"value\">").append(String.format("%.2f%%", metrics.failureRate())).append("</p>\n");
        html.append("      </div>\n");
        html.append("    </div>\n");
        
        // Detailed Metrics Table
        html.append("    <div class=\"section\">\n");
        html.append("      <h2>Detailed Metrics</h2>\n");
        html.append("      <table>\n");
        html.append("        <tr><th>Metric</th><th>Value</th></tr>\n");
        html.append("        <tr><td>Total Executions</td><td>").append(metrics.totalExecutions()).append("</td></tr>\n");
        html.append("        <tr><td>Successful</td><td>").append(metrics.successCount()).append(" (").append(String.format("%.2f%%", metrics.successRate())).append(")</td></tr>\n");
        html.append("        <tr><td>Failed</td><td>").append(metrics.failureCount()).append(" (").append(String.format("%.2f%%", metrics.failureRate())).append(")</td></tr>\n");
        html.append("        <tr><td>Response TPS</td><td>").append(String.format("%.2f", metrics.responseTps())).append("</td></tr>\n");
        html.append("        <tr><td>Success TPS</td><td>").append(String.format("%.2f", metrics.successTps())).append("</td></tr>\n");
        html.append("        <tr><td>Failure TPS</td><td>").append(String.format("%.2f", metrics.failureTps())).append("</td></tr>\n");
        html.append("        <tr><td>Queue Size</td><td>").append(metrics.queueSize()).append("</td></tr>\n");
        html.append("      </table>\n");
        html.append("    </div>\n");
        
        // Success Latency Chart
        if (!metrics.successPercentiles().isEmpty()) {
            html.append("    <div class=\"section\">\n");
            html.append("      <h2>Success Latency (ms)</h2>\n");
            html.append("      <canvas id=\"successChart\"></canvas>\n");
            html.append("    </div>\n");
        }
        
        // Failure Latency Chart
        if (!metrics.failurePercentiles().isEmpty()) {
            html.append("    <div class=\"section\">\n");
            html.append("      <h2>Failure Latency (ms)</h2>\n");
            html.append("      <canvas id=\"failureChart\"></canvas>\n");
            html.append("    </div>\n");
        }
        
        // Queue Wait Time Chart
        if (!metrics.queueWaitPercentiles().isEmpty()) {
            html.append("    <div class=\"section\">\n");
            html.append("      <h2>Queue Wait Time (ms)</h2>\n");
            html.append("      <canvas id=\"queueChart\"></canvas>\n");
            html.append("    </div>\n");
        }
        
        html.append("  </div>\n");
        
        // JavaScript for charts
        html.append("  <script>\n");
        html.append(generateChartScript(metrics));
        html.append("  </script>\n");
        
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    private String generateChartScript(AggregatedMetrics metrics) {
        StringBuilder script = new StringBuilder();
        
        // Success latency chart
        if (!metrics.successPercentiles().isEmpty()) {
            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            metrics.successPercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    labels.add(formatPercentileLabel(e.getKey()));
                    double value = toDouble(e.getValue());
                    values.add(nanosToMillis(value));
                });
            
            script.append("new Chart(document.getElementById('successChart'), {\n");
            script.append("  type: 'bar',\n");
            script.append("  data: {\n");
            script.append("    labels: ").append(toJsonArray(labels)).append(",\n");
            script.append("    datasets: [{\n");
            script.append("      label: 'Latency (ms)',\n");
            script.append("      data: ").append(toJsonArray(values)).append(",\n");
            script.append("      backgroundColor: 'rgba(75, 192, 192, 0.6)',\n");
            script.append("      borderColor: 'rgba(75, 192, 192, 1)',\n");
            script.append("      borderWidth: 1\n");
            script.append("    }]\n");
            script.append("  },\n");
            script.append("  options: {\n");
            script.append("    responsive: true,\n");
            script.append("    scales: { y: { beginAtZero: true } }\n");
            script.append("  }\n");
            script.append("});\n");
        }
        
        // Failure latency chart
        if (!metrics.failurePercentiles().isEmpty()) {
            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            metrics.failurePercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    labels.add(formatPercentileLabel(e.getKey()));
                    double value = toDouble(e.getValue());
                    values.add(nanosToMillis(value));
                });
            
            script.append("new Chart(document.getElementById('failureChart'), {\n");
            script.append("  type: 'bar',\n");
            script.append("  data: {\n");
            script.append("    labels: ").append(toJsonArray(labels)).append(",\n");
            script.append("    datasets: [{\n");
            script.append("      label: 'Latency (ms)',\n");
            script.append("      data: ").append(toJsonArray(values)).append(",\n");
            script.append("      backgroundColor: 'rgba(255, 99, 132, 0.6)',\n");
            script.append("      borderColor: 'rgba(255, 99, 132, 1)',\n");
            script.append("      borderWidth: 1\n");
            script.append("    }]\n");
            script.append("  },\n");
            script.append("  options: {\n");
            script.append("    responsive: true,\n");
            script.append("    scales: { y: { beginAtZero: true } }\n");
            script.append("  }\n");
            script.append("});\n");
        }
        
        // Queue wait time chart
        if (!metrics.queueWaitPercentiles().isEmpty()) {
            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            metrics.queueWaitPercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(e -> {
                    labels.add(formatPercentileLabel(e.getKey()));
                    double value = toDouble(e.getValue());
                    values.add(nanosToMillis(value));
                });
            
            script.append("new Chart(document.getElementById('queueChart'), {\n");
            script.append("  type: 'bar',\n");
            script.append("  data: {\n");
            script.append("    labels: ").append(toJsonArray(labels)).append(",\n");
            script.append("    datasets: [{\n");
            script.append("      label: 'Wait Time (ms)',\n");
            script.append("      data: ").append(toJsonArray(values)).append(",\n");
            script.append("      backgroundColor: 'rgba(153, 102, 255, 0.6)',\n");
            script.append("      borderColor: 'rgba(153, 102, 255, 1)',\n");
            script.append("      borderWidth: 1\n");
            script.append("    }]\n");
            script.append("  },\n");
            script.append("  options: {\n");
            script.append("    responsive: true,\n");
            script.append("    scales: { y: { beginAtZero: true } }\n");
            script.append("  }\n");
            script.append("});\n");
        }
        
        return script.toString();
    }
    
    private String getCssStyles() {
        return """
            body {
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
              margin: 0;
              padding: 20px;
              background-color: #f5f5f5;
            }
            .container {
              max-width: 1200px;
              margin: 0 auto;
              background: white;
              padding: 30px;
              border-radius: 8px;
              box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }
            h1 {
              color: #333;
              margin-top: 0;
            }
            .metadata {
              color: #666;
              margin-bottom: 30px;
            }
            .summary-grid {
              display: grid;
              grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
              gap: 20px;
              margin-bottom: 30px;
            }
            .card {
              background: #f8f9fa;
              padding: 20px;
              border-radius: 6px;
              border-left: 4px solid #007bff;
            }
            .card h3 {
              margin: 0 0 10px 0;
              font-size: 14px;
              color: #666;
              text-transform: uppercase;
            }
            .card .value {
              margin: 0;
              font-size: 32px;
              font-weight: bold;
              color: #333;
            }
            .section {
              margin-top: 40px;
            }
            .section h2 {
              color: #333;
              border-bottom: 2px solid #007bff;
              padding-bottom: 10px;
            }
            table {
              width: 100%;
              border-collapse: collapse;
              margin-top: 20px;
            }
            th, td {
              padding: 12px;
              text-align: left;
              border-bottom: 1px solid #ddd;
            }
            th {
              background-color: #f8f9fa;
              font-weight: 600;
            }
            canvas {
              max-height: 400px;
              margin-top: 20px;
            }
            """;
    }
    
    private String toJsonArray(List<?> items) {
        if (items.isEmpty()) {
            return "[]";
        }
        return "[" + items.stream()
            .map(item -> item instanceof String ? "\"" + escapeJson((String) item) + "\"" : item.toString())
            .collect(Collectors.joining(", ")) + "]";
    }
    
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
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
    
    private String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        } else if (millis < 60000) {
            return String.format("%.1fs", millis / 1000.0);
        } else {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            return minutes + "m " + seconds + "s";
        }
    }
    
    private double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof Double d) {
            return d;
        }
        throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to double");
    }
}

