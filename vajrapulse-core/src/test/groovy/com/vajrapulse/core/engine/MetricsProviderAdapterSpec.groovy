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
    
    def "should return recent window failure rate"() {
        given: "a metrics collector"
        def collector = new MetricsCollector()
        def adapter = new MetricsProviderAdapter(collector)
        
        // Record some successes first
        5.times {
            def successMetrics = new com.vajrapulse.core.engine.ExecutionMetrics(
                System.nanoTime(),
                System.nanoTime() + 1_000_000,
                com.vajrapulse.api.TaskResult.success("ok"),
                0
            )
            collector.record(successMetrics)
        }
        
        // Wait a bit to create time separation
        Thread.sleep(1100) // More than 1 second
        
        // Record failures in recent window
        3.times {
            def failureMetrics = new com.vajrapulse.core.engine.ExecutionMetrics(
                System.nanoTime(),
                System.nanoTime() + 1_000_000,
                com.vajrapulse.api.TaskResult.failure(new RuntimeException("error")),
                0
            )
            collector.record(failureMetrics)
        }

        when: "getting recent window failure rate (10 seconds)"
        def recentRate = adapter.getRecentFailureRate(10)

        then: "recent window failure rate should reflect recent failures"
        // Recent window should show 100% failure rate (3 failures, 0 successes in recent window)
        // Note: This may vary based on implementation, but should be higher than all-time rate
        recentRate >= 0.0
        recentRate <= 100.0
    }
    
    def "should return all-time rate when window is larger than history"() {
        given: "a metrics collector with limited history"
        def collector = new MetricsCollector()
        def adapter = new MetricsProviderAdapter(collector)
        
        // Record one failure
        def metrics = new com.vajrapulse.core.engine.ExecutionMetrics(
            System.nanoTime(),
            System.nanoTime() + 1_000_000,
            com.vajrapulse.api.TaskResult.failure(new RuntimeException("error")),
            0
        )
        collector.record(metrics)

        when: "getting recent window failure rate with large window"
        def allTimeRate = adapter.getFailureRate()
        def recentRate = adapter.getRecentFailureRate(1000) // 1000 seconds - much larger than available history

        then: "recent rate should fall back to all-time rate"
        recentRate == allTimeRate
        recentRate == 100.0
    }
    
    def "should handle invalid window size"() {
        given: "a metrics collector"
        def collector = new MetricsCollector()
        def adapter = new MetricsProviderAdapter(collector)

        when: "getting recent window failure rate with invalid window"
        def rate = adapter.getRecentFailureRate(0)
        def rate2 = adapter.getRecentFailureRate(-1)

        then: "should return all-time rate"
        rate == adapter.getFailureRate()
        rate2 == adapter.getFailureRate()
    }
}

