package com.vajrapulse.exporter.report

import com.vajrapulse.api.metrics.RunContext
import com.vajrapulse.api.metrics.SystemInfo
import com.vajrapulse.core.metrics.AggregatedMetrics
import com.vajrapulse.core.metrics.LatencyStats
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Timeout

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Tests for JsonReportExporter.
 */
@Timeout(10)
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
    
    def "should include run context metadata in JSON"() {
        given:
        def outputPath = tempDir.resolve("context-report.json")
        def exporter = new JsonReportExporter(outputPath)
        def metrics = new AggregatedMetrics(100L, 95L, 5L, [:], [:], 1000L, 0L, [:])
        def context = RunContext.of(
            "test-run-456",
            Instant.parse("2024-01-01T12:00:00Z"),
            Instant.parse("2024-01-01T12:01:00Z"),
            "MyLoadTest",
            "RampUpLoad",
            [startTps: 10.0, endTps: 100.0],
            new SystemInfo("21.0.1", "Eclipse", "Linux", "5.15", "x86_64", "server-1", 16)
        )
        
        when:
        exporter.export("Context Test", metrics, context)
        
        then:
        Files.exists(outputPath)
        def json = Files.readString(outputPath)
        json.contains('"runId" : "test-run-456"')
        json.contains('"taskClass" : "MyLoadTest"')
        json.contains('"loadPatternType" : "RampUpLoad"')
        json.contains("systemInfo")
        json.contains('"javaVersion" : "21.0.1"')
        json.contains('"hostname" : "server-1"')
        json.contains("configuration")
    }
    
    def "should include statistical summary in JSON"() {
        given:
        def outputPath = tempDir.resolve("stats-report.json")
        def exporter = new JsonReportExporter(outputPath)
        def successStats = new LatencyStats(10_000_000.0d, 2_000_000.0d, 1_000_000.0d, 50_000_000.0d, 950L)
        def failureStats = new LatencyStats(100_000_000.0d, 20_000_000.0d, 50_000_000.0d, 200_000_000.0d, 50L)
        def metrics = new AggregatedMetrics(
            1000L, 950L, 50L,
            [:], [:],
            1000L, 0L,
            [:] as Map<Double, Double>,
            successStats,
            failureStats
        )
        
        when:
        exporter.export("Stats Test", metrics)
        
        then:
        Files.exists(outputPath)
        def json = Files.readString(outputPath)
        json.contains("statistics")
        json.contains("success")
        json.contains("failure")
        json.contains("meanMs")
        json.contains("stdDevMs")
        json.contains("minMs")
        json.contains("maxMs")
        json.contains("coefficientOfVariation")
    }
    
    def "should handle empty RunContext gracefully"() {
        given:
        def outputPath = tempDir.resolve("empty-context-report.json")
        def exporter = new JsonReportExporter(outputPath)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        when:
        exporter.export("Empty Context", metrics, RunContext.empty())
        
        then:
        Files.exists(outputPath)
        def json = Files.readString(outputPath)
        !json.contains('"runId" : "unknown"')  // Should not include unknown values
        noExceptionThrown()
    }
}

