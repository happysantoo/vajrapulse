package com.vajrapulse.api

import spock.lang.Specification
import spock.lang.Timeout
import com.vajrapulse.api.pattern.StepLoad
import com.vajrapulse.api.pattern.SpikeLoad
import com.vajrapulse.api.pattern.SineWaveLoad
import java.time.Duration

@Timeout(10)
class LoadPatternCoverageSpec extends Specification {

    def "StepLoad getDuration should sum all step durations"() {
        given:
        def steps = [
            new StepLoad.Step(100.0, Duration.ofSeconds(10)),
            new StepLoad.Step(200.0, Duration.ofSeconds(20)),
            new StepLoad.Step(50.0, Duration.ofSeconds(5))
        ]
        def pattern = new StepLoad(steps)

        when:
        def totalDuration = pattern.getDuration()

        then:
        totalDuration == Duration.ofSeconds(35)
    }

    def "SpikeLoad getDuration should return total duration"() {
        given:
        def pattern = new SpikeLoad(
            100.0,  // base
            1000.0, // spike
            Duration.ofMinutes(5),  // total
            Duration.ofSeconds(30), // interval
            Duration.ofSeconds(5)   // spike duration
        )

        when:
        def totalDuration = pattern.getDuration()

        then:
        totalDuration == Duration.ofMinutes(5)
    }

    def "SineWaveLoad getDuration should return total duration"() {
        given:
        def pattern = new SineWaveLoad(
            100.0, // mean
            50.0,  // amplitude
            Duration.ofMinutes(5),   // total
            Duration.ofSeconds(60)   // period
        )

        when:
        def totalDuration = pattern.getDuration()

        then:
        totalDuration == Duration.ofMinutes(5)
    }

    def "StepLoad calculateTps edge cases"() {
        given:
        def steps = [
            new StepLoad.Step(100.0, Duration.ofMillis(10000)),
            new StepLoad.Step(200.0, Duration.ofMillis(10000))
        ]
        def pattern = new StepLoad(steps)

        expect:
        pattern.calculateTps(0) == 100.0
        pattern.calculateTps(5000) == 100.0
        pattern.calculateTps(10000) == 200.0  // exactly at boundary
        pattern.calculateTps(15000) == 200.0
        pattern.calculateTps(25000) == 0.0  // past end returns 0
    }

    def "SpikeLoad validation - rejects invalid parameters"() {
        when:
        new SpikeLoad(-1.0, 100.0, Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofSeconds(5))

        then:
        thrown(IllegalArgumentException)
    }

    def "SpikeLoad validation - rejects negative peak"() {
        when:
        new SpikeLoad(100.0, -10.0, Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofSeconds(5))

        then:
        thrown(IllegalArgumentException)
    }

    def "SpikeLoad validation - rejects zero warmup"() {
        when:
        new SpikeLoad(100.0, 200.0, Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(5))

        then:
        thrown(IllegalArgumentException)
    }

    def "SineWaveLoad validation - rejects negative baseline"() {
        when:
        new SineWaveLoad(-10.0, 100.0, Duration.ofSeconds(30), Duration.ofMinutes(5))

        then:
        thrown(IllegalArgumentException)
    }

    def "SineWaveLoad validation - rejects negative amplitude"() {
        when:
        new SineWaveLoad(100.0, -50.0, Duration.ofSeconds(30), Duration.ofMinutes(5))

        then:
        thrown(IllegalArgumentException)
    }

    def "SineWaveLoad calculateTps during different phases"() {
        given:
        def pattern = new SineWaveLoad(
            100.0,
            50.0,
            Duration.ofMinutes(3),
            Duration.ofSeconds(60)
        )

        expect:
        pattern.calculateTps(0) >= 100.0  // Start at baseline
        pattern.calculateTps(15000) >= 100.0  // Rising or falling
    }
}
