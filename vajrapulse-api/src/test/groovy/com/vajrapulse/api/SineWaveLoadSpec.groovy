package com.vajrapulse.api

import spock.lang.Specification
import spock.lang.Timeout
import com.vajrapulse.api.pattern.SineWaveLoad

import java.time.Duration

@Timeout(10)
class SineWaveLoadSpec extends Specification {

    def "sine wave produces expected oscillation values"() {
        given:
        def mean = 100.0d
        def amp = 50.0d
        def period = Duration.ofSeconds(10)
        def total = Duration.ofSeconds(30)
        def pattern = new SineWaveLoad(mean, amp, total, period)
        def pMillis = period.toMillis()

        when:
        def v0 = pattern.calculateTps(0)                 // sin(0)=0 => mean
        def vQuarter = pattern.calculateTps((pMillis/4) as long)   // sin(pi/2)=1 => mean+amp
        def vHalf = pattern.calculateTps((pMillis/2) as long)      // sin(pi)=0 => mean
        def vThreeQuarter = pattern.calculateTps((3*pMillis/4) as long) // sin(3pi/2)=-1 => mean-amp

        then:
        v0 == mean
        Math.abs(vQuarter - (mean + amp)) < 0.0001d
        vHalf == mean
        Math.abs(vThreeQuarter - (mean - amp)) < 0.0001d
    }

    def "sine wave validation rejects negative amplitude"() {
        when:
        new SineWaveLoad(100.0d, -1.0d, Duration.ofSeconds(10), Duration.ofSeconds(1))

        then:
        thrown(IllegalArgumentException)
    }

    def "sine wave validation rejects zero period"() {
        when:
        new SineWaveLoad(100.0d, 10.0d, Duration.ofSeconds(10), Duration.ZERO)

        then:
        thrown(IllegalArgumentException)
    }

    def "sine wave validation rejects zero total duration"() {
        when:
        new SineWaveLoad(100.0d, 10.0d, Duration.ZERO, Duration.ofSeconds(1))

        then:
        thrown(IllegalArgumentException)
    }
}
