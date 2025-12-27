package com.vajrapulse.api

import com.vajrapulse.api.pattern.adaptive.AdaptiveDecisionEngine
import com.vajrapulse.api.pattern.adaptive.AdaptiveConfig
import com.vajrapulse.api.pattern.adaptive.AdaptivePhase
import com.vajrapulse.api.pattern.adaptive.AdaptiveState
import com.vajrapulse.api.pattern.adaptive.MetricsSnapshot
import com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy
import spock.lang.Specification

import java.time.Duration

/**
 * Tests for AdaptiveDecisionEngine decision logic.
 * 
 * <p>Tests verify that decision engine correctly:
 * <ul>
 *   <li>Makes ramp-up decisions based on metrics</li>
 *   <li>Makes ramp-down decisions when errors occur</li>
 *   <li>Detects stability and transitions to sustain</li>
 *   <li>Handles recovery from minimum TPS</li>
 *   <li>Respects max/min TPS constraints</li>
 * </ul>
 */
class AdaptiveDecisionEngineSpec extends Specification {
    
    def "should decide to ramp up when conditions are good"() {
        given: "good metrics and ramp-up state"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_UP, 10.0, 0, 0,
            -1.0, 0, 10.0, false, 0
        )
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000)
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should ramp up"
        decision.newPhase() == AdaptivePhase.RAMP_UP
        decision.newTps() == 20.0 // 10.0 + 10.0 increment
        decision.reason().contains("ramping up")
    }
    
    def "should decide to ramp down when errors detected"() {
        given: "high error rate and ramp-up state"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_UP, 50.0, 0, 0,
            -1.0, 0, 50.0, false, 0
        )
        def metrics = new MetricsSnapshot(0.05, 0.05, 0.0, 1000) // 5% error rate
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should ramp down"
        decision.newPhase() == AdaptivePhase.RAMP_DOWN
        decision.newTps() == 30.0 // 50.0 - 20.0 decrement
        decision.reason().contains("Errors/backpressure")
    }
    
    def "should detect stability and transition to sustain"() {
        given: "stable conditions with enough intervals"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_UP, 50.0, 0, 0,
            -1.0, 2, 50.0, false, 0 // 2 stable intervals, need 3
        )
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000)
        
        when: "making decision (will have 3 stable intervals after increment)"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should transition to sustain"
        decision.newPhase() == AdaptivePhase.SUSTAIN
        decision.newTps() == 50.0
        decision.reason().contains("Stability")
    }
    
    def "should respect max TPS constraint"() {
        given: "state at max TPS"
        def config = new AdaptiveConfig(
            10.0, 50.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_UP, 50.0, 0, 0,
            -1.0, 0, 50.0, false, 0
        )
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000)
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should transition to sustain at max TPS"
        decision.newPhase() == AdaptivePhase.SUSTAIN
        decision.newTps() == 50.0
        decision.reason().contains("Max TPS")
    }
    
    def "should handle recovery from minimum TPS"() {
        given: "recovery state at minimum"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_DOWN, 5.0, 0, 0,
            -1.0, 0, 50.0, true, 1 // inRecovery = true
        )
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000) // conditions improved
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should recover and ramp up"
        decision.newPhase() == AdaptivePhase.RAMP_UP
        decision.newTps() == 25.0 // 50.0 * 0.5 (recovery ratio)
        decision.reason().contains("Recovery")
    }
    
    def "should continue sustaining when conditions remain good"() {
        given: "sustain state with good conditions"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.SUSTAIN, 50.0, 0, 0,
            50.0, 0, 50.0, false, 1
        )
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000)
        
        when: "making decision (sustain duration not elapsed)"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 2000) // 2s < 10s sustain
        
        then: "should continue sustaining"
        decision.newPhase() == AdaptivePhase.SUSTAIN
        decision.newTps() == 50.0
        decision.reason().contains("Continuing")
    }
    
    def "should hold TPS when conditions are moderate during ramp up"() {
        given: "moderate backpressure during ramp up"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_UP, 30.0, 0, 0,
            -1.0, 0, 30.0, false, 0
        )
        // Metrics that don't trigger ramp up or ramp down (moderate)
        def metrics = new MetricsSnapshot(0.005, 0.005, 0.3, 1000) // Low error, moderate backpressure
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should hold current TPS"
        decision.newPhase() == AdaptivePhase.RAMP_UP
        decision.newTps() == 30.0
        decision.reason().contains("holding")
    }
    
    def "should wait for recovery when conditions not improved"() {
        given: "recovery state with poor conditions"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_DOWN, 5.0, 0, 0,
            -1.0, 0, 50.0, true, 1 // inRecovery = true
        )
        def metrics = new MetricsSnapshot(0.05, 0.05, 0.8, 1000) // Poor conditions
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should continue waiting"
        decision.newPhase() == AdaptivePhase.RAMP_DOWN
        decision.newTps() == 5.0
        decision.reason().contains("waiting")
    }
    
    def "should check stability when conditions improve during ramp down"() {
        given: "improved conditions during ramp down but not stable yet"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_DOWN, 30.0, 0, 0,
            -1.0, 1, 30.0, false, 1 // Only 1 stable interval, need 3
        )
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000) // Good conditions
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should check stability"
        decision.newPhase() == AdaptivePhase.RAMP_DOWN
        decision.newTps() == 30.0
        decision.reason().contains("checking stability")
    }
    
    def "should ramp down when conditions worsen during sustain"() {
        given: "sustain state with worsening conditions"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.SUSTAIN, 50.0, 0, 0,
            50.0, 0, 50.0, false, 1
        )
        def metrics = new MetricsSnapshot(0.05, 0.05, 0.0, 1000) // High error rate
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 2000)
        
        then: "should ramp down"
        decision.newPhase() == AdaptivePhase.RAMP_DOWN
        decision.newTps() == 30.0 // 50.0 - 20.0
        decision.reason().contains("worsened")
    }
    
    def "should ramp up after sustain duration when conditions allow"() {
        given: "sustain state with elapsed duration and good conditions"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.SUSTAIN, 50.0, 0, 0, // phaseStartTime = 0
            50.0, 0, 50.0, false, 1
        )
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000) // Good conditions
        
        when: "making decision (sustain duration elapsed: 11s > 10s)"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 11000)
        
        then: "should ramp up"
        decision.newPhase() == AdaptivePhase.RAMP_UP
        decision.newTps() == 60.0 // 50.0 + 10.0
        decision.reason().contains("Sustain duration elapsed")
    }
    
    def "should not ramp up after sustain duration if at max TPS"() {
        given: "sustain state at max TPS with elapsed duration"
        def config = new AdaptiveConfig(
            10.0, 50.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.SUSTAIN, 50.0, 0, 0, // At max TPS
            50.0, 0, 50.0, false, 1
        )
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000)
        
        when: "making decision (sustain duration elapsed)"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 11000)
        
        then: "should continue sustaining"
        decision.newPhase() == AdaptivePhase.SUSTAIN
        decision.newTps() == 50.0
        decision.reason().contains("Continuing")
    }
    
    def "should not ramp up after sustain duration if conditions not good"() {
        given: "sustain state with elapsed duration but poor conditions"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.SUSTAIN, 50.0, 0, 0,
            50.0, 0, 50.0, false, 1
        )
        def metrics = new MetricsSnapshot(0.005, 0.005, 0.3, 1000) // Moderate conditions
        
        when: "making decision (sustain duration elapsed)"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 11000)
        
        then: "should continue sustaining"
        decision.newPhase() == AdaptivePhase.SUSTAIN
        decision.newTps() == 50.0
        decision.reason().contains("Continuing")
    }
    
    def "should clamp ramp up to max TPS"() {
        given: "state near max TPS"
        def config = new AdaptiveConfig(
            10.0, 50.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_UP, 45.0, 0, 0,
            -1.0, 0, 45.0, false, 0
        )
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000)
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should clamp to max TPS"
        decision.newTps() == 50.0 // min(50.0, 45.0 + 10.0)
    }
    
    def "should clamp ramp down to min TPS"() {
        given: "state near min TPS"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_DOWN, 8.0, 0, 0,
            -1.0, 0, 8.0, false, 1
        )
        def metrics = new MetricsSnapshot(0.05, 0.05, 0.0, 1000)
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should clamp to min TPS"
        decision.newTps() == 5.0 // max(5.0, 8.0 - 20.0)
    }
    
    def "should clamp recovery TPS to min"() {
        given: "recovery with low last known good TPS"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_DOWN, 5.0, 0, 0,
            -1.0, 0, 8.0, true, 1 // lastKnownGood = 8.0, recovery = 4.0 < min 5.0
        )
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000)
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should clamp recovery to min TPS"
        decision.newTps() == 5.0 // max(5.0, 8.0 * 0.5)
    }
    
    def "should not detect stability when conditions not good"() {
        given: "state with not enough stable intervals"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_UP, 50.0, 0, 0,
            -1.0, 0, 50.0, false, 0 // 0 stable intervals
        )
        def metrics = new MetricsSnapshot(0.005, 0.005, 0.3, 1000) // Moderate conditions
        
        when: "making decision"
        def decision = AdaptiveDecisionEngine.decide(state, metrics, config, policy, 5000)
        
        then: "should not transition to sustain"
        decision.newPhase() != AdaptivePhase.SUSTAIN || !decision.reason().contains("Stability")
    }
    
    def "should throw NullPointerException for null state"() {
        given: "null state"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def metrics = new MetricsSnapshot(0.0, 0.0, 0.0, 1000)
        
        when: "making decision with null state"
        AdaptiveDecisionEngine.decide(null, metrics, config, policy, 5000)
        
        then: "should throw NullPointerException"
        thrown(NullPointerException)
    }
    
    def "should throw NullPointerException for null metrics"() {
        given: "null metrics"
        def config = new AdaptiveConfig(
            10.0, 100.0, 5.0, 10.0, 20.0,
            Duration.ofSeconds(5), Duration.ofSeconds(10), 3,
            Duration.ZERO
        )
        def policy = new DefaultRampDecisionPolicy(0.01)
        def state = new AdaptiveState(
            AdaptivePhase.RAMP_UP, 10.0, 0, 0,
            -1.0, 0, 10.0, false, 0
        )
        
        when: "making decision with null metrics"
        AdaptiveDecisionEngine.decide(state, null, config, policy, 5000)
        
        then: "should throw NullPointerException"
        thrown(NullPointerException)
    }
}
