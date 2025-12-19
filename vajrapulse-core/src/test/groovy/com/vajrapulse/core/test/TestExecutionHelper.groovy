package com.vajrapulse.core.test

import com.vajrapulse.core.engine.ExecutionEngine
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static org.awaitility.Awaitility.*
import static java.util.concurrent.TimeUnit.*

/**
 * Test utilities for executing ExecutionEngine with proper synchronization and timeout handling.
 * 
 * <p>This helper class provides common patterns for testing ExecutionEngine in a reliable way,
 * eliminating the need for background threads and Thread.sleep() calls.
 * 
 * <p><strong>Example usage:</strong>
 * <pre>{@code
 * def engine = ExecutionEngine.builder()
 *     .withTask(task)
 *     .withLoadPattern(pattern)
 *     .withMetricsCollector(collector)
 *     .withShutdownHook(false)
 *     .build()
 * 
 * // Run engine and wait for completion with timeout
 * TestExecutionHelper.runWithTimeout(engine, Duration.ofSeconds(10))
 * 
 * // Or run and stop after condition is met
 * TestExecutionHelper.runUntilCondition(engine, { collector.snapshot().totalExecutions() > 100 })
 * }</pre>
 * 
 * @since 0.9.9
 */
class TestExecutionHelper {
    
    /**
     * Runs an ExecutionEngine with timeout protection and proper synchronization.
     * 
     * <p>This method runs the engine in a virtual thread and waits for completion
     * with a timeout. If the engine doesn't complete within the timeout, an
     * AssertionError is thrown.
     * 
     * @param engine the ExecutionEngine to run
     * @param timeout maximum time to wait for completion
     * @throws AssertionError if engine doesn't complete within timeout
     */
    static void runWithTimeout(ExecutionEngine engine, Duration timeout) {
        def executionLatch = new CountDownLatch(1)
        def executionThread = Thread.startVirtualThread {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected - test may stop engine
            } finally {
                executionLatch.countDown()
            }
        }
        
        // Wait for execution to complete with timeout
        def timeoutSeconds = timeout.toSeconds()
        assert executionLatch.await(timeoutSeconds, TimeUnit.SECONDS) : 
            "Engine should complete within ${timeoutSeconds} seconds"
    }
    
    /**
     * Runs an ExecutionEngine until a condition is met, then stops it.
     * 
     * <p>This method runs the engine in a virtual thread, waits for the condition
     * to become true, then stops the engine and waits for completion.
     * 
     * @param engine the ExecutionEngine to run
     * @param condition closure that returns true when condition is met
     * @param timeout maximum time to wait for condition
     * @throws AssertionError if condition isn't met within timeout
     */
    static void runUntilCondition(ExecutionEngine engine, Closure<Boolean> condition, Duration timeout) {
        def executionLatch = new CountDownLatch(1)
        def executionThread = Thread.startVirtualThread {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected - test will stop engine
            } finally {
                executionLatch.countDown()
            }
        }
        
        // Wait for condition to be met
        await().atMost(timeout.toSeconds(), SECONDS)
            .pollInterval(100, MILLISECONDS)
            .until(condition)
        
        // Stop the engine
        engine.stop()
        
        // Wait for execution to complete
        assert executionLatch.await(5, TimeUnit.SECONDS) : 
            "Engine should complete within 5 seconds after stop"
    }
    
    /**
     * Waits for a condition to become true using Awaitility.
     * 
     * <p>This is a convenience method that wraps Awaitility for common test patterns.
     * 
     * @param condition closure that returns true when condition is met
     * @param timeout maximum time to wait
     * @param pollInterval how often to check the condition
     */
    static void awaitCondition(Closure<Boolean> condition, Duration timeout, Duration pollInterval = Duration.ofMillis(100)) {
        await().atMost(timeout.toSeconds(), SECONDS)
            .pollInterval(pollInterval.toMillis(), MILLISECONDS)
            .until(condition)
    }
}
