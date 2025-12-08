package com.vajrapulse.api

import spock.lang.Specification
import java.time.Duration

/**
 * Tests for RampDownStrategy.
 */
class RampDownStrategySpec extends Specification {
    
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
    
    def "should return current TPS when not stable"() {
        given:
        def config = AdaptiveConfig.defaults()
        def provider = new MockMetricsProvider()
        provider.setFailureRate(2.0) // High error rate
        def pattern = new AdaptiveLoadPattern(config, provider)
        def strategy = new RampDownStrategy()
        
        pattern.calculateTps(0)
        pattern.calculateTps(1001) // Trigger ramp down
        def currentTps = pattern.getCurrentTps()
        
        def coreState = new AdaptiveLoadPattern.CoreState(
            AdaptiveLoadPattern.Phase.RAMP_DOWN,
            currentTps,
            1001L,
            1001L,
            1,
            1L
        )
        def state = new AdaptiveLoadPattern.AdaptiveState(
            coreState,
            AdaptiveLoadPattern.StabilityTracking.empty(),
            new AdaptiveLoadPattern.RecoveryTracking(200.0, -1)
        )
        def metrics = new MetricsSnapshot(0.02, 0.02, 0.2, 1000L)
        def policy = new DefaultRampDecisionPolicy(0.01)
        def context = new PhaseStrategy.PhaseContext(state, config, metrics, policy, pattern)
        
        when:
        def result = strategy.handle(context, 1001L)
        
        then:
        result == currentTps
    }
}

