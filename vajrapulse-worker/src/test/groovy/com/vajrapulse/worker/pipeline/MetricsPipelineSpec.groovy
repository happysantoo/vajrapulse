package com.vajrapulse.worker.pipeline

import com.vajrapulse.api.pattern.LoadPattern
import com.vajrapulse.api.task.Task
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.api.task.VirtualThreads
import com.vajrapulse.core.metrics.AggregatedMetrics
import com.vajrapulse.core.metrics.MetricsExporter
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class MetricsPipelineSpec extends Specification {

    def "should execute task and return aggregated metrics"() {
        given: "a simple task"
        Task task = new SimpleSuccessTask()
        
        and: "a minimal load pattern"
        LoadPattern pattern = new StaticLoadPattern(10.0, Duration.ofMillis(100))
        
        and: "a pipeline with no exporters"
        MetricsPipeline pipeline = MetricsPipeline.builder().build()
        
        when: "running the pipeline"
        AggregatedMetrics metrics = pipeline.run(task, pattern)
        
        then: "metrics are captured"
        metrics.totalExecutions() > 0
        metrics.successCount() > 0
    }
    
    def "should invoke exporter with final results"() {
        given: "a stub exporter tracking invocations"
        StubMetricsExporter exporter = new StubMetricsExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(10.0, Duration.ofMillis(50))
        
        and: "a pipeline with the stub exporter"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(exporter)
            .build()
        
        when: "running the pipeline"
        pipeline.run(task, pattern)
        
        then: "exporter was invoked at least once for final results"
        exporter.invocationCount.get() >= 1
        exporter.lastTitle == "Final Results"
    }
    
    def "should invoke periodic exporter during execution"() {
        given: "a stub exporter tracking invocations"
        StubMetricsExporter exporter = new StubMetricsExporter()
        
        and: "a simple task and longer pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(50.0, Duration.ofMillis(300))
        
        and: "a pipeline with periodic reporting"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(exporter)
            .withPeriodic(Duration.ofMillis(50))
            .build()
        
        when: "running the pipeline"
        pipeline.run(task, pattern)
        
        then: "exporter invoked multiple times (periodic + final)"
        exporter.invocationCount.get() > 1
    }
    
    def "should configure custom percentiles"() {
        given: "custom percentiles configuration"
        double[] customPercentiles = [0.50, 0.75, 0.90, 0.95, 0.99] as double[]
        
        and: "a stub exporter"
        StubMetricsExporter exporter = new StubMetricsExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(20.0, Duration.ofMillis(100))
        
        and: "a pipeline with custom percentiles"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .withPercentiles(customPercentiles)
            .addExporter(exporter)
            .build()
        
        when: "running the pipeline"
        pipeline.run(task, pattern)
        
        then: "aggregated metrics contain all configured percentiles"
        AggregatedMetrics metrics = exporter.lastMetrics
        metrics.successPercentiles().keySet().containsAll([0.50d, 0.75d, 0.90d, 0.95d, 0.99d])
    }
    
    def "should handle multiple exporters"() {
        given: "two stub exporters"
        StubMetricsExporter exporter1 = new StubMetricsExporter()
        StubMetricsExporter exporter2 = new StubMetricsExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(10.0, Duration.ofMillis(50))
        
        and: "a pipeline with both exporters"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(exporter1)
            .addExporter(exporter2)
            .build()
        
        when: "running the pipeline"
        pipeline.run(task, pattern)
        
        then: "both exporters invoked"
        exporter1.invocationCount.get() >= 1
        exporter2.invocationCount.get() >= 1
    }
    
    def "should handle task with failures"() {
        given: "a task that fails sometimes"
        Task task = new MixedResultTask()
        
        and: "a stub exporter"
        StubMetricsExporter exporter = new StubMetricsExporter()
        
        and: "a pattern generating several iterations"
        LoadPattern pattern = new StaticLoadPattern(50.0, Duration.ofMillis(100))
        
        and: "a pipeline"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(exporter)
            .build()
        
        when: "running the pipeline"
        pipeline.run(task, pattern)
        
        then: "metrics capture both successes and failures"
        AggregatedMetrics metrics = exporter.lastMetrics
        metrics.totalExecutions() > 0
        metrics.successCount() > 0
        metrics.failureCount() > 0
        metrics.failurePercentiles().size() > 0
    }
    
    def "should handle exporter throwing exception"() {
        given: "a failing exporter"
        MetricsExporter failingExporter = new MetricsExporter() {
            @Override
            void export(String title, AggregatedMetrics metricsSnapshot) {
                throw new RuntimeException("Exporter failure simulation")
            }
        }
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(10.0, Duration.ofMillis(50))
        
        and: "a pipeline with failing exporter"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(failingExporter)
            .build()
        
        when: "running the pipeline"
        AggregatedMetrics resultMetrics = pipeline.run(task, pattern)
        
        then: "pipeline still completes and returns metrics"
        notThrown(Exception)
        resultMetrics.totalExecutions() > 0
    }
    
    def "should use default percentiles when none specified"() {
        given: "a stub exporter"
        StubMetricsExporter exporter = new StubMetricsExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(10.0, Duration.ofMillis(50))
        
        and: "a pipeline without custom percentiles"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(exporter)
            .build()
        
        when: "running the pipeline"
        pipeline.run(task, pattern)
        
        then: "default percentiles are present (0.50, 0.95, 0.99)"
        AggregatedMetrics metrics = exporter.lastMetrics
        metrics.successPercentiles().keySet().containsAll([0.50d, 0.95d, 0.99d])
    }
    
    def "should invoke task setup and cleanup"() {
        given: "a task tracking lifecycle"
        LifecycleTrackingTask task = new LifecycleTrackingTask()
        
        and: "a simple pattern"
        LoadPattern pattern = new StaticLoadPattern(5.0, Duration.ofMillis(50))
        
        and: "a pipeline"
        MetricsPipeline pipeline = MetricsPipeline.builder().build()
        
        when: "running the pipeline"
        pipeline.run(task, pattern)
        
        then: "setup and cleanup were called"
        task.setupCalled
        task.cleanupCalled
    }

    // Helper classes
    
    @VirtualThreads
    static class SimpleSuccessTask implements Task {
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(5)
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    @VirtualThreads
    static class MixedResultTask implements Task {
        private final AtomicInteger counter = new AtomicInteger(0)
        
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(5)
            int count = counter.incrementAndGet()
            if (count % 3 == 0) {
                return TaskResult.failure(new RuntimeException("Simulated failure"))
            }
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    @VirtualThreads
    static class LifecycleTrackingTask implements Task {
        boolean setupCalled = false
        boolean cleanupCalled = false
        
        @Override
        void setup() {
            setupCalled = true
        }
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(2)
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {
            cleanupCalled = true
        }
    }
    
    static class StubMetricsExporter implements MetricsExporter {
        final AtomicInteger invocationCount = new AtomicInteger(0)
        String lastTitle = null
        AggregatedMetrics lastMetrics = null
        
        @Override
        void export(String title, AggregatedMetrics metrics) {
            invocationCount.incrementAndGet()
            lastTitle = title
            lastMetrics = metrics
        }
    }
    
    static class StaticLoadPattern implements LoadPattern {
        private final double tps
        private final Duration duration
        
        StaticLoadPattern(double tps, Duration duration) {
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
}
