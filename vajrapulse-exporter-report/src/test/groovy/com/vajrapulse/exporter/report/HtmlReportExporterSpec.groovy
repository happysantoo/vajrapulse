package com.vajrapulse.exporter.report

import com.vajrapulse.core.metrics.AggregatedMetrics
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for HtmlReportExporter.
 */
class HtmlReportExporterSpec extends Specification {
    
    @TempDir
    Path tempDir
    
    def "should export metrics to HTML file"() {
        given:
        def outputPath = tempDir.resolve("test-report.html")
        def exporter = new HtmlReportExporter(outputPath)
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
        def html = Files.readString(outputPath)
        html.contains("<!DOCTYPE html>")
        html.contains("Test Report")
        html.contains("1000")
        html.contains("950")
        html.contains("50")
        html.contains("chart.js")
        html.contains("successChart")
        html.contains("failureChart")
        html.contains("queueChart")
    }
    
    def "should create parent directories if they don't exist"() {
        given:
        def outputPath = tempDir.resolve("subdir/report.html")
        def exporter = new HtmlReportExporter(outputPath)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        when:
        exporter.export("Test", metrics)
        
        then:
        Files.exists(outputPath)
        Files.exists(outputPath.parent)
    }
    
    def "should escape HTML special characters"() {
        given:
        def outputPath = tempDir.resolve("test.html")
        def exporter = new HtmlReportExporter(outputPath)
        def metrics = new AggregatedMetrics(100L, 100L, 0L, [:], [:], 1000L, 0L, [:])
        
        when:
        exporter.export("Test <script>alert('xss')</script>", metrics)
        
        then:
        def html = Files.readString(outputPath)
        html.contains("&lt;")
        !html.contains("<script>alert")
    }
    
    def "should handle empty percentiles"() {
        given:
        def outputPath = tempDir.resolve("empty.html")
        def exporter = new HtmlReportExporter(outputPath)
        def metrics = new AggregatedMetrics(0L, 0L, 0L, [:], [:], 0L, 0L, [:])
        
        when:
        exporter.export("Empty", metrics)
        
        then:
        Files.exists(outputPath)
        def html = Files.readString(outputPath)
        html.contains("Total Executions")
        !html.contains("successChart")
        !html.contains("failureChart")
    }
}

