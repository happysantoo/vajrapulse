package com.vajrapulse.core.metrics

import com.vajrapulse.core.engine.ExecutionMetrics
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(10)
class MetricsCollectorRoundingSpec extends Specification {

    def "should round configured percentiles to 3 decimals and dedupe"() {
        given:
        def collector = new MetricsCollector(0.9750001d, 0.9749999d, 0.95d)

        and: "a single successful execution to produce a snapshot"
        def metrics = new ExecutionMetrics(System.nanoTime(), System.nanoTime() + 1_000_000, com.vajrapulse.api.task.TaskResult.success(null), 0)
        collector.record(metrics)

        when:
        def snapshot = collector.snapshot()

        then: "keys are rounded and deduped"
        snapshot.successPercentiles().keySet().contains(0.95d)
        snapshot.successPercentiles().keySet().contains(0.975d)
        snapshot.successPercentiles().size() == 2
    }
}
