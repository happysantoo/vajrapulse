package com.example.http;

import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.pattern.StaticLoad;
import com.vajrapulse.exporter.otel.OpenTelemetryExporter;
import com.vajrapulse.exporter.otel.OpenTelemetryExporter.Protocol;
import com.vajrapulse.api.task.TaskIdentity;
import com.vajrapulse.worker.pipeline.LoadTestRunner;

import java.time.Duration;
import java.util.Map;

/**
 * Runner demonstrating the HTTP load test using the OpenTelemetry exporter.
 *
 * <p>This variant sends metrics to an OTLP collector endpoint, enabling
 * integration with backends like Grafana, Prometheus (via OTel Collector),
 * or any OTLP-compatible platform.
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :examples:http-load-test:runOtel 
 * </pre>
 *
 * <p>Collector Assumptions:
 * <ul>
 *   <li>OTLP gRPC endpoint at http://localhost:4317</li>
 *   <li>Metrics receiver enabled (default if using official Docker image)</li>
 * </ul>
 */
public final class HttpLoadTestOtelRunner {

    /**
     * Default constructor for HttpLoadTestOtelRunner.
     * This is a utility class with static main method.
     */
    private HttpLoadTestOtelRunner() {
        // Utility class - prevent instantiation
    }

    /**
     * Main entry point for the HTTP load test with OpenTelemetry export.
     * 
     * <p>Runs a load test and exports metrics to an OTLP collector endpoint.
     * Requires an OpenTelemetry collector running at http://localhost:4317.
     * 
     * @param args command-line arguments (currently unused)
     * @throws Exception if test execution or export fails
     */
    public static void main(String[] args) throws Exception {
        HttpLoadTest task = new HttpLoadTest();

        // 100 TPS for 30 seconds (same as console example)
        LoadPattern loadPattern = new StaticLoad(100.0, Duration.ofSeconds(30));

        // Configure OpenTelemetry exporter
        // Define task identity for tagging in observability backends
        TaskIdentity identity = new TaskIdentity(
            "http-load-test",
            Map.of(
                "scenario", "baseline",
                "component", "http-client"
            )
        );

        OpenTelemetryExporter otelExporter = OpenTelemetryExporter.builder()
            .endpoint("http://localhost:4317") // gRPC endpoint
            .protocol(Protocol.GRPC) // Use gRPC protocol
            .exportInterval(5) // Align with periodic pipeline snapshots
            .taskIdentity(identity)
            // runId is optional - if not set, a UUID is auto-generated
            // .runId("http-test-" + System.currentTimeMillis())
            .resourceAttributes(Map.of(
                "service.name", "vajrapulse-http-example",
                "service.version", "1.0.0",
                "environment", "dev",
                "example.type", "http-load-test",
                "team", "platform"
            ))
            .build();

        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║   VajraPulse HTTP Load Test (OpenTelemetry Export)    ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Service Name: vajrapulse-http-example");
        System.out.println("  Protocol:     gRPC (OTLP)");
        System.out.println("  Endpoint:     http://localhost:4317");
        System.out.println("  Run ID:       " + otelExporter.getRunId());
        System.out.println("  Task Name:    " + identity.name());
        System.out.println("  Tags:         " + identity.tags());
        System.out.println("  TPS:          100");
        System.out.println("  Duration:     30 seconds");
        System.out.println("Starting load test with OpenTelemetry export...\n");

        // Pipeline automatically closes exporter after final metrics are exported
        try (LoadTestRunner pipeline = LoadTestRunner.builder()
            .addExporter(otelExporter) // OTLP export
            .withRunId(otelExporter.getRunId()) // Use the same run ID from exporter
            .withPeriodic(Duration.ofSeconds(5)) // Still allows periodic aggregation, exporter flush handles send
            .withPercentiles(0.5,0.9,0.95,0.99)
            .build()) {
            
            pipeline.run(task, loadPattern);
        } // Automatic final export + exporter cleanup

        System.out.println("Load test completed (metrics exported to OpenTelemetry collector).\n");
    }
}
