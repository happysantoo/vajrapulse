package com.vajrapulse.exporter.report

import com.vajrapulse.core.metrics.AggregatedMetrics
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for CsvReportExporter.
 */
class CsvReportExporterSpec extends Specification {
    
    @TempDir
    Path tempDir
    
    def "should export metrics to CSV file"() {
        given:
        def outputPath = tempDir.resolve("test-report.csv")
        def exporter = new CsvReportExporter(outputPath)
        def metrics = new AggregatedMetrics(
            1000L,
            950L,
            50L,
            [0.5d: 12_500_000.0d, 0.95d: 45_200_000.0d],
            [0.5d: 150_300_000.0d],
            30_000L,
            10L,
            [0.5d: 2_000_000.0d],
        )
        
        when:
        exporter.export("Test Report", metrics)
        
        then:
        Files.exists(outputPath)
        def csv = Files.readString(outputPath)
        csv.contains("Metric,Value")
        csv.contains("Title,Test Report")
        csv.contains("Total Executions,1000")
        csv.contains("Success Count,950")
        csv.contains("Failure Count,50")
        csv.contains("P50,")
        csv.contains("P95,")
    }
    
    def "should create parent directories if they don't exist"() {
        given:
        def outputPath = tempDir.resolve("subdir/report.csv")
        def exporter = new CsvReportExporter(outputPath)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        when:
        exporter.export("Test", metrics)
        
        then:
        Files.exists(outputPath)
        Files.exists(outputPath.parent)
    }
    
    def "should escape CSV values with commas"() {
        given:
        def outputPath = tempDir.resolve("test.csv")
        def exporter = new CsvReportExporter(outputPath)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        when:
        exporter.export("Test, Report", metrics)
        
        then:
        def csv = Files.readString(outputPath)
        csv.contains('"Test, Report"')
    }
    
    def "should handle empty percentiles"() {
        given:
        def outputPath = tempDir.resolve("empty.csv")
        def exporter = new CsvReportExporter(outputPath)
        def metrics = new AggregatedMetrics(0L, 0L, 0L, [:], [:], 0L, 0L, [:])
        
        when:
        exporter.export("Empty", metrics)
        
        then:
        Files.exists(outputPath)
        def csv = Files.readString(outputPath)
        csv.contains("Total Executions,0")
    }

    def "should include adaptive pattern metrics when registry provided"() {
        given:
        def outputPath = tempDir.resolve("adaptive.csv")
        def registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        def exporter = new CsvReportExporter(outputPath, registry)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        // Register adaptive pattern metrics
        io.micrometer.core.instrument.Gauge.builder("vajrapulse.adaptive.phase", { -> 0.0 }).register(registry)
        io.micrometer.core.instrument.Gauge.builder("vajrapulse.adaptive.current_tps", { -> 50.0 }).register(registry)
        io.micrometer.core.instrument.Gauge.builder("vajrapulse.adaptive.stable_tps", { -> 45.0 }).register(registry)
        io.micrometer.core.instrument.Gauge.builder("vajrapulse.adaptive.phase_transitions", { -> 3.0 }).register(registry)
        
        when:
        exporter.export("Adaptive Test", metrics)
        
        then:
        Files.exists(outputPath)
        def csv = Files.readString(outputPath)
        csv.contains("Adaptive Pattern")
        csv.contains("Phase,RAMP_UP")
        csv.contains("Current TPS,50.00")
        csv.contains("Stable TPS,45.00")
        csv.contains("Phase Transitions,3")
    }

    def "should handle registry with missing adaptive metrics"() {
        given:
        def outputPath = tempDir.resolve("no-adaptive.csv")
        def registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        def exporter = new CsvReportExporter(outputPath, registry)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        when:
        exporter.export("No Adaptive", metrics)
        
        then:
        Files.exists(outputPath)
        noExceptionThrown()
    }

    def "should escape CSV values with quotes"() {
        given:
        def outputPath = tempDir.resolve("escape.csv")
        def exporter = new CsvReportExporter(outputPath)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        when:
        exporter.export('Test "quoted" Report', metrics)
        
        then:
        def csv = Files.readString(outputPath)
        csv.contains('"Test ""quoted"" Report"')
    }

    def "should escape CSV values with newlines"() {
        given:
        def outputPath = tempDir.resolve("newline.csv")
        def exporter = new CsvReportExporter(outputPath)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        when:
        exporter.export("Test\nReport", metrics)
        
        then:
        def csv = Files.readString(outputPath)
        csv.contains('"Test\nReport"')
    }
}

