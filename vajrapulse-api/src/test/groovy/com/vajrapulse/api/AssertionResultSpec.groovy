package com.vajrapulse.api

import spock.lang.Specification
import spock.lang.Timeout
import com.vajrapulse.api.assertion.AssertionResult

@Timeout(10)
class AssertionResultSpec extends Specification {

    def "should create successful result"() {
        when:
        def result = AssertionResult.pass()

        then:
        result.success()
        result.passed()
        !result.failed()
        result.message() == null
    }

    def "should create successful result with message"() {
        when:
        def result = AssertionResult.pass("All checks passed")

        then:
        result.success()
        result.passed()
        !result.failed()
        result.message() == "All checks passed"
    }

    def "should create failed result with message"() {
        when:
        def result = AssertionResult.failure("Latency exceeded threshold")

        then:
        !result.success()
        !result.passed()
        result.failed()
        result.message() == "Latency exceeded threshold"
    }

    def "should create failed result with formatted message"() {
        when:
        def result = AssertionResult.failure("P%.0f latency %.2fms exceeds %.2fms", 95.0, 150.5, 100.0)

        then:
        result.failed()
        result.message() == "P95 latency 150.50ms exceeds 100.00ms"
    }

    def "should reject null failure message"() {
        when:
        AssertionResult.failure(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject blank failure message"() {
        when:
        AssertionResult.failure("   ")

        then:
        thrown(IllegalArgumentException)
    }
}

