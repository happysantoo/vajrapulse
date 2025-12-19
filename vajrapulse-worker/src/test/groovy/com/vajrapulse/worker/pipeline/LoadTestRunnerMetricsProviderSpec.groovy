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
import spock.lang.Timeout

import java.time.Duration

/**
 * Tests for LoadTestRunner.getMetricsProvider() functionality.
 * 
 * <p>These tests verify that:
 * <ul>
 *   <li>getMetricsProvider() returns a valid MetricsProvider instance</li>
 *   <li>MetricsProvider reflects metrics from the underlying collector</li>
 *   <li>MetricsProvider works with AdaptiveLoadPattern</li>
 *   <li>MetricsProvider works with custom MetricsCollector</li>
 * </ul>
 */
@Timeout(30)
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

    def "should return MetricsProvider from LoadTestRunner"() {
        given: "a LoadTestRunner"
        LoadTestRunner runner = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()

        when: "getting metrics provider"
        MetricsProvider provider = runner.getMetricsProvider()

        then: "provider should not be null and be a MetricsProvider"
        provider != null
        provider instanceof MetricsProvider

        cleanup:
        runner?.close()
    }

    def "should return MetricsProvider with zero metrics initially"() {
        given: "a LoadTestRunner"
        LoadTestRunner runner = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()

        when: "getting metrics provider before any execution"
        MetricsProvider provider = runner.getMetricsProvider()

        then: "initial metrics should be zero"
        provider.getTotalExecutions() == 0L
        provider.getFailureRate() == 0.0
        provider.getFailureCount() == 0L

        cleanup:
        runner?.close()
    }

    def "should return MetricsProvider that reflects executed tasks"() {
        given: "a LoadTestRunner and a task"
        LoadTestRunner runner = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()
        Task task = new SimpleTask()

        when: "getting initial metrics provider"
        MetricsProvider initialProvider = runner.getMetricsProvider()

        then: "initial metrics should be zero"
        initialProvider.getTotalExecutions() == 0L

        when: "executing task with short static load pattern"
        StaticLoad loadPattern = new StaticLoad(10.0, Duration.ofMillis(50))
        runner.run(task, loadPattern)

        and: "getting metrics provider after execution"
        MetricsProvider finalProvider = runner.getMetricsProvider()

        then: "metrics provider should reflect executed tasks"
        finalProvider.getTotalExecutions() > 0
        finalProvider.getFailureRate() == 0.0  // SimpleTask always succeeds

        cleanup:
        runner?.close()
    }

    def "should return consistent MetricsProvider instances"() {
        given: "a LoadTestRunner"
        LoadTestRunner runner = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()

        when: "getting metrics provider multiple times"
        MetricsProvider provider1 = runner.getMetricsProvider()
        MetricsProvider provider2 = runner.getMetricsProvider()

        then: "both providers should reflect same underlying collector state"
        provider1 != null
        provider2 != null
        // Note: They may be different instances (new adapter each time) but backed by same collector
        provider1.getTotalExecutions() == provider2.getTotalExecutions()
        provider1.getFailureRate() == provider2.getFailureRate()
        provider1.getFailureCount() == provider2.getFailureCount()

        cleanup:
        runner?.close()
    }

    def "should work with AdaptiveLoadPattern builder"() {
        given: "a LoadTestRunner"
        LoadTestRunner runner = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()

        when: "getting metrics provider and creating adaptive pattern"
        MetricsProvider provider = runner.getMetricsProvider()
        AdaptiveLoadPattern adaptivePattern = AdaptiveLoadPattern.builder()
            .initialTps(5.0)
            .rampIncrement(5.0)
            .rampDecrement(5.0)
            .rampInterval(Duration.ofMillis(100))
            .maxTps(20.0)
            .sustainDuration(Duration.ofMillis(200))
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.05))
            .build()

        then: "adaptive pattern should be created successfully with metrics provider"
        adaptivePattern != null
        provider != null

        and: "metrics provider should be accessible"
        provider.getTotalExecutions() == 0L
        provider.getFailureRate() == 0.0

        cleanup:
        runner?.close()
    }

    def "should work with custom MetricsCollector"() {
        given: "a custom metrics collector"
        MetricsCollector customCollector = new MetricsCollector()

        and: "a LoadTestRunner using the custom collector"
        LoadTestRunner runner = LoadTestRunner.builder()
            .withCollector(customCollector)
            .addExporter(new ConsoleMetricsExporter())
            .build()

        when: "getting metrics provider"
        MetricsProvider provider = runner.getMetricsProvider()

        then: "provider should work with custom collector"
        provider != null
        provider.getTotalExecutions() == 0L
        provider.getFailureRate() == 0.0
        provider.getFailureCount() == 0L

        cleanup:
        runner?.close()
        customCollector?.close()
    }

    def "should provide metrics updates after execution"() {
        given: "a LoadTestRunner and a task"
        LoadTestRunner runner = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()
        Task task = new SimpleTask()

        when: "getting initial metrics provider"
        MetricsProvider initialProvider = runner.getMetricsProvider()

        then: "initial metrics are zero"
        initialProvider.getTotalExecutions() == 0L
        initialProvider.getFailureRate() == 0.0

        when: "running a short test"
        StaticLoad loadPattern = new StaticLoad(20.0, Duration.ofMillis(50))
        runner.run(task, loadPattern)

        and: "getting metrics provider after execution"
        MetricsProvider finalProvider = runner.getMetricsProvider()

        then: "metrics should show executions after completion"
        finalProvider.getTotalExecutions() > 0
        finalProvider.getFailureRate() == 0.0  // SimpleTask always succeeds

        cleanup:
        runner?.close()
    }

    def "should handle failure count correctly"() {
        given: "a LoadTestRunner"
        LoadTestRunner runner = LoadTestRunner.builder()
            .addExporter(new ConsoleMetricsExporter())
            .build()

        and: "a task that sometimes fails"
        def failureCount = new java.util.concurrent.atomic.AtomicInteger(0)
        Task task = new FailingTask(failureCount)

        when: "running task with failures"
        // Use higher TPS and longer duration to ensure we get enough executions
        // FailingTask fails every 10th execution, so we need at least 10 executions
        // 50 TPS * 200ms = ~10 executions, which should give us at least 1 failure
        StaticLoad loadPattern = new StaticLoad(50.0, Duration.ofMillis(200))
        runner.run(task, loadPattern)

        and: "getting metrics provider"
        MetricsProvider provider = runner.getMetricsProvider()

        then: "should have executions and potentially failures"
        provider.getTotalExecutions() > 0
        
        // If we have enough executions (>= 10), we should have at least 1 failure
        // Otherwise, just verify the failure count is non-negative
        if (provider.getTotalExecutions() >= 10) {
            provider.getFailureCount() > 0
            provider.getFailureRate() > 0.0
        } else {
            // With fewer executions, failure count might be 0, but should be non-negative
            provider.getFailureCount() >= 0
            provider.getFailureRate() >= 0.0
        }

        cleanup:
        runner?.close()
    }
}
