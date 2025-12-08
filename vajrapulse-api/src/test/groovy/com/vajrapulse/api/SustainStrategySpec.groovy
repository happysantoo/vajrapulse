package com.vajrapulse.api

import spock.lang.Specification
import java.time.Duration

/**
 * Tests for SustainStrategy.
 */
class SustainStrategySpec extends Specification {
    
    // Mock MetricsProvider for testing
    static class MockMetricsProvider implements MetricsProvider {
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
    
    def "should transition to RAMP_DOWN when conditions worsen"() {
        given:
        def config = AdaptiveConfig.defaults()
        def provider = new MockMetricsProvider()
        provider.setFailureRate(2.0) // High error rate
        def pattern = new AdaptiveLoadPattern(config, provider)
        def strategy = new SustainStrategy()
        
        // Simulate being in SUSTAIN
        def stableTps = 150.0
        def phaseStartTime = 1000L
        def coreState = new AdaptiveLoadPattern.CoreState(
            AdaptiveLoadPattern.Phase.SUSTAIN,
            stableTps,
            1000L,
            phaseStartTime,
            0,
            1L
        )
        def state = new AdaptiveLoadPattern.AdaptiveState(
            coreState,
            new AdaptiveLoadPattern.StabilityTracking(stableTps, -1, -1, 0),
            new AdaptiveLoadPattern.RecoveryTracking(200.0, -1)
        )
        def metrics = new MetricsSnapshot(0.02, 0.02, 0.2, 1000L)
        def policy = new DefaultRampDecisionPolicy(0.01)
        def context = new PhaseStrategy.PhaseContext(state, config, metrics, policy, pattern)
        
        when:
        def result = strategy.handle(context, 2000L)
        
        then:
        result < stableTps // TPS should decrease
        result == Math.max(config.minTps(), stableTps - config.rampDecrement())
    }
    
    def "should continue at stable TPS when conditions are good and duration not elapsed"() {
        given:
        def config = AdaptiveConfig.defaults()
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(config, provider)
        def strategy = new SustainStrategy()
        
        def stableTps = 150.0
        def phaseStartTime = 1000L
        def coreState = new AdaptiveLoadPattern.CoreState(
            AdaptiveLoadPattern.Phase.SUSTAIN,
            stableTps,
            1000L,
            phaseStartTime,
            0,
            1L
        )
        def state = new AdaptiveLoadPattern.AdaptiveState(
            coreState,
            new AdaptiveLoadPattern.StabilityTracking(stableTps, -1, -1, 0),
            new AdaptiveLoadPattern.RecoveryTracking(200.0, -1)
        )
        def metrics = new MetricsSnapshot(0.005, 0.005, 0.2, 1000L)
        def policy = new DefaultRampDecisionPolicy(0.01)
        def context = new PhaseStrategy.PhaseContext(state, config, metrics, policy, pattern)
        
        when:
        def result = strategy.handle(context, 2000L) // Not enough time elapsed
        
        then:
        result == stableTps
    }
}

