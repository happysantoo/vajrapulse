package com.vajrapulse.api

import spock.lang.Specification

/**
 * Tests for AdaptivePatternListener and event records.
 */
class AdaptivePatternListenerSpec extends Specification {
    
    def "should create valid PhaseTransitionEvent"() {
        when:
        def event = new AdaptivePatternListener.PhaseTransitionEvent(
            AdaptiveLoadPattern.Phase.RAMP_UP,
            AdaptiveLoadPattern.Phase.RAMP_DOWN,
            100.0,
            1000L
        )
        
        then:
        event.from() == AdaptiveLoadPattern.Phase.RAMP_UP
        event.to() == AdaptiveLoadPattern.Phase.RAMP_DOWN
        event.tps() == 100.0
        event.timestamp() == 1000L
    }
    
    def "should reject null phase in PhaseTransitionEvent"() {
        when:
        new AdaptivePatternListener.PhaseTransitionEvent(
            null,
            AdaptiveLoadPattern.Phase.RAMP_DOWN,
            100.0,
            1000L
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("From phase must not be null")
    }
    
    def "should reject negative TPS in PhaseTransitionEvent"() {
        when:
        new AdaptivePatternListener.PhaseTransitionEvent(
            AdaptiveLoadPattern.Phase.RAMP_UP,
            AdaptiveLoadPattern.Phase.RAMP_DOWN,
            -1.0,
            1000L
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("TPS must be non-negative")
    }
    
    def "should create valid TpsChangeEvent"() {
        when:
        def event = new AdaptivePatternListener.TpsChangeEvent(100.0, 150.0, 1000L)
        
        then:
        event.previousTps() == 100.0
        event.newTps() == 150.0
        event.timestamp() == 1000L
    }
    
    def "should reject negative TPS in TpsChangeEvent"() {
        when:
        new AdaptivePatternListener.TpsChangeEvent(-1.0, 150.0, 1000L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Previous TPS must be non-negative")
    }
    
    def "should create valid StabilityDetectedEvent"() {
        when:
        def event = new AdaptivePatternListener.StabilityDetectedEvent(200.0, 1000L)
        
        then:
        event.stableTps() == 200.0
        event.timestamp() == 1000L
    }
    
    def "should reject negative stableTps in StabilityDetectedEvent"() {
        when:
        new AdaptivePatternListener.StabilityDetectedEvent(-1.0, 1000L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Stable TPS must be non-negative")
    }
    
    def "should create valid RecoveryEvent"() {
        when:
        def event = new AdaptivePatternListener.RecoveryEvent(300.0, 150.0, 1000L)
        
        then:
        event.lastKnownGoodTps() == 300.0
        event.recoveryTps() == 150.0
        event.timestamp() == 1000L
    }
    
    def "should reject negative TPS in RecoveryEvent"() {
        when:
        new AdaptivePatternListener.RecoveryEvent(-1.0, 150.0, 1000L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Last known good TPS must be non-negative")
    }
    
    def "should have default implementations in listener"() {
        given:
        def listener = new AdaptivePatternListener() {}
        def event1 = new AdaptivePatternListener.PhaseTransitionEvent(
            AdaptiveLoadPattern.Phase.RAMP_UP,
            AdaptiveLoadPattern.Phase.RAMP_DOWN,
            100.0,
            1000L
        )
        def event2 = new AdaptivePatternListener.TpsChangeEvent(100.0, 150.0, 1000L)
        def event3 = new AdaptivePatternListener.StabilityDetectedEvent(200.0, 1000L)
        def event4 = new AdaptivePatternListener.RecoveryEvent(300.0, 150.0, 1000L)
        
        when:
        listener.onPhaseTransition(event1)
        listener.onTpsChange(event2)
        listener.onStabilityDetected(event3)
        listener.onRecovery(event4)
        
        then:
        noExceptionThrown()
    }
}

