package com.vajrapulse.core.integration

import com.vajrapulse.api.pattern.StaticLoad
import com.vajrapulse.api.task.TaskLifecycle
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.core.engine.ExecutionEngine
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

import static org.awaitility.Awaitility.*

/**
 * Integration tests for graceful shutdown scenarios.
 *
 * <p>Verifies that shutdown completes gracefully in various scenarios:
 * <ul>
 *   <li>Manual stop() call during active load</li>
 *   <li>Shutdown hook triggered (simulated SIGINT/SIGTERM)</li>
 *   <li>Final metrics snapshot completeness</li>
 *   <li>Shutdown timing (completes within 5s)</li>
 * </ul>
 */
@Timeout(30)
class GracefulShutdownSpec extends Specification {

    /**
     * Runs an engine with the given task and load, stops it after {@code runBeforeStopMs},
     * and returns the shutdown result for assertion.
     */
    private def runAndStop(TaskLifecycle task, StaticLoad load, long runBeforeStopMs, long joinTimeoutMs = 5000) {
        def collector = new MetricsCollector()
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()

        def startTime = System.currentTimeMillis()
        def stopLatch = new CountDownLatch(1)

        def engineThread = Thread.startVirtualThread {
            try {
                engine.run()
            } catch (Exception ignored) {
                // Expected when stopped
            }
        }

        Thread.startVirtualThread {
            Thread.sleep(runBeforeStopMs)
            engine.stop()
            stopLatch.countDown()
        }

        assert stopLatch.await(2, TimeUnit.SECONDS) : "Stop should be invoked"
        engineThread.join(joinTimeoutMs)
        def shutdownDuration = System.currentTimeMillis() - startTime

        def metrics = collector.snapshot()
        engine.close()

        return [durationMs: shutdownDuration, metrics: metrics, engineStillAlive: engineThread.alive]
    }

    def "should shutdown gracefully on manual stop() call"() {
        given: "a long-running load test"
        def executionCount = new AtomicInteger(0)
        def teardownCalled = new AtomicBoolean(false)

        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {}
            @Override
            TaskResult execute(long iteration) throws Exception {
                executionCount.incrementAndGet()
                Thread.sleep(1)
                return TaskResult.success()
            }
            @Override
            void teardown() throws Exception {
                teardownCalled.set(true)
            }
        }

        def load = new StaticLoad(100.0, Duration.ofSeconds(10))

        when: "starting engine and stopping after 200ms"
        def result = runAndStop(task, load, 200)

        then: "shutdown completes gracefully within 5s"
        result.durationMs < 5000
        !result.engineStillAlive
        executionCount.get() > 0
        teardownCalled.get()
        result.metrics.totalExecutions() > 0
        result.metrics.totalExecutions() == executionCount.get()
        result.metrics.successCount() == executionCount.get()
    }

    def "should complete shutdown within 5 seconds"() {
        given: "a load test with active executions"
        def executionCount = new AtomicInteger(0)
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {}
            @Override
            TaskResult execute(long iteration) throws Exception {
                executionCount.incrementAndGet()
                Thread.sleep(5)
                return TaskResult.success()
            }
            @Override
            void teardown() throws Exception {}
        }

        def load = new StaticLoad(50.0, Duration.ofSeconds(5))

        when: "stopping engine after 100ms"
        def result = runAndStop(task, load, 100, 6000)

        then: "shutdown completes within 5 seconds"
        result.durationMs < 5000
        executionCount.get() > 0
        result.metrics.totalExecutions() > 0
    }

    def "should have complete final metrics snapshot after shutdown"() {
        given: "a load test"
        def executionCount = new AtomicInteger(0)
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {}
            @Override
            TaskResult execute(long iteration) throws Exception {
                executionCount.incrementAndGet()
                return TaskResult.success()
            }
            @Override
            void teardown() throws Exception {}
        }

        def load = new StaticLoad(100.0, Duration.ofMillis(300))

        when: "running and stopping engine after 100ms"
        def result = runAndStop(task, load, 100)

        then: "final metrics snapshot is complete"
        result.metrics.totalExecutions() > 0
        result.metrics.totalExecutions() == executionCount.get()
        result.metrics.successCount() == executionCount.get()
        result.metrics.failureCount() == 0
        result.metrics.successPercentiles() != null
        result.metrics.elapsedMillis() > 0
    }

    def "should call teardown even when stopped early"() {
        given: "a task that tracks teardown"
        def teardownCalled = new AtomicBoolean(false)
        def initCalled = new AtomicBoolean(false)

        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {
                initCalled.set(true)
            }
            @Override
            TaskResult execute(long iteration) throws Exception {
                return TaskResult.success()
            }
            @Override
            void teardown() throws Exception {
                teardownCalled.set(true)
            }
        }

        def load = new StaticLoad(10.0, Duration.ofSeconds(10))

        when: "waiting for init then stopping immediately"
        def collector = new MetricsCollector()
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()

        def engineThread = Thread.startVirtualThread {
            try { engine.run() } catch (Exception ignored) {}
        }

        // Wait for init asynchronously before stopping
        await().atMost(2, TimeUnit.SECONDS).until { initCalled.get() }
        engine.stop()
        engineThread.join(5000)
        engine.close()

        then: "teardown is called"
        initCalled.get()
        teardownCalled.get()
    }

    def "should handle shutdown during high concurrency"() {
        given: "a high-concurrency load test"
        def executionCount = new AtomicInteger(0)
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {}
            @Override
            TaskResult execute(long iteration) throws Exception {
                executionCount.incrementAndGet()
                Thread.sleep(10)
                return TaskResult.success()
            }
            @Override
            void teardown() throws Exception {}
        }

        def load = new StaticLoad(200.0, Duration.ofSeconds(5))

        when: "stopping after 200ms during high concurrency"
        def result = runAndStop(task, load, 200, 6000)

        then: "shutdown completes gracefully"
        result.durationMs < 5000
        executionCount.get() > 0
        result.metrics.totalExecutions() > 0
        result.metrics.totalExecutions() <= executionCount.get() + 50
    }

    def "should not record metrics after shutdown initiated"() {
        given: "a load test"
        def executionCount = new AtomicInteger(0)

        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {}
            @Override
            TaskResult execute(long iteration) throws Exception {
                executionCount.incrementAndGet()
                return TaskResult.success()
            }
            @Override
            void teardown() throws Exception {}
        }

        def load = new StaticLoad(100.0, Duration.ofSeconds(5))

        when: "stopping and checking metrics"
        def result = runAndStop(task, load, 100)

        then: "metrics don't increase significantly after stop"
        result.metrics.totalExecutions() > 0
        result.metrics.totalExecutions() <= executionCount.get() + 10
    }
}
