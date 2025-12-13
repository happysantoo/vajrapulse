package com.vajrapulse.api

import spock.lang.Specification
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern
import com.vajrapulse.api.pattern.adaptive.AdaptivePhase
import com.vajrapulse.api.pattern.adaptive.AdaptivePatternListener
import com.vajrapulse.api.pattern.adaptive.TpsChangeEvent
import com.vajrapulse.api.metrics.MetricsProvider
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests for AdaptiveLoadPattern listener notifications.
 * 
 * <p>Verifies that listeners are notified of TPS changes and phase transitions.
 * 
 * <p>Note: Complex pattern behaviors (stability, recovery) are tested in AdaptiveLoadPatternSpec.
 * This spec focuses on verifying listener integration works correctly.
 */
class AdaptiveLoadPatternListenerNotificationSpec extends Specification {
    
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
    
    def "should notify listener on TPS change during ramp up"() {
        given:
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0) // No errors
        def tpsChanges = new CopyOnWriteArrayList<TpsChangeEvent>()
        def listener = new AdaptivePatternListener() {
            @Override
            void onTpsChange(TpsChangeEvent event) {
                tpsChanges.add(event)
            }
        }
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(100.0)
            .rampIncrement(50.0)
            .rampDecrement(100.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(1000.0)
            .minTps(10.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.01))
            .listener(listener)
            .build()
        
        when: "Initialize and wait for ramp interval to trigger ramp up"
        pattern.calculateTps(0)
        def tps = pattern.calculateTps(1001) // After ramp interval
        
        then: "TPS change should be notified"
        tpsChanges.size() >= 1
        tpsChanges[0].previousTps() == 100.0
        tpsChanges[0].newTps() == 150.0 // 100 + 50 increment
        tpsChanges[0].phase() == AdaptivePhase.RAMP_UP
    }
    
    def "should notify multiple listeners"() {
        given:
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0)
        def listener1Events = new CopyOnWriteArrayList<TpsChangeEvent>()
        def listener2Events = new CopyOnWriteArrayList<TpsChangeEvent>()
        def listener1 = new AdaptivePatternListener() {
            @Override
            void onTpsChange(TpsChangeEvent event) {
                listener1Events.add(event)
            }
        }
        def listener2 = new AdaptivePatternListener() {
            @Override
            void onTpsChange(TpsChangeEvent event) {
                listener2Events.add(event)
            }
        }
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(100.0)
            .rampIncrement(50.0)
            .rampDecrement(100.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(1000.0)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.01))
            .metricsProvider(provider)
            .listener(listener1)
            .listener(listener2)
            .build()
        
        when: "Trigger TPS change"
        pattern.calculateTps(0)
        pattern.calculateTps(1001)
        
        then: "Both listeners should be notified"
        listener1Events.size() == listener2Events.size()
        listener1Events.size() >= 1
        listener1Events[0].newTps() == listener2Events[0].newTps()
    }
    
    def "should include phase in TPS change event"() {
        given:
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0)
        def tpsChanges = new CopyOnWriteArrayList<TpsChangeEvent>()
        def listener = new AdaptivePatternListener() {
            @Override
            void onTpsChange(TpsChangeEvent event) {
                tpsChanges.add(event)
            }
        }
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(100.0)
            .rampIncrement(50.0)
            .rampDecrement(100.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(1000.0)
            .minTps(10.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.01))
            .listener(listener)
            .build()
        
        when: "Trigger ramp up"
        pattern.calculateTps(0)
        pattern.calculateTps(1001)
        
        then: "TPS change event should include phase"
        tpsChanges.size() >= 1
        tpsChanges[0].phase() != null
        tpsChanges[0].phase() == AdaptivePhase.RAMP_UP
    }
}
