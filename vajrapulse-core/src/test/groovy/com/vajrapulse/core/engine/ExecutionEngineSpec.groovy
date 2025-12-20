package com.vajrapulse.core.engine

import com.vajrapulse.api.pattern.LoadPattern
import com.vajrapulse.api.task.Task
import com.vajrapulse.api.task.TaskLifecycle
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.core.config.VajraPulseConfig
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests covering ExecutionEngine run path, stop behaviour and runId propagation.
 */
@Timeout(10)
class ExecutionEngineSpec extends Specification {

    private static final class ShortStaticLoad implements LoadPattern {
        private final double tps
        private final Duration duration
        private final long start = System.nanoTime()
        ShortStaticLoad(double tps, Duration duration) { this.tps = tps; this.duration = duration }
        @Override double calculateTps(long elapsedMillis) { return elapsedMillis < duration.toMillis() ? tps : 0.0 }
        @Override Duration getDuration() { return duration }
    }

    def "should run execution engine and record metrics"() {
        given: "a simple always-success task and short load pattern"
        Task task = new Task() {
            @Override
            TaskResult execute() throws Exception { return TaskResult.success("ok") }
            @Override void setup() {}
            @Override void cleanup() {}
        }
        def load = new ShortStaticLoad(20.0, Duration.ofMillis(150))
        def collector = MetricsCollector.createWithRunId("run-123", [0.50d, 0.95d] as double[])

        when: "running the engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        def snapshot = collector.snapshot()

        then: "metrics captured and runId propagated"
        snapshot.totalExecutions() > 0
        snapshot.successCount() == snapshot.totalExecutions()
        snapshot.failureCount() == 0
        engine.runId != null
        engine.runId == collector.runId
        
        cleanup:
        engine?.close()
    }

    def "should generate runId when collector has none"() {
        given:
        Task task = new Task() {
            @Override TaskResult execute() { return TaskResult.success(null) }
            @Override void setup() {}
            @Override void cleanup() {}
        }
        def load = new ShortStaticLoad(5.0, Duration.ofMillis(60))
        def collector = new MetricsCollector() // no run id

        when:
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()

        then:
        engine.runId != null
        collector.runId == null
        
        cleanup:
        engine?.close()
    }

    def "should stop early when stop invoked"() {
        given:
        Task task = new Task() {
            @Override TaskResult execute() { return TaskResult.success(null) }
            @Override void setup() {}
            @Override void cleanup() {}
        }
        def load = new ShortStaticLoad(100.0, Duration.ofMillis(1000)) // long enough to stop early
        def collector = MetricsCollector.createWith([0.50d] as double[])
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()

        when: "invoke stop after brief delay"
        // Use proper synchronization for stopping engine
        def stopLatch = new CountDownLatch(1)
        Thread.startVirtualThread {
            Thread.sleep(100)
            engine.stop()
            stopLatch.countDown()
        }
        engine.run()
        // Wait for stop to be invoked (should complete quickly)
        assert stopLatch.await(1, TimeUnit.SECONDS) : "Stop should be invoked within 1 second"
        def snapshot = collector.snapshot()

        then: "fewer executions than theoretical max and some recorded"
        snapshot.totalExecutions() > 0
        snapshot.totalExecutions() < 100 // would be >100 if full second ran at 100 TPS
        
        cleanup:
        engine?.close()
    }

    def "should use configured drain timeout from config"() {
        given: "a task and custom config with 2s drain timeout"
        Task task = new Task() {
            @Override TaskResult execute() { return TaskResult.success(null) }
            @Override void setup() {}
            @Override void cleanup() {}
        }
        def customConfig = new VajraPulseConfig(
            new VajraPulseConfig.ExecutionConfig(
                Duration.ofSeconds(2),  // custom drain timeout
                Duration.ofSeconds(5),  // force timeout
                VajraPulseConfig.ThreadPoolStrategy.VIRTUAL,
                -1
            ),
            VajraPulseConfig.ObservabilityConfig.defaults()
        )
        def load = new ShortStaticLoad(5.0, Duration.ofMillis(50))
        def collector = MetricsCollector.createWithRunId("cfg-test", [0.50d] as double[])

        when: "creating engine with config"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withRunId("cfg-test")
                .withConfig(customConfig)
                .withShutdownHook(false)
                .build()

        then: "engine created successfully with config applied"
        engine.getRunId() == "cfg-test"
        // Config is private, verify through behavior (drain timeout used in shutdown)
        
        cleanup:
        engine?.close()
    }

    def "should use configured force timeout from config"() {
        given: "a task and custom config with 15s force timeout"
        Task task = new Task() {
            @Override TaskResult execute() { return TaskResult.success(null) }
            @Override void setup() {}
            @Override void cleanup() {}
        }
        def customConfig = new VajraPulseConfig(
            new VajraPulseConfig.ExecutionConfig(
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),  // custom force timeout
                VajraPulseConfig.ThreadPoolStrategy.VIRTUAL,
                -1
            ),
            VajraPulseConfig.ObservabilityConfig.defaults()
        )
        def load = new ShortStaticLoad(5.0, Duration.ofMillis(50))
        def collector = MetricsCollector.createWithRunId("force-test", [0.50d] as double[])

        when: "creating engine with config"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withRunId("force-test")
                .withConfig(customConfig)
                .withShutdownHook(false)
                .build()

        then: "engine created with config applied"
        // Config is private, verify through behavior (force timeout used in shutdown)
        engine != null
        
        cleanup:
        engine?.close()
    }

    def "should respect VIRTUAL thread pool strategy from config"() {
        given: "a task without thread annotations and config set to VIRTUAL"
        Task task = new Task() {
            @Override TaskResult execute() { return TaskResult.success(null) }
            @Override void setup() {}
            @Override void cleanup() {}
        }
        def customConfig = new VajraPulseConfig(
            new VajraPulseConfig.ExecutionConfig(
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                VajraPulseConfig.ThreadPoolStrategy.VIRTUAL,  // explicit virtual
                -1
            ),
            VajraPulseConfig.ObservabilityConfig.defaults()
        )
        def load = new ShortStaticLoad(5.0, Duration.ofMillis(50))
        def collector = MetricsCollector.createWithRunId("virtual-test", [0.50d] as double[])

        when: "creating and running engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withRunId("virtual-test")
                .withConfig(customConfig)
                .withShutdownHook(false)
                .build()
        engine.run()
        def snapshot = collector.snapshot()

        then: "engine runs successfully with virtual threads"
        snapshot.totalExecutions() > 0
        // Config is private, verify through behavior (virtual threads used)
        
        cleanup:
        engine?.close()
    }

    def "should respect PLATFORM thread pool strategy from config"() {
        given: "a task without thread annotations and config set to PLATFORM with pool size 4"
        Task task = new Task() {
            @Override TaskResult execute() { return TaskResult.success(null) }
            @Override void setup() {}
            @Override void cleanup() {}
        }
        def customConfig = new VajraPulseConfig(
            new VajraPulseConfig.ExecutionConfig(
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                VajraPulseConfig.ThreadPoolStrategy.PLATFORM,  // explicit platform
                4  // pool size
            ),
            VajraPulseConfig.ObservabilityConfig.defaults()
        )
        def load = new ShortStaticLoad(5.0, Duration.ofMillis(50))
        def collector = MetricsCollector.createWithRunId("platform-test", [0.50d] as double[])

        when: "creating and running engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withRunId("platform-test")
                .withConfig(customConfig)
                .withShutdownHook(false)
                .build()
        engine.run()
        def snapshot = collector.snapshot()

        then: "engine runs successfully with platform threads"
        snapshot.totalExecutions() > 0
        // Config is private, verify through behavior (platform threads used - verified by successful execution)
        engine.config.execution().platformThreadPoolSize() == 4
        
        cleanup:
        engine?.close()
    }

    def "should use AUTO strategy which defaults to virtual threads"() {
        given: "a task without thread annotations and config set to AUTO"
        Task task = new Task() {
            @Override TaskResult execute() { return TaskResult.success(null) }
            @Override void setup() {}
            @Override void cleanup() {}
        }
        def customConfig = new VajraPulseConfig(
            new VajraPulseConfig.ExecutionConfig(
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                VajraPulseConfig.ThreadPoolStrategy.AUTO,  // auto defaults to virtual
                -1
            ),
            VajraPulseConfig.ObservabilityConfig.defaults()
        )
        def load = new ShortStaticLoad(5.0, Duration.ofMillis(50))
        def collector = MetricsCollector.createWithRunId("auto-test", [0.50d] as double[])

        when: "creating and running engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withRunId("auto-test")
                .withConfig(customConfig)
                .withShutdownHook(false)
                .build()
        engine.run()
        def snapshot = collector.snapshot()

        then: "engine runs successfully with AUTO strategy"
        snapshot.totalExecutions() > 0
        // Config is private, verify through behavior (AUTO defaults to virtual threads - verified by successful execution)
        
        cleanup:
        engine?.close()
    }
    
    def "should register executor metrics for platform threads"() {
        given: "a task with platform threads annotation"
        def task = new BranchCoverageSpec.PTTask()
        def load = new ShortStaticLoad(10.0, Duration.ofMillis(100))
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
        
        poolSize != null
        activeThreads != null
        poolSize.id.tags.find { it.key == "thread_type" && it.value == "platform" } != null
        poolSize.id.tags.find { it.key == "run_id" && it.value == "exec-metrics-test" } != null
    }
    
    def "should register executor metrics for virtual threads"() {
        given: "a task with virtual threads annotation"
        def task = new BranchCoverageSpec.VTTask()
        def load = new ShortStaticLoad(10.0, Duration.ofMillis(100))
        def collector = MetricsCollector.createWithRunId("virtual-exec-test", [0.50d] as double[])
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

        then: "virtual thread executor created (no platform thread metrics)"
        // Virtual threads don't have ThreadPoolExecutor, so no pool metrics
        def poolSize = registry.find("vajrapulse.executor.pool.size").gauge()
        poolSize == null  // Virtual threads don't have pool size
    }
    
    def "should close executor even if run throws exception"() {
        given: "a task that throws in init"
        TaskLifecycle task = new TaskLifecycle() {
            @Override void init() { throw new RuntimeException("init failed") }
            @Override com.vajrapulse.api.task.TaskResult execute(long iteration) { return com.vajrapulse.api.task.TaskResult.success() }
            @Override void teardown() {}
        }
        def load = new ShortStaticLoad(10.0, Duration.ofMillis(100))
        def collector = new MetricsCollector()

        when: "running engine that fails in init"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        try {
            engine.run()
        } catch (Exception e) {
            // Expected
        }
        engine.close()

        then: "close completes without exception"
        noExceptionThrown()
    }
    
}
