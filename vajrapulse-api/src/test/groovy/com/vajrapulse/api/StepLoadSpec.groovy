package com.vajrapulse.api

import spock.lang.Specification
import com.vajrapulse.api.pattern.StepLoad

import java.time.Duration

class StepLoadSpec extends Specification {

    def "step load computes correct rates across steps"() {
        given:
        def pattern = new StepLoad([
                new StepLoad.Step(50.0d, Duration.ofMillis(500)),
                new StepLoad.Step(100.0d, Duration.ofMillis(1000))
        ])

        expect:
        pattern.calculateTps(0) == 50.0d
        pattern.calculateTps(499) == 50.0d
        pattern.calculateTps(500) == 100.0d
        pattern.calculateTps(1499) == 100.0d
        pattern.calculateTps(1500) == 0.0d // past end
    }

    def "step load aggregates total duration"() {
        given:
        def pattern = new StepLoad([
                new StepLoad.Step(10.0d, Duration.ofSeconds(1)),
                new StepLoad.Step(20.0d, Duration.ofSeconds(2)),
                new StepLoad.Step(30.0d, Duration.ofMillis(500))
        ])

        expect:
        pattern.duration == Duration.ofMillis(1000 + 2000 + 500)
    }

    def "step load validation rejects empty steps"() {
        when:
        new StepLoad([])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("steps must not be empty")
    }

    def "step load validation rejects negative rate"() {
        when:
        new StepLoad([
                new StepLoad.Step(-1.0d, Duration.ofSeconds(1))
        ])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.toLowerCase().contains("step rate")
    }

    def "step load validation rejects zero duration"() {
        when:
        new StepLoad([
                new StepLoad.Step(10.0d, Duration.ZERO)
        ])

        then:
        def e = thrown(IllegalArgumentException)
        e.message.toLowerCase().contains("step duration")
    }
}
