package com.vajrapulse.core.tracing

import io.opentelemetry.api.trace.Span
import spock.lang.Specification
import spock.lang.Timeout

/**
 * Tests for Tracing bootstrap & span helpers. Uses system properties path to enable.
 */
@Timeout(10)
class TracingSpec extends Specification {

    def setup() {
        System.setProperty("vajrapulse.trace.enabled", "true")
        System.setProperty("vajrapulse.otel.traces.endpoint", "http://localhost:4317")
    }

    def cleanup() {
        System.clearProperty("vajrapulse.trace.enabled")
        System.clearProperty("vajrapulse.otel.traces.endpoint")
    }

    def "should initialize tracing and create scenario + execution spans"() {
        when:
        Tracing.initIfEnabled("rid-1")
        def scenario = Tracing.startScenarioSpan("rid-1", "TestTask", "StaticLoad")
        def exec = Tracing.startExecutionSpan(scenario, "rid-1", 42L)
        Tracing.markSuccess(exec)
        exec.end()
        scenario.end()

        then:
        scenario != Span.getInvalid()
        exec != Span.getInvalid()
        Tracing.enabled
    }

    def "should mark failure and record exception"() {
        given:
        Tracing.initIfEnabled("rid-2")
        def scenario = Tracing.startScenarioSpan("rid-2", "TestTask", "StaticLoad")
        def exec = Tracing.startExecutionSpan(scenario, "rid-2", 1L)

        when:
        def ex = new IllegalStateException("boom")
        Tracing.markFailure(exec, ex)
        exec.end(); scenario.end()

        then: "span should not be invalid"
        exec != Span.getInvalid()
    }
}
