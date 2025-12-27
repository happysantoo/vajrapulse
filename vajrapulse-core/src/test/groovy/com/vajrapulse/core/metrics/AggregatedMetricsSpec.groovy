package com.vajrapulse.core.metrics

import spock.lang.Specification
import spock.lang.Timeout

@Timeout(10)
class AggregatedMetricsSpec extends Specification {

    def "should calculate response TPS correctly"() {
        given: "metrics with 1000 total executions in 1 second"
        def metrics = new AggregatedMetrics(
            1000L,
            950L,
            50L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            1000L,  // 1 second
            0L,     // queue size
            [:] as Map<Double, Double>  // queue wait percentiles
        )
        
        when: "calculating response TPS"
        def tps = metrics.responseTps()
        
        then: "TPS is 1000"
        tps == 1000.0
    }
    
    def "should calculate success TPS correctly"() {
        given: "metrics with 950 successes in 1 second"
        def metrics = new AggregatedMetrics(
            1000L,
            950L,
            50L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            1000L,  // elapsed time
            0L,     // queue size
            [:] as Map<Double, Double>  // queue wait percentiles
        )
        
        when: "calculating success TPS"
        def tps = metrics.successTps()
        
        then: "TPS is 950"
        tps == 950.0
    }
    
    def "should calculate failure TPS correctly"() {
        given: "metrics with 50 failures in 1 second"
        def metrics = new AggregatedMetrics(
            1000L,
            950L,
            50L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            1000L,  // elapsed time
            0L,     // queue size
            [:] as Map<Double, Double>  // queue wait percentiles
        )
        
        when: "calculating failure TPS"
        def tps = metrics.failureTps()
        
        then: "TPS is 50"
        tps == 50.0
    }
    
    def "should handle fractional seconds correctly"() {
        given: "metrics with 500 executions in 500ms (0.5 seconds)"
        def metrics = new AggregatedMetrics(
            500L,
            500L,
            0L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            500L,  // 0.5 seconds
            0L,     // queue size
            [:] as Map<Double, Double>  // queue wait percentiles
        )
        
        when: "calculating response TPS"
        def tps = metrics.responseTps()
        
        then: "TPS is 1000 (500 / 0.5)"
        tps == 1000.0
    }
    
    def "should calculate TPS for longer durations"() {
        given: "metrics with 3050 executions in 30.5 seconds"
        def metrics = new AggregatedMetrics(
            3050L,
            2995L,
            55L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            30500L,  // 30.5 seconds
            0L,     // queue size
            [:] as Map<Double, Double>  // queue wait percentiles
        )
        
        when: "calculating TPS values"
        def responseTps = metrics.responseTps()
        def successTps = metrics.successTps()
        def failureTps = metrics.failureTps()
        
        then: "TPS values are correct"
        Math.abs(responseTps - 100.0) < 0.01
        Math.abs(successTps - 98.197) < 0.01
        Math.abs(failureTps - 1.803) < 0.01
    }
    
    def "should handle zero elapsed time gracefully"() {
        given: "metrics with 1ms elapsed time (minimum)"
        def metrics = new AggregatedMetrics(
            1L,
            1L,
            0L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            1L,  // 1ms = 1000 TPS
            0L,     // queue size
            [:] as Map<Double, Double>  // queue wait percentiles
        )
        
        when: "calculating TPS"
        def tps = metrics.responseTps()
        
        then: "TPS is 1000 (1 execution per millisecond)"
        tps == 1000.0
    }
    
    def "should calculate success and failure rates correctly"() {
        given: "metrics with mixed success and failure"
        def metrics = new AggregatedMetrics(
            1000L,
            950L,
            50L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            5000L,  // elapsed time
            0L,     // queue size
            [:] as Map<Double, Double>  // queue wait percentiles
        )
        
        when: "calculating rates"
        def successRate = metrics.successRate()
        def failureRate = metrics.failureRate()
        
        then: "rates are calculated correctly"
        successRate == 95.0
        failureRate == 5.0
    }
    
    // New tests for statistical summary
    
    def "should include statistical summary when provided"() {
        given: "metrics with statistical summary"
        def successStats = new LatencyStats(10_000_000.0, 2_000_000.0, 1_000_000.0, 50_000_000.0, 950L)
        def failureStats = new LatencyStats(100_000_000.0, 20_000_000.0, 50_000_000.0, 200_000_000.0, 50L)
        
        when: "creating metrics with stats"
        def metrics = new AggregatedMetrics(
            1000L,
            950L,
            50L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            1000L,
            0L,
            [:] as Map<Double, Double>,
            successStats,
            failureStats
        )
        
        then: "statistics are accessible"
        metrics.hasStatistics()
        metrics.successStats() == successStats
        metrics.failureStats() == failureStats
        metrics.meanSuccessLatencyNanos() == 10_000_000.0
        metrics.meanFailureLatencyNanos() == 100_000_000.0
        metrics.minSuccessLatencyNanos() == 1_000_000.0
        metrics.maxSuccessLatencyNanos() == 50_000_000.0
        metrics.stdDevSuccessLatencyNanos() == 2_000_000.0
    }
    
    def "should handle null statistics gracefully"() {
        given: "metrics without statistical summary (using old constructor)"
        def metrics = new AggregatedMetrics(
            1000L,
            950L,
            50L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            1000L,
            0L,
            [:] as Map<Double, Double>
        )
        
        expect: "statistics methods return defaults"
        !metrics.hasStatistics()
        metrics.successStats() == null
        metrics.failureStats() == null
        metrics.meanSuccessLatencyNanos() == 0.0
        metrics.meanFailureLatencyNanos() == 0.0
        metrics.minSuccessLatencyNanos() == 0.0
        metrics.maxSuccessLatencyNanos() == 0.0
        metrics.stdDevSuccessLatencyNanos() == 0.0
    }
    
    def "should report hasStatistics true when only success stats present"() {
        given: "metrics with only success stats"
        def successStats = new LatencyStats(10_000_000.0, 2_000_000.0, 1_000_000.0, 50_000_000.0, 950L)
        
        when: "creating metrics with only success stats"
        def metrics = new AggregatedMetrics(
            1000L,
            950L,
            50L,
            [:] as Map<Double, Double>,
            [:] as Map<Double, Double>,
            1000L,
            0L,
            [:] as Map<Double, Double>,
            successStats,
            null
        )
        
        then: "hasStatistics returns true"
        metrics.hasStatistics()
        metrics.successStats() != null
        metrics.failureStats() == null
    }
    
    def "should create immutable percentile maps"() {
        given: "mutable percentile maps"
        def successPercentiles = new HashMap<Double, Double>()
        successPercentiles.put(0.50, 10_000_000.0)
        
        when: "creating metrics and modifying original"
        def metrics = new AggregatedMetrics(
            1000L, 950L, 50L,
            successPercentiles,
            [:] as Map<Double, Double>,
            1000L, 0L,
            [:] as Map<Double, Double>
        )
        successPercentiles.put(0.95, 50_000_000.0)
        
        then: "metrics percentiles are not affected"
        metrics.successPercentiles().size() == 1
        !metrics.successPercentiles().containsKey(0.95)
    }
}
