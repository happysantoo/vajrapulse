package com.vajrapulse.api

import spock.lang.Specification
import spock.lang.Timeout
import com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy
import com.vajrapulse.api.pattern.adaptive.MetricsSnapshot

/**
 * Tests for DefaultRampDecisionPolicy.
 */
@Timeout(10)
class DefaultRampDecisionPolicySpec extends Specification {
    
    def "should ramp up when conditions are good"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01)
        def metrics = new MetricsSnapshot(0.005, 0.005, 0.2, 1000L)
        
        when:
        def shouldRampUp = policy.shouldRampUp(metrics)
        
        then:
        shouldRampUp == true
    }
    
    def "should not ramp up when error rate is high"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01)
        def metrics = new MetricsSnapshot(0.02, 0.02, 0.2, 1000L)
        
        when:
        def shouldRampUp = policy.shouldRampUp(metrics)
        
        then:
        shouldRampUp == false
    }
    
    def "should not ramp up when backpressure is high"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01, 0.3, 0.7)
        def metrics = new MetricsSnapshot(0.005, 0.005, 0.5, 1000L)
        
        when:
        def shouldRampUp = policy.shouldRampUp(metrics)
        
        then:
        shouldRampUp == false
    }
    
    def "should ramp down when error rate exceeds threshold"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01)
        def metrics = new MetricsSnapshot(0.02, 0.02, 0.2, 1000L)
        
        when:
        def shouldRampDown = policy.shouldRampDown(metrics)
        
        then:
        shouldRampDown == true
    }
    
    def "should ramp down when backpressure is high"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01, 0.3, 0.7)
        def metrics = new MetricsSnapshot(0.005, 0.005, 0.8, 1000L)
        
        when:
        def shouldRampDown = policy.shouldRampDown(metrics)
        
        then:
        shouldRampDown == true
    }
    
    def "should not ramp down when conditions are good"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01)
        def metrics = new MetricsSnapshot(0.005, 0.005, 0.2, 1000L)
        
        when:
        def shouldRampDown = policy.shouldRampDown(metrics)
        
        then:
        shouldRampDown == false
    }
    
    def "should sustain when stable intervals count is sufficient"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01, 0.3, 0.7)
        
        when:
        def shouldSustain = policy.shouldSustain(3, 3)
        
        then:
        shouldSustain == true
    }
    
    def "should not sustain when stable intervals count is insufficient"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01, 0.3, 0.7)
        
        when:
        def shouldSustain = policy.shouldSustain(2, 3)
        
        then:
        shouldSustain == false
    }
    
    def "should recover when backpressure is low"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01)
        def metrics = new MetricsSnapshot(0.02, 0.005, 0.2, 1000L)
        
        when:
        def canRecover = policy.canRecoverFromMinimum(metrics)
        
        then:
        canRecover == true
    }
    
    def "should recover when recent error rate is low and backpressure is moderate"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01)
        def metrics = new MetricsSnapshot(0.02, 0.005, 0.4, 1000L)
        
        when:
        def canRecover = policy.canRecoverFromMinimum(metrics)
        
        then:
        canRecover == true
    }
    
    def "should not recover when conditions are poor"() {
        given:
        def policy = new DefaultRampDecisionPolicy(0.01)
        def metrics = new MetricsSnapshot(0.02, 0.02, 0.8, 1000L)
        
        when:
        def canRecover = policy.canRecoverFromMinimum(metrics)
        
        then:
        canRecover == false
    }
    
    def "should reject invalid error threshold"() {
        when:
        new DefaultRampDecisionPolicy(1.5)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Error threshold must be between 0.0 and 1.0")
    }
    
    def "should reject invalid backpressure thresholds"() {
        when:
        new DefaultRampDecisionPolicy(0.01, 0.8, 0.7)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Backpressure ramp up threshold must be less than ramp down threshold")
    }
}
