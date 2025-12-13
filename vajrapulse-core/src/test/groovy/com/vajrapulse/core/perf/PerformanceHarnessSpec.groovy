package com.vajrapulse.core.perf

import com.vajrapulse.api.pattern.StaticLoad
import com.vajrapulse.api.task.TaskLifecycle
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.api.task.VirtualThreads
import com.vajrapulse.core.engine.ExecutionEngine
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification

import java.time.Duration

class PerformanceHarnessSpec extends Specification {

    @VirtualThreads
    static class NoOpTask implements TaskLifecycle {
        @Override void init() { }
        @Override TaskResult execute(long iteration) { TaskResult.success(null) }
        @Override void teardown() { }
    }

    def "engine achieves reasonable throughput for no-op task"() {
        given:
        def tps = 1000d
        def pattern = new StaticLoad(tps, Duration.ofSeconds(1))
        def collector = MetricsCollector.createWithRunId("perf-test", new double[]{0.5})

        when:
        try (def engine = ExecutionEngine.builder()
                .withTask(new NoOpTask())
                .withLoadPattern(pattern)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()) {
            engine.run()
        }
        def snapshot = collector.snapshot()

        then:
        // Allow headroom; scheduler + thread setup may reduce total slightly.
        snapshot.totalExecutions() >= 800
        snapshot.failureCount() == 0
    }
}
