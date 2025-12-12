package com.vajrapulse.api

import spock.lang.Specification
import com.vajrapulse.api.pattern.adaptive.AdaptiveConfig
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern
import com.vajrapulse.api.pattern.adaptive.RampUpStrategy
import com.vajrapulse.api.pattern.adaptive.AdaptiveCoreState
import com.vajrapulse.api.pattern.adaptive.AdaptiveState
import com.vajrapulse.api.pattern.adaptive.AdaptiveStabilityTracking
import com.vajrapulse.api.pattern.adaptive.AdaptiveRecoveryTracking
import com.vajrapulse.api.pattern.adaptive.AdaptivePhase
import com.vajrapulse.api.pattern.adaptive.MetricsSnapshot
import com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy
import com.vajrapulse.api.pattern.adaptive.PhaseStrategy
import com.vajrapulse.api.pattern.adaptive.PhaseContext
import java.time.Duration

/**
 * Tests for RampUpStrategy.
 */
class RampUpStrategySpec extends Specification {
    
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
    
    def "should transition to SUSTAIN when max TPS reached"() {
        given:
        def config = new AdaptiveConfig(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            200.0, 10.0, Duration.ofMinutes(10),
            0.01, 0.3, 0.7, 3, 50.0, 0.5
        )
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(config, provider)
        def strategy = new RampUpStrategy()
        
        // Set TPS to max
        pattern.calculateTps(0)
        pattern.calculateTps(1001) // Adjust to max
        def current = pattern.getCurrentPhase()
        def currentTps = pattern.getCurrentTps()
        
        // Create context with TPS at max
        def coreState = new AdaptiveCoreState(
            AdaptivePhase.RAMP_UP,
            config.maxTps(),
            1001L,
            0L,
            0,
            0L
        )
        def state = new AdaptiveState(
            coreState,
            AdaptiveStabilityTracking.empty(),
            new AdaptiveRecoveryTracking(100.0, -1)
        )
        def metrics = new MetricsSnapshot(0.005, 0.005, 0.2, 1000L)
        def policy = new DefaultRampDecisionPolicy(0.01)
        def context = new PhaseContext(state, config, metrics, policy, pattern)
        
        when:
        def result = strategy.handle(context, 1001L)
        
        then:
        result == config.maxTps()
        pattern.getCurrentPhase() == AdaptivePhase.SUSTAIN
    }
    
    def "should return current TPS when not at max and not stable"() {
        given:
        def config = AdaptiveConfig.defaults()
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(config, provider)
        def strategy = new RampUpStrategy()
        
        pattern.calculateTps(0)
        def currentTps = pattern.getCurrentTps()
        
        def coreState = new AdaptiveCoreState(
            AdaptivePhase.RAMP_UP,
            currentTps,
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
        def metrics = new MetricsSnapshot(0.005, 0.005, 0.2, 1000L)
        def policy = new DefaultRampDecisionPolicy(0.01)
        def context = new PhaseContext(state, config, metrics, policy, pattern)
        
        when:
        def result = strategy.handle(context, 0L)
        
        then:
        result == currentTps
    }
}

