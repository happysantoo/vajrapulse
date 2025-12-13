package com.vajrapulse.exporter.report

import com.vajrapulse.core.metrics.AggregatedMetrics
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for JsonReportExporter.
 */
class JsonReportExporterSpec extends Specification {
    
    @TempDir
    Path tempDir
    
    def "should export metrics to JSON file"() {
        given:
        def outputPath = tempDir.resolve("test-report.json")
        def exporter = new JsonReportExporter(outputPath)
        def metrics = new AggregatedMetrics(
            1000L,
            950L,
            50L,
            [0.5d: 12_500_000.0d, 0.95d: 45_200_000.0d, 0.99d: 78_900_000.0d],
            [0.5d: 150_300_000.0d, 0.99d: 380_100_000.0d],
            30_000L,
            10L,
            [0.5d: 2_000_000.0d, 0.95d: 15_000_000.0d],
        )
        
        when:
        exporter.export("Test Report", metrics)
        
        then:
        Files.exists(outputPath)
        def json = Files.readString(outputPath)
        json.contains("title")
        json.contains("Test Report")
        json.contains("totalExecutions")
        json.contains("1000")
        json.contains("successCount")
        json.contains("950")
        json.contains("failureCount")
        json.contains("50")
        json.contains("successRate")
        json.contains("responseTps")
    }
    
    def "should create parent directories if they don't exist"() {
        given:
        def outputPath = tempDir.resolve("subdir/report.json")
        def exporter = new JsonReportExporter(outputPath)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        when:
        exporter.export("Test", metrics)
        
        then:
        Files.exists(outputPath)
        Files.exists(outputPath.parent)
    }
    
    def "should handle empty percentiles"() {
        given:
        def outputPath = tempDir.resolve("empty-report.json")
        def exporter = new JsonReportExporter(outputPath)
        def metrics = new AggregatedMetrics(0L, 0L, 0L, [:], [:], 0L, 0L, [:])
        
        when:
        exporter.export("Empty Report", metrics)
        
        then:
        Files.exists(outputPath)
        def json = Files.readString(outputPath)
        json.contains('"totalExecutions" : 0')
        !json.contains("successLatencyMs")
        !json.contains("failureLatencyMs")
    }

    def "should include adaptive pattern metrics when registry provided"() {
        given:
        def outputPath = tempDir.resolve("adaptive-report.json")
        def registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        def exporter = new JsonReportExporter(outputPath, registry)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        // Register adaptive pattern metrics
        io.micrometer.core.instrument.Gauge.builder("vajrapulse.adaptive.phase", { -> 1.0 }).register(registry)
        io.micrometer.core.instrument.Gauge.builder("vajrapulse.adaptive.current_tps", { -> 60.0 }).register(registry)
        io.micrometer.core.instrument.Gauge.builder("vajrapulse.adaptive.stable_tps", { -> 55.0 }).register(registry)
        io.micrometer.core.instrument.Gauge.builder("vajrapulse.adaptive.phase_transitions", { -> 2.0 }).register(registry)
        
        when:
        exporter.export("Adaptive Test", metrics)
        
        then:
        Files.exists(outputPath)
        def json = Files.readString(outputPath)
        json.contains("adaptivePattern")
        json.contains('"phase" : "RAMP_DOWN"')
        json.contains('"phaseOrdinal" : 1')
        json.contains('"currentTps" : 60.0')
        json.contains('"stableTps" : 55.0')
        json.contains('"phaseTransitions" : 2')
    }

    def "should handle registry with missing adaptive metrics"() {
        given:
        def outputPath = tempDir.resolve("no-adaptive-report.json")
        def registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        def exporter = new JsonReportExporter(outputPath, registry)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        when:
        exporter.export("No Adaptive", metrics)
        
        then:
        Files.exists(outputPath)
        def json = Files.readString(outputPath)
        !json.contains("adaptivePattern")
        noExceptionThrown()
    }

    def "should handle queue wait percentiles"() {
        given:
        def outputPath = tempDir.resolve("queue-report.json")
        def exporter = new JsonReportExporter(outputPath)
        def metrics = new AggregatedMetrics(
            100L, 100L, 0L,
            [:], [:],
            1000L,
            5L,
            [0.5d: 1_000_000.0d, 0.95d: 5_000_000.0d],
        )
        
        when:
        exporter.export("Queue Test", metrics)
        
        then:
        Files.exists(outputPath)
        def json = Files.readString(outputPath)
        json.contains("queue")
        json.contains("waitTimeMs")
        json.contains("p50")
        json.contains("p95")
    }
}

