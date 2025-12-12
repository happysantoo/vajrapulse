package com.vajrapulse.api

import spock.lang.Specification
import com.vajrapulse.api.pattern.adaptive.AdaptiveConfig
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern
import com.vajrapulse.api.pattern.adaptive.AdaptiveState
import com.vajrapulse.api.pattern.adaptive.AdaptiveCoreState
import com.vajrapulse.api.pattern.adaptive.AdaptiveStabilityTracking
import com.vajrapulse.api.pattern.adaptive.AdaptiveRecoveryTracking
import com.vajrapulse.api.pattern.adaptive.AdaptivePhase
import com.vajrapulse.api.pattern.adaptive.PhaseStrategy
import com.vajrapulse.api.pattern.adaptive.PhaseContext
import com.vajrapulse.api.pattern.adaptive.MetricsSnapshot
import com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy
import java.time.Duration

/**
 * Tests for PhaseStrategy and PhaseContext.
 */
class PhaseStrategySpec extends Specification {
    
    // Mock MetricsProvider for testing
    static class MockMetricsProvider implements com.vajrapulse.api.metrics.MetricsProvider {
        private volatile double failureRate = 0.0
        private volatile long totalExecutions = 0
        private volatile long failureCount = 0
        private volatile double recentFailureRate = 0.0
        
        void setFailureRate(double rate) {
            this.failureRate = rate
            this.recentFailureRate = rate
        }
        
        @Override
        double getFailureRate() { return failureRate }
        
        @Override
        double getRecentFailureRate(int windowSeconds) { return recentFailureRate }
        
        @Override
        long getTotalExecutions() { return totalExecutions }
        
        @Override
        long getFailureCount() { return failureCount }
    }
    
    def "should create valid PhaseContext"() {
        given:
        def config = AdaptiveConfig.defaults()
        def metrics = new MetricsSnapshot(0.01, 0.005, 0.3, 1000L)
        def policy = new DefaultRampDecisionPolicy(0.01)
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(config, provider)
        // Get state by calling calculateTps which initializes it
        pattern.calculateTps(0)
        def state = pattern.getCurrentPhase() // Just verify we can get phase
        
        when:
        // Create a minimal state for testing
        def coreState = new AdaptiveCoreState(
            AdaptivePhase.RAMP_UP,
            100.0,
            0L,
            0L,
            0,
            0L
        )
        def adaptiveState = new AdaptiveState(
            coreState,
            AdaptiveStabilityTracking.empty(),
            new AdaptiveRecoveryTracking(100.0, -1)
        )
        def context = new PhaseContext(adaptiveState, config, metrics, policy, pattern)
        
        then:
        context.current() == adaptiveState
        context.config() == config
        context.metrics() == metrics
        context.decisionPolicy() == policy
        context.pattern() == pattern
    }
    
    def "should reject null current state in PhaseContext"() {
        given:
        def config = AdaptiveConfig.defaults()
        def metrics = new MetricsSnapshot(0.01, 0.005, 0.3, 1000L)
        def policy = new DefaultRampDecisionPolicy(0.01)
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(config, provider)
        
        when:
        new PhaseContext(null, config, metrics, policy, pattern)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Current state must not be null")
    }
    
    def "should reject null config in PhaseContext"() {
        given:
        def config = AdaptiveConfig.defaults()
        def metrics = new MetricsSnapshot(0.01, 0.005, 0.3, 1000L)
        def policy = new DefaultRampDecisionPolicy(0.01)
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(config, provider)
        def coreState = new AdaptiveCoreState(
            AdaptivePhase.RAMP_UP,
            100.0,
            0L,
            0L,
            0,
            0L
        )
        def state = new AdaptiveState(
            coreState,
            AdaptiveStabilityTracking.empty(),
            new AdaptiveRecoveryTracking(100.0, -1)
        )
        
        when:
        new PhaseContext(state, null, metrics, policy, pattern)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Config must not be null")
    }
    
    def "should reject null metrics in PhaseContext"() {
        given:
        def config = AdaptiveConfig.defaults()
        def policy = new DefaultRampDecisionPolicy(0.01)
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(config, provider)
        def coreState = new AdaptiveCoreState(
            AdaptivePhase.RAMP_UP,
            100.0,
            0L,
            0L,
            0,
            0L
        )
        def state = new AdaptiveState(
            coreState,
            AdaptiveStabilityTracking.empty(),
            new AdaptiveRecoveryTracking(100.0, -1)
        )
        
        when:
        new PhaseContext(state, config, null, policy, pattern)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Metrics must not be null")
    }
    
    def "should reject null decisionPolicy in PhaseContext"() {
        given:
        def config = AdaptiveConfig.defaults()
        def metrics = new MetricsSnapshot(0.01, 0.005, 0.3, 1000L)
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(config, provider)
        def coreState = new AdaptiveCoreState(
            AdaptivePhase.RAMP_UP,
            100.0,
            0L,
            0L,
            0,
            0L
        )
        def state = new AdaptiveState(
            coreState,
            AdaptiveStabilityTracking.empty(),
            new AdaptiveRecoveryTracking(100.0, -1)
        )
        
        when:
        new PhaseContext(state, config, metrics, null, pattern)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Decision policy must not be null")
    }
    
    def "should reject null pattern in PhaseContext"() {
        given:
        def config = AdaptiveConfig.defaults()
        def metrics = new MetricsSnapshot(0.01, 0.005, 0.3, 1000L)
        def policy = new DefaultRampDecisionPolicy(0.01)
        def coreState = new AdaptiveCoreState(
            AdaptivePhase.RAMP_UP,
            100.0,
            0L,
            0L,
            0,
            0L
        )
        def state = new AdaptiveState(
            coreState,
            AdaptiveStabilityTracking.empty(),
            new AdaptiveRecoveryTracking(100.0, -1)
        )
        
        when:
        new PhaseContext(state, config, metrics, policy, null)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Pattern must not be null")
    }
}

