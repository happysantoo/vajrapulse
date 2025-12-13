package com.vajrapulse.api

import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern
import com.vajrapulse.api.pattern.adaptive.AdaptiveConfig
import com.vajrapulse.api.pattern.adaptive.AdaptivePhase
import com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy
import com.vajrapulse.api.metrics.MetricsProvider
import com.vajrapulse.api.backpressure.BackpressureProvider
import spock.lang.Specification
import spock.lang.Timeout
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.Collections

import static org.awaitility.Awaitility.*
import static java.util.concurrent.TimeUnit.*

/**
 * Comprehensive unit tests for AdaptiveLoadPattern.
 * 
 * <p>Tests verify:
 * <ul>
 *   <li>State machine initialization and transitions</li>
 *   <li>TPS calculations at various elapsed times</li>
 *   <li>Phase transitions (RAMP_UP → RAMP_DOWN → SUSTAIN)</li>
 *   <li>Error threshold detection</li>
 *   <li>Stable point detection</li>
 *   <li>Thread safety</li>
 *   <li>Edge cases</li>
 * </ul>
 */
@Timeout(30)
class AdaptiveLoadPatternSpec extends Specification {
    
    // Mock MetricsProvider for testing
    static class MockMetricsProvider implements com.vajrapulse.api.metrics.MetricsProvider {
        private volatile double failureRate = 0.0
        private volatile long totalExecutions = 0
        private volatile long failureCount = 0
        private volatile double recentFailureRate = 0.0
        
        void setFailureRate(double rate) {
            this.failureRate = rate
            this.recentFailureRate = rate // Default: same as all-time
        }
        
        void setRecentFailureRate(double rate) {
            this.recentFailureRate = rate
        }
        
        void setTotalExecutions(long count) {
            this.totalExecutions = count
        }
        
        void setFailureCount(long count) {
            this.failureCount = count
        }
        
        @Override
        double getFailureRate() {
            return failureRate
        }
        
        @Override
        double getRecentFailureRate(int windowSeconds) {
            return recentFailureRate
        }
        
        @Override
        long getTotalExecutions() {
            return totalExecutions
        }
        
        @Override
        long getFailureCount() {
            return failureCount
        }
    }
    
    // Mock BackpressureProvider for testing
    static class MockBackpressureProvider implements com.vajrapulse.api.backpressure.BackpressureProvider {
        private volatile double backpressure = 0.0
        
        void setBackpressure(double level) {
            this.backpressure = level
        }
        
        @Override
        double getBackpressureLevel() {
            return backpressure
        }
        
        @Override
        String getBackpressureDescription() {
            return "Mock backpressure: ${backpressure}"
        }
    }
    
    def "should initialize with correct state"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
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
            .build()
        
        when: "checking initial state"
        def phase = pattern.getCurrentPhase()
        def currentTps = pattern.getCurrentTps()
        def stableTps = pattern.getStableTps()
        def transitions = pattern.getPhaseTransitionCount()
        
        then: "state should be correct"
        phase == AdaptivePhase.RAMP_UP
        currentTps == 100.0
        stableTps == -1.0
        transitions == 0
    }
    
    def "should return initial TPS on first call"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
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
            .build()
        
        when: "calling calculateTps with small elapsed time"
        def tps = pattern.calculateTps(10) // 10ms elapsed
        
        then: "should return initial TPS"
        tps == 100.0
        pattern.getCurrentPhase() == AdaptivePhase.RAMP_UP
    }
    
    def "should not adjust TPS before ramp interval"() {
        given: "a new adaptive pattern with 1 second ramp interval"
        def provider = new MockMetricsProvider()
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
            .build()
        
        when: "calling calculateTps before ramp interval"
        def tps1 = pattern.calculateTps(100) // 100ms - before interval
        def tps2 = pattern.calculateTps(500) // 500ms - still before interval
        
        then: "TPS should remain at initial value"
        tps1 == 100.0
        tps2 == 100.0
        pattern.getCurrentTps() == 100.0
    }
    
    def "should ramp up TPS after ramp interval with no errors"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0) // No errors
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
            .build()
        
        when: "calling calculateTps after ramp interval"
        // Initialize first call
        pattern.calculateTps(0)
        // Wait for ramp interval (1000ms)
        def tps = pattern.calculateTps(1001)
        
        then: "TPS should increase by ramp increment"
        tps == 150.0 // 100 + 50
        pattern.getCurrentTps() == 150.0
        pattern.getCurrentPhase() == AdaptivePhase.RAMP_UP
    }
    
    def "should transition to RAMP_DOWN when error threshold exceeded"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        provider.setFailureRate(2.0) // 2% failure rate (above 1% threshold)
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
            .build()
        
        when: "calling calculateTps after ramp interval with errors"
        pattern.calculateTps(0) // Initialize
        def tps = pattern.calculateTps(1001) // After 1 second
        
        then: "should transition to RAMP_DOWN and decrease TPS"
        def phase = pattern.getCurrentPhase()
        phase == AdaptivePhase.RAMP_DOWN
        tps == 10.0 // max(10.0, 100 - 100) = 10.0 (minTps)
    }
    
    def "should find stable point after 3 consecutive stable intervals"() {
        given: "a new adaptive pattern in RAMP_DOWN phase"
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0) // No errors (stable)
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(200.0)
            .rampIncrement(50.0)
            .rampDecrement(50.0) // Use smaller decrement to avoid hitting minimum
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(1000.0)
            .minTps(10.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .build()
        
        when: "simulating 3 consecutive stable intervals"
        // First, trigger RAMP_DOWN (by setting errors, then clearing)
        provider.setFailureRate(2.0)
        pattern.calculateTps(0) // Initialize
        pattern.calculateTps(1001) // Trigger RAMP_DOWN: 200 -> 150
        
        // Now clear errors and wait for 3 stable intervals
        provider.setFailureRate(0.0)
        provider.setRecentFailureRate(0.0)
        pattern.calculateTps(2001) // First stable interval (still at 150)
        pattern.calculateTps(3001) // Second stable interval (still at 150)
        def tps = pattern.calculateTps(4001) // Third stable interval - should transition to SUSTAIN
        
        then: "should transition to SUSTAIN phase"
        pattern.getCurrentPhase() == AdaptivePhase.SUSTAIN
        pattern.getStableTps() >= 0.0
        tps == pattern.getStableTps()
        tps == 150.0 // Should sustain at 150 TPS
    }
    
    def "should transition to SUSTAIN when max TPS reached"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0)
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(100.0)
            .rampIncrement(50.0)
            .rampDecrement(100.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(150.0)
            .minTps(10.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .build()
        
        when: "ramping up to max TPS"
        pattern.calculateTps(0) // Initialize
        def tps1 = pattern.calculateTps(1001) // First interval: 100 -> 150
        def tps2 = pattern.calculateTps(2001) // Second interval: 150 (max) -> SUSTAIN
        
        then: "should transition to SUSTAIN at max TPS"
        tps1 == 150.0
        tps2 == 150.0
        pattern.getCurrentPhase() == AdaptivePhase.SUSTAIN
        pattern.getStableTps() == 150.0
    }
    
    def "should handle recovery behavior when TPS reaches minimum in RAMP_DOWN"() {
        given: "a new adaptive pattern that ramps down to minimum TPS"
        def provider = new MockMetricsProvider()
        def backpressureProvider = new MockBackpressureProvider()
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
            .backpressureProvider(backpressureProvider)
            .build()
        
        when: "ramping down until TPS reaches minimum"
        pattern.calculateTps(0) // Initialize
        // Trigger RAMP_DOWN
        provider.setFailureRate(2.0)
        provider.setRecentFailureRate(2.0) // Keep recent rate high to prevent recovery
        backpressureProvider.setBackpressure(0.8) // High backpressure to prevent recovery
        pattern.calculateTps(1001) // First ramp down: 100 -> 10 (minimum)
        
        def tps = pattern.calculateTps(2001)
        
        then: "should stay in RAMP_DOWN at minimum TPS (recovery behavior)"
        pattern.getCurrentPhase() == AdaptivePhase.RAMP_DOWN
        tps == 10.0 // minTps
    }
    
    def "should transition from RAMP_DOWN (at minimum) to RAMP_UP when conditions improve"() {
        given: "a pattern at minimum TPS in RAMP_DOWN phase"
        def provider = new MockMetricsProvider()
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
            .build()
        
        when: "pattern ramps down to minimum, then conditions improve"
        pattern.calculateTps(0) // Initialize
        // Trigger RAMP_DOWN to minimum
        provider.setFailureRate(2.0)
        provider.setRecentFailureRate(2.0)
        pattern.calculateTps(1001) // Ramp down to 10 (minimum, recovery behavior in RAMP_DOWN)
        
        // Conditions improve - use recent window for recovery decision
        provider.setFailureRate(0.0)
        provider.setRecentFailureRate(0.0) // Recent window shows improvement
        def tps = pattern.calculateTps(2001) // Should transition to RAMP_UP
        
        then: "should transition to RAMP_UP with recovery TPS"
        pattern.getCurrentPhase() == AdaptivePhase.RAMP_UP
        tps == 50.0 // Recovery TPS is 50% of lastKnownGood (100) = 50.0
    }
    
    def "should use lastKnownGoodTps for recovery TPS calculation"() {
        given: "a pattern that ramps up then down to minimum"
        def provider = new MockMetricsProvider()
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
            .build()
        
        when: "pattern ramps up to 500 TPS, then down to minimum"
        pattern.calculateTps(0) // Initialize at 100 TPS
        // Ramp up to 500 TPS
        provider.setFailureRate(0.0)
        pattern.calculateTps(1001) // 100 -> 150
        pattern.calculateTps(2001) // 150 -> 200
        pattern.calculateTps(3001) // 200 -> 250
        pattern.calculateTps(4001) // 250 -> 300
        pattern.calculateTps(5001) // 300 -> 350
        pattern.calculateTps(6001) // 350 -> 400
        pattern.calculateTps(7001) // 400 -> 450
        pattern.calculateTps(8001) // 450 -> 500
        
        // Now ramp down to minimum (recovery behavior in RAMP_DOWN)
        provider.setFailureRate(2.0)
        provider.setRecentFailureRate(2.0)
        pattern.calculateTps(9001) // 500 -> 400 (RAMP_DOWN, lastKnownGoodTps = 500)
        pattern.calculateTps(10001) // 400 -> 300
        pattern.calculateTps(11001) // 300 -> 200
        pattern.calculateTps(12001) // 200 -> 100
        pattern.calculateTps(13001) // 100 -> 10 (minimum, recovery behavior in RAMP_DOWN)
        
        // Conditions improve
        provider.setFailureRate(0.0)
        provider.setRecentFailureRate(0.0)
        def recoveryTps = pattern.calculateTps(14001) // Should recover at 50% of 500 = 250
        
        then: "recovery TPS should be 50% of last known good"
        pattern.getCurrentPhase() == AdaptivePhase.RAMP_UP
        // Recovery TPS is 50% of lastKnownGoodTps (which should be the peak TPS before ramping down)
        recoveryTps >= 150.0 // At least 50% of 300 (minimum expected)
        recoveryTps <= 250.0 // At most 50% of 500 (ideal case)
    }
    
    def "should transition from SUSTAIN to RAMP_DOWN when conditions worsen"() {
        given: "a pattern in SUSTAIN phase"
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0)
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(100.0)
            .rampIncrement(50.0)
            .rampDecrement(100.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(150.0)
            .minTps(10.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .build()
        
        when: "pattern sustains at max TPS, then errors occur"
        pattern.calculateTps(0) // Initialize
        pattern.calculateTps(1001) // Ramp to 150 (max) -> SUSTAIN
        def phase1 = pattern.getCurrentPhase()
        
        // Conditions worsen
        provider.setFailureRate(2.0)
        def tps = pattern.calculateTps(2001) // Should transition to RAMP_DOWN
        
        then: "should transition to RAMP_DOWN"
        phase1 == AdaptivePhase.SUSTAIN
        pattern.getCurrentPhase() == AdaptivePhase.RAMP_DOWN
        tps == 50.0 // 150 - 100 = 50
    }
    
    def "should handle zero elapsed time"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
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
            .build()
        
        when: "calling calculateTps with zero elapsed time"
        def tps = pattern.calculateTps(0)
        
        then: "should return initial TPS"
        tps == 100.0
    }
    
    def "should handle negative elapsed time"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
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
            .build()
        
        when: "calling calculateTps with negative elapsed time"
        def tps = pattern.calculateTps(-100)
        
        then: "should return 0.0"
        tps == 0.0
    }
    
    def "should be thread-safe for concurrent calculateTps calls"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0)
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
            .build()
        
        when: "calling calculateTps concurrently from multiple threads"
        def executor = Executors.newFixedThreadPool(10)
        def latch = new CountDownLatch(100)
        def results = Collections.synchronizedList(new ArrayList<Double>())
        
        100.times {
            executor.submit {
                try {
                    def tps = pattern.calculateTps(System.currentTimeMillis() % 10000)
                    results.add(tps)
                } finally {
                    latch.countDown()
                }
            }
        }
        
        latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()
        
        then: "all calls should complete without errors"
        results.size() == 100
        results.every { it >= 0.0 }
        !results.contains(Double.NaN)
        !results.contains(Double.POSITIVE_INFINITY)
        !results.contains(Double.NEGATIVE_INFINITY)
    }
    
    def "should return very long duration"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
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
            .build()
        
        when: "checking duration"
        def duration = pattern.getDuration()
        
        then: "should return very long duration (indefinite)"
        duration.toDays() >= 365
    }
    
    def "should track phase transitions correctly"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(100.0)
            .rampIncrement(50.0)
            .rampDecrement(100.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(150.0)
            .minTps(10.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .build()
        
        when: "transitioning through phases"
        pattern.calculateTps(0) // Initialize
        def transitions1 = pattern.getPhaseTransitionCount()
        def phase1 = pattern.getCurrentPhase()
        
        // Ramp up to max
        provider.setFailureRate(0.0)
        pattern.calculateTps(1001) // RAMP_UP (100 -> 150, reaches max, transitions to SUSTAIN)
        def transitions2 = pattern.getPhaseTransitionCount()
        def phase2 = pattern.getCurrentPhase()
        
        // At 2001ms, already in SUSTAIN
        pattern.calculateTps(2001) // Still in SUSTAIN
        def transitions3 = pattern.getPhaseTransitionCount()
        def phase3 = pattern.getCurrentPhase()
        
        then: "transition count should increase and phase should change"
        transitions1 == 0
        phase1 == AdaptivePhase.RAMP_UP
        
        // After first ramp interval, TPS reaches 150 (max), so transitions to SUSTAIN immediately
        transitions2 == 1 // Transitioned to SUSTAIN when max reached
        phase2 == AdaptivePhase.SUSTAIN
        
        transitions3 == 1 // Still in SUSTAIN, no new transition
        phase3 == AdaptivePhase.SUSTAIN
    }
    
    def "should use backpressure provider when provided"() {
        given: "a metrics provider and backpressure provider"
        def metricsProvider = new MockMetricsProvider()
        metricsProvider.setFailureRate(0.0)
        def backpressureProvider = new MockBackpressureProvider()
        backpressureProvider.setBackpressure(0.8)
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(5.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(100.0)
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(metricsProvider)
            .backpressureProvider(backpressureProvider)
            .build()
        
        when: "calculating TPS with high backpressure"
        def tps1 = pattern.calculateTps(0)
        def tps2 = pattern.calculateTps(1100) // After ramp interval
        
        then: "pattern should ramp down due to backpressure"
        tps2 <= tps1
    }
    
    def "should work without backpressure provider"() {
        given: "a metrics provider without backpressure provider"
        def metricsProvider = new MockMetricsProvider()
        metricsProvider.setFailureRate(0.0)
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(5.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(100.0)
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(metricsProvider)
            .build()
        
        when: "getting backpressure level"
        def backpressure = pattern.getBackpressureLevel()
        
        then: "should return 0.0 when no provider"
        backpressure == 0.0
    }
    
    def "should combine error rate and backpressure for ramp down decision"() {
        given: "low error rate but high backpressure"
        def metricsProvider = new MockMetricsProvider()
        metricsProvider.setFailureRate(0.5) // 0.5% error rate (below 1% threshold)
        def backpressureProvider = new MockBackpressureProvider()
        backpressureProvider.setBackpressure(0.75) // High backpressure
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(5.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(100.0)
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(metricsProvider)
            .backpressureProvider(backpressureProvider)
            .build()
        
        when: "calculating TPS"
        def tps1 = pattern.calculateTps(0)
        def tps2 = pattern.calculateTps(1100) // After ramp interval
        
        then: "should ramp down due to backpressure even with low error rate"
        tps2 <= tps1
    }
    
    def "should ramp up only when both error rate and backpressure are low"() {
        given: "low error rate and low backpressure"
        def metricsProvider = new MockMetricsProvider()
        metricsProvider.setFailureRate(0.5) // 0.5% error rate (below 1% threshold)
        def backpressureProvider = new MockBackpressureProvider()
        backpressureProvider.setBackpressure(0.2) // Low backpressure
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(5.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(100.0)
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(metricsProvider)
            .backpressureProvider(backpressureProvider)
            .build()
        
        when: "calculating TPS"
        def tps1 = pattern.calculateTps(0)
        def tps2 = pattern.calculateTps(1100) // After ramp interval
        
        then: "should ramp up when both conditions are met"
        tps2 >= tps1
    }
}
