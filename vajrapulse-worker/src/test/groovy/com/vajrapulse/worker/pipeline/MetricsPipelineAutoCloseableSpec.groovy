package com.vajrapulse.worker.pipeline

import com.vajrapulse.api.pattern.LoadPattern
import com.vajrapulse.api.task.Task
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.api.task.VirtualThreads
import com.vajrapulse.core.metrics.AggregatedMetrics
import com.vajrapulse.core.metrics.MetricsExporter
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for MetricsPipeline AutoCloseable lifecycle management.
 */
class MetricsPipelineAutoCloseableSpec extends Specification {

    def "should close AutoCloseable exporters when pipeline is closed"() {
        given: "an AutoCloseable exporter tracking close calls"
        CloseableStubExporter exporter = new CloseableStubExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleTask()
        LoadPattern pattern = new SimplePattern(10.0, Duration.ofMillis(50))
        
        and: "a pipeline with the exporter"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(exporter)
            .build()
        
        when: "running and closing the pipeline"
        pipeline.run(task, pattern)
        pipeline.close()
        
        then: "exporter close was called"
        exporter.closeCalled.get()
    }
    
    def "should work with try-with-resources pattern"() {
        given: "an AutoCloseable exporter"
        CloseableStubExporter exporter = new CloseableStubExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleTask()
        LoadPattern pattern = new SimplePattern(10.0, Duration.ofMillis(50))
        
        when: "using try-with-resources"
        AggregatedMetrics result
        try (MetricsPipeline pipeline = MetricsPipeline.builder()
                .addExporter(exporter)
                .build()) {
            result = pipeline.run(task, pattern)
        }
        
        then: "exporter received final metrics before close"
        exporter.exportCount.get() >= 1
        exporter.lastTitle == "Final Results"
        
        and: "exporter was closed automatically"
        exporter.closeCalled.get()
        
        and: "pipeline returned metrics"
        result.totalExecutions() > 0
    }
    
    def "should export final metrics before closing exporters"() {
        given: "an ordered tracking exporter"
        OrderedCloseableExporter exporter = new OrderedCloseableExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleTask()
        LoadPattern pattern = new SimplePattern(10.0, Duration.ofMillis(50))
        
        when: "using try-with-resources"
        try (MetricsPipeline pipeline = MetricsPipeline.builder()
                .addExporter(exporter)
                .build()) {
            pipeline.run(task, pattern)
        }
        
        then: "export happened before close"
        exporter.operations == ["export", "close"]
    }
    
    def "should close multiple AutoCloseable exporters"() {
        given: "multiple AutoCloseable exporters"
        CloseableStubExporter exporter1 = new CloseableStubExporter()
        CloseableStubExporter exporter2 = new CloseableStubExporter()
        CloseableStubExporter exporter3 = new CloseableStubExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleTask()
        LoadPattern pattern = new SimplePattern(10.0, Duration.ofMillis(50))
        
        when: "using try-with-resources with multiple exporters"
        try (MetricsPipeline pipeline = MetricsPipeline.builder()
                .addExporter(exporter1)
                .addExporter(exporter2)
                .addExporter(exporter3)
                .build()) {
            pipeline.run(task, pattern)
        }
        
        then: "all exporters closed"
        exporter1.closeCalled.get()
        exporter2.closeCalled.get()
        exporter3.closeCalled.get()
    }
    
    def "should handle close failure gracefully"() {
        given: "an exporter that throws on close"
        FailingCloseExporter failingExporter = new FailingCloseExporter()
        CloseableStubExporter normalExporter = new CloseableStubExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleTask()
        LoadPattern pattern = new SimplePattern(10.0, Duration.ofMillis(50))
        
        when: "using try-with-resources"
        try (MetricsPipeline pipeline = MetricsPipeline.builder()
                .addExporter(failingExporter)
                .addExporter(normalExporter)
                .build()) {
            pipeline.run(task, pattern)
        }
        
        then: "no exception propagated"
        notThrown(Exception)
        
        and: "other exporters still closed"
        normalExporter.closeCalled.get()
    }
    
    def "should not close non-AutoCloseable exporters"() {
        given: "a regular exporter without AutoCloseable"
        SimpleExporter simpleExporter = new SimpleExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleTask()
        LoadPattern pattern = new SimplePattern(10.0, Duration.ofMillis(50))
        
        when: "using try-with-resources"
        try (MetricsPipeline pipeline = MetricsPipeline.builder()
                .addExporter(simpleExporter)
                .build()) {
            pipeline.run(task, pattern)
        }
        
        then: "exporter received metrics"
        simpleExporter.exportCount.get() > 0
        
        and: "no exceptions thrown"
        notThrown(Exception)
    }

    // Helper classes
    
    @VirtualThreads
    static class SimpleTask implements Task {
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(2)
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    static class SimplePattern implements LoadPattern {
        private final double tps
        private final Duration duration
        
        SimplePattern(double tps, Duration duration) {
            this.tps = tps
            this.duration = duration
        }
        
        @Override
        double calculateTps(long elapsedMillis) {
            return tps
        }
        
        @Override
        Duration getDuration() {
            return duration
        }
    }
    
    static class CloseableStubExporter implements MetricsExporter, AutoCloseable {
        final AtomicInteger exportCount = new AtomicInteger(0)
        final AtomicBoolean closeCalled = new AtomicBoolean(false)
        String lastTitle = null
        
        @Override
        void export(String title, AggregatedMetrics metrics) {
            exportCount.incrementAndGet()
            lastTitle = title
        }
        
        @Override
        void close() {
            closeCalled.set(true)
        }
    }
    
    static class OrderedCloseableExporter implements MetricsExporter, AutoCloseable {
        final List<String> operations = []
        
        @Override
        void export(String title, AggregatedMetrics metrics) {
            operations.add("export")
        }
        
        @Override
        void close() {
            operations.add("close")
        }
    }
    
    static class FailingCloseExporter implements MetricsExporter, AutoCloseable {
        @Override
        void export(String title, AggregatedMetrics metrics) {
            // No-op
        }
        
        @Override
        void close() throws Exception {
            throw new RuntimeException("Close failure simulation")
        }
    }
    
    static class SimpleExporter implements MetricsExporter {
        final AtomicInteger exportCount = new AtomicInteger(0)
        
        @Override
        void export(String title, AggregatedMetrics metrics) {
            exportCount.incrementAndGet()
        }
    }
}
