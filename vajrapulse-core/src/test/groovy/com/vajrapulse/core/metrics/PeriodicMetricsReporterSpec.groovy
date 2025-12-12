package com.vajrapulse.core.metrics

import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.core.engine.ExecutionMetrics
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class PeriodicMetricsReporterSpec extends Specification {

    static class CountingExporter implements MetricsExporter {
        final AtomicInteger calls = new AtomicInteger(0)
        @Override
        void export(String title, AggregatedMetrics metrics) {
            calls.incrementAndGet()
        }
    }

    def "should fire immediate snapshot when enabled"() {
        given:
        def collector = new MetricsCollector()
        // record one metric so reportSafe emits
        collector.record(new ExecutionMetrics(System.nanoTime(), System.nanoTime() + 1_000_000, TaskResult.success(), 0))
        def exporter = new CountingExporter()

        and:
        def reporter = new PeriodicMetricsReporter(collector, exporter, java.time.Duration.ofSeconds(60), true)

        when:
        reporter.start()
        // give a little time for the immediate task to run
        Thread.sleep(50)
        reporter.close()

        then:
        exporter.calls.get() >= 1
    }
}
