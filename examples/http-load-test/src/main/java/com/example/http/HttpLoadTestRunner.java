package com.example.http;

import com.vajrapulse.api.AdaptiveLoadPattern;
import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.MetricsProvider;
import com.vajrapulse.api.StaticLoad;
import com.vajrapulse.api.StepLoad;
import com.vajrapulse.api.SineWaveLoad;
import com.vajrapulse.api.SpikeLoad;
import com.vajrapulse.core.engine.MetricsProviderAdapter;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import com.vajrapulse.exporter.report.HtmlReportExporter;
import com.vajrapulse.exporter.report.JsonReportExporter;
import com.vajrapulse.exporter.report.CsvReportExporter;
import com.vajrapulse.worker.pipeline.MetricsPipeline;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Runner application for HTTP load test example.
 * 
 * <p>This demonstrates how to programmatically configure and run
 * a load test without using the CLI.
 * 
 * <p>Usage:
 * <pre>
 * ./gradlew :examples:http-load-test:run
 * </pre>
 */
public final class HttpLoadTestRunner {
    
    /**
     * Default constructor for HttpLoadTestRunner.
     * This is a utility class with static main method.
     */
    private HttpLoadTestRunner() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Main entry point for the HTTP load test example.
     * 
     * <p>Runs a load test with configurable load patterns. Supports:
     * <ul>
     *   <li>static - Constant TPS</li>
     *   <li>step - Discrete TPS steps</li>
     *   <li>sine - Sinusoidal TPS pattern</li>
     *   <li>spike - Periodic spike pattern</li>
     * </ul>
     * 
     * @param args command-line arguments (optional pattern type: static|step|sine|spike)
     * @throws Exception if test execution fails
     */
    public static void main(String[] args) throws Exception {
        // Create task instance
        HttpLoadTest task = new HttpLoadTest();
        
        // Choose load pattern via simple arg convention (default: static)
        // args[0] may be: static | step | sine | spike | adaptive
        String patternType = (args.length > 0) ? args[0].toLowerCase() : "static";
        
        // For adaptive pattern, we need metrics collector first (shared with pipeline)
        com.vajrapulse.core.metrics.MetricsCollector metricsCollector = null;
        LoadPattern loadPattern;
        
        if ("adaptive".equals(patternType)) {
            metricsCollector = com.vajrapulse.core.metrics.MetricsCollector.createWith(new double[]{0.50, 0.95, 0.99});
            MetricsProvider metricsProvider = new MetricsProviderAdapter(metricsCollector);
            loadPattern = new AdaptiveLoadPattern(
                100.0,  // Start at 100 TPS
                50.0,   // Increase 50 TPS per interval
                100.0,  // Decrease 100 TPS per interval when errors occur
                Duration.ofSeconds(5),  // Check/adjust every 5 seconds
                500.0,  // Max 500 TPS
                Duration.ofSeconds(10), // Sustain at stable point for 10 seconds
                0.01,   // 1% error rate threshold
                metricsProvider
            );
        } else {
            loadPattern = switch (patternType) {
                case "static" -> new StaticLoad(100.0, Duration.ofSeconds(30));
                case "step" -> new StepLoad(java.util.List.of(
                    new StepLoad.Step(50.0, Duration.ofSeconds(10)),
                    new StepLoad.Step(150.0, Duration.ofSeconds(10)),
                    new StepLoad.Step(300.0, Duration.ofSeconds(10))
                ));
                case "sine" -> new SineWaveLoad(150.0, 75.0, Duration.ofSeconds(30), Duration.ofSeconds(10));
                case "spike" -> new SpikeLoad(100.0, 600.0, Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(2));
                default -> throw new IllegalArgumentException("Unknown pattern type: " + patternType);
            };
        }
        
        // Display test configuration
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║        VajraPulse HTTP Load Test Example              ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Task:     " + task.getClass().getSimpleName());
        System.out.println("  Pattern:  " + patternType);
        System.out.println("  Duration: " + loadPattern.getDuration().toSeconds() + " seconds");
        if (loadPattern instanceof StaticLoad sl) {
            System.out.println("  TPS:      " + sl.tps());
        } else if (loadPattern instanceof StepLoad st) {
            System.out.println("  Steps:    " + st.steps());
        } else if (loadPattern instanceof SineWaveLoad sw) {
            System.out.println("  Mean:     " + sw.meanRate());
            System.out.println("  Amp:      " + sw.amplitude());
            System.out.println("  Period:   " + sw.period());
        } else if (loadPattern instanceof SpikeLoad sp) {
            System.out.println("  BaseRate: " + sp.baseRate());
            System.out.println("  SpikeRate:" + sp.spikeRate());
            System.out.println("  Interval: " + sp.spikeInterval());
            System.out.println("  SpikeDur: " + sp.spikeDuration());
        } else if (loadPattern instanceof AdaptiveLoadPattern ap) {
            System.out.println("  Initial TPS: " + ap.getCurrentTps());
            System.out.println("  Max TPS:     500.0");
            System.out.println("  Error Threshold: 1%");
        }
        System.out.println("  Endpoint: https://httpbin.org/delay/0");
        System.out.println("Starting load test...");
        System.out.println();
        
        // Create reports directory
        Path reportsDir = Path.of("reports");
        java.nio.file.Files.createDirectories(reportsDir);
        
        // Generate timestamped report filenames
        String timestamp = java.time.Instant.now().toString().replace(":", "-");
        Path htmlReport = reportsDir.resolve("load-test-" + timestamp + ".html");
        Path jsonReport = reportsDir.resolve("load-test-" + timestamp + ".json");
        Path csvReport = reportsDir.resolve("load-test-" + timestamp + ".csv");
        
        // Pipeline automatically manages lifecycle
        // Add multiple exporters: console for live updates, reports for analysis
        var pipelineBuilder = MetricsPipeline.builder()
            .addExporter(new ConsoleMetricsExporter());
        
        // For adaptive pattern, pass registry to report exporters for phase visualization
        if (loadPattern instanceof AdaptiveLoadPattern && metricsCollector != null) {
            var registry = metricsCollector.getRegistry();
            pipelineBuilder
                .addExporter(new HtmlReportExporter(htmlReport, registry))
                .addExporter(new JsonReportExporter(jsonReport, registry))
                .addExporter(new CsvReportExporter(csvReport, registry))
                .withCollector(metricsCollector);  // Use the same collector for adaptive pattern
        } else {
            pipelineBuilder
                .addExporter(new HtmlReportExporter(htmlReport))
                .addExporter(new JsonReportExporter(jsonReport))
                .addExporter(new CsvReportExporter(csvReport));
        }
        
        try (MetricsPipeline pipeline = pipelineBuilder
            .withPeriodic(Duration.ofSeconds(5))
            .withPercentiles(0.1,0.2,0.5,0.75,0.9,0.95,0.99)
            .build()) {
            
            pipeline.run(task, loadPattern);
        } // Automatic cleanup - reports are written on final export
        
        System.out.println("Load test completed!");
        System.out.println();
        System.out.println("Reports generated:");
        System.out.println("  HTML: " + htmlReport.toAbsolutePath());
        System.out.println("  JSON: " + jsonReport.toAbsolutePath());
        System.out.println("  CSV:  " + csvReport.toAbsolutePath());
        System.out.println();
    }
}
