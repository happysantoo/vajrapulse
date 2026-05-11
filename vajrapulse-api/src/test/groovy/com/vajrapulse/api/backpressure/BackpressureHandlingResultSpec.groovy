package com.vajrapulse.api.backpressure

import spock.lang.Specification
import spock.lang.Timeout

@Timeout(5)
class BackpressureHandlingResultSpec extends Specification {

    def "should have all four expected enum values"() {
        expect:
        BackpressureHandlingResult.values().length == 4
        BackpressureHandlingResult.valueOf("DROPPED") == BackpressureHandlingResult.DROPPED
        BackpressureHandlingResult.valueOf("QUEUED") == BackpressureHandlingResult.QUEUED
        BackpressureHandlingResult.valueOf("REJECTED") == BackpressureHandlingResult.REJECTED
        BackpressureHandlingResult.valueOf("ACCEPTED") == BackpressureHandlingResult.ACCEPTED
    }

    def "should resolve valueOf case-sensitively"() {
        when:
        BackpressureHandlingResult.valueOf("dropped")

        then:
        thrown(IllegalArgumentException)
    }
}
