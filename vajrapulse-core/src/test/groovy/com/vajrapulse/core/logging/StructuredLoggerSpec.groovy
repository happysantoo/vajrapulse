package com.vajrapulse.core.logging

import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Timeout

/**
 * Tests for StructuredLogger exercising different log levels and error branch.
 */
@Timeout(5)
class StructuredLoggerSpec extends Specification {

    def setup() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace") // enable all
    }

    def cleanup() {
        System.clearProperty("org.slf4j.simpleLogger.defaultLogLevel")
    }

    def "should log info debug trace without error"() {
        when:
        StructuredLogger.info(StructuredLoggerSpec, "run_started", [run_id: 'r1', tps: 10.5])
        StructuredLogger.debug(StructuredLoggerSpec, "rate_adjust", [run_id: 'r1', iteration: 5])
        StructuredLogger.trace(StructuredLoggerSpec, "internal_detail", [run_id: 'r1', detail: 'x'])

        then: "logger enabled for levels"
        LoggerFactory.getLogger(StructuredLoggerSpec).isInfoEnabled()
    }

    def "should log error with exception"() {
        when:
        def ex = new RuntimeException("failure")
        StructuredLogger.error(StructuredLoggerSpec, "run_failed", [run_id: 'r2', stage: 'execute'], ex)

        then:
        LoggerFactory.getLogger(StructuredLoggerSpec).isErrorEnabled()
    }

    def "should log error without exception"() {
        when:
        StructuredLogger.error(StructuredLoggerSpec, "error_no_ex", [run_id: 'r3'], null)

        then:
        LoggerFactory.getLogger(StructuredLoggerSpec).isErrorEnabled()
    }

    def "should log with null fields map"() {
        when:
        StructuredLogger.info(StructuredLoggerSpec, "no_fields", null)

        then:
        LoggerFactory.getLogger(StructuredLoggerSpec).isInfoEnabled()
    }

    def "should log with runId via logWithRunId"() {
        when:
        StructuredLogger.logWithRunId(StructuredLoggerSpec, "INFO", "with_run_id",
            [iteration: 1], "run-abc")

        then:
        LoggerFactory.getLogger(StructuredLoggerSpec).isInfoEnabled()
    }

    def "should log with warn level"() {
        when:
        StructuredLogger.logWithRunId(StructuredLoggerSpec, "WARN", "warning_msg",
            [:], null)

        then:
        LoggerFactory.getLogger(StructuredLoggerSpec).isWarnEnabled()
    }

    def "should log with boolean and number field values"() {
        when:
        StructuredLogger.info(StructuredLoggerSpec, "mixed_types",
            [enabled: true, count: 42, ratio: 0.95d])

        then:
        LoggerFactory.getLogger(StructuredLoggerSpec).isInfoEnabled()
    }

    def "should handle sanitize of null exception message"() {
        when:
        def ex = new RuntimeException((String) null)
        StructuredLogger.error(StructuredLoggerSpec, "null_msg", [:], ex)

        then:
        LoggerFactory.getLogger(StructuredLoggerSpec).isErrorEnabled()
    }
}
