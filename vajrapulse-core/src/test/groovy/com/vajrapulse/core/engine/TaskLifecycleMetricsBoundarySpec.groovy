package com.vajrapulse.core.engine

import com.vajrapulse.api.pattern.StaticLoad
import com.vajrapulse.api.task.TaskLifecycle
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration

/**
 * Tests to verify that metrics exclude init/teardown phases.
 * 
 * <p>This ensures that only execute() iterations contribute to metrics,
 * not initialization or cleanup time.
 */
@Timeout(30)
class TaskLifecycleMetricsBoundarySpec extends Specification {
    
    def "should exclude init time from execution metrics"() {
        given: "a task with slow init"
        def initStartTime = 0L
        def initEndTime = 0L
        def executeStartTime = 0L
        
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {
                initStartTime = System.nanoTime()
                // Simulate slow initialization (10ms)
                Thread.sleep(10)
                initEndTime = System.nanoTime()
            }
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                if (iteration == 0) {
                    executeStartTime = System.nanoTime()
                }
                // Fast execution (< 1ms)
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {
                // No-op
            }
        }
        
        def load = new StaticLoad(100.0, Duration.ofMillis(100))
        def collector = new MetricsCollector()
        
        when: "running the engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        engine.close()
        
        def metrics = collector.snapshot()
        
        then: "init time is not included in metrics"
        // Verify init took significant time (> 5ms)
        def initDurationMs = (initEndTime - initStartTime) / 1_000_000.0
        initDurationMs >= 5.0
        
        // Verify execute started after init
        executeStartTime >= initEndTime
        
        // Verify metrics only reflect execute() calls (should be fast, < 5ms p99)
        // Since execute() is fast, p99 should be much less than init duration
        def p99Value = metrics.successPercentiles().get(0.99)
        if (p99Value != null) {
            def p99LatencyMs = p99Value / 1_000_000.0
            p99LatencyMs < initDurationMs
        }
        
        // Verify execution count matches expected (not including init/teardown)
        metrics.totalExecutions() > 0
        metrics.totalExecutions() < 20 // Should be reasonable for 100ms at 100 TPS
    }
    
    def "should exclude teardown time from execution metrics"() {
        given: "a task with slow teardown"
        def teardownStartTime = 0L
        def teardownEndTime = 0L
        def lastExecuteEndTime = 0L
        
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {
                // Fast init
            }
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                // Fast execution
                lastExecuteEndTime = System.nanoTime()
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {
                teardownStartTime = System.nanoTime()
                // Simulate slow teardown (10ms)
                Thread.sleep(10)
                teardownEndTime = System.nanoTime()
            }
        }
        
        def load = new StaticLoad(50.0, Duration.ofMillis(100))
        def collector = new MetricsCollector()
        
        when: "running the engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        engine.close()
        
        def metrics = collector.snapshot()
        
        then: "teardown time is not included in metrics"
        // Verify teardown took significant time (> 5ms)
        def teardownDurationMs = (teardownEndTime - teardownStartTime) / 1_000_000.0
        teardownDurationMs >= 5.0
        
        // Verify teardown started after last execute
        teardownStartTime >= lastExecuteEndTime
        
        // Verify metrics only reflect execute() calls (should be fast, < 5ms p99)
        def p99Value = metrics.successPercentiles().get(0.99)
        if (p99Value != null) {
            def p99LatencyMs = p99Value / 1_000_000.0
            p99LatencyMs < teardownDurationMs
        }
        
        // Verify execution count matches expected (not including init/teardown)
        metrics.totalExecutions() > 0
    }
    
    def "should exclude init exceptions from failure metrics"() {
        given: "a task that throws in init"
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {
                throw new RuntimeException("init failed")
            }
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {
                // Should not be called
            }
        }
        
        def load = new StaticLoad(10.0, Duration.ofMillis(50))
        def collector = new MetricsCollector()
        
        when: "running the engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        
        then: "init exception is thrown and no metrics recorded"
        thrown(RuntimeException)
        
        when: "checking metrics"
        def metrics = collector.snapshot()
        
        then: "no execution metrics recorded (init failed before any execute calls)"
        metrics.totalExecutions() == 0
        metrics.successCount() == 0
        metrics.failureCount() == 0
        
        cleanup:
        engine?.close()
    }
    
    def "should exclude teardown exceptions from failure metrics"() {
        given: "a task that throws in teardown"
        def executeCount = 0
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {
                // Fast init
            }
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                executeCount++
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {
                throw new RuntimeException("teardown failed")
            }
        }
        
        def load = new StaticLoad(50.0, Duration.ofMillis(100))
        def collector = new MetricsCollector()
        
        when: "running the engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        engine.close()
        
        def metrics = collector.snapshot()
        
        then: "teardown exception is caught and metrics only reflect execute() calls"
        noExceptionThrown() // Teardown exceptions are caught and logged
        
        // Verify metrics only include execute() calls
        metrics.totalExecutions() == executeCount
        metrics.successCount() == executeCount
        metrics.failureCount() == 0 // Teardown exception doesn't count as failure
    }
    
    def "should only record metrics for execute() iterations"() {
        given: "a task that tracks method calls"
        def initCalls = 0
        def executeCalls = 0
        def teardownCalls = 0
        
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {
                initCalls++
            }
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                executeCalls++
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {
                teardownCalls++
            }
        }
        
        def load = new StaticLoad(100.0, Duration.ofMillis(200))
        def collector = new MetricsCollector()
        
        when: "running the engine"
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        engine.run()
        engine.close()
        
        def metrics = collector.snapshot()
        
        then: "metrics count matches execute() calls only"
        initCalls == 1
        teardownCalls == 1
        executeCalls > 0
        
        // Metrics should only reflect execute() calls
        metrics.totalExecutions() == executeCalls
        metrics.successCount() == executeCalls
        metrics.failureCount() == 0
        
        // Init and teardown don't contribute to execution count
        metrics.totalExecutions() != (initCalls + executeCalls + teardownCalls)
    }
}
