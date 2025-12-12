package com.vajrapulse.core.metrics

import com.vajrapulse.core.engine.ExecutionMetrics
import com.vajrapulse.api.task.TaskResult
import spock.lang.Specification
import spock.lang.Timeout

/**
 * Additional runId-specific coverage for MetricsCollector factories & tags.
 */
@Timeout(5)
class MetricsCollectorRunIdSpec extends Specification {

    def "should create collector with runId and record tagged metrics"() {
        given:
        def collector = MetricsCollector.createWithRunId('rid-x', [0.50d] as double[])

        when: "record success and failure"
        collector.record(new ExecutionMetrics(System.nanoTime(), System.nanoTime()+1000, TaskResult.success(null), 0))
        collector.record(new ExecutionMetrics(System.nanoTime(), System.nanoTime()+2000, TaskResult.failure(new RuntimeException('err')), 1))
        def snapshot = collector.snapshot()

        then:
        snapshot.totalExecutions() == 2
        snapshot.successCount() == 1
        snapshot.failureCount() == 1
        collector.runId == 'rid-x'
    }
}
