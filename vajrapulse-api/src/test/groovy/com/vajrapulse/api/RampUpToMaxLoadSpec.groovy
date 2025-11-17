package com.vajrapulse.api

import spock.lang.Specification

import java.time.Duration

class RampUpToMaxLoadSpec extends Specification {

    def "should ramp up then sustain at max"() {
        given: "ramp to 200 TPS over 10 seconds, then sustain for 5 minutes"
        def load = new RampUpToMaxLoad(
            200.0,
            Duration.ofSeconds(10),
            Duration.ofMinutes(5)
        )
        
        expect: "TPS ramps during ramp period"
        load.calculateTps(0) == 0.0
        load.calculateTps(5_000) == 100.0  // 50% of ramp
        
        and: "TPS sustains at max after ramp"
        load.calculateTps(10_000) == 200.0  // At end of ramp
        load.calculateTps(60_000) == 200.0  // During sustain
        load.calculateTps(300_000) == 200.0 // End of sustain
        
        and: "total duration is ramp + sustain"
        load.getDuration() == Duration.ofSeconds(10).plus(Duration.ofMinutes(5))
    }
    
    def "should reject invalid parameters"() {
        when: "creating with invalid max TPS"
        new RampUpToMaxLoad(0.0, Duration.ofSeconds(10), Duration.ofMinutes(5))
        
        then: "throws exception"
        thrown(IllegalArgumentException)
        
        when: "creating with invalid ramp duration"
        new RampUpToMaxLoad(100.0, Duration.ZERO, Duration.ofMinutes(5))
        
        then: "throws exception"
        thrown(IllegalArgumentException)
        
        when: "creating with invalid sustain duration"
        new RampUpToMaxLoad(100.0, Duration.ofSeconds(10), Duration.ZERO)
        
        then: "throws exception"
        thrown(IllegalArgumentException)
    }
}
