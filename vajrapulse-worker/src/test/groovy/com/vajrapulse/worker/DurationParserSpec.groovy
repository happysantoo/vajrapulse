package com.vajrapulse.worker

import spock.lang.Specification
import spock.lang.Timeout

@Timeout(10)
class DurationParserSpec extends Specification {

    def "parseDuration should parse seconds"() {
        when:
        def duration = LoadPatternFactory.parseDuration("30s")

        then:
        duration == java.time.Duration.ofSeconds(30)
    }

    def "parseDuration should parse minutes"() {
        when:
        def duration = LoadPatternFactory.parseDuration("5m")

        then:
        duration == java.time.Duration.ofMinutes(5)
    }

    def "parseDuration should parse hours"() {
        when:
        def duration = LoadPatternFactory.parseDuration("2h")

        then:
        duration == java.time.Duration.ofHours(2)
    }

    def "parseDuration should parse milliseconds"() {
        when:
        def duration = LoadPatternFactory.parseDuration("500ms")

        then:
        duration == java.time.Duration.ofMillis(500)
    }

    def "parseDuration should throw on invalid format"() {
        when:
        LoadPatternFactory.parseDuration("invalid")

        then:
        thrown(IllegalArgumentException)
    }

    def "parseDuration should throw on invalid unit"() {
        when:
        LoadPatternFactory.parseDuration("10x")

        then:
        thrown(IllegalArgumentException)
    }
}
