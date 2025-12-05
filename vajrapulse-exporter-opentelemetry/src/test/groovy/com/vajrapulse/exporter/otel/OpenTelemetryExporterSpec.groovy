package com.vajrapulse.exporter.otel

import com.vajrapulse.core.metrics.AggregatedMetrics
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Tests for OpenTelemetryExporter using Spock.
 * 
 * Note: These tests verify the exporter's functionality without requiring
 * a real OTLP collector. We test the builder, configuration, and ensure
 * the exporter doesn't throw exceptions during normal operation.
 */
class OpenTelemetryExporterSpec extends Specification {
    
    @TempDir
    Path tempDir
    
    def "should build exporter with default configuration"() {
        when: "building with defaults"
        def exporter = OpenTelemetryExporter.builder()
            .build()
        
        then: "exporter is created successfully"
        exporter != null
        
        cleanup:
        exporter?.close()
    }
    
    def "should build exporter with custom endpoint"() {
        given: "a custom OTLP endpoint"
        def endpoint = "http://custom-collector:4318"
        
        when: "building with custom endpoint"
        def exporter = OpenTelemetryExporter.builder()
            .endpoint(endpoint)
            .build()
        
        then: "exporter is created successfully"
        exporter != null
        
        cleanup:
        exporter?.close()
    }
    

    
    def "should build exporter with custom export interval"() {
        given: "a custom export interval"
        def intervalSeconds = 5
        
        when: "building with custom interval"
        def exporter = OpenTelemetryExporter.builder()
            .exportInterval(intervalSeconds)
            .build()
        
        then: "exporter is created successfully"
        exporter != null
        
        cleanup:
        exporter?.close()
    }
    
    def "should build exporter with custom headers"() {
        given: "custom authentication headers"
        def headers = [
            "Authorization": "Bearer my-token",
            "X-Custom-Header": "custom-value"
        ]
        
        when: "building with custom headers"
        def exporter = OpenTelemetryExporter.builder()
            .headers(headers)
            .build()
        
        then: "exporter is created successfully"
        exporter != null
        
        cleanup:
        exporter?.close()
    }
    
    def "should build exporter with custom resource attributes"() {
        given: "custom resource attributes"
        def attributes = [
            "environment": "production",
            "region": "us-east-1",
            "test.name": "checkout-flow",
            "team": "platform"
        ]
        
        when: "building with custom resource attributes"
        def exporter = OpenTelemetryExporter.builder()
            .resourceAttributes(attributes)
            .build()
        
        then: "exporter is created successfully"
        exporter != null
        
        cleanup:
        exporter?.close()
    }
    
    def "should export with custom resource attributes included"() {
        given: "an exporter with custom resource attributes"
        def attributes = [
            "environment": "staging",
            "datacenter": "aws-eu-west-1"
        ]
        def exporter = OpenTelemetryExporter.builder()
            .resourceAttributes(attributes)
            .build()
        
        and: "sample metrics"
        def metrics = createSampleMetrics(totalExecutions: 50L, successCount: 50L, failureCount: 0L)
        
        when: "exporting with resource attributes"
        exporter.export("Test with Attributes", metrics)
        
        then: "no exceptions are thrown"
        noExceptionThrown()
        
        cleanup:
        exporter?.close()
    }
    
    def "should build exporter with gRPC protocol (default)"() {
        when: "building with default gRPC protocol"
        def exporter = OpenTelemetryExporter.builder()
            .protocol(OpenTelemetryExporter.Protocol.GRPC)
            .build()
        
        then: "exporter is created successfully"
        exporter != null
        
        cleanup:
        exporter?.close()
    }
    
    def "should build exporter with HTTP protocol"() {
        when: "building with HTTP protocol"
        def exporter = OpenTelemetryExporter.builder()
            .protocol(OpenTelemetryExporter.Protocol.HTTP)
            .endpoint("http://localhost:4318")
            .build()
        
        then: "exporter is created successfully"
        exporter != null
        
        cleanup:
        exporter?.close()
    }
    
    def "should export metrics with HTTP protocol"() {
        given: "an exporter configured with HTTP protocol"
        def exporter = OpenTelemetryExporter.builder()
            .protocol(OpenTelemetryExporter.Protocol.HTTP)
            .endpoint("http://localhost:4318")
            .build()
        
        and: "sample metrics"
        def metrics = createSampleMetrics(totalExecutions: 30L, successCount: 28L, failureCount: 2L)
        
        when: "exporting via HTTP"
        exporter.export("HTTP Test", metrics)
        
        then: "no exceptions are thrown"
        noExceptionThrown()
        
        cleanup:
        exporter?.close()
    }
    
    def "should handle protocol switching between gRPC and HTTP"() {
        given: "exporters with different protocols"
        def grpcExporter = OpenTelemetryExporter.builder()
            .protocol(OpenTelemetryExporter.Protocol.GRPC)
            .build()
        def httpExporter = OpenTelemetryExporter.builder()
            .protocol(OpenTelemetryExporter.Protocol.HTTP)
            .endpoint("http://localhost:4318")
            .build()
        
        and: "metrics"
        def metrics = createSampleMetrics(totalExecutions: 20L, successCount: 20L, failureCount: 0L)
        
        when: "exporting with both protocols"
        grpcExporter.export("gRPC test", metrics)
        httpExporter.export("HTTP test", metrics)
        
        then: "both exporters work without errors"
        noExceptionThrown()
        
        cleanup:
        grpcExporter?.close()
        httpExporter?.close()
    }
    
    def "should fail to build with blank endpoint"() {
        when: "building with blank endpoint"
        OpenTelemetryExporter.builder()
            .endpoint("")
            .build()
        
        then: "throws IllegalStateException"
        thrown(IllegalStateException)
    }
    
    def "should fail to build with null endpoint"() {
        when: "building with null endpoint"
        OpenTelemetryExporter.builder()
            .endpoint(null)
            .build()
        
        then: "throws IllegalStateException"
        thrown(IllegalStateException)
    }
    

    

    
    def "should export metrics without throwing exceptions"() {
        given: "an exporter instance"
        def exporter = OpenTelemetryExporter.builder()
            .endpoint("http://localhost:4318")
            .resourceAttributes(["service.name": "test-service"])
            .build()
        
        and: "sample aggregated metrics"
        def metrics = createSampleMetrics(
            totalExecutions: 100L,
            successCount: 95L,
            failureCount: 5L
        )
        
        when: "exporting metrics"
        exporter.export("Test Load", metrics)
        
        then: "no exceptions are thrown"
        noExceptionThrown()
        
        cleanup:
        exporter?.close()
    }
    
    def "should handle metrics with zero executions gracefully"() {
        given: "an exporter instance"
        def exporter = OpenTelemetryExporter.builder()
            .build()
        
        and: "metrics with zero executions"
        def metrics = createSampleMetrics(
            totalExecutions: 0L,
            successCount: 0L,
            failureCount: 0L
        )
        
        when: "exporting zero metrics"
        exporter.export("Empty Test", metrics)
        
        then: "no exceptions are thrown"
        noExceptionThrown()
        
        cleanup:
        exporter?.close()
    }
    
    def "should handle all-success metrics"() {
        given: "an exporter instance"
        def exporter = OpenTelemetryExporter.builder()
            .build()
        
        and: "metrics with 100% success rate"
        def metrics = createSampleMetrics(
            totalExecutions: 1000L,
            successCount: 1000L,
            failureCount: 0L
        )
        
        when: "exporting all-success metrics"
        exporter.export("Perfect Test", metrics)
        
        then: "no exceptions are thrown"
        noExceptionThrown()
        
        cleanup:
        exporter?.close()
    }
    
    def "should handle all-failure metrics"() {
        given: "an exporter instance"
        def exporter = OpenTelemetryExporter.builder()
            .build()
        
        and: "metrics with 0% success rate"
        def metrics = createSampleMetrics(
            totalExecutions: 50L,
            successCount: 0L,
            failureCount: 50L
        )
        
        when: "exporting all-failure metrics"
        exporter.export("Failed Test", metrics)
        
        then: "no exceptions are thrown"
        noExceptionThrown()
        
        cleanup:
        exporter?.close()
    }
    
    def "should export multiple times without issues"() {
        given: "an exporter instance"
        def exporter = OpenTelemetryExporter.builder()
            .build()
        
        and: "a series of metrics snapshots"
        def metrics1 = createSampleMetrics(totalExecutions: 100L, successCount: 95L, failureCount: 5L)
        def metrics2 = createSampleMetrics(totalExecutions: 200L, successCount: 190L, failureCount: 10L)
        def metrics3 = createSampleMetrics(totalExecutions: 300L, successCount: 285L, failureCount: 15L)
        
        when: "exporting multiple times"
        exporter.export("Iteration 1", metrics1)
        exporter.export("Iteration 2", metrics2)
        exporter.export("Iteration 3", metrics3)
        
        then: "no exceptions are thrown"
        noExceptionThrown()
        
        cleanup:
        exporter?.close()
    }
    
    def "should close cleanly"() {
        given: "an exporter instance"
        def exporter = OpenTelemetryExporter.builder()
            .build()
        
        when: "closing the exporter"
        exporter.close()
        
        then: "no exceptions are thrown"
        noExceptionThrown()
    }
    
    def "should be usable as AutoCloseable with try-with-resources"() {
        when: "using try-with-resources"
        try (def exporter = OpenTelemetryExporter.builder().build()) {
            def metrics = createSampleMetrics(totalExecutions: 10L, successCount: 10L, failureCount: 0L)
            exporter.export("Test", metrics)
        }
        
        then: "no exceptions are thrown"
        noExceptionThrown()
    }
    
    def "should handle metrics with percentile data"() {
        given: "an exporter instance"
        def exporter = OpenTelemetryExporter.builder()
            .build()
        
        and: "metrics with percentile information"
        def successPercentiles = [
            0.50: 100_000_000.0d,  // 100ms in nanos
            0.75: 150_000_000.0d,  // 150ms
            0.95: 200_000_000.0d,  // 200ms
            0.99: 300_000_000.0d   // 300ms
        ]
        
        def failurePercentiles = [
            0.50: 50_000_000.0d,   // 50ms in nanos
            0.95: 100_000_000.0d   // 100ms
        ]
        
        def metrics = new AggregatedMetrics(
            100L,
            90L,
            10L,
            successPercentiles,
            failurePercentiles,
            1000L,  // 1 second elapsed
            0L,     // queue size
            [:] as Map<Double, Double>, new com.vajrapulse.core.metrics.ClientMetrics()  // client metrics
        )
        
        when: "exporting metrics with percentiles"
        exporter.export("Percentile Test", metrics)
        
        then: "no exceptions are thrown"
        noExceptionThrown()
        
        cleanup:
        exporter?.close()
    }

    def "should compute TPS metrics from snapshot"() {
        given: "an exporter instance"
        def exporter = OpenTelemetryExporter.builder().build()
        and: "aggregated metrics with known counts and elapsed time (1s)"
        def metrics = new AggregatedMetrics(
            3050L,
            2995L,
            55L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            1000L, // 1 second elapsed
            0L,     // queue size
            [:] as Map<Double, Double>, new com.vajrapulse.core.metrics.ClientMetrics()  // client metrics
        )
        when: "exporting metrics"
        exporter.export("TPS Test", metrics)
        then: "TPS calculations exposed by exporter match expected values"
        exporter.getLastResponseTps() == 3050.0
        exporter.getLastSuccessTps() == 2995.0
        exporter.getLastFailureTps() == 55.0
        cleanup:
        exporter?.close()
    }
    
    def "should not fail when OTLP endpoint is unreachable"() {
        given: "exporter pointing to unreachable endpoint"
        def unreachableEndpoint = "http://nonexistent-host.invalid:9999"
        def exporter = OpenTelemetryExporter.builder()
            .endpoint(unreachableEndpoint)
            .resourceAttributes(["service.name": "resilient-test"])
            .build()
        
        and: "sample metrics"
        def metrics = createSampleMetrics(totalExecutions: 10L, successCount: 10L, failureCount: 0L)
        
        when: "exporting to unreachable endpoint"
        exporter.export("Resilience Test", metrics)
        
        then: "exporter is resilient and doesn't throw"
        noExceptionThrown()
        
        cleanup:
        exporter?.close()
    }
    
    // Helper method to create sample AggregatedMetrics
    private AggregatedMetrics createSampleMetrics(Map params) {
        long totalExecutions = params.totalExecutions ?: 0L
        long successCount = params.successCount ?: 0L
        long failureCount = params.failureCount ?: 0L
        
        // Default percentiles (empty if no successes/failures)
        def successPercentiles = successCount > 0 ? [
            0.50: 50_000_000.0d,
            0.95: 100_000_000.0d,
            0.99: 150_000_000.0d
        ] : [:]
        
        def failurePercentiles = failureCount > 0 ? [
            0.50: 25_000_000.0d,
            0.95: 75_000_000.0d
        ] : [:]
        
        new AggregatedMetrics(
            totalExecutions,
            successCount,
            failureCount,
            successPercentiles,
            failurePercentiles,
            1000L,  // 1 second elapsed
            0L,     // queue size
            [:] as Map<Double, Double>, new com.vajrapulse.core.metrics.ClientMetrics()  // client metrics
        )
    }
}
