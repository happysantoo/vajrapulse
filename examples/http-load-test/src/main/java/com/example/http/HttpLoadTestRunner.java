package com.example.http;

import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.StaticLoad;
import com.vajrapulse.api.StepLoad;
import com.vajrapulse.api.SineWaveLoad;
import com.vajrapulse.api.SpikeLoad;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import com.vajrapulse.worker.pipeline.MetricsPipeline;

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
    
    public static void main(String[] args) throws Exception {
        // Create task instance
        HttpLoadTest task = new HttpLoadTest();
        
        // Choose load pattern via simple arg convention (default: static)
        // args[0] may be: static | step | sine | spike
        String patternType = (args.length > 0) ? args[0].toLowerCase() : "static";
        LoadPattern loadPattern = switch (patternType) {
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
        }
        System.out.println("  Endpoint: https://httpbin.org/delay/0");
        System.out.println("Starting load test...");
        System.out.println();
        
        // Pipeline automatically manages lifecycle
        try (MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(new ConsoleMetricsExporter())
            .withPeriodic(Duration.ofSeconds(5))
            .withPercentiles(0.1,0.2,0.5,0.75,0.9,0.95,0.99)
            .build()) {
            
            pipeline.run(task, loadPattern);
        } // Automatic cleanup
        
        System.out.println("Load test completed!\n");
    }
}
