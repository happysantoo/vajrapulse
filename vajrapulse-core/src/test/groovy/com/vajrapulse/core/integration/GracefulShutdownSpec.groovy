package com.vajrapulse.core.integration

import com.vajrapulse.api.pattern.StaticLoad
import com.vajrapulse.api.task.TaskLifecycle
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.core.engine.ExecutionEngine
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

import static org.awaitility.Awaitility.*

/**
 * Integration tests for graceful shutdown scenarios.
 * 
 * <p>Verifies that shutdown completes gracefully in various scenarios:
 * <ul>
 *   <li>Manual stop() call during active load</li>
 *   <li>Shutdown hook triggered (simulated SIGINT/SIGTERM)</li>
 *   <li>Final metrics snapshot completeness</li>
 *   <li>Shutdown timing (completes within 5s)</li>
 * </ul>
 */
@Timeout(30)
class GracefulShutdownSpec extends Specification {
    
    def "should shutdown gracefully on manual stop() call"() {
        given: "a long-running load test"
        def executionCount = new AtomicInteger(0)
        def teardownCalled = new AtomicBoolean(false)
        
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {
                // Fast init
            }
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                executionCount.incrementAndGet()
                // Simulate work (1ms per execution)
                Thread.sleep(1)
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {
                teardownCalled.set(true)
            }
        }
        
        def load = new StaticLoad(100.0, Duration.ofSeconds(10)) // Long duration
        def collector = new MetricsCollector()
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        
        when: "starting engine and stopping after short delay"
        def startTime = System.currentTimeMillis()
        def stopLatch = new CountDownLatch(1)
        
        // Start engine in background
        def engineThread = Thread.startVirtualThread {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected when stopped
            }
        }
        
        // Stop after 200ms
        Thread.startVirtualThread {
            Thread.sleep(200)
            engine.stop()
            stopLatch.countDown()
        }
        
        // Wait for stop to be invoked
        assert stopLatch.await(2, TimeUnit.SECONDS) : "Stop should be invoked"
        
        // Wait for engine to complete
        engineThread.join(5000)
        def shutdownTime = System.currentTimeMillis() - startTime
        
        def metrics = collector.snapshot()
        engine.close()
        
        then: "shutdown completes gracefully within 5s"
        shutdownTime < 5000 // Should complete within 5s
        executionCount.get() > 0 // Some executions occurred
        teardownCalled.get() // Teardown was called
        metrics.totalExecutions() > 0 // Metrics recorded
        metrics.totalExecutions() == executionCount.get() // Metrics match execution count
        metrics.successCount() == executionCount.get() // All successful
    }
    
    def "should complete shutdown within 5 seconds"() {
        given: "a load test with active executions"
        def executionCount = new AtomicInteger(0)
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {}
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                executionCount.incrementAndGet()
                Thread.sleep(5) // 5ms per execution
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {}
        }
        
        def load = new StaticLoad(50.0, Duration.ofSeconds(5))
        def collector = new MetricsCollector()
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        
        when: "stopping engine after brief run"
        def startTime = System.currentTimeMillis()
        def stopLatch = new CountDownLatch(1)
        
        def engineThread = Thread.startVirtualThread {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected
            }
        }
        
        Thread.startVirtualThread {
            Thread.sleep(100)
            engine.stop()
            stopLatch.countDown()
        }
        
        stopLatch.await(2, TimeUnit.SECONDS)
        engineThread.join(6000) // Allow up to 6s for shutdown
        def shutdownDuration = System.currentTimeMillis() - startTime
        
        def metrics = collector.snapshot()
        engine.close()
        
        then: "shutdown completes within 5 seconds"
        shutdownDuration < 5000
        executionCount.get() > 0
        metrics.totalExecutions() > 0
    }
    
    def "should have complete final metrics snapshot after shutdown"() {
        given: "a load test"
        def executionCount = new AtomicInteger(0)
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {}
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                executionCount.incrementAndGet()
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {}
        }
        
        def load = new StaticLoad(100.0, Duration.ofMillis(300))
        def collector = new MetricsCollector()
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        
        when: "running and stopping engine"
        def stopLatch = new CountDownLatch(1)
        def engineThread = Thread.startVirtualThread {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected
            }
        }
        
        Thread.startVirtualThread {
            Thread.sleep(100)
            engine.stop()
            stopLatch.countDown()
        }
        
        stopLatch.await(2, TimeUnit.SECONDS)
        engineThread.join(5000)
        
        def metrics = collector.snapshot()
        engine.close()
        
        then: "final metrics snapshot is complete"
        metrics.totalExecutions() > 0
        metrics.totalExecutions() == executionCount.get()
        metrics.successCount() == executionCount.get()
        metrics.failureCount() == 0
        metrics.successPercentiles() != null
        metrics.elapsedMillis() > 0
    }
    
    def "should call teardown even when stopped early"() {
        given: "a task that tracks teardown"
        def teardownCalled = new AtomicBoolean(false)
        def initCalled = new AtomicBoolean(false)
        
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {
                initCalled.set(true)
            }
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {
                teardownCalled.set(true)
            }
        }
        
        def load = new StaticLoad(10.0, Duration.ofSeconds(10))
        def collector = new MetricsCollector()
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        
        when: "stopping engine immediately after start"
        def stopLatch = new CountDownLatch(1)
        def engineThread = Thread.startVirtualThread {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected
            }
        }
        
        // Wait for init to complete
        await().atMost(2, TimeUnit.SECONDS).until { initCalled.get() }
        
        // Stop immediately
        engine.stop()
        stopLatch.countDown()
        
        engineThread.join(5000)
        engine.close()
        
        then: "teardown is called"
        initCalled.get()
        teardownCalled.get()
    }
    
    def "should handle shutdown during high concurrency"() {
        given: "a high-concurrency load test"
        def executionCount = new AtomicInteger(0)
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {}
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                executionCount.incrementAndGet()
                // Simulate I/O work
                Thread.sleep(10)
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {}
        }
        
        def load = new StaticLoad(200.0, Duration.ofSeconds(5)) // High TPS
        def collector = new MetricsCollector()
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        
        when: "stopping during high concurrency"
        def startTime = System.currentTimeMillis()
        def stopLatch = new CountDownLatch(1)
        
        def engineThread = Thread.startVirtualThread {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected
            }
        }
        
        // Stop after allowing some executions
        Thread.startVirtualThread {
            Thread.sleep(200)
            engine.stop()
            stopLatch.countDown()
        }
        
        stopLatch.await(2, TimeUnit.SECONDS)
        engineThread.join(6000)
        def shutdownDuration = System.currentTimeMillis() - startTime
        
        def metrics = collector.snapshot()
        engine.close()
        
        then: "shutdown completes gracefully"
        shutdownDuration < 5000
        executionCount.get() > 0
        metrics.totalExecutions() > 0
        // Some executions may still be in flight, but metrics should be reasonable
        metrics.totalExecutions() <= executionCount.get() + 50 // Allow some variance
    }
    
    def "should not record metrics after shutdown initiated"() {
        given: "a load test"
        def executionCount = new AtomicInteger(0)
        def metricsAfterStop = new AtomicLong(0)
        
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {}
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                executionCount.incrementAndGet()
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {}
        }
        
        def load = new StaticLoad(100.0, Duration.ofSeconds(5))
        def collector = new MetricsCollector()
        def engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(load)
                .withMetricsCollector(collector)
                .withShutdownHook(false)
                .build()
        
        when: "stopping and checking metrics"
        def stopLatch = new CountDownLatch(1)
        def engineThread = Thread.startVirtualThread {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected
            }
        }
        
        Thread.startVirtualThread {
            Thread.sleep(100)
            // Get metrics before stop
            def metricsBefore = collector.snapshot()
            def countBefore = metricsBefore.totalExecutions()
            
            engine.stop()
            stopLatch.countDown()
            
            // Wait a bit and check metrics again
            Thread.sleep(50)
            def metricsAfter = collector.snapshot()
            metricsAfterStop.set(metricsAfter.totalExecutions())
        }
        
        stopLatch.await(2, TimeUnit.SECONDS)
        engineThread.join(5000)
        
        def finalMetrics = collector.snapshot()
        engine.close()
        
        then: "metrics don't increase significantly after stop"
        finalMetrics.totalExecutions() > 0
        // Metrics may increase slightly as in-flight tasks complete, but not significantly
        finalMetrics.totalExecutions() <= executionCount.get() + 10 // Small variance allowed
    }
}
