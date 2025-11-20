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
            [0.5d: 2_000_000.0d, 0.95d: 15_000_000.0d]
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
}

