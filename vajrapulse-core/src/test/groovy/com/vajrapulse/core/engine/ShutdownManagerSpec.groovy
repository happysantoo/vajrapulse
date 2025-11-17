package com.vajrapulse.core.engine

import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
        Thread.sleep(200) // Give interrupt a chance to propagate
        taskInterrupted.get()
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

    def "should handle callback exceptions gracefully"() {
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
        notThrown(Exception)
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
}
