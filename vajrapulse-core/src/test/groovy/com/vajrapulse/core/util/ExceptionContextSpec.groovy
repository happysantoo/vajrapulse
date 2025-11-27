package com.vajrapulse.core.util

import spock.lang.Specification

class ExceptionContextSpec extends Specification {

    def "should create exception with runId context"() {
        when: "creating exception with runId"
        def exception = ExceptionContext.withContext("test-run-123", "Task execution failed")

        then: "exception message includes runId"
        exception.message.contains("[runId=test-run-123]")
        exception.message.contains("Task execution failed")
    }

    def "should create exception with runId and cause"() {
        given: "a cause exception"
        def cause = new RuntimeException("Original error")

        when: "creating exception with runId and cause"
        def exception = ExceptionContext.withContext("test-run-123", "Task execution failed", cause)

        then: "exception message includes runId and cause is preserved"
        exception.message.contains("[runId=test-run-123]")
        exception.message.contains("Task execution failed")
        exception.cause == cause
    }

    def "should create exception with full context"() {
        when: "creating exception with full context"
        def exception = ExceptionContext.withContext("test-run-123", 42L, "RAMP_UP", "Task execution failed")

        then: "exception message includes all context"
        exception.message.contains("[runId=test-run-123]")
        exception.message.contains("[iteration=42]")
        exception.message.contains("[phase=RAMP_UP]")
        exception.message.contains("Task execution failed")
    }

    def "should create exception with full context and cause"() {
        given: "a cause exception"
        def cause = new IllegalStateException("Original error")

        when: "creating exception with full context and cause"
        def exception = ExceptionContext.withContext("test-run-123", 42L, "RAMP_UP", "Task execution failed", cause)

        then: "exception message includes all context and cause is preserved"
        exception.message.contains("[runId=test-run-123]")
        exception.message.contains("[iteration=42]")
        exception.message.contains("[phase=RAMP_UP]")
        exception.message.contains("Task execution failed")
        exception.cause == cause
    }

    def "should handle null runId"() {
        when: "creating exception with null runId"
        def exception = ExceptionContext.withContext(null, "Task execution failed")

        then: "exception message does not include runId"
        !exception.message.contains("[runId=")
        exception.message.contains("Task execution failed")
    }

    def "should handle null iteration"() {
        when: "creating exception with null iteration"
        def exception = ExceptionContext.withContext("test-run-123", null, "RAMP_UP", "Task execution failed")

        then: "exception message does not include iteration"
        !exception.message.contains("[iteration=")
        exception.message.contains("[runId=test-run-123]")
        exception.message.contains("[phase=RAMP_UP]")
    }

    def "should handle null phase"() {
        when: "creating exception with null phase"
        def exception = ExceptionContext.withContext("test-run-123", 42L, null, "Task execution failed")

        then: "exception message does not include phase"
        !exception.message.contains("[phase=")
        exception.message.contains("[runId=test-run-123]")
        exception.message.contains("[iteration=42]")
    }

    def "should wrap existing exception with context"() {
        given: "an existing exception"
        def cause = new RuntimeException("Original error")

        when: "wrapping with context"
        def exception = ExceptionContext.wrapWithContext("test-run-123", "Wrapped error", cause)

        then: "exception message includes context and cause is preserved"
        exception.message.contains("[runId=test-run-123]")
        exception.message.contains("Wrapped error")
        exception.cause == cause
    }

    def "should format message correctly with all fields"() {
        when: "creating exception with all context fields"
        def exception = ExceptionContext.withContext("run-123", 100L, "SUSTAIN", "Execution failed")

        then: "message format is correct"
        def message = exception.message
        message.startsWith("[runId=run-123]")
        message.contains("[iteration=100]")
        message.contains("[phase=SUSTAIN]")
        message.endsWith("Execution failed")
    }
}

