package com.vajrapulse.core.engine

import com.vajrapulse.api.task.PlatformThreads
import com.vajrapulse.api.task.VirtualThreads
import com.vajrapulse.api.pattern.LoadPattern
import com.vajrapulse.api.task.TaskLifecycle
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.core.metrics.MetricsCollector
import com.vajrapulse.core.tracing.Tracing
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration

@Timeout(10)
class BranchCoverageSpec extends Specification {

    private static final class ShortLoad implements LoadPattern {
        double tps; Duration duration
        ShortLoad(double tps, Duration d) { this.tps = tps; this.duration = d }
        @Override double calculateTps(long elapsedMillis) { elapsedMillis < duration.toMillis() ? tps : 0.0 }
        @Override Duration getDuration() { duration }
    }

    @VirtualThreads
    static final class VTTask implements TaskLifecycle {
        @Override void init() {}
        @Override TaskResult execute(long iteration) { TaskResult.success("vt") }
        @Override void teardown() {}
    }

    @PlatformThreads(poolSize = 2)
    static final class PTTask implements TaskLifecycle {
        @Override void init() {}
        @Override TaskResult execute(long iteration) { TaskResult.success("pt") }
        @Override void teardown() {}
    }

    def "virtual threads annotation path"() {
        given:
        def task = new VTTask()
        def load = new ShortLoad(10.0, Duration.ofMillis(80))
        def collector = new MetricsCollector()

        when:
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run(); engine.close()

        then:
        collector.snapshot().totalExecutions() > 0
    }

    def "platform threads annotation path"() {
        given:
        def task = new PTTask()
        def load = new ShortLoad(10.0, Duration.ofMillis(80))
        def collector = new MetricsCollector()

        when:
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run(); engine.close()

        then:
        collector.snapshot().totalExecutions() > 0
    }

    def "task executor tracing branch"() {
        given:
        System.setProperty('vajrapulse.trace.enabled','true')
        Tracing.initIfEnabled('trace-run')
        def task = new VTTask()
        def executor = new TaskExecutor(task)

        when:
        def metrics = executor.executeWithMetrics(5)

        then:
        metrics.isSuccess()
        cleanup:
        System.clearProperty('vajrapulse.trace.enabled')
    }
}
