package com.vajrapulse.core.engine

import com.vajrapulse.api.PlatformThreads
import com.vajrapulse.api.VirtualThreads
import com.vajrapulse.api.LoadPattern
import com.vajrapulse.api.TaskLifecycle
import com.vajrapulse.api.TaskResult
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
        def engine = new ExecutionEngine(task, load, collector)
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
        def engine = new ExecutionEngine(task, load, collector)
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
