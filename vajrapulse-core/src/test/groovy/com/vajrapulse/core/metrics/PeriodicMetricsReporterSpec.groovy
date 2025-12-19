package com.vajrapulse.core.metrics

import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.core.engine.ExecutionMetrics
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.atomic.AtomicInteger

import static org.awaitility.Awaitility.*
import static java.util.concurrent.TimeUnit.*

@Timeout(10)
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
        // Wait for the immediate task to run
        await().atMost(200, MILLISECONDS)
            .pollInterval(10, MILLISECONDS)
            .until { exporter.calls.get() >= 1 }
        reporter.close()

        then:
        exporter.calls.get() >= 1
    }
}
