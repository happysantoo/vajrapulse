package com.vajrapulse.api

import spock.lang.Specification
import java.time.Duration

class WarmupCooldownLoadPatternSpec extends Specification {

    def "should reject null base pattern"() {
        when:
        new WarmupCooldownLoadPattern(null, Duration.ofSeconds(10), Duration.ofSeconds(5))

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject null warmup duration"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))

        when:
        new WarmupCooldownLoadPattern(basePattern, null, Duration.ofSeconds(5))

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject null cooldown duration"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))

        when:
        new WarmupCooldownLoadPattern(basePattern, Duration.ofSeconds(10), null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject negative warmup duration"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))

        when:
        new WarmupCooldownLoadPattern(basePattern, Duration.ofSeconds(-1), Duration.ofSeconds(5))

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject negative cooldown duration"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))

        when:
        new WarmupCooldownLoadPattern(basePattern, Duration.ofSeconds(10), Duration.ofSeconds(-1))

        then:
        thrown(IllegalArgumentException)
    }

    def "should calculate correct total duration"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))
        def warmup = Duration.ofSeconds(30)
        def cooldown = Duration.ofSeconds(10)
        def pattern = new WarmupCooldownLoadPattern(basePattern, warmup, cooldown)

        expect:
        pattern.getDuration() == warmup.plus(basePattern.getDuration()).plus(cooldown)
    }

    def "should ramp from 0 to initial TPS during warmup"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))
        def warmup = Duration.ofSeconds(30)
        def pattern = new WarmupCooldownLoadPattern(basePattern, warmup, Duration.ZERO)

        when:
        def tpsAtStart = pattern.calculateTps(0)
        def tpsAtMid = pattern.calculateTps(15_000) // 15 seconds
        def tpsAtEnd = pattern.calculateTps(30_000) // 30 seconds

        then:
        tpsAtStart == 0.0
        Math.abs(tpsAtMid - 50.0) < 1.0 // Should be ~50 TPS (halfway)
        Math.abs(tpsAtEnd - 100.0) < 0.1 // Should be ~100 TPS
    }

    def "should use base pattern during steady state"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))
        def warmup = Duration.ofSeconds(30)
        def pattern = new WarmupCooldownLoadPattern(basePattern, warmup, Duration.ZERO)

        when:
        def tpsAtSteadyStateStart = pattern.calculateTps(30_000) // At warmup boundary (enters steady-state)
        def tpsAtSteadyStateMid = pattern.calculateTps(120_000) // Middle of steady state
        def tpsAtSteadyStateEnd = pattern.calculateTps(329_999) // Just before steady-state ends

        then:
        Math.abs(tpsAtSteadyStateStart - 100.0) < 0.1
        Math.abs(tpsAtSteadyStateMid - 100.0) < 0.1
        Math.abs(tpsAtSteadyStateEnd - 100.0) < 0.1
    }

    def "should ramp from final TPS to 0 during cooldown"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))
        def warmup = Duration.ofSeconds(30)
        def cooldown = Duration.ofSeconds(10)
        def pattern = new WarmupCooldownLoadPattern(basePattern, warmup, cooldown)
        def steadyStateEnd = (warmup.plus(basePattern.getDuration())).toMillis()

        when:
        def tpsAtCooldownStart = pattern.calculateTps(steadyStateEnd)
        def tpsAtCooldownMid = pattern.calculateTps(steadyStateEnd + 5_000) // 5 seconds into cooldown
        def tpsAtCooldownEnd = pattern.calculateTps(steadyStateEnd + 10_000) // End of cooldown

        then:
        Math.abs(tpsAtCooldownStart - 100.0) < 0.1
        Math.abs(tpsAtCooldownMid - 50.0) < 1.0 // Should be ~50 TPS (halfway)
        Math.abs(tpsAtCooldownEnd - 0.0) < 0.1
    }

    def "should return 0 TPS after completion"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))
        def warmup = Duration.ofSeconds(30)
        def cooldown = Duration.ofSeconds(10)
        def pattern = new WarmupCooldownLoadPattern(basePattern, warmup, cooldown)
        def totalDuration = pattern.getDuration().toMillis()

        when:
        def tpsAfterCompletion = pattern.calculateTps(totalDuration + 1000)

        then:
        tpsAfterCompletion == 0.0
    }

    def "should detect correct phase"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofSeconds(60))
        def warmup = Duration.ofSeconds(30)
        def cooldown = Duration.ofSeconds(10)
        def pattern = new WarmupCooldownLoadPattern(basePattern, warmup, cooldown)

        expect:
        pattern.getCurrentPhase(0) == WarmupCooldownLoadPattern.Phase.WARMUP
        pattern.getCurrentPhase(15_000) == WarmupCooldownLoadPattern.Phase.WARMUP
        pattern.getCurrentPhase(30_000) == WarmupCooldownLoadPattern.Phase.STEADY_STATE
        pattern.getCurrentPhase(60_000) == WarmupCooldownLoadPattern.Phase.STEADY_STATE
        pattern.getCurrentPhase(90_000) == WarmupCooldownLoadPattern.Phase.COOLDOWN
        pattern.getCurrentPhase(100_000) == WarmupCooldownLoadPattern.Phase.COMPLETE
    }

    def "should only record metrics during steady state"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofSeconds(60))
        def warmup = Duration.ofSeconds(30)
        def cooldown = Duration.ofSeconds(10)
        def pattern = new WarmupCooldownLoadPattern(basePattern, warmup, cooldown)

        expect:
        !pattern.shouldRecordMetrics(0) // Warm-up
        !pattern.shouldRecordMetrics(15_000) // Warm-up
        !pattern.shouldRecordMetrics(29_999) // Just before warmup ends
        pattern.shouldRecordMetrics(30_000) // At warmup boundary (enters steady-state)
        pattern.shouldRecordMetrics(30_001) // Steady-state
        pattern.shouldRecordMetrics(60_000) // Steady-state middle
        pattern.shouldRecordMetrics(89_999) // Just before steady-state end
        !pattern.shouldRecordMetrics(90_000) // At steady-state boundary (enters cool-down)
        !pattern.shouldRecordMetrics(90_001) // Cool-down
        !pattern.shouldRecordMetrics(95_000) // Cool-down
        !pattern.shouldRecordMetrics(100_000) // Complete
    }

    def "should work with RampUpLoad pattern"() {
        given:
        def basePattern = new RampUpLoad(200.0, Duration.ofSeconds(60))
        def warmup = Duration.ofSeconds(10)
        def pattern = new WarmupCooldownLoadPattern(basePattern, warmup, Duration.ZERO)

        when:
        def tpsAtWarmupEnd = pattern.calculateTps(10_000)
        def tpsAtRampMid = pattern.calculateTps(40_000) // 30 seconds into ramp (10s warmup + 30s ramp)

        then:
        // After warmup, should be at start of ramp (0 TPS)
        Math.abs(tpsAtWarmupEnd - 0.0) < 0.1
        // At ramp mid, should be ~100 TPS (halfway through ramp)
        Math.abs(tpsAtRampMid - 100.0) < 1.0
    }

    def "should work with RampUpToMaxLoad pattern"() {
        given:
        def basePattern = new RampUpToMaxLoad(200.0, Duration.ofSeconds(30), Duration.ofSeconds(60))
        def warmup = Duration.ofSeconds(10)
        def cooldown = Duration.ofSeconds(5)
        def pattern = new WarmupCooldownLoadPattern(basePattern, warmup, cooldown)

        when:
        def tpsAtWarmupEnd = pattern.calculateTps(10_000)
        def tpsAtRampEnd = pattern.calculateTps(40_000) // 10s warmup + 30s ramp
        def tpsAtSustainMid = pattern.calculateTps(70_000) // 10s warmup + 30s ramp + 30s sustain

        then:
        // After warmup, should be at start of ramp (0 TPS)
        Math.abs(tpsAtWarmupEnd - 0.0) < 0.1
        // At ramp end, should be at max TPS
        Math.abs(tpsAtRampEnd - 200.0) < 0.1
        // At sustain mid, should still be at max TPS
        Math.abs(tpsAtSustainMid - 200.0) < 0.1
    }

    def "should create pattern with warmup only"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))
        def pattern = WarmupCooldownLoadPattern.withWarmup(basePattern, Duration.ofSeconds(30))

        expect:
        pattern.getWarmupDuration() == Duration.ofSeconds(30)
        pattern.getCooldownDuration() == Duration.ZERO
    }

    def "should create pattern with cooldown only"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))
        def pattern = WarmupCooldownLoadPattern.withCooldown(basePattern, Duration.ofSeconds(10))

        expect:
        pattern.getWarmupDuration() == Duration.ZERO
        pattern.getCooldownDuration() == Duration.ofSeconds(10)
    }

    def "should handle zero warmup duration"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))
        def pattern = new WarmupCooldownLoadPattern(basePattern, Duration.ZERO, Duration.ofSeconds(10))

        when:
        def tpsAtStart = pattern.calculateTps(0)

        then:
        // Should jump directly to base pattern TPS
        Math.abs(tpsAtStart - 100.0) < 0.1
    }

    def "should handle zero cooldown duration"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))
        def pattern = new WarmupCooldownLoadPattern(basePattern, Duration.ofSeconds(30), Duration.ZERO)
        def steadyStateEnd = (Duration.ofSeconds(30).plus(basePattern.getDuration())).toMillis()

        when:
        def tpsAtEnd = pattern.calculateTps(steadyStateEnd)

        then:
        // Should return 0 immediately after steady state
        Math.abs(tpsAtEnd - 0.0) < 0.1
    }

    def "should return base pattern"() {
        given:
        def basePattern = new StaticLoad(100.0, Duration.ofMinutes(5))
        def pattern = new WarmupCooldownLoadPattern(basePattern, Duration.ofSeconds(30), Duration.ofSeconds(10))

        expect:
        pattern.getBasePattern() == basePattern
    }
}

