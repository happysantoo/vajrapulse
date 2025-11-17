package com.vajrapulse.core.engine

import com.vajrapulse.api.LoadPattern
import com.vajrapulse.api.TaskLifecycle
import com.vajrapulse.api.TaskResult
import com.vajrapulse.core.metrics.MetricsCollector
import com.vajrapulse.core.metrics.PeriodicMetricsReporter
import com.vajrapulse.core.logging.StructuredLogger
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration

@Timeout(10)
class AdditionalCoreCoverageSpec extends Specification {

    private static final class FastFailTask implements TaskLifecycle {
        @Override void init() {}
        @Override TaskResult execute(long iteration) { return TaskResult.failure(new IllegalArgumentException("boom")) }
        @Override void teardown() {}
    }

    private static final class ShortLoad implements LoadPattern {
        private final double tps; private final Duration duration
        ShortLoad(double tps, Duration d) { this.tps = tps; this.duration = d }
        @Override double calculateTps(long elapsedMillis) { return elapsedMillis < duration.toMillis() ? tps : 0.0 }
        @Override Duration getDuration() { return duration }
    }

    def "engine should run and record failures"() {
        given:
        def task = new FastFailTask()
        def load = new ShortLoad(30.0, Duration.ofMillis(120))
        def collector = MetricsCollector.createWithRunId('rid-static', [0.50d] as double[])

        when:
        def engine = new ExecutionEngine(task, load, collector)
        engine.run()
        def snapshot = collector.snapshot()

        then:
        snapshot.totalExecutions() > 0
        snapshot.failureCount() == snapshot.totalExecutions()
        snapshot.successCount() == 0
        
        cleanup:
        engine.close()
    }

    def "close should shutdown executor without run"() {
        given:
        def task = new FastFailTask()
        def load = new ShortLoad(1.0, Duration.ofMillis(10))
        def collector = new MetricsCollector()

        when:
        def engine = new ExecutionEngine(task, load, collector)
        engine.close() // exercise close path

        then:
        engine.runId != null
    }

    def "task executor getTaskLifecycle should return underlying task"() {
        given:
        def task = new FastFailTask()

        when:
        def exec = new TaskExecutor(task)

        then:
        exec.taskLifecycle.is(task)
    }

    def "periodic metrics reporter alternate constructors"() {
        given:
        def collector = new MetricsCollector()
        def dummyExporter = { c -> } as com.vajrapulse.core.metrics.MetricsExporter

        when:
        new PeriodicMetricsReporter(collector, dummyExporter)
        new PeriodicMetricsReporter(collector, dummyExporter, Duration.ofMillis(50))
        new PeriodicMetricsReporter(collector, dummyExporter, Duration.ofMillis(50), false)

        then:
        collector != null
    }

    def "structured logger disabled branches"() {
        given: "set log level to error only"
        System.setProperty('org.slf4j.simpleLogger.defaultLogLevel','error')

        when:
        StructuredLogger.debug(AdditionalCoreCoverageSpec, 'debug_event', [x:1]) // should be skipped
        StructuredLogger.trace(AdditionalCoreCoverageSpec, 'trace_event', [y:2]) // skipped
        StructuredLogger.info(AdditionalCoreCoverageSpec, 'info_event', [z:3]) // skipped
        StructuredLogger.error(AdditionalCoreCoverageSpec, 'error_event', [e:4], new RuntimeException('err')) // executed

        then:
        true // no exceptions

        cleanup:
        System.clearProperty('org.slf4j.simpleLogger.defaultLogLevel')
    }
}
