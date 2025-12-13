package com.vajrapulse.api

import spock.lang.Specification
import com.vajrapulse.api.pattern.adaptive.AdaptivePatternListener
import com.vajrapulse.api.pattern.adaptive.AdaptivePhase
import com.vajrapulse.api.pattern.adaptive.PhaseTransitionEvent
import com.vajrapulse.api.pattern.adaptive.TpsChangeEvent
import com.vajrapulse.api.pattern.adaptive.StabilityDetectedEvent
import com.vajrapulse.api.pattern.adaptive.RecoveryEvent

/**
 * Tests for AdaptivePatternListener and event records.
 */
class AdaptivePatternListenerSpec extends Specification {
    
    def "should create valid PhaseTransitionEvent"() {
        when:
        def event = new PhaseTransitionEvent(
            AdaptivePhase.RAMP_UP,
            AdaptivePhase.RAMP_DOWN,
            100.0,
            1000L
        )
        
        then:
        event.from() == AdaptivePhase.RAMP_UP
        event.to() == AdaptivePhase.RAMP_DOWN
        event.tps() == 100.0
        event.timestamp() == 1000L
    }
    
    def "should reject null phase in PhaseTransitionEvent"() {
        when:
        new PhaseTransitionEvent(
            null,
            AdaptivePhase.RAMP_DOWN,
            100.0,
            1000L
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("From phase must not be null")
    }
    
    def "should reject negative TPS in PhaseTransitionEvent"() {
        when:
        new PhaseTransitionEvent(
            AdaptivePhase.RAMP_UP,
            AdaptivePhase.RAMP_DOWN,
            -1.0,
            1000L
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("TPS must be non-negative")
    }
    
    def "should create valid TpsChangeEvent"() {
        when:
        def event = new TpsChangeEvent(100.0, 150.0, AdaptivePhase.RAMP_UP, 1000L)
        
        then:
        event.previousTps() == 100.0
        event.newTps() == 150.0
        event.phase() == AdaptivePhase.RAMP_UP
        event.timestamp() == 1000L
    }
    
    def "should reject negative TPS in TpsChangeEvent"() {
        when:
        new TpsChangeEvent(-1.0, 150.0, AdaptivePhase.RAMP_UP, 1000L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Previous TPS must be non-negative")
    }
    
    def "should reject null phase in TpsChangeEvent"() {
        when:
        new TpsChangeEvent(100.0, 150.0, null, 1000L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Phase must not be null")
    }
    
    def "should create valid StabilityDetectedEvent"() {
        when:
        def event = new StabilityDetectedEvent(200.0, 1000L)
        
        then:
        event.stableTps() == 200.0
        event.timestamp() == 1000L
    }
    
    def "should reject negative stableTps in StabilityDetectedEvent"() {
        when:
        new StabilityDetectedEvent(-1.0, 1000L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Stable TPS must be non-negative")
    }
    
    def "should create valid RecoveryEvent"() {
        when:
        def event = new RecoveryEvent(300.0, 150.0, 1000L)
        
        then:
        event.lastKnownGoodTps() == 300.0
        event.recoveryTps() == 150.0
        event.timestamp() == 1000L
    }
    
    def "should reject negative TPS in RecoveryEvent"() {
        when:
        new RecoveryEvent(-1.0, 150.0, 1000L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Last known good TPS must be non-negative")
    }
    
    def "should have default implementations in listener"() {
        given:
        def listener = new AdaptivePatternListener() {}
        def event1 = new PhaseTransitionEvent(
            AdaptivePhase.RAMP_UP,
            AdaptivePhase.RAMP_DOWN,
            100.0,
            1000L
        )
        def event2 = new TpsChangeEvent(100.0, 150.0, AdaptivePhase.RAMP_UP, 1000L)
        def event3 = new StabilityDetectedEvent(200.0, 1000L)
        def event4 = new RecoveryEvent(300.0, 150.0, 1000L)
        
        when:
        listener.onPhaseTransition(event1)
        listener.onTpsChange(event2)
        listener.onStabilityDetected(event3)
        listener.onRecovery(event4)
        
        then:
        noExceptionThrown()
    }
}

