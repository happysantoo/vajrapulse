package com.vajrapulse.core.engine

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import static org.awaitility.Awaitility.*
import static java.util.concurrent.TimeUnit.*

/**
 * Tests for ShutdownManager graceful shutdown functionality.
 */
@Timeout(15)
class ShutdownManagerSpec extends Specification {

    def "should create shutdown manager with defaults"() {
        when:
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .build()

        then:
        manager != null
        !manager.isShutdownRequested()
    }

    def "should initiate shutdown when requested"() {
        given:
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .build()

        when:
        def result = manager.initiateShutdown()

        then:
        result == true
        manager.isShutdownRequested()
    }

    def "should be idempotent - multiple shutdown calls"() {
        given:
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .build()

        when:
        def first = manager.initiateShutdown()
        def second = manager.initiateShutdown()
        def third = manager.initiateShutdown()

        then:
        first == true
        second == false
        third == false
        manager.isShutdownRequested()
    }

    def "should execute shutdown callback during awaitShutdown"() {
        given:
        def callbackExecuted = new AtomicBoolean(false)
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .onShutdown { callbackExecuted.set(true) }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        manager.awaitShutdown(executor)

        then:
        callbackExecuted.get()
    }

    def "should drain executor gracefully within timeout"() {
        given:
        def tasksCompleted = new AtomicBoolean(false)
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .withDrainTimeout(Duration.ofSeconds(2))
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()
        
        // Submit a short task
        executor.submit {
            Thread.sleep(100)
            tasksCompleted.set(true)
        }

        when:
        manager.initiateShutdown()
        def graceful = manager.awaitShutdown(executor)

        then:
        graceful == true
        tasksCompleted.get()
    }

    def "should force shutdown if drain timeout exceeded"() {
        given:
        def taskInterrupted = new AtomicBoolean(false)
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .withDrainTimeout(Duration.ofMillis(100))
            .withForceTimeout(Duration.ofMillis(500))
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()
        
        // Submit a long-running task that will exceed drain timeout
        executor.submit {
            try {
                Thread.sleep(5000) // Much longer than drain timeout
            } catch (InterruptedException e) {
                taskInterrupted.set(true)
            }
        }

        when:
        manager.initiateShutdown()
        def graceful = manager.awaitShutdown(executor)

        then:
        graceful == false
        // Force shutdown should interrupt the task
        await().atMost(1, SECONDS)
            .until { taskInterrupted.get() }
    }

    def "should register and remove shutdown hook"() {
        given:
        def manager = ShutdownManager.builder()
            .withRunId("test-hook")
            .build()

        when:
        manager.registerShutdownHook()
        def removed = manager.removeShutdownHook()

        then:
        removed == true
    }

    def "should handle remove shutdown hook when not registered"() {
        given:
        def manager = ShutdownManager.builder()
            .withRunId("test-hook")
            .build()

        when:
        def removed = manager.removeShutdownHook()

        then:
        removed == false
    }

    def "should track shutdown completion"() {
        given:
        def latch = new CountDownLatch(1)
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .onShutdown { latch.countDown() }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        manager.awaitShutdown(executor)
        
        def completed = latch.await(1, TimeUnit.SECONDS)

        then:
        completed
    }

    def "should handle callback exceptions and report them"() {
        given:
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .onShutdown { throw new RuntimeException("callback failed") }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        manager.awaitShutdown(executor)

        then:
        def exception = thrown(ShutdownManager.ShutdownException)
        // Exception message format: "Shutdown callback failed for runId=..."
        exception.message.contains("Shutdown callback failed") || exception.message.contains("callback failed")
        manager.getCallbackExceptions().size() == 1
        // The actual exception message should match
        def callbackException = manager.getCallbackExceptions()[0]
        callbackException.message == "callback failed" || callbackException.cause?.message == "callback failed"
    }

    def "should use custom timeouts"() {
        given:
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .withDrainTimeout(Duration.ofMillis(50))
            .withForceTimeout(Duration.ofMillis(100))
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        long start = System.nanoTime()
        manager.awaitShutdown(executor)
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        then:
        elapsed < 500 // Should complete quickly with short timeouts
    }

    def "should support concurrent shutdown requests"() {
        given:
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .build()
        
        def executor = Executors.newFixedThreadPool(10)
        def initiators = new AtomicBoolean[10]
        
        for (int i = 0; i < 10; i++) {
            initiators[i] = new AtomicBoolean(false)
        }

        when: "multiple threads try to initiate shutdown"
        def futures = []
        for (int i = 0; i < 10; i++) {
            final int index = i
            futures << executor.submit {
                def result = manager.initiateShutdown()
                initiators[index].set(result)
            }
        }
        
        futures.each { it.get() }
        def trueCount = initiators.count { it.get() }

        then: "only one succeeds"
        trueCount == 1
        manager.isShutdownRequested()
        
        cleanup:
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)
    }

    def "should timeout callback execution"() {
        given:
        def manager = ShutdownManager.builder()
            .withRunId("test-timeout")
            .withCallbackTimeout(Duration.ofMillis(100))
            .onShutdown {
                Thread.sleep(500) // Exceeds timeout
            }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        manager.awaitShutdown(executor)

        then:
        def exception = thrown(ShutdownManager.ShutdownException)
        exception.cause instanceof TimeoutException
        manager.getCallbackExceptions().size() == 1
        manager.getCallbackExceptions()[0] instanceof TimeoutException
    }

    def "should record metrics for callback failures"() {
        given:
        def registry = new SimpleMeterRegistry()
        def manager = ShutdownManager.builder()
            .withRunId("test-metrics")
            .withRegistry(registry)
            .onShutdown { throw new RuntimeException("callback failed") }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        manager.awaitShutdown(executor)

        then:
        def exception = thrown(ShutdownManager.ShutdownException)
        
        and: "metrics counter incremented"
        def counter = registry.find("vajrapulse.shutdown.callback.failures").counter()
        counter != null
        counter.count() == 1.0
        counter.getId().getTag("run_id") == "test-metrics"
    }

    def "should record metrics for callback timeout"() {
        given:
        def registry = new SimpleMeterRegistry()
        def manager = ShutdownManager.builder()
            .withRunId("test-timeout-metrics")
            .withRegistry(registry)
            .withCallbackTimeout(Duration.ofMillis(50))
            .onShutdown {
                Thread.sleep(200) // Exceeds timeout
            }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        manager.awaitShutdown(executor)

        then:
        def exception = thrown(ShutdownManager.ShutdownException)
        
        and: "metrics counter incremented for timeout"
        def counter = registry.find("vajrapulse.shutdown.callback.failures").counter()
        counter != null
        counter.count() == 1.0
    }

    def "should handle multiple callback failures"() {
        given:
        def registry = new SimpleMeterRegistry()
        def failureCount = new AtomicBoolean(false)
        def manager = ShutdownManager.builder()
            .withRunId("test-multiple")
            .withRegistry(registry)
            .onShutdown {
                if (failureCount.compareAndSet(false, true)) {
                    throw new RuntimeException("first failure")
                } else {
                    throw new IllegalStateException("second failure")
                }
            }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        manager.awaitShutdown(executor)

        then:
        def exception = thrown(ShutdownManager.ShutdownException)
        exception.suppressed.length > 0 || exception.cause != null
        
        and: "all exceptions collected"
        manager.getCallbackExceptions().size() >= 1
        
        and: "metrics counter incremented"
        def counter = registry.find("vajrapulse.shutdown.callback.failures").counter()
        counter != null
        counter.count() >= 1.0
    }

    def "should work without registry (no metrics)"() {
        given:
        def manager = ShutdownManager.builder()
            .withRunId("test-no-registry")
            .onShutdown { throw new RuntimeException("callback failed") }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        manager.awaitShutdown(executor)

        then: "exception still thrown but no metrics"
        thrown(ShutdownManager.ShutdownException)
        manager.getCallbackExceptions().size() == 1
    }

    def "should use custom callback timeout"() {
        given:
        def manager = ShutdownManager.builder()
            .withRunId("test-custom-timeout")
            .withCallbackTimeout(Duration.ofSeconds(2))
            .onShutdown {
                Thread.sleep(100) // Should complete within timeout
            }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        manager.awaitShutdown(executor)

        then: "callback completes successfully"
        manager.getCallbackExceptions().isEmpty()
        noExceptionThrown()
    }

    def "should handle callback that completes quickly"() {
        given:
        def registry = new SimpleMeterRegistry()
        def callbackExecuted = new AtomicBoolean(false)
        def manager = ShutdownManager.builder()
            .withRunId("test-quick")
            .withRegistry(registry)
            .withCallbackTimeout(Duration.ofSeconds(1))
            .onShutdown {
                callbackExecuted.set(true)
            }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        manager.initiateShutdown()
        manager.awaitShutdown(executor)

        then: "callback executed successfully"
        callbackExecuted.get()
        manager.getCallbackExceptions().isEmpty()
        
        and: "no metrics recorded for successful callback"
        def counter = registry.find("vajrapulse.shutdown.callback.failures").counter()
        counter == null || counter.count() == 0.0
    }
}
