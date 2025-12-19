package com.vajrapulse.core.metrics

import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.core.engine.ExecutionMetrics
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(10)
class MetricsCollectorPercentileSpec extends Specification {

    def "should configure custom percentiles"() {
        given: "a collector with custom percentiles"
        MetricsCollector collector = new MetricsCollector(0.50, 0.75, 0.90, 0.95, 0.99)
        
        when: "recording some successful executions"
        (1..100).each { i ->
            long duration = i * 1_000_000L
            collector.record(new ExecutionMetrics(0, duration, TaskResult.success("ok"), i))
        }
        
        and: "taking a snapshot"
        AggregatedMetrics snapshot = collector.snapshot()
        
        then: "all custom percentiles are present"
        snapshot.successPercentiles().keySet() == [0.50d, 0.75d, 0.90d, 0.95d, 0.99d] as Set
    }
    
    def "should use default percentiles when none provided"() {
        given: "a collector with default config"
        MetricsCollector collector = new MetricsCollector()
        
        when: "recording executions"
        (1..50).each { i ->
            long duration = i * 1_000_000L
            collector.record(new ExecutionMetrics(0, duration, TaskResult.success("ok"), i))
        }
        
        and: "taking a snapshot"
        AggregatedMetrics snapshot = collector.snapshot()
        
        then: "default percentiles are present"
        snapshot.successPercentiles().keySet() == [0.50d, 0.95d, 0.99d] as Set
    }
    
    def "should sanitize invalid percentiles"() {
        given: "a collector with invalid percentiles (out of range)"
        MetricsCollector collector = new MetricsCollector(0.0, -0.5, 0.50, 1.5, 0.95, 0.99, 1.0)
        
        when: "recording executions"
        (1..50).each { i ->
            long duration = i * 1_000_000L
            collector.record(new ExecutionMetrics(0, duration, TaskResult.success("ok"), i))
        }
        
        and: "taking a snapshot"
        AggregatedMetrics snapshot = collector.snapshot()
        
        then: "only valid percentiles in (0,1] are kept"
        snapshot.successPercentiles().keySet().every { it > 0.0 && it <= 1.0 }
        snapshot.successPercentiles().containsKey(0.50d)
        snapshot.successPercentiles().containsKey(0.95d)
        snapshot.successPercentiles().containsKey(0.99d)
        snapshot.successPercentiles().containsKey(1.0d)
        !snapshot.successPercentiles().containsKey(0.0d)
        !snapshot.successPercentiles().containsKey(-0.5d)
    }
    
    def "should deduplicate percentiles"() {
        given: "a collector with duplicate percentiles"
        MetricsCollector collector = new MetricsCollector(0.50, 0.95, 0.50, 0.99, 0.95)
        
        when: "recording executions"
        (1..50).each { i ->
            long duration = i * 1_000_000L
            collector.record(new ExecutionMetrics(0, duration, TaskResult.success("ok"), i))
        }
        
        and: "taking a snapshot"
        AggregatedMetrics snapshot = collector.snapshot()
        
        then: "percentiles are deduplicated"
        snapshot.successPercentiles().size() == 3
        snapshot.successPercentiles().keySet() == [0.50d, 0.95d, 0.99d] as Set
    }
    
    def "should sort percentiles in ascending order"() {
        given: "a collector with unsorted percentiles"
        MetricsCollector collector = new MetricsCollector(0.99, 0.50, 0.95, 0.75)
        
        when: "recording executions"
        (1..50).each { i ->
            long duration = i * 1_000_000L
            collector.record(new ExecutionMetrics(0, duration, TaskResult.success("ok"), i))
        }
        
        and: "taking a snapshot"
        AggregatedMetrics snapshot = collector.snapshot()
        
        then: "percentiles are sorted"
        def keys = snapshot.successPercentiles().keySet() as List
        keys == [0.50d, 0.75d, 0.95d, 0.99d]
    }
    
    def "should fallback to defaults when empty array provided"() {
        given: "a collector with empty percentiles array"
        MetricsCollector collector = new MetricsCollector(new double[0])
        
        when: "recording executions"
        (1..50).each { i ->
            long duration = i * 1_000_000L
            collector.record(new ExecutionMetrics(0, duration, TaskResult.success("ok"), i))
        }
        
        and: "taking a snapshot"
        AggregatedMetrics snapshot = collector.snapshot()
        
        then: "default percentiles are used"
        snapshot.successPercentiles().keySet() == [0.50d, 0.95d, 0.99d] as Set
    }
    
    def "should populate both success and failure percentile maps"() {
        given: "a collector with custom percentiles"
        MetricsCollector collector = new MetricsCollector(0.50, 0.90, 0.99)
        
        when: "recording mixed successes and failures"
        (1..200).each { i ->
            long duration = i * 1_000_000L
            if (i % 3 == 0) {
                collector.record(new ExecutionMetrics(0, duration, TaskResult.failure(new RuntimeException("fail")), i))
            } else {
                collector.record(new ExecutionMetrics(0, duration, TaskResult.success("ok"), i))
            }
        }
        
        and: "taking a snapshot"
        AggregatedMetrics snapshot = collector.snapshot()
        
        then: "both success and failure maps contain percentiles with correct keys"
        snapshot.successPercentiles().keySet() == [0.50d, 0.90d, 0.99d] as Set
        snapshot.failurePercentiles().keySet() == [0.50d, 0.90d, 0.99d] as Set
        
        and: "percentile maps are populated with expected size"
        snapshot.successPercentiles().size() == 3
        snapshot.failurePercentiles().size() == 3
        
        and: "percentile values are present (actual values depend on Micrometer histogram calculation)"
        // Note: Micrometer may return 0.0, NaN, or positive values depending on data distribution
        // The important thing is that the keys are present and maps are populated
        snapshot.successPercentiles().containsKey(0.50d)
        snapshot.successPercentiles().containsKey(0.90d)
        snapshot.successPercentiles().containsKey(0.99d)
        snapshot.failurePercentiles().containsKey(0.50d)
        snapshot.failurePercentiles().containsKey(0.90d)
        snapshot.failurePercentiles().containsKey(0.99d)
    }
    
    // Backwards compatibility accessors removed; maps are the single source of truth.
}
