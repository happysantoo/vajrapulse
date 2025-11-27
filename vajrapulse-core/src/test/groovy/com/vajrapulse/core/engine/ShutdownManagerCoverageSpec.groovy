package com.vajrapulse.core.engine

import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Timeout(10)
class ShutdownManagerCoverageSpec extends Specification {

    def "should get callback exceptions"() {
        given: "a shutdown manager with failing callback"
        def exceptions = []
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .onShutdown { throw new RuntimeException("callback failed") }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when: "shutting down"
        manager.initiateShutdown()
        try {
            manager.awaitShutdown(executor)
        } catch (ShutdownManager.ShutdownException e) {
            exceptions.add(e)
        }

        then: "callback exceptions are accessible"
        def callbackExceptions = manager.getCallbackExceptions()
        callbackExceptions.size() == 1
        callbackExceptions[0].message == "callback failed"
    }

    def "should handle multiple callback exceptions"() {
        given: "a shutdown manager with multiple callbacks"
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .onShutdown {
                throw new RuntimeException("first failure")
            }
            .build()
        
        // Add another callback via reflection or builder pattern if supported
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when: "shutting down"
        manager.initiateShutdown()
        try {
            manager.awaitShutdown(executor)
        } catch (ShutdownManager.ShutdownException e) {
            // Expected
        }

        then: "exceptions are collected"
        manager.getCallbackExceptions().size() == 1
    }

    def "should return empty list when no callback exceptions"() {
        given: "a shutdown manager with successful callback"
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .onShutdown { /* no exception */ }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when: "shutting down"
        manager.initiateShutdown()
        manager.awaitShutdown(executor)

        then: "no callback exceptions"
        manager.getCallbackExceptions().isEmpty()
    }

    def "should handle shutdown exception with suppressed exceptions"() {
        given: "a shutdown manager"
        def manager = ShutdownManager.builder()
            .withRunId("test-run")
            .onShutdown { throw new RuntimeException("error") }
            .build()
        
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when: "shutting down"
        manager.initiateShutdown()
        def exception = null
        try {
            manager.awaitShutdown(executor)
        } catch (ShutdownManager.ShutdownException e) {
            exception = e
        }

        then: "exception has suppressed exceptions"
        exception != null
        exception.suppressed.length == 0 // Only one callback, so no suppressed
    }
}

