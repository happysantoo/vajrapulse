package com.vajrapulse.core.integration

import com.vajrapulse.api.pattern.StaticLoad
import com.vajrapulse.api.task.TaskLifecycle
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.api.task.VirtualThreads
import com.vajrapulse.core.config.VajraPulseConfig
import com.vajrapulse.core.engine.ExecutionEngine
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verifies behavior when the drain timeout is exceeded and {@link java.util.concurrent.ExecutorService#shutdownNow()}
 * is used: the engine must still terminate and {@code queueSize} in {@link com.vajrapulse.core.metrics.AggregatedMetrics}
 * must remain bounded (best-effort gauge semantics).
 */
@Timeout(60)
class ForcedShutdownQueueGaugeSpec extends Specification {

    def "terminates after forced shutdown with bounded queue gauge snapshot"() {
        given: "short drain and force windows so hung tasks trigger shutdownNow"
        def exec = new VajraPulseConfig.ExecutionConfig(
                Duration.ofMillis(200),
                Duration.ofMillis(400),
                VajraPulseConfig.ThreadPoolStrategy.VIRTUAL,
                -1)
        def config = new VajraPulseConfig(exec, VajraPulseConfig.ObservabilityConfig.defaults())
        def collector = new MetricsCollector()
        def teardownCalled = new AtomicBoolean(false)
        TaskLifecycle task = new BlockingTask(teardownCalled)
        def load = new StaticLoad(400.0, Duration.ofSeconds(120))
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withConfig(config)
                .withShutdownHook(false)
                .build()

        when: "engine runs, stop is requested while many long-running tasks are in flight"
        def engineThread = Thread.startVirtualThread { engine.run() }
        // Allow engine to start and accumulate tasks before stopping.
        // BlockingTask never completes (sleeps 600s), so use a fixed delay
        // past the drain timeout (200ms) to ensure shutdownNow is triggered.
        Thread.sleep(500)
        engine.stop()
        engineThread.join(45_000)
        def snap = collector.snapshot()
        engine.close()

        then: "run thread finished, teardown ran, queue snapshot is non-negative and bounded"
        !engineThread.alive
        teardownCalled.get()
        snap.queueSize() >= 0L
        snap.queueSize() < 500_000L
    }

    /**
     * Task that stays in {@code execute} long enough to overlap drain timeout.
     */
    @VirtualThreads
    static final class BlockingTask implements TaskLifecycle {
        private final AtomicBoolean teardownCalled

        BlockingTask(AtomicBoolean teardownCalled) {
            this.teardownCalled = teardownCalled
        }

        @Override
        void init() throws Exception {
        }

        @Override
        TaskResult execute(long iteration) throws Exception {
            try {
                Thread.sleep(600_000)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt()
                return TaskResult.failure(e)
            }
            return TaskResult.success()
        }

        @Override
        void teardown() throws Exception {
            teardownCalled.set(true)
        }
    }
}
