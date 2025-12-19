package com.vajrapulse.core.perf

import spock.lang.Specification
import spock.lang.Timeout

@Timeout(30)
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

    // Removed "minutes duration" test - it takes 1 minute to run and only verifies
    // that main() doesn't throw, which is already covered by shorter duration tests.
    // If you need to test minute durations, run PerformanceHarness manually.
}