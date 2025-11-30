package com.vajrapulse.core.metrics

import com.vajrapulse.api.TaskResult
import com.vajrapulse.core.engine.ExecutionMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.util.Collections

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
    
    def "should register JVM GC and memory metrics"() {
        given: "a metrics collector"
        def registry = new SimpleMeterRegistry()
        def collector = MetricsCollector.createWithRunId(registry, "test-run-id", [0.50d, 0.95d] as double[])
        
        when: "checking registered metrics"
        def heapUsed = registry.find("vajrapulse.jvm.memory.heap.used").gauge()
        def heapCommitted = registry.find("vajrapulse.jvm.memory.heap.committed").gauge()
        def heapMax = registry.find("vajrapulse.jvm.memory.heap.max").gauge()
        def nonHeapUsed = registry.find("vajrapulse.jvm.memory.nonheap.used").gauge()
        def gcCollections = registry.find("vajrapulse.jvm.gc.collections").gauges()
        
        then: "JVM metrics are registered"
        heapUsed != null
        heapCommitted != null
        heapMax != null
        nonHeapUsed != null
        !gcCollections.isEmpty()  // At least one GC metric
        
        and: "metrics have run_id tag"
        def runIdTag = heapUsed.id.tags.find { it.key == "run_id" }
        runIdTag != null
        runIdTag.value == "test-run-id"
    }
    
    def "should register JVM metrics without runId"() {
        given: "a metrics collector without runId"
        def registry = new SimpleMeterRegistry()
        def collector = new MetricsCollector(registry)
        
        when: "checking registered metrics"
        def heapUsed = registry.find("vajrapulse.jvm.memory.heap.used").gauge()
        
        then: "JVM metrics are registered without run_id tag"
        heapUsed != null
        heapUsed.id.tags.find { it.key == "run_id" } == null
    }
    
    def "should clean up ThreadLocal when closed"() {
        given: "a metrics collector"
        def collector = new MetricsCollector()
        
        when: "using collector and closing it"
        collector.record(new ExecutionMetrics(
            System.nanoTime(),
            System.nanoTime() + 1_000_000,
            TaskResult.success(),
            0
        ))
        collector.snapshot()
        collector.close()
        
        and: "closing again"
        collector.close()
        
        then: "collector can be closed without exception"
        noExceptionThrown()
    }
    
    def "should work with try-with-resources"() {
        given: "a metrics collector in try-with-resources"
        def snapshot
        
        when: "using collector in try-with-resources"
        try (def collector = new MetricsCollector()) {
            collector.record(new ExecutionMetrics(
                System.nanoTime(),
                System.nanoTime() + 1_000_000,
                TaskResult.success(),
                0
            ))
            snapshot = collector.snapshot()
        }
        
        then: "collector is automatically closed"
        snapshot != null
        snapshot.totalExecutions() == 1
        noExceptionThrown()
    }
    
    def "should prevent ThreadLocal memory leak in thread pool"() {
        given: "a thread pool that reuses threads"
        def executor = java.util.concurrent.Executors.newFixedThreadPool(4)
        def collectors = Collections.synchronizedList([])
        def snapshots = Collections.synchronizedList([])
        
        when: "creating and using many collectors in thread pool"
        100.times { iteration ->
            executor.submit {
                def collector = new MetricsCollector()
                collectors.add(collector)
                
                // Use collector
                collector.record(new ExecutionMetrics(
                    System.nanoTime(),
                    System.nanoTime() + 1_000_000,
                    TaskResult.success(),
                    iteration
                ))
                snapshots.add(collector.snapshot())
                
                // Close collector to clean up ThreadLocal
                collector.close()
            }
        }
        
        and: "waiting for all tasks to complete"
        executor.shutdown()
        def completed = executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)
        
        then: "all tasks completed"
        completed
        
        and: "all collectors were used and closed"
        collectors.size() == 100
        snapshots.size() == 100
        snapshots.every { it.totalExecutions() == 1 }
        
        cleanup:
        if (!executor.isShutdown()) {
            executor.shutdownNow()
        }
    }
    
    def "should handle concurrent snapshot and close"() {
        given: "a metrics collector"
        def collector = new MetricsCollector()
        
        when: "calling snapshot and close concurrently"
        def results = []
        def exceptions = []
        def threads = []
        
        10.times {
            threads << Thread.startVirtualThread {
                try {
                    def snapshot = collector.snapshot()
                    results << snapshot
                } catch (Exception e) {
                    exceptions << e
                }
            }
        }
        
        Thread.sleep(50) // Let some snapshots start
        collector.close()
        
        threads.each { it.join() }
        
        then: "operations complete without deadlock"
        // Some snapshots may succeed, some may fail after close - both are acceptable
        // The important thing is no deadlock or crash
        (results.size() + exceptions.size()) == 10
    }
    
    def "should not leak memory in long-running scenario"() {
        given: "a thread pool that reuses threads"
        def executor = java.util.concurrent.Executors.newFixedThreadPool(4)
        def initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        when: "creating and closing many collectors over time"
        def iterations = 1000
        iterations.times { iteration ->
            executor.submit {
                try (def collector = new MetricsCollector()) {
                    // Use collector
                    collector.record(new ExecutionMetrics(
                        System.nanoTime(),
                        System.nanoTime() + 1_000_000,
                        TaskResult.success(),
                        iteration
                    ))
                    collector.snapshot()
                } // Automatically closed
            }
        }
        
        and: "waiting for all tasks to complete"
        executor.shutdown()
        executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)
        
        and: "forcing GC and checking memory"
        System.gc()
        Thread.sleep(100) // Give GC time
        def finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        def memoryIncrease = finalMemory - initialMemory
        
        then: "memory increase is reasonable (not a leak)"
        // Memory increase should be small relative to the number of iterations
        // If ThreadLocal was leaking, we'd see significant growth
        // Allow for some variance but flag if memory grows too much
        def memoryPerIteration = memoryIncrease / iterations
        // Each collector should use minimal memory after close
        // Allow up to 1KB per iteration as reasonable overhead
        memoryPerIteration < 1024
        
        cleanup:
        if (!executor.isShutdown()) {
            executor.shutdownNow()
        }
    }
    
    def "should record dropped requests"() {
        given: "a metrics collector"
        def collector = new MetricsCollector()
        
        when: "recording dropped requests"
        collector.recordDroppedRequest()
        collector.recordDroppedRequest()
        collector.recordDroppedRequest()
        
        then: "dropped counter is incremented"
        def registry = collector.getRegistry()
        def counter = registry.find("vajrapulse.execution.backpressure.dropped").counter()
        counter.count() == 3.0
    }
    
    def "should record rejected requests"() {
        given: "a metrics collector"
        def collector = new MetricsCollector()
        
        when: "recording rejected requests"
        collector.recordRejectedRequest()
        collector.recordRejectedRequest()
        
        then: "rejected counter is incremented"
        def registry = collector.getRegistry()
        def counter = registry.find("vajrapulse.execution.backpressure.rejected").counter()
        counter.count() == 2.0
    }
    
    def "should record both dropped and rejected requests"() {
        given: "a metrics collector"
        def collector = new MetricsCollector()
        
        when: "recording both types"
        collector.recordDroppedRequest()
        collector.recordRejectedRequest()
        collector.recordDroppedRequest()
        
        then: "both counters are incremented correctly"
        def registry = collector.getRegistry()
        def droppedCounter = registry.find("vajrapulse.execution.backpressure.dropped").counter()
        def rejectedCounter = registry.find("vajrapulse.execution.backpressure.rejected").counter()
        droppedCounter.count() == 2.0
        rejectedCounter.count() == 1.0
    }
}
