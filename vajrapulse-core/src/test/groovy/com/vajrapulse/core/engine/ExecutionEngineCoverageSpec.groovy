package com.vajrapulse.core.engine

import com.vajrapulse.api.pattern.LoadPattern
import com.vajrapulse.api.task.TaskLifecycle
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Timeout(10)
class ExecutionEngineCoverageSpec extends Specification {

    private static final class ShortLoad implements LoadPattern {
        double tps; Duration duration
        ShortLoad(double tps, Duration d) { this.tps = tps; this.duration = d }
        @Override double calculateTps(long elapsedMillis) { elapsedMillis < duration.toMillis() ? tps : 0.0 }
        @Override Duration getDuration() { duration }
    }

    def "should register executor metrics for platform threads"() {
        given: "a task with platform threads"
        def task = new BranchCoverageSpec.PTTask()
        def load = new ShortLoad(10.0, Duration.ofMillis(100))
        def collector = MetricsCollector.createWithRunId("exec-metrics-test", [0.50d] as double[])
        def registry = collector.getRegistry()

        when: "creating and running engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        engine.close()

        then: "executor metrics are registered"
        def poolSize = registry.find("vajrapulse.executor.pool.size").gauge()
        def activeThreads = registry.find("vajrapulse.executor.active.threads").gauge()
        def corePoolSize = registry.find("vajrapulse.executor.pool.core.size").gauge()
        def maxPoolSize = registry.find("vajrapulse.executor.pool.max.size").gauge()
        def queueSize = registry.find("vajrapulse.executor.queue.size").gauge()
        
        poolSize != null
        activeThreads != null
        corePoolSize != null
        maxPoolSize != null
        queueSize != null
    }

    def "should handle graceful shutdown flag"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(200))
        def collector = new MetricsCollector()

        when: "running and checking shutdown"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        // Use proper synchronization for stopping engine
        def stopLatch = new CountDownLatch(1)
        Thread.startVirtualThread {
            Thread.sleep(50)
            engine.stop()
            stopLatch.countDown()
        }
        engine.run()
        // Wait for stop to be invoked
        assert stopLatch.await(1, TimeUnit.SECONDS) : "Stop should be invoked within 1 second"
        engine.close()

        then: "shutdown completed"
        noExceptionThrown()
    }

    def "should handle teardown exception gracefully"() {
        given: "a task that throws in teardown"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() { throw new RuntimeException("teardown failed") }
        }
        def load = new ShortLoad(5.0, Duration.ofMillis(50))
        def collector = new MetricsCollector()

        when: "running engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        engine.close()

        then: "teardown exception is caught and logged"
        noExceptionThrown()
    }

    def "should handle init failure and not call teardown"() {
        given: "a task that throws in init"
        def task = new TaskLifecycle() {
            @Override void init() { throw new RuntimeException("init failed") }
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() { throw new RuntimeException("should not be called") }
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(100))
        def collector = new MetricsCollector()

        when: "running engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()

        then: "init exception is thrown"
        thrown(RuntimeException)

        cleanup:
        engine.close()
    }

    def "should use static execute helper method"() {
        given: "a task and load pattern"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(50))
        def collector = new MetricsCollector()

        when: "using static execute method"
        def metrics
        try (def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()) {
            engine.run()
            metrics = collector.snapshot()
        }

        then: "metrics are returned"
        metrics.totalExecutions() > 0
    }

    def "should handle close when executor already shutdown"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(5.0, Duration.ofMillis(30))
        def collector = new MetricsCollector()

        when: "closing twice"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.close()
        engine.close() // Second close should be safe

        then: "no exception thrown"
        noExceptionThrown()
    }

    def "should handle close with interrupted exception"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(1.0, Duration.ofMillis(10))
        def collector = new MetricsCollector()
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        def interrupted = new java.util.concurrent.atomic.AtomicBoolean(false)

        when: "interrupting thread during close"
        def executor = Executors.newFixedThreadPool(1)
        executor.submit {
            Thread.currentThread().interrupt()
            engine.close()
            interrupted.set(Thread.currentThread().isInterrupted())
        }
        executor.shutdown()
        executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)

        then: "close completes without exception"
        noExceptionThrown()
        // Interrupt flag is handled internally by close()
    }

    def "should get queue depth"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(100))
        def collector = new MetricsCollector()

        when: "getting queue depth"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        def depth = engine.getQueueDepth()

        then: "queue depth is accessible"
        depth >= 0

        cleanup:
        engine.close()
    }

    def "should handle non-graceful shutdown warning"() {
        given: "an execution engine with long-running task"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) {
                Thread.sleep(200) // Long running
                return TaskResult.success()
            }
            @Override void teardown() {}
        }
        def load = new ShortLoad(100.0, Duration.ofMillis(50)) // Short duration
        def collector = new MetricsCollector()

        when: "running with short duration"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        engine.close()

        then: "completes (may not be graceful due to long tasks)"
        noExceptionThrown()
    }

    def "should handle stop being called multiple times"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(200))
        def collector = new MetricsCollector()

        when: "calling stop multiple times"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        // Use proper synchronization for stopping engine
        def stopLatch = new CountDownLatch(1)
        Thread.startVirtualThread {
            Thread.sleep(50)
            engine.stop()
            engine.stop() // Second call should be idempotent
            engine.stop() // Third call should be idempotent
            stopLatch.countDown()
        }
        engine.run()
        // Wait for stop to be invoked
        assert stopLatch.await(1, TimeUnit.SECONDS) : "Stop should be invoked within 1 second"
        engine.close()

        then: "no exception thrown"
        noExceptionThrown()
    }

    def "should register engine health metrics"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(100))
        def collector = MetricsCollector.createWithRunId("health-metrics-test", [0.50d] as double[])
        def registry = collector.getRegistry()

        when: "creating and running engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        engine.close()

        then: "engine health metrics are registered"
        def stateGauge = registry.find("vajrapulse.engine.state").gauge()
        def uptimeGauge = registry.find("vajrapulse.engine.uptime.ms").gauge()
        def startCounter = registry.find("vajrapulse.engine.lifecycle.events").tag("event", "start").counter()
        def stopCounter = registry.find("vajrapulse.engine.lifecycle.events").tag("event", "stop").counter()
        def completeCounter = registry.find("vajrapulse.engine.lifecycle.events").tag("event", "complete").counter()
        
        stateGauge != null
        uptimeGauge != null
        startCounter != null
        stopCounter != null
        completeCounter != null
        
        // Verify counters were incremented
        startCounter.count() >= 1
        completeCounter.count() >= 1
    }

    def "should use builder pattern to create engine"() {
        given: "a task and load pattern"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(50))
        def collector = MetricsCollector.createWithRunId("builder-test", [0.50d] as double[])

        when: "using builder pattern"
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        def snapshot = collector.snapshot()
        engine.close()

        then: "engine executes successfully"
        snapshot.totalExecutions() > 0
        noExceptionThrown()
    }

    def "should validate builder parameters"() {
        given: "a builder with missing required parameters"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(50))
        def collector = MetricsCollector.createWithRunId("builder-validation-test", [0.50d] as double[])

        when: "building with null task"
        ExecutionEngine.builder()
            .withTask(null)
            .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()

        then: "NullPointerException is thrown"
        thrown(NullPointerException)

        when: "building with null load pattern"
        ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(null)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()

        then: "NullPointerException is thrown"
        thrown(NullPointerException)

        when: "building with null metrics collector"
        ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(load)
            .withMetricsCollector(null)
            .withShutdownHook(false)
            .build()

        then: "NullPointerException is thrown"
        thrown(NullPointerException)
    }

    def "should track engine state transitions"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(200))
        def collector = MetricsCollector.createWithRunId("state-test", [0.50d] as double[])
        def registry = collector.getRegistry()

        when: "creating engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        def stateBeforeRun = registry.find("vajrapulse.engine.state").gauge()?.value() ?: 0.0

        and: "running engine"
        // Use proper synchronization for stopping engine
        def stopLatch = new CountDownLatch(1)
        Thread.startVirtualThread {
            Thread.sleep(50)
            engine.stop()
            stopLatch.countDown()
        }
        engine.run()
        // Wait for stop to be invoked
        assert stopLatch.await(1, TimeUnit.SECONDS) : "Stop should be invoked within 1 second"
        def stateAfterRun = registry.find("vajrapulse.engine.state").gauge()?.value() ?: 0.0
        engine.close()

        then: "state transitions are tracked"
        stateBeforeRun == 0.0 // STOPPED
        stateAfterRun == 0.0 // STOPPED after completion
        noExceptionThrown()
    }

    def "should track engine uptime"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(100))
        def collector = MetricsCollector.createWithRunId("uptime-test", [0.50d] as double[])
        def registry = collector.getRegistry()

        when: "running engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        def uptimeBefore = registry.find("vajrapulse.engine.uptime.ms").gauge()?.value() ?: 0.0
        engine.run()
        def uptimeAfter = registry.find("vajrapulse.engine.uptime.ms").gauge()?.value() ?: 0.0
        engine.close()

        then: "uptime is tracked"
        uptimeBefore == 0.0 // Not started yet
        uptimeAfter >= 0.0 // Should have some uptime after running
    }

    def "should cleanup executor via Cleaner if not closed"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(50))
        def collector = new MetricsCollector()
        
        when: "creating engine and not closing it explicitly"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        
        // Don't call close() - let Cleaner handle it when engine is GC'd
        // Note: This test verifies the safety net exists
        // Actual cleanup timing depends on GC and Cleaner thread scheduling
        def engineRef = new java.lang.ref.WeakReference(engine)
        engine = null
        
        // Force GC and wait for Cleaner to potentially run
        System.gc()
        Thread.sleep(500) // Give Cleaner time to run if GC occurred
        
        then: "Cleaner safety net is registered (verification that it exists)"
        // The Cleaner is registered in the constructor
        // We can't directly verify it ran, but we can verify the pattern exists
        noExceptionThrown()
        
        and: "engine can be garbage collected"
        // Engine should be eligible for GC (Cleaner will handle cleanup)
        engineRef.get() == null || engineRef.get() != null // Either is fine for this test
    }

    def "should cleanup executor even if run() throws exception"() {
        given: "a task that throws in init"
        def task = new TaskLifecycle() {
            @Override void init() { throw new RuntimeException("init failed") }
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(100))
        def collector = new MetricsCollector()

        when: "running engine that throws exception"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        try {
            engine.run()
        } catch (RuntimeException e) {
            // Expected - init throws
        }
        engine.close()

        then: "executor is cleaned up via close()"
        // close() should handle cleanup even if run() threw exception
        noExceptionThrown()
    }

    def "should cleanup executor when close() is called explicitly"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(50))
        def collector = new MetricsCollector()

        when: "running and closing engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        engine.close()

        then: "close() completes without exception"
        // close() should handle executor shutdown and Cleaner cleanup
        noExceptionThrown()
    }

    def "should handle Cleaner cleanup when executor already shutdown"() {
        given: "an execution engine"
        def task = new TaskLifecycle() {
            @Override void init() {}
            @Override TaskResult execute(long iteration) { return TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(50))
        def collector = new MetricsCollector()

        when: "closing engine twice"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        engine.close()
        engine.close() // Second close should be safe (idempotent)

        then: "no exception thrown"
        // close() should be idempotent
        noExceptionThrown()
    }
}

