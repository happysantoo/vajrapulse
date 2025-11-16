package com.vajrapulse.api

import spock.lang.Specification
import java.time.Duration

class LoadPatternSpec extends Specification {

    def "step load returns correct rates per segment and zero after end"() {
        given:
        def pattern = new StepLoad([
            new StepLoad.Step(10d, Duration.ofMillis(100)),
            new StepLoad.Step(20d, Duration.ofMillis(200)),
            new StepLoad.Step(30d, Duration.ofMillis(300))
        ])

        expect:
        pattern.calculateTps(0) == 10d
        pattern.calculateTps(99) == 10d
        pattern.calculateTps(100) == 20d
        pattern.calculateTps(299) == 20d
        pattern.calculateTps(300) == 30d
        pattern.calculateTps(599) == 30d
        pattern.calculateTps(600) == 0d
    }

    def "spike load alternates base and spike correctly"() {
        given:
        def pattern = new SpikeLoad(50d, 200d, Duration.ofSeconds(10), Duration.ofSeconds(2), Duration.ofSeconds(1))
        // spike every 1s lasting 2s would normally be invalid; adjust valid config
        // Correct config: spikeInterval=2s, spikeDuration=1s
        pattern = new SpikeLoad(50d, 200d, Duration.ofSeconds(10), Duration.ofSeconds(2), Duration.ofSeconds(1))

        expect:
        pattern.calculateTps(0) == 200d  // spike window start
        pattern.calculateTps(999) == 200d
        pattern.calculateTps(1000) == 50d // outside spike (position 1000 % 2000 = 1000 >= 1000? Actually spikeDuration=1000ms)
        pattern.calculateTps(1500) == 50d
        pattern.calculateTps(2000) == 200d // next spike cycle
    }

    def "sine wave stays non-negative and oscillates around mean"() {
        given:
        def pattern = new SineWaveLoad(100d, 40d, Duration.ofSeconds(5), Duration.ofSeconds(2))

        when:
        def samples = (0..2000).step(200).collect { ms -> [ms, pattern.calculateTps(ms)] }

        then:
        samples.every { it[1] >= 0d }
        samples.collect { it[1] }.max() <= 140d + 0.0001d
        samples.collect { it[1] }.min() >= 60d - 0.0001d
    }
}
