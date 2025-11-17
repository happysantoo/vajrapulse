package com.vajrapulse.core.perf

import spock.lang.Specification

class PerformanceHarnessMainSpec extends Specification {

    def "harness main runs with ms duration"() {
        when:
        PerformanceHarness.main(["500","500ms"] as String[])
        then:
        noExceptionThrown()
    }

    def "harness main runs with seconds duration"() {
        when:
        PerformanceHarness.main(["250","1s"] as String[])
        then:
        noExceptionThrown()
    }

    def "harness main runs with minutes duration"() {
        when:
        PerformanceHarness.main(["10","1m"] as String[])
        then:
        noExceptionThrown()
    }
}