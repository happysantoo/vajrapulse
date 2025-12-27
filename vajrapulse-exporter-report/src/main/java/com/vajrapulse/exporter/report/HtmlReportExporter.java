package com.vajrapulse.exporter.report;

import com.vajrapulse.api.metrics.RunContext;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.LatencyStats;
import com.vajrapulse.core.metrics.MetricsExporter;
import io.micrometer.core.instrument.MeterRegistry;
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
 * Reports include summary tables, percentile graphs, run metadata, and statistical
 * summaries.
 * 
 * <p>Example usage:
 * <pre>{@code
 * LoadTestRunner.builder()
 *     .addExporter(new HtmlReportExporter(Path.of("reports/test-run.html")))
 *     .build()
 *     .run(task, loadPattern);
 * }</pre>
 * 
 * @since 0.9.0
 */
public final class HtmlReportExporter implements MetricsExporter {
    private static final Logger logger = LoggerFactory.getLogger(HtmlReportExporter.class);
    
    private final Path outputPath;
    private final MeterRegistry registry; // Optional, for adaptive pattern metrics
    
    /**
     * Creates a new HTML report exporter.
     * 
     * @param outputPath path to output HTML file
     */
    public HtmlReportExporter(Path outputPath) {
        this(outputPath, null);
    }
    
    /**
     * Creates a new HTML report exporter with registry access for adaptive pattern metrics.
     * 
     * @param outputPath path to output HTML file
     * @param registry meter registry to query for adaptive pattern metrics (optional)
     */
    public HtmlReportExporter(Path outputPath, MeterRegistry registry) {
        this.outputPath = outputPath;
        this.registry = registry;
    }
    
    @Override
    public void export(String title, AggregatedMetrics metrics) {
        export(title, metrics, RunContext.empty());
    }
    
    @Override
    public void export(String title, AggregatedMetrics metrics, RunContext context) {
        try {
            String html = generateHtml(title, metrics, context);
            
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
    
    private String generateHtml(String title, AggregatedMetrics metrics, RunContext context) {
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
        
        // Run Metadata Section
        html.append(generateRunMetadataSection(metrics, context));
        
        // Adaptive Pattern Section (if available)
        if (registry != null) {
            html.append(generateAdaptivePatternSection());
        }
        
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
        
        // Statistical Summary Section (if available)
        if (metrics.hasStatistics()) {
            html.append(generateStatisticalSummarySection(metrics));
        }
        
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
    
    private String generateRunMetadataSection(AggregatedMetrics metrics, RunContext context) {
        StringBuilder html = new StringBuilder();
        
        html.append("    <div class=\"metadata-section\">\n");
        html.append("      <div class=\"metadata-grid\">\n");
        
        // Run ID
        if (context != null && !"unknown".equals(context.runId())) {
            html.append("        <div class=\"metadata-item\">\n");
            html.append("          <span class=\"metadata-label\">Run ID:</span>\n");
            html.append("          <span class=\"metadata-value run-id\">").append(escapeHtml(context.runId())).append("</span>\n");
            html.append("        </div>\n");
        }
        
        // Task Class
        if (context != null && !"unknown".equals(context.taskClass())) {
            html.append("        <div class=\"metadata-item\">\n");
            html.append("          <span class=\"metadata-label\">Task:</span>\n");
            html.append("          <span class=\"metadata-value\">").append(escapeHtml(context.taskClass())).append("</span>\n");
            html.append("        </div>\n");
        }
        
        // Load Pattern
        if (context != null && !"unknown".equals(context.loadPatternType())) {
            html.append("        <div class=\"metadata-item\">\n");
            html.append("          <span class=\"metadata-label\">Pattern:</span>\n");
            html.append("          <span class=\"metadata-value\">").append(escapeHtml(context.loadPatternType())).append("</span>\n");
            html.append("        </div>\n");
        }
        
        // Start Time
        if (context != null && context.startTime() != null && !context.startTime().equals(Instant.EPOCH)) {
            html.append("        <div class=\"metadata-item\">\n");
            html.append("          <span class=\"metadata-label\">Start Time:</span>\n");
            html.append("          <span class=\"metadata-value\">").append(context.startTime().toString()).append("</span>\n");
            html.append("        </div>\n");
        }
        
        // End Time / Generated
        html.append("        <div class=\"metadata-item\">\n");
        html.append("          <span class=\"metadata-label\">Generated:</span>\n");
        html.append("          <span class=\"metadata-value\">").append(Instant.now().toString()).append("</span>\n");
        html.append("        </div>\n");
        
        // Duration
        html.append("        <div class=\"metadata-item\">\n");
        html.append("          <span class=\"metadata-label\">Duration:</span>\n");
        html.append("          <span class=\"metadata-value\">").append(formatDuration(metrics.elapsedMillis())).append("</span>\n");
        html.append("        </div>\n");
        
        // System Info
        if (context != null && context.systemInfo() != null && !"unknown".equals(context.systemInfo().javaVersion())) {
            html.append("        <div class=\"metadata-item\">\n");
            html.append("          <span class=\"metadata-label\">Java:</span>\n");
            html.append("          <span class=\"metadata-value\">").append(escapeHtml(context.systemInfo().javaVersion())).append("</span>\n");
            html.append("        </div>\n");
            
            html.append("        <div class=\"metadata-item\">\n");
            html.append("          <span class=\"metadata-label\">OS:</span>\n");
            html.append("          <span class=\"metadata-value\">").append(escapeHtml(context.systemInfo().osName())).append(" ").append(escapeHtml(context.systemInfo().osArch())).append("</span>\n");
            html.append("        </div>\n");
            
            html.append("        <div class=\"metadata-item\">\n");
            html.append("          <span class=\"metadata-label\">Host:</span>\n");
            html.append("          <span class=\"metadata-value\">").append(escapeHtml(context.systemInfo().hostname())).append("</span>\n");
            html.append("        </div>\n");
            
            html.append("        <div class=\"metadata-item\">\n");
            html.append("          <span class=\"metadata-label\">CPUs:</span>\n");
            html.append("          <span class=\"metadata-value\">").append(context.systemInfo().availableProcessors()).append("</span>\n");
            html.append("        </div>\n");
        }
        
        html.append("      </div>\n");
        html.append("    </div>\n");
        
        return html.toString();
    }
    
    private String generateStatisticalSummarySection(AggregatedMetrics metrics) {
        StringBuilder html = new StringBuilder();
        
        html.append("    <div class=\"section stats-section\">\n");
        html.append("      <h2>Statistical Summary</h2>\n");
        html.append("      <div class=\"stats-grid\">\n");
        
        // Success Statistics
        LatencyStats successStats = metrics.successStats();
        if (successStats != null && successStats.hasData()) {
            html.append("        <div class=\"stats-card success-stats\">\n");
            html.append("          <h3>Success Latency</h3>\n");
            html.append("          <table class=\"stats-table\">\n");
            html.append("            <tr><td>Mean</td><td>").append(String.format("%.2f ms", successStats.meanMillis())).append("</td></tr>\n");
            html.append("            <tr><td>Std Dev</td><td>").append(String.format("%.2f ms", successStats.stdDevMillis())).append("</td></tr>\n");
            html.append("            <tr><td>Min</td><td>").append(String.format("%.2f ms", successStats.minMillis())).append("</td></tr>\n");
            html.append("            <tr><td>Max</td><td>").append(String.format("%.2f ms", successStats.maxMillis())).append("</td></tr>\n");
            html.append("            <tr><td>CV</td><td>").append(String.format("%.1f%%", successStats.coefficientOfVariation())).append("</td></tr>\n");
            html.append("            <tr><td>Count</td><td>").append(successStats.count()).append("</td></tr>\n");
            html.append("          </table>\n");
            html.append("        </div>\n");
        }
        
        // Failure Statistics
        LatencyStats failureStats = metrics.failureStats();
        if (failureStats != null && failureStats.hasData()) {
            html.append("        <div class=\"stats-card failure-stats\">\n");
            html.append("          <h3>Failure Latency</h3>\n");
            html.append("          <table class=\"stats-table\">\n");
            html.append("            <tr><td>Mean</td><td>").append(String.format("%.2f ms", failureStats.meanMillis())).append("</td></tr>\n");
            html.append("            <tr><td>Std Dev</td><td>").append(String.format("%.2f ms", failureStats.stdDevMillis())).append("</td></tr>\n");
            html.append("            <tr><td>Min</td><td>").append(String.format("%.2f ms", failureStats.minMillis())).append("</td></tr>\n");
            html.append("            <tr><td>Max</td><td>").append(String.format("%.2f ms", failureStats.maxMillis())).append("</td></tr>\n");
            html.append("            <tr><td>CV</td><td>").append(String.format("%.1f%%", failureStats.coefficientOfVariation())).append("</td></tr>\n");
            html.append("            <tr><td>Count</td><td>").append(failureStats.count()).append("</td></tr>\n");
            html.append("          </table>\n");
            html.append("        </div>\n");
        }
        
        html.append("      </div>\n");
        html.append("    </div>\n");
        
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
        
        // Adaptive pattern chart (if available)
        if (registry != null) {
            script.append(generateAdaptivePatternChartScript());
        }
        
        return script.toString();
    }
    
    private String generateAdaptivePatternSection() {
        var phaseGauge = registry.find("vajrapulse.adaptive.phase").gauge();
        var currentTpsGauge = registry.find("vajrapulse.adaptive.current_tps").gauge();
        var stableTpsGauge = registry.find("vajrapulse.adaptive.stable_tps").gauge();
        var transitionsGauge = registry.find("vajrapulse.adaptive.phase_transitions").gauge();
        
        if (phaseGauge == null || currentTpsGauge == null) {
            return ""; // Adaptive pattern metrics not available
        }
        
        StringBuilder html = new StringBuilder();
        html.append("    <div class=\"section adaptive-section\">\n");
        html.append("      <h2>Adaptive Load Pattern</h2>\n");
        html.append("      <div class=\"summary-grid\">\n");
        
        // Phase card
        int phaseOrdinal = (int) phaseGauge.value();
        String phaseName = getPhaseName(phaseOrdinal);
        html.append("        <div class=\"card adaptive-card\">\n");
        html.append("          <h3>Current Phase</h3>\n");
        html.append("          <p class=\"value phase-").append(phaseOrdinal).append("\">").append(phaseName).append("</p>\n");
        html.append("        </div>\n");
        
        // Current TPS card
        html.append("        <div class=\"card adaptive-card\">\n");
        html.append("          <h3>Current TPS</h3>\n");
        html.append("          <p class=\"value\">").append(String.format("%.2f", currentTpsGauge.value())).append("</p>\n");
        html.append("        </div>\n");
        
        // Stable TPS card (if found)
        if (stableTpsGauge != null && !Double.isNaN(stableTpsGauge.value())) {
            html.append("        <div class=\"card adaptive-card\">\n");
            html.append("          <h3>Stable TPS</h3>\n");
            html.append("          <p class=\"value\">").append(String.format("%.2f", stableTpsGauge.value())).append("</p>\n");
            html.append("        </div>\n");
        }
        
        // Phase transitions card
        if (transitionsGauge != null) {
            html.append("        <div class=\"card adaptive-card\">\n");
            html.append("          <h3>Phase Transitions</h3>\n");
            html.append("          <p class=\"value\">").append((long) transitionsGauge.value()).append("</p>\n");
            html.append("        </div>\n");
        }
        
        html.append("      </div>\n");
        html.append("    </div>\n");
        
        return html.toString();
    }
    
    private String generateAdaptivePatternChartScript() {
        var phaseGauge = registry.find("vajrapulse.adaptive.phase").gauge();
        var currentTpsGauge = registry.find("vajrapulse.adaptive.current_tps").gauge();
        
        if (phaseGauge == null || currentTpsGauge == null) {
            return ""; // Adaptive pattern metrics not available
        }
        
        // For now, just return empty - phase timeline would require time-series data
        // which we don't have in the final snapshot. This could be enhanced in the future
        // to collect phase transitions over time.
        return "";
    }
    
    private String getPhaseName(int phaseOrdinal) {
        return switch (phaseOrdinal) {
            case 0 -> "RAMP_UP";
            case 1 -> "RAMP_DOWN";
            case 2 -> "SUSTAIN";
            case 3 -> "COMPLETE";
            default -> "UNKNOWN";
        };
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
            .metadata-section {
              background: #f8f9fa;
              padding: 15px 20px;
              border-radius: 6px;
              margin-bottom: 30px;
              border-left: 4px solid #17a2b8;
            }
            .metadata-grid {
              display: grid;
              grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
              gap: 10px 20px;
            }
            .metadata-item {
              display: flex;
              gap: 8px;
            }
            .metadata-label {
              color: #666;
              font-weight: 500;
            }
            .metadata-value {
              color: #333;
            }
            .metadata-value.run-id {
              font-family: monospace;
              background: #e9ecef;
              padding: 2px 6px;
              border-radius: 3px;
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
            .stats-section {
              background: #f8f9fa;
              padding: 20px;
              border-radius: 6px;
            }
            .stats-grid {
              display: grid;
              grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
              gap: 20px;
            }
            .stats-card {
              background: white;
              padding: 15px;
              border-radius: 6px;
              box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            }
            .stats-card h3 {
              margin: 0 0 15px 0;
              font-size: 16px;
              color: #333;
            }
            .success-stats {
              border-left: 4px solid #28a745;
            }
            .failure-stats {
              border-left: 4px solid #dc3545;
            }
            .stats-table {
              width: 100%;
              border-collapse: collapse;
            }
            .stats-table td {
              padding: 6px 0;
              border-bottom: 1px solid #eee;
            }
            .stats-table td:first-child {
              color: #666;
              width: 100px;
            }
            .stats-table td:last-child {
              text-align: right;
              font-weight: 500;
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
            .adaptive-section {
              background: #f0f8ff;
              padding: 20px;
              border-radius: 6px;
              border: 2px solid #007bff;
            }
            .adaptive-card {
              border-left-color: #28a745;
            }
            .phase-0 { color: #007bff; }
            .phase-1 { color: #ffc107; }
            .phase-2 { color: #28a745; }
            .phase-3 { color: #dc3545; }
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
        throw new IllegalArgumentException("Cannot convert " + value.getClass() + " to double");
    }
}
