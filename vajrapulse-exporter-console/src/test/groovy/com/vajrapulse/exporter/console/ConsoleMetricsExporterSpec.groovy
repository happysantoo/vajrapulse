package com.vajrapulse.exporter.console

import com.vajrapulse.core.metrics.AggregatedMetrics
import spock.lang.Specification

import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ConsoleMetricsExporterSpec extends Specification {

    def "should display all configured percentiles"() {
        given: "metrics with custom percentiles"
        Map<Double, Double> successPercentiles = [
            0.50d: 12_500_000.0d,  // 12.5ms
            0.75d: 23_800_000.0d,  // 23.8ms
            0.90d: 38_400_000.0d,  // 38.4ms
            0.95d: 45_200_000.0d,  // 45.2ms
            0.99d: 78_900_000.0d   // 78.9ms
        ]
        Map<Double, Double> failurePercentiles = [
            0.50d: 150_300_000.0d,
            0.95d: 250_700_000.0d,
            0.99d: 380_100_000.0d
        ]
        
        AggregatedMetrics metrics = new AggregatedMetrics(
            1000,
            950,
            50,
            successPercentiles,
            failurePercentiles
        )
        
        and: "a console exporter capturing output"
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        PrintStream printStream = new PrintStream(outputStream)
        ConsoleMetricsExporter exporter = new ConsoleMetricsExporter(printStream)
        
        when: "exporting metrics"
        exporter.export("Test Results", metrics)
        String output = outputStream.toString()
        
        then: "all success percentiles are displayed"
        output.contains("P50:  12.50")
        output.contains("P75:  23.80")
        output.contains("P90:  38.40")
        output.contains("P95:  45.20")
        output.contains("P99:  78.90")
        
        and: "all failure percentiles are displayed"
        output.contains("P50:  150.30")
        output.contains("P95:  250.70")
        output.contains("P99:  380.10")
    }
    
    def "should handle default percentiles"() {
        given: "metrics with default percentiles (P50, P95, P99)"
        Map<Double, Double> defaultPercentiles = [
            0.50d: 10_000_000.0d,
            0.95d: 50_000_000.0d,
            0.99d: 100_000_000.0d
        ]
        
        AggregatedMetrics metrics = new AggregatedMetrics(
            100,
            100,
            0,
            defaultPercentiles,
            [:] as Map<Double, Double> // no failures
        )
        
        and: "a console exporter"
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ConsoleMetricsExporter exporter = new ConsoleMetricsExporter(new PrintStream(outputStream))
        
        when: "exporting metrics"
        exporter.export(metrics)
        String output = outputStream.toString()
        
        then: "default percentiles are shown"
        output.contains("P50:")
        output.contains("P95:")
        output.contains("P99:")
        
        and: "no failure section since count is 0"
        !output.contains("Failure Latency")
    }
    
    def "should format non-integer percentiles correctly"() {
        given: "metrics with fractional percentiles"
        Map<Double, Double> customPercentiles = [
            0.50d: 10_000_000.0d,
            0.95d: 50_000_000.0d,
            0.975d: 75_000_000.0d,  // P97.5
            0.99d: 100_000_000.0d,
            0.999d: 150_000_000.0d  // P99.9
        ]
        
        AggregatedMetrics metrics = new AggregatedMetrics(
            1000,
            1000,
            0,
            customPercentiles,
            [:] as Map<Double, Double>
        )
        
        and: "a console exporter"
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ConsoleMetricsExporter exporter = new ConsoleMetricsExporter(new PrintStream(outputStream))
        
        when: "exporting metrics"
        exporter.export(metrics)
        String output = outputStream.toString()
        
        then: "fractional percentiles formatted with decimal"
        output.contains("P97.5:")
        output.contains("P99.9:")
        
        and: "integer percentiles formatted without decimal"
        output =~ /P50:\s+10\.00/
        output =~ /P99:\s+100\.00/
    }
    
    def "should not show percentiles section when empty"() {
        given: "metrics with no executions"
        AggregatedMetrics metrics = new AggregatedMetrics(
            0,
            0,
            0,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>
        )
        
        and: "a console exporter"
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ConsoleMetricsExporter exporter = new ConsoleMetricsExporter(new PrintStream(outputStream))
        
        when: "exporting metrics"
        exporter.export("Empty Test", metrics)
        String output = outputStream.toString()
        
        then: "no latency sections shown"
        !output.contains("Success Latency")
        !output.contains("Failure Latency")
        
        and: "summary still shown"
        output.contains("Total Executions:    0")
    }
    
    def "should show title when provided"() {
        given: "metrics"
        Map<Double, Double> percentiles = [
            0.50d: 5_000_000.0d,
            0.95d: 10_000_000.0d,
            0.99d: 15_000_000.0d
        ]
        AggregatedMetrics metrics = new AggregatedMetrics(
            50,
            50,
            0,
            percentiles,
            [:] as Map<Double, Double>
        )
        
        and: "a console exporter"
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ConsoleMetricsExporter exporter = new ConsoleMetricsExporter(new PrintStream(outputStream))
        
        when: "exporting with custom title"
        exporter.export("Custom Load Test Results", metrics)
        String output = outputStream.toString()
        
        then: "custom title is shown"
        output.contains("Custom Load Test Results")
    }

    def "should sort percentile keys ascending and format up to 3 decimals"() {
        given: "metrics with unsorted, fractional percentiles"
        Map<Double, Double> successPercentiles = new LinkedHashMap<>()
        successPercentiles.put(0.99d, 100_000_000.0d)
        successPercentiles.put(0.97125d, 75_000_000.0d) // P97.125
        successPercentiles.put(0.50d, 10_000_000.0d)

        AggregatedMetrics metrics = new AggregatedMetrics(
            3,
            3,
            0,
            successPercentiles,
            [:] as Map<Double, Double>
        )

        and: "a console exporter"
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ConsoleMetricsExporter exporter = new ConsoleMetricsExporter(new PrintStream(outputStream))

        when: "exporting"
        exporter.export(metrics)
        String output = outputStream.toString()

        then: "labels are sorted and formatted to max 3 decimals"
        output.contains("P50:")
        output.contains("P97.125:")
        output.contains("P99:")
        output.indexOf("P50:") < output.indexOf("P97.125:")
        output.indexOf("P97.125:") < output.indexOf("P99:")
    }
}
