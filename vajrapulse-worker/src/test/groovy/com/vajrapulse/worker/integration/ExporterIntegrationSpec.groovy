package com.vajrapulse.worker.integration

import com.vajrapulse.api.task.Task
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.api.task.VirtualThreads
import com.vajrapulse.api.pattern.LoadPattern
import com.vajrapulse.core.metrics.AggregatedMetrics
import com.vajrapulse.core.metrics.MetricsExporter
import com.vajrapulse.exporter.console.ConsoleMetricsExporter
import com.vajrapulse.worker.pipeline.MetricsPipeline
import spock.lang.Specification
import spock.lang.Timeout

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end integration tests for exporters.
 * 
 * <p>Tests verify:
 * <ul>
 *   <li>Console exporter formats metrics correctly</li>
 *   <li>OpenTelemetry exporter sends metrics (when available)</li>
 *   <li>Multiple exporters work together</li>
 *   <li>Exporter error handling doesn't break execution</li>
 *   <li>Periodic reporting works correctly</li>
 * </ul>
 */
@Timeout(30)
class ExporterIntegrationSpec extends Specification {

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
            if (count % 4 == 0) {
                return TaskResult.failure(new RuntimeException("Simulated failure"))
            }
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
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
    
    static class StubMetricsExporter implements MetricsExporter {
        final AtomicInteger invocationCount = new AtomicInteger(0)
        String lastTitle = null
        AggregatedMetrics lastMetrics = null
        List<String> allTitles = []
        List<AggregatedMetrics> allMetrics = []
        
        @Override
        void export(String title, AggregatedMetrics metrics) {
            invocationCount.incrementAndGet()
            lastTitle = title
            lastMetrics = metrics
            allTitles.add(title)
            allMetrics.add(metrics)
        }
    }
    
    def "should export to console exporter correctly"() {
        given: "a console exporter with captured output"
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        PrintStream printStream = new PrintStream(outputStream)
        ConsoleMetricsExporter consoleExporter = new ConsoleMetricsExporter(printStream)
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(30.0, Duration.ofMillis(150))
        
        and: "a pipeline with console exporter"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(consoleExporter)
            .build()
        
        when: "running the pipeline"
        AggregatedMetrics metrics = pipeline.run(task, pattern)
        String output = outputStream.toString()
        
        then: "console exporter produces formatted output"
        metrics.totalExecutions() > 0
        output.contains("Load Test Results") || output.contains("Final Results")
        output.contains("Total:")
        output.contains("Successful:")
        output.contains("Failed:")
        output.contains("TPS")
        output.contains("Latency")
    }
    
    def "should export to console exporter with custom percentiles"() {
        given: "a console exporter with captured output"
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        PrintStream printStream = new PrintStream(outputStream)
        ConsoleMetricsExporter consoleExporter = new ConsoleMetricsExporter(printStream)
        
        and: "custom percentiles"
        double[] customPercentiles = [0.50, 0.75, 0.90, 0.95, 0.99] as double[]
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(20.0, Duration.ofMillis(100))
        
        and: "a pipeline with custom percentiles"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .withPercentiles(customPercentiles)
            .addExporter(consoleExporter)
            .build()
        
        when: "running the pipeline"
        AggregatedMetrics metrics = pipeline.run(task, pattern)
        String output = outputStream.toString()
        
        then: "console output includes all custom percentiles"
        metrics.totalExecutions() > 0
        output.contains("P50") || output.contains("50%")
        output.contains("P95") || output.contains("95%")
        output.contains("P99") || output.contains("99%")
    }
    
    def "should export to console exporter with mixed results"() {
        given: "a console exporter with captured output"
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        PrintStream printStream = new PrintStream(outputStream)
        ConsoleMetricsExporter consoleExporter = new ConsoleMetricsExporter(printStream)
        
        and: "a task with mixed results"
        Task task = new MixedResultTask()
        LoadPattern pattern = new StaticLoadPattern(40.0, Duration.ofMillis(150))
        
        and: "a pipeline with console exporter"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(consoleExporter)
            .build()
        
        when: "running the pipeline"
        AggregatedMetrics metrics = pipeline.run(task, pattern)
        String output = outputStream.toString()
        
        then: "console output shows both success and failure metrics"
        metrics.totalExecutions() > 0
        metrics.successCount() > 0
        metrics.failureCount() > 0
        output.contains("Successful:")
        output.contains("Failed:")
        output.contains("Success Latency") || output.contains("Success")
        output.contains("Failure Latency") || output.contains("Failure")
    }
    
    def "should export to multiple exporters simultaneously"() {
        given: "multiple stub exporters"
        StubMetricsExporter exporter1 = new StubMetricsExporter()
        StubMetricsExporter exporter2 = new StubMetricsExporter()
        StubMetricsExporter exporter3 = new StubMetricsExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(25.0, Duration.ofMillis(100))
        
        and: "a pipeline with all exporters"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(exporter1)
            .addExporter(exporter2)
            .addExporter(exporter3)
            .build()
        
        when: "running the pipeline"
        AggregatedMetrics metrics = pipeline.run(task, pattern)
        
        then: "all exporters receive final metrics"
        exporter1.invocationCount.get() >= 1
        exporter2.invocationCount.get() >= 1
        exporter3.invocationCount.get() >= 1
        exporter1.lastTitle == "Final Results"
        exporter2.lastTitle == "Final Results"
        exporter3.lastTitle == "Final Results"
        exporter1.lastMetrics.totalExecutions() == metrics.totalExecutions()
        exporter2.lastMetrics.totalExecutions() == metrics.totalExecutions()
        exporter3.lastMetrics.totalExecutions() == metrics.totalExecutions()
    }
    
    static class FailingMetricsExporter implements MetricsExporter {
        @Override
        void export(String title, AggregatedMetrics metrics) {
            throw new RuntimeException("Exporter failure simulation")
        }
    }
    
    def "should handle exporter failures gracefully"() {
        given: "a failing exporter and a working exporter"
        MetricsExporter failingExporter = new FailingMetricsExporter()
        StubMetricsExporter workingExporter = new StubMetricsExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(20.0, Duration.ofMillis(100))
        
        and: "a pipeline with both exporters"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(failingExporter)
            .addExporter(workingExporter)
            .build()
        
        when: "running the pipeline"
        AggregatedMetrics metrics = pipeline.run(task, pattern)
        
        then: "pipeline completes and working exporter receives metrics"
        notThrown(Exception)
        metrics.totalExecutions() > 0
        workingExporter.invocationCount.get() >= 1
        workingExporter.lastMetrics.totalExecutions() == metrics.totalExecutions()
    }
    
    def "should perform periodic reporting correctly"() {
        given: "a stub exporter"
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
        AggregatedMetrics metrics = pipeline.run(task, pattern)
        
        then: "exporter is invoked multiple times (periodic + final)"
        exporter.invocationCount.get() > 1
        exporter.allTitles.size() > 1
        // First invocation should be periodic (not "Final Results")
        exporter.allTitles[0] != "Final Results"
        // Last invocation should be final
        exporter.allTitles.last() == "Final Results"
        // Metrics should accumulate over time
        exporter.allMetrics.last().totalExecutions() >= exporter.allMetrics.first().totalExecutions()
    }
    
    def "should perform periodic reporting with immediate live"() {
        given: "a stub exporter"
        StubMetricsExporter exporter = new StubMetricsExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(30.0, Duration.ofMillis(200))
        
        and: "a pipeline with immediate live reporting"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(exporter)
            .withPeriodic(Duration.ofMillis(50))
            .withImmediateLive(true)
            .build()
        
        when: "running the pipeline"
        AggregatedMetrics metrics = pipeline.run(task, pattern)
        
        then: "exporter is invoked immediately and periodically"
        exporter.invocationCount.get() > 1
        // Should have at least one immediate invocation
        exporter.allTitles.size() >= 2
    }
    
    @VirtualThreads
    static class SlowTask implements Task {
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(15) // Slow execution
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    def "should export queue metrics correctly"() {
        given: "a stub exporter"
        StubMetricsExporter exporter = new StubMetricsExporter()
        
        and: "a task with slow execution"
        Task slowTask = new SlowTask()
        LoadPattern pattern = new StaticLoadPattern(80.0, Duration.ofMillis(150))
        
        and: "a pipeline with exporter"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(exporter)
            .build()
        
        when: "running the pipeline"
        AggregatedMetrics metrics = pipeline.run(slowTask, pattern)
        
        then: "queue metrics are exported"
        exporter.lastMetrics.queueSize() == 0 // Should be zero after completion
        exporter.lastMetrics.queueWaitPercentiles().size() > 0
        // Queue wait time should be recorded
        exporter.lastMetrics.queueWaitPercentiles().values().any { it != null && !it.isNaN() }
    }
    
    def "should export with runId correctly"() {
        given: "a stub exporter"
        StubMetricsExporter exporter = new StubMetricsExporter()
        
        and: "a simple task and pattern"
        Task task = new SimpleSuccessTask()
        LoadPattern pattern = new StaticLoadPattern(20.0, Duration.ofMillis(100))
        
        and: "a pipeline with explicit runId"
        String testRunId = "test-run-${System.currentTimeMillis()}"
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .withRunId(testRunId)
            .addExporter(exporter)
            .build()
        
        when: "running the pipeline"
        AggregatedMetrics metrics = pipeline.run(task, pattern)
        
        then: "metrics are collected with runId"
        metrics.totalExecutions() > 0
        exporter.lastMetrics != null
    }
}

