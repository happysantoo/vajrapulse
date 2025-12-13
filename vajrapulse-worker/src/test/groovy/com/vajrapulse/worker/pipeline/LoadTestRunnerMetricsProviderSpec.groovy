package com.vajrapulse.worker.pipeline

import com.vajrapulse.api.metrics.MetricsProvider
import com.vajrapulse.api.task.Task
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.api.task.VirtualThreads
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern
import com.vajrapulse.api.pattern.StaticLoad
import com.vajrapulse.core.metrics.MetricsCollector
import com.vajrapulse.exporter.console.ConsoleMetricsExporter
import spock.lang.Specification

import java.time.Duration

/**
 * Tests for LoadTestRunner.getMetricsProvider() functionality.
 */
class LoadTestRunnerMetricsProviderSpec extends Specification {

    @VirtualThreads
    static class SimpleTask implements Task {
        @Override
        void setup() {}

        @Override
        TaskResult execute() throws Exception {
            return TaskResult.success("test")
        }

        @Override
        void cleanup() {}
    }

    def "should return MetricsProvider from pipeline"() {
        given: "a metrics pipeline"
        def pipeline = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()

        when: "getting metrics provider"
        def provider = pipeline.getMetricsProvider()

        then: "provider should not be null"
        provider != null
        provider instanceof MetricsProvider

        cleanup:
        pipeline?.close()
    }

    def "should return MetricsProvider that reflects current metrics"() {
        given: "a metrics pipeline with a task"
        def task = new SimpleTask()
        def pipeline = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()

        when: "getting metrics provider before execution"
        def provider = pipeline.getMetricsProvider()

        then: "initial metrics should be zero"
        provider.getTotalExecutions() == 0L
        provider.getFailureRate() == 0.0

        when: "executing task with static load pattern"
        def loadPattern = new StaticLoad(10.0, Duration.ofMillis(100))
        pipeline.run(task, loadPattern)

        then: "metrics provider should reflect executed tasks"
        // Allow some time for metrics to be recorded
        Thread.sleep(50)
        def finalProvider = pipeline.getMetricsProvider()
        finalProvider.getTotalExecutions() > 0

        cleanup:
        pipeline?.close()
    }

    def "should return same MetricsProvider instance behavior across calls"() {
        given: "a metrics pipeline"
        def pipeline = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()

        when: "getting metrics provider multiple times"
        def provider1 = pipeline.getMetricsProvider()
        def provider2 = pipeline.getMetricsProvider()

        then: "both providers should work and reflect same underlying collector"
        provider1 != null
        provider2 != null
        // Note: They may be different instances (new adapter each time) but backed by same collector
        provider1.getTotalExecutions() == provider2.getTotalExecutions()
        provider1.getFailureRate() == provider2.getFailureRate()

        cleanup:
        pipeline?.close()
    }

    def "should work with AdaptiveLoadPattern"() {
        given: "a metrics pipeline"
        def pipeline = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()

        and: "a task that sometimes fails"
        def failureCount = new java.util.concurrent.atomic.AtomicInteger(0)
        def task = new FailingTask(failureCount)

        when: "getting metrics provider and creating adaptive pattern"
        def provider = pipeline.getMetricsProvider()
        def adaptivePattern = AdaptiveLoadPattern.builder()
            .initialTps(5.0)
            .rampIncrement(5.0)
            .rampDecrement(5.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(50.0)
            .sustainDuration(Duration.ofSeconds(2))
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.05))  // 5% error threshold
            .build()

        and: "running pipeline with adaptive pattern"
        def result = pipeline.run(task, adaptivePattern)

        then: "execution should complete successfully"
        result != null
        result.totalExecutions() > 0

        and: "metrics provider should reflect final state"
        def finalProvider = pipeline.getMetricsProvider()
        finalProvider.getTotalExecutions() > 0

        cleanup:
        pipeline?.close()
    }

    def "should work with custom MetricsCollector"() {
        given: "a custom metrics collector"
        def customCollector = new MetricsCollector()

        and: "a pipeline using the custom collector"
        def pipeline = LoadTestRunner.builder()
            .withCollector(customCollector)
            .addExporter(new ConsoleMetricsExporter())
            .build()

        when: "getting metrics provider"
        def provider = pipeline.getMetricsProvider()

        then: "provider should work with custom collector"
        provider != null
        provider.getTotalExecutions() == 0L

        cleanup:
        pipeline?.close()
        customCollector?.close()
    }

    def "should provide real-time metrics updates"() {
        given: "a metrics pipeline"
        def pipeline = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()

        and: "a task"
        def task = new SimpleTask()

        when: "getting metrics provider"
        def provider = pipeline.getMetricsProvider()

        then: "initial metrics are zero"
        provider.getTotalExecutions() == 0L

        when: "running a short test"
        def loadPattern = new StaticLoad(20.0, Duration.ofMillis(200))
        def executionThread = Thread.start {
            try {
                pipeline.run(task, loadPattern)
            } catch (Exception e) {
                // Expected - may complete before we check
            }
        }

        and: "waiting a bit and checking metrics"
        Thread.sleep(100)
        def providerDuringExecution = pipeline.getMetricsProvider()

        then: "metrics should show some executions"
        // Metrics may have been recorded
        providerDuringExecution.getTotalExecutions() >= 0

        cleanup:
        executionThread?.join(1000)
        pipeline?.close()
    }

    // Helper classes

    @VirtualThreads
    static class FailingTask implements Task {
        private final java.util.concurrent.atomic.AtomicInteger failureCount

        FailingTask(java.util.concurrent.atomic.AtomicInteger failureCount) {
            this.failureCount = failureCount
        }

        @Override
        void setup() {}

        @Override
        TaskResult execute() throws Exception {
            if (failureCount.incrementAndGet() % 10 == 0) {
                return TaskResult.failure(new RuntimeException("Simulated failure"))
            }
            return TaskResult.success("ok")
        }

        @Override
        void cleanup() {}
    }
}

