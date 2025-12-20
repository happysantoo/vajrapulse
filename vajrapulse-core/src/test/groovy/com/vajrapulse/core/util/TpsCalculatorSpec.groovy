package com.vajrapulse.core.util

import spock.lang.Specification
import spock.lang.Timeout

@Timeout(10)
class TpsCalculatorSpec extends Specification {

    def "should calculate actual TPS correctly"() {
        when: "calculating TPS for 100 executions in 1 second"
        def tps = TpsCalculator.calculateActualTps(100L, 1000L)

        then: "TPS is 100"
        tps == 100.0
    }

    def "should calculate actual TPS for fractional seconds"() {
        when: "calculating TPS for 50 executions in 500ms"
        def tps = TpsCalculator.calculateActualTps(50L, 500L)

        then: "TPS is 100"
        tps == 100.0
    }

    def "should return zero for zero elapsed time"() {
        when: "calculating TPS with zero elapsed time"
        def tps = TpsCalculator.calculateActualTps(100L, 0L)

        then: "TPS is zero"
        tps == 0.0
    }

    def "should return zero for negative elapsed time"() {
        when: "calculating TPS with negative elapsed time"
        def tps = TpsCalculator.calculateActualTps(100L, -1000L)

        then: "TPS is zero"
        tps == 0.0
    }

    def "should handle high TPS values"() {
        when: "calculating TPS for 10000 executions in 1 second"
        def tps = TpsCalculator.calculateActualTps(10000L, 1000L)

        then: "TPS is 10000"
        tps == 10000.0
    }

    def "should handle low TPS values"() {
        when: "calculating TPS for 1 execution in 10 seconds"
        def tps = TpsCalculator.calculateActualTps(1L, 10000L)

        then: "TPS is 0.1"
        tps == 0.1
    }

    def "should calculate TPS error correctly"() {
        when: "calculating error for target 100 TPS, actual 90 TPS"
        def error = TpsCalculator.calculateTpsError(100.0, 90L, 1000L)

        then: "error is 10 (target - actual)"
        error == 10.0
    }

    def "should calculate negative TPS error when ahead of target"() {
        when: "calculating error for target 100 TPS, actual 110 TPS"
        def error = TpsCalculator.calculateTpsError(100.0, 110L, 1000L)

        then: "error is -10 (target - actual)"
        error == -10.0
    }

    def "should return target TPS as error when elapsed time is zero"() {
        when: "calculating error with zero elapsed time"
        def error = TpsCalculator.calculateTpsError(100.0, 50L, 0L)

        then: "error equals target TPS"
        error == 100.0
    }

    def "should calculate expected count correctly"() {
        when: "calculating expected count for 100 TPS over 1 second"
        def count = TpsCalculator.calculateExpectedCount(100.0, 1000L)

        then: "expected count is 100"
        count == 100L
    }

    def "should calculate expected count for fractional seconds"() {
        when: "calculating expected count for 100 TPS over 500ms"
        def count = TpsCalculator.calculateExpectedCount(100.0, 500L)

        then: "expected count is 50"
        count == 50L
    }

    def "should return zero for zero target TPS"() {
        when: "calculating expected count with zero target TPS"
        def count = TpsCalculator.calculateExpectedCount(0.0, 1000L)

        then: "expected count is zero"
        count == 0L
    }

    def "should return zero for negative target TPS"() {
        when: "calculating expected count with negative target TPS"
        def count = TpsCalculator.calculateExpectedCount(-10.0, 1000L)

        then: "expected count is zero"
        count == 0L
    }

    def "should return zero for zero elapsed time"() {
        when: "calculating expected count with zero elapsed time"
        def count = TpsCalculator.calculateExpectedCount(100.0, 0L)

        then: "expected count is zero"
        count == 0L
    }

    def "should handle high TPS in expected count"() {
        when: "calculating expected count for 10000 TPS over 1 second"
        def count = TpsCalculator.calculateExpectedCount(10000.0, 1000L)

        then: "expected count is 10000"
        count == 10000L
    }

    def "should handle fractional TPS in expected count"() {
        when: "calculating expected count for 0.5 TPS over 2 seconds"
        def count = TpsCalculator.calculateExpectedCount(0.5, 2000L)

        then: "expected count is 1"
        count == 1L
    }
}

