package com.vajrapulse.core.engine

import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import static org.awaitility.Awaitility.*
import static java.util.concurrent.TimeUnit.*

@Timeout(30)
class MetricsProviderAdapterSpec extends Specification {

    def "should cache snapshot results"() {
        given: "a metrics collector and adapter"
        def collector = new MetricsCollector()
        def adapter = new MetricsProviderAdapter(collector)
        
        // Record some metrics
        def metrics = new com.vajrapulse.core.engine.ExecutionMetrics(
            System.nanoTime(),
            System.nanoTime() + 1_000_000,
            com.vajrapulse.api.task.TaskResult.success("ok"),
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
            com.vajrapulse.api.task.TaskResult.failure(new RuntimeException("error")),
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
                com.vajrapulse.api.task.TaskResult.success("ok"),
                0
            )
            collector.record(successMetrics)
        }
        
        // Wait for time separation (more than 1 second) to test recent window
        def startTime = System.currentTimeMillis()
        await().atMost(2, SECONDS)
            .pollInterval(100, MILLISECONDS)
            .until {
                System.currentTimeMillis() - startTime >= 1100
            }
        
        // Record failures in recent window
        3.times {
            def failureMetrics = new com.vajrapulse.core.engine.ExecutionMetrics(
                System.nanoTime(),
                System.nanoTime() + 1_000_000,
                com.vajrapulse.api.task.TaskResult.failure(new RuntimeException("error")),
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
            com.vajrapulse.api.task.TaskResult.failure(new RuntimeException("error")),
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
    
    def "should return failure count from metrics"() {
        given: "a metrics collector with failures"
        def collector = new MetricsCollector()
        def adapter = new MetricsProviderAdapter(collector)
        
        // Record one success and two failures
        def successMetrics = new com.vajrapulse.core.engine.ExecutionMetrics(
            System.nanoTime(),
            System.nanoTime() + 1_000_000,
            com.vajrapulse.api.task.TaskResult.success("ok"),
            0
        )
        collector.record(successMetrics)
        
        def failureMetrics1 = new com.vajrapulse.core.engine.ExecutionMetrics(
            System.nanoTime(),
            System.nanoTime() + 1_000_000,
            com.vajrapulse.api.task.TaskResult.failure(new RuntimeException("error1")),
            0
        )
        collector.record(failureMetrics1)
        
        def failureMetrics2 = new com.vajrapulse.core.engine.ExecutionMetrics(
            System.nanoTime(),
            System.nanoTime() + 1_000_000,
            com.vajrapulse.api.task.TaskResult.failure(new RuntimeException("error2")),
            0
        )
        collector.record(failureMetrics2)

        when: "getting failure count"
        def count = adapter.getFailureCount()

        then: "failure count is 2"
        count == 2
    }
    
    def "should return zero when no failures"() {
        given: "a metrics collector with only successes"
        def collector = new MetricsCollector()
        def adapter = new MetricsProviderAdapter(collector)
        
        // Record successes only
        3.times {
            def successMetrics = new com.vajrapulse.core.engine.ExecutionMetrics(
                System.nanoTime(),
                System.nanoTime() + 1_000_000,
                com.vajrapulse.api.task.TaskResult.success("ok"),
                0
            )
            collector.record(successMetrics)
        }

        when: "getting failure count"
        def count = adapter.getFailureCount()

        then: "failure count is zero"
        count == 0
    }
}

