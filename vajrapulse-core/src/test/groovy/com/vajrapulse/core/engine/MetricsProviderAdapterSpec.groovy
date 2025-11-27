package com.vajrapulse.core.engine

import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification

class MetricsProviderAdapterSpec extends Specification {

    def "should cache snapshot results"() {
        given: "a metrics collector and adapter"
        def collector = new MetricsCollector()
        def adapter = new MetricsProviderAdapter(collector)
        
        // Record some metrics
        def metrics = new com.vajrapulse.core.engine.ExecutionMetrics(
            System.nanoTime(),
            System.nanoTime() + 1_000_000,
            com.vajrapulse.api.TaskResult.success("ok"),
            0
        )
        collector.record(metrics)

        when: "calling methods multiple times"
        def rate1 = adapter.getFailureRate()
        def exec1 = adapter.getTotalExecutions()
        def rate2 = adapter.getFailureRate()
        def exec2 = adapter.getTotalExecutions()

        then: "values are consistent (cached)"
        rate1 == rate2
        exec1 == exec2
        exec1 > 0
    }

    def "should handle null metrics collector"() {
        when: "creating adapter with null"
        new MetricsProviderAdapter(null)

        then: "exception thrown"
        thrown(IllegalArgumentException)
    }

    def "should return failure rate from metrics"() {
        given: "a metrics collector with failures"
        def collector = new MetricsCollector()
        def adapter = new MetricsProviderAdapter(collector)
        
        // Record failure
        def metrics = new com.vajrapulse.core.engine.ExecutionMetrics(
            System.nanoTime(),
            System.nanoTime() + 1_000_000,
            com.vajrapulse.api.TaskResult.failure(new RuntimeException("error")),
            0
        )
        collector.record(metrics)

        when: "getting failure rate"
        def rate = adapter.getFailureRate()

        then: "failure rate is 100%"
        rate == 100.0
    }
}

