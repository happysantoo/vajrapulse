package com.vajra.api

import spock.lang.Specification

import java.time.Duration

class StaticLoadSpec extends Specification {

    def "should maintain constant TPS"() {
        given: "a static load of 100 TPS for 5 minutes"
        def load = new StaticLoad(100.0, Duration.ofMinutes(5))
        
        expect: "TPS is constant at any time"
        load.calculateTps(0) == 100.0
        load.calculateTps(1000) == 100.0
        load.calculateTps(60_000) == 100.0
        load.calculateTps(300_000) == 100.0
        
        and: "duration is correct"
        load.getDuration() == Duration.ofMinutes(5)
    }
    
    def "should reject invalid TPS"() {
        when: "creating with zero TPS"
        new StaticLoad(0.0, Duration.ofMinutes(1))
        
        then: "throws exception"
        thrown(IllegalArgumentException)
        
        when: "creating with negative TPS"
        new StaticLoad(-10.0, Duration.ofMinutes(1))
        
        then: "throws exception"
        thrown(IllegalArgumentException)
    }
    
    def "should reject invalid duration"() {
        when: "creating with zero duration"
        new StaticLoad(100.0, Duration.ZERO)
        
        then: "throws exception"
        thrown(IllegalArgumentException)
        
        when: "creating with negative duration"
        new StaticLoad(100.0, Duration.ofSeconds(-1))
        
        then: "throws exception"
        thrown(IllegalArgumentException)
    }
}
