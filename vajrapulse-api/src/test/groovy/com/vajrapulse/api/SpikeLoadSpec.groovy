package com.vajrapulse.api

import spock.lang.Specification
import spock.lang.Timeout
import com.vajrapulse.api.pattern.SpikeLoad

import java.time.Duration

@Timeout(10)
class SpikeLoadSpec extends Specification {

    def "spike load alternates base and spike rates"() {
        given:
        def base = 100.0d
        def spike = 500.0d
        def interval = Duration.ofSeconds(10)
        def spikeDur = Duration.ofSeconds(2)
        def total = Duration.ofSeconds(30)
        def pattern = new SpikeLoad(base, spike, total, interval, spikeDur)

        expect:
        pattern.calculateTps(0) == spike          // start spike
        pattern.calculateTps(spikeDur.toMillis()-1) == spike
        pattern.calculateTps(spikeDur.toMillis()) == base
        pattern.calculateTps(interval.toMillis()-1) == base
        pattern.calculateTps(interval.toMillis()) == spike  // second spike start
    }

    def "spike load validation rejects spikeDuration >= interval"() {
        when:
        new SpikeLoad(10.0d, 20.0d, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(5))

        then:
        def e = thrown(IllegalArgumentException)
        e.message.toLowerCase().contains("spike duration") && e.message.toLowerCase().contains("spike interval")
    }

    def "spike load validation rejects negative rates"() {
        when:
        new SpikeLoad(-1.0d, 10.0d, Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(1))

        then:
        thrown(IllegalArgumentException)
    }

    def "spike load validation rejects zero spike duration"() {
        when:
        new SpikeLoad(10.0d, 20.0d, Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO)

        then:
        thrown(IllegalArgumentException)
    }
}
