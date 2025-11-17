package com.vajrapulse.api

import spock.lang.Specification

import java.time.Duration

class RampUpLoadSpec extends Specification {

    def "should ramp up linearly from 0 to max"() {
        given: "a ramp-up load to 200 TPS over 10 seconds"
        def load = new RampUpLoad(200.0, Duration.ofSeconds(10))
        
        expect: "TPS increases linearly"
        load.calculateTps(0) == 0.0
        load.calculateTps(2_500) == 50.0  // 25% of duration = 25% of max
        load.calculateTps(5_000) == 100.0 // 50% of duration = 50% of max
        load.calculateTps(7_500) == 150.0 // 75% of duration = 75% of max
        load.calculateTps(10_000) == 200.0 // 100% of duration = 100% of max
        load.calculateTps(15_000) == 200.0 // After ramp = max
        
        and: "duration equals ramp duration"
        load.getDuration() == Duration.ofSeconds(10)
    }
    
    def "should reject invalid max TPS"() {
        when: "creating with zero max TPS"
        new RampUpLoad(0.0, Duration.ofSeconds(10))
        
        then: "throws exception"
        thrown(IllegalArgumentException)
    }
    
    def "should reject invalid ramp duration"() {
        when: "creating with zero duration"
        new RampUpLoad(100.0, Duration.ZERO)
        
        then: "throws exception"
        thrown(IllegalArgumentException)
    }
}
