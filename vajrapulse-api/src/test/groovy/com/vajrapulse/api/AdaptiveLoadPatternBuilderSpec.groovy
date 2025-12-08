package com.vajrapulse.api

import spock.lang.Specification
import java.time.Duration

/**
 * Tests for AdaptiveLoadPattern builder.
 */
class AdaptiveLoadPatternBuilderSpec extends Specification {
    
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
    
    def "should build pattern with defaults"() {
        given:
        def provider = new MockMetricsProvider()
        
        when:
        def pattern = AdaptiveLoadPattern.builder()
            .metricsProvider(provider)
            .build()
        
        then:
        pattern != null
        pattern.getCurrentPhase() == AdaptiveLoadPattern.Phase.RAMP_UP
        pattern.getCurrentTps() == 100.0 // Default initial TPS
    }
    
    def "should build pattern with custom config"() {
        given:
        def provider = new MockMetricsProvider()
        def config = AdaptiveConfig.defaults()
        
        when:
        def pattern = AdaptiveLoadPattern.builder()
            .config(config)
            .metricsProvider(provider)
            .build()
        
        then:
        pattern != null
        pattern.getCurrentTps() == config.initialTps()
    }
    
    def "should build pattern with fluent API"() {
        given:
        def provider = new MockMetricsProvider()
        
        when:
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(200.0)
            .rampIncrement(75.0)
            .rampDecrement(150.0)
            .rampInterval(Duration.ofSeconds(30))
            .maxTps(10000.0)
            .minTps(5.0)
            .sustainDuration(Duration.ofMinutes(5))
            .errorThreshold(0.02)
            .backpressureRampUpThreshold(0.2)
            .backpressureRampDownThreshold(0.8)
            .stableIntervalsRequired(5)
            .tpsTolerance(100.0)
            .recoveryTpsRatio(0.3)
            .metricsProvider(provider)
            .build()
        
        then:
        pattern != null
        pattern.getCurrentTps() == 200.0
    }
    
    def "should build pattern with backpressure provider"() {
        given:
        def provider = new MockMetricsProvider()
        def backpressureProvider = new AdaptiveLoadPatternSpec.MockBackpressureProvider()
        
        when:
        def pattern = AdaptiveLoadPattern.builder()
            .metricsProvider(provider)
            .backpressureProvider(backpressureProvider)
            .build()
        
        then:
        pattern != null
        pattern.getBackpressureLevel() == 0.0
    }
    
    def "should build pattern with listener"() {
        given:
        def provider = new MockMetricsProvider()
        def listener = new AdaptivePatternListener() {
            @Override
            void onPhaseTransition(AdaptivePatternListener.PhaseTransitionEvent event) {
                // Test listener
            }
        }
        
        when:
        def pattern = AdaptiveLoadPattern.builder()
            .metricsProvider(provider)
            .listener(listener)
            .build()
        
        then:
        pattern != null
    }
    
    def "should build pattern with multiple listeners"() {
        given:
        def provider = new MockMetricsProvider()
        def listener1 = new AdaptivePatternListener() {}
        def listener2 = new AdaptivePatternListener() {}
        
        when:
        def pattern = AdaptiveLoadPattern.builder()
            .metricsProvider(provider)
            .listener(listener1)
            .listener(listener2)
            .build()
        
        then:
        pattern != null
    }
    
    def "should reject build without metrics provider"() {
        when:
        AdaptiveLoadPattern.builder()
            .initialTps(100.0)
            .build()
        
        then:
        def e = thrown(IllegalStateException)
        e.message.contains("Metrics provider must be set")
    }
    
    def "should reject null listener"() {
        given:
        def provider = new MockMetricsProvider()
        
        when:
        AdaptiveLoadPattern.builder()
            .metricsProvider(provider)
            .listener(null)
            .build()
        
        then:
        def e = thrown(NullPointerException)
        e.message.contains("Listener must not be null")
    }
    
    def "should validate config in builder"() {
        given:
        def provider = new MockMetricsProvider()
        
        when:
        AdaptiveLoadPattern.builder()
            .initialTps(0.0) // Invalid
            .metricsProvider(provider)
            .build()
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Initial TPS must be positive")
    }
}

