package com.vajrapulse.worker

import com.vajrapulse.api.pattern.*
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration

@Timeout(10)
class LoadPatternFactorySpec extends Specification {

    def "should create static load pattern"() {
        when:
        def pattern = LoadPatternFactory.create(
            "static", 100.0, "30s", null, null,
            null, null, null, null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        pattern instanceof StaticLoad
        pattern.getDuration() == Duration.ofSeconds(30)
    }

    def "should create ramp load pattern"() {
        when:
        def pattern = LoadPatternFactory.create(
            "ramp", 200.0, "60s", "30s", null,
            null, null, null, null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        pattern instanceof RampUpLoad
    }

    def "should create ramp-sustain load pattern"() {
        when:
        def pattern = LoadPatternFactory.create(
            "ramp-sustain", 150.0, "90s", "30s", null,
            null, null, null, null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        pattern instanceof RampUpToMaxLoad
    }

    def "should create step load pattern"() {
        when:
        def pattern = LoadPatternFactory.create(
            "step", 0, "60s", null, "50:30s,200:30s",
            null, null, null, null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        pattern instanceof StepLoad
    }

    def "should throw for step mode with null steps"() {
        when:
        LoadPatternFactory.create(
            "step", 0, "60s", null, null,
            null, null, null, null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should throw for step mode with blank steps"() {
        when:
        LoadPatternFactory.create(
            "step", 0, "60s", null, "   ",
            null, null, null, null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should create sine load pattern"() {
        when:
        def pattern = LoadPatternFactory.create(
            "sine", 0, "120s", null, null,
            100.0, 50.0, "30s", null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        pattern instanceof SineWaveLoad
    }

    def "should throw for sine mode with missing params"() {
        when:
        LoadPatternFactory.create(
            "sine", 0, "120s", null, null,
            null, null, null, null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should create spike load pattern"() {
        when:
        def pattern = LoadPatternFactory.create(
            "spike", 0, "60s", null, null,
            null, null, null, 50.0, 500.0, "10s", "2s",
            0, 0, 0, null, null, null, 0, null)

        then:
        pattern instanceof SpikeLoad
    }

    def "should throw for spike mode with missing params"() {
        when:
        LoadPatternFactory.create(
            "spike", 0, "60s", null, null,
            null, null, null, null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should create adaptive load pattern"() {
        given:
        def collector = new MetricsCollector()

        when:
        def pattern = LoadPatternFactory.create(
            "adaptive", 0, "120s", null, null,
            null, null, null, null, null, null, null,
            10.0, 5.0, 2.0, "10s", "100", "30s", 0.05, collector)

        then:
        pattern instanceof AdaptiveLoadPattern

        cleanup:
        collector?.close()
    }

    def "should create adaptive load pattern with unlimited maxTps"() {
        given:
        def collector = new MetricsCollector()

        when:
        def pattern = LoadPatternFactory.create(
            "adaptive", 0, "120s", null, null,
            null, null, null, null, null, null, null,
            10.0, 5.0, 2.0, "10s", "unlimited", "30s", 0.05, collector)

        then:
        pattern instanceof AdaptiveLoadPattern

        cleanup:
        collector?.close()
    }

    def "should throw for unknown mode"() {
        when:
        LoadPatternFactory.create(
            "invalid_mode", 0, "30s", null, null,
            null, null, null, null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("Unknown mode")
        ex.message.contains("invalid_mode")
    }

    def "should handle mode case-insensitively"() {
        when:
        def pattern = LoadPatternFactory.create(
            "STATIC", 50.0, "10s", null, null,
            null, null, null, null, null, null, null,
            0, 0, 0, null, null, null, 0, null)

        then:
        pattern instanceof StaticLoad
    }

    def "parseDuration should handle numeric-only as seconds"() {
        when:
        def d = LoadPatternFactory.parseDuration("45")

        then:
        d == Duration.ofSeconds(45)
    }

    def "parseDuration should handle uppercase units"() {
        when:
        def d = LoadPatternFactory.parseDuration("5S")

        then:
        d == Duration.ofSeconds(5)
    }

    def "parseDuration should trim whitespace"() {
        when:
        def d = LoadPatternFactory.parseDuration("  10s  ")

        then:
        d == Duration.ofSeconds(10)
    }

    def "parseDuration should throw for empty string"() {
        when:
        LoadPatternFactory.parseDuration("")

        then:
        thrown(IllegalArgumentException)
    }

    def "parseDuration should throw for blank string"() {
        when:
        LoadPatternFactory.parseDuration("   ")

        then:
        thrown(IllegalArgumentException)
    }
}
