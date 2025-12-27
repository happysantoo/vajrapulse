package com.vajrapulse.core.metrics

import com.vajrapulse.api.metrics.RunContext
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Instant

@Timeout(10)
class MetricsExporterSpec extends Specification {

    def "should call export with RunContext by default"() {
        given: "a custom exporter that tracks calls"
        def exportedTitle = null
        def exportedMetrics = null
        def exportedContext = null
        
        MetricsExporter exporter = new MetricsExporter() {
            @Override
            void export(String title, AggregatedMetrics metrics) {
                exportedTitle = title
                exportedMetrics = metrics
            }
            
            @Override
            void export(String title, AggregatedMetrics metrics, RunContext context) {
                exportedTitle = title
                exportedMetrics = metrics
                exportedContext = context
            }
        }
        
        def metrics = new AggregatedMetrics(100L, 95L, 5L, [:], [:], 1000L, 0L, [:])
        def context = RunContext.of("run-123", Instant.now(), "TestTask", "StaticLoad", [tps: 100.0])
        
        when: "exporting with context"
        exporter.export("Test Results", metrics, context)
        
        then: "context is passed"
        exportedTitle == "Test Results"
        exportedMetrics == metrics
        exportedContext == context
        exportedContext.runId() == "run-123"
    }
    
    def "should fallback to simple export when context method not overridden"() {
        given: "an exporter that only implements simple export"
        def exportCalled = false
        
        MetricsExporter exporter = new MetricsExporter() {
            @Override
            void export(String title, AggregatedMetrics metrics) {
                exportCalled = true
            }
        }
        
        def metrics = new AggregatedMetrics(100L, 95L, 5L, [:], [:], 1000L, 0L, [:])
        def context = RunContext.of("run-123", Instant.now(), "TestTask", "StaticLoad", [:])
        
        when: "exporting with context"
        exporter.export("Test Results", metrics, context)
        
        then: "fallback to simple export is called"
        exportCalled
    }
    
    def "should handle empty RunContext"() {
        given: "an exporter and empty context"
        def exportedContext = null
        
        MetricsExporter exporter = new MetricsExporter() {
            @Override
            void export(String title, AggregatedMetrics metrics) {
                // Not called
            }
            
            @Override
            void export(String title, AggregatedMetrics metrics, RunContext context) {
                exportedContext = context
            }
        }
        
        def metrics = new AggregatedMetrics(100L, 95L, 5L, [:], [:], 1000L, 0L, [:])
        
        when: "exporting with empty context"
        exporter.export("Test Results", metrics, RunContext.empty())
        
        then: "empty context is passed"
        exportedContext != null
        exportedContext.runId() == "unknown"
        exportedContext.taskClass() == "unknown"
    }
}
