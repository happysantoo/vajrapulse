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
}
