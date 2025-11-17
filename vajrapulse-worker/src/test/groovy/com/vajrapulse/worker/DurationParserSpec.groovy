package com.vajrapulse.worker

import spock.lang.Specification

class DurationParserSpec extends Specification {

    def "parseDuration should parse seconds"() {
        when:
        def worker = new VajraPulseWorker()
        def duration = worker.parseDuration("30s")

        then:
        duration == java.time.Duration.ofSeconds(30)
    }

    def "parseDuration should parse minutes"() {
        when:
        def worker = new VajraPulseWorker()
        def duration = worker.parseDuration("5m")

        then:
        duration == java.time.Duration.ofMinutes(5)
    }

    def "parseDuration should parse hours"() {
        when:
        def worker = new VajraPulseWorker()
        def duration = worker.parseDuration("2h")

        then:
        duration == java.time.Duration.ofHours(2)
    }

    def "parseDuration should parse milliseconds"() {
        when:
        def worker = new VajraPulseWorker()
        def duration = worker.parseDuration("500ms")

        then:
        duration == java.time.Duration.ofMillis(500)
    }

    def "parseDuration should throw on invalid format"() {
        when:
        def worker = new VajraPulseWorker()
        worker.parseDuration("invalid")

        then:
        thrown(IllegalArgumentException)
    }

    def "parseDuration should throw on invalid unit"() {
        when:
        def worker = new VajraPulseWorker()
        worker.parseDuration("10x")

        then:
        thrown(IllegalArgumentException)
    }
}
