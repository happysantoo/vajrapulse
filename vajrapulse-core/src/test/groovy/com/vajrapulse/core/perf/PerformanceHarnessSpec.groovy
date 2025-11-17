package com.vajrapulse.core.perf

import com.vajrapulse.api.StaticLoad
import com.vajrapulse.api.TaskLifecycle
import com.vajrapulse.api.TaskResult
import com.vajrapulse.api.VirtualThreads
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
        try (def engine = new ExecutionEngine(new NoOpTask(), pattern, collector)) {
            engine.run()
        }
        def snapshot = collector.snapshot()

        then:
        // Allow headroom; scheduler + thread setup may reduce total slightly.
        snapshot.totalExecutions() >= 800
        snapshot.failureCount() == 0
    }
}
