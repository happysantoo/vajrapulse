package com.vajra.core.metrics

import com.vajra.api.TaskResult
import com.vajra.core.engine.ExecutionMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

class MetricsCollectorSpec extends Specification {

    def "should record successful executions"() {
        given: "a metrics collector"
        def collector = new MetricsCollector()
        
        when: "recording successful metrics"
        def startNanos = System.nanoTime()
        def endNanos = startNanos + 1_000_000  // 1ms
        def metrics = new ExecutionMetrics(
            startNanos,
            endNanos,
            TaskResult.success("data"),
            0
        )
        collector.record(metrics)
        
        and: "taking a snapshot"
        def snapshot = collector.snapshot()
        
        then: "snapshot shows success"
        snapshot.totalExecutions() == 1
        snapshot.successCount() == 1
        snapshot.failureCount() == 0
        snapshot.successRate() == 100.0
        snapshot.failureRate() == 0.0
    }
    
    def "should record failed executions"() {
        given: "a metrics collector"
        def collector = new MetricsCollector()
        
        when: "recording failure metrics"
        def startNanos = System.nanoTime()
        def endNanos = startNanos + 2_000_000  // 2ms
        def metrics = new ExecutionMetrics(
            startNanos,
            endNanos,
            TaskResult.failure(new Exception()),
            0
        )
        collector.record(metrics)
        
        and: "taking a snapshot"
        def snapshot = collector.snapshot()
        
        then: "snapshot shows failure"
        snapshot.totalExecutions() == 1
        snapshot.successCount() == 0
        snapshot.failureCount() == 1
        snapshot.successRate() == 0.0
        snapshot.failureRate() == 100.0
    }
    
    def "should aggregate multiple executions"() {
        given: "a metrics collector"
        def collector = new MetricsCollector()
        
        when: "recording mix of successes and failures"
        7.times { iteration ->
            def startNanos = System.nanoTime()
            def endNanos = startNanos + (iteration + 1) * 1_000_000  // Variable duration
            def result = iteration < 5 ? TaskResult.success() : TaskResult.failure(new Exception())
            def metrics = new ExecutionMetrics(startNanos, endNanos, result, iteration)
            collector.record(metrics)
        }
        
        and: "taking a snapshot"
        def snapshot = collector.snapshot()
        
        then: "snapshot aggregates correctly"
        snapshot.totalExecutions() == 7
        snapshot.successCount() == 5
        snapshot.failureCount() == 2
        snapshot.successRate() > 70.0
        snapshot.successRate() < 72.0  // ~71.4%
    }
    
    def "should use provided meter registry"() {
        given: "a custom registry"
        def customRegistry = new SimpleMeterRegistry()
        def collector = new MetricsCollector(customRegistry)
        
        expect: "collector uses the custom registry"
        collector.getRegistry() == customRegistry
    }
}
