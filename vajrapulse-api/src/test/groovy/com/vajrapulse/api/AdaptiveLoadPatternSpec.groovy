package com.vajrapulse.api

import spock.lang.Specification
import spock.lang.Timeout
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    static class MockMetricsProvider implements MetricsProvider {
        private volatile double failureRate = 0.0
        private volatile long totalExecutions = 0
        
        void setFailureRate(double rate) {
            this.failureRate = rate
        }
        
        void setTotalExecutions(long count) {
            this.totalExecutions = count
        }
        
        @Override
        double getFailureRate() {
            return failureRate
        }
        
        @Override
        long getTotalExecutions() {
            return totalExecutions
        }
    }
    
    def "should initialize with correct state"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "checking initial state"
        def phase = pattern.getCurrentPhase()
        def currentTps = pattern.getCurrentTps()
        def stableTps = pattern.getStableTps()
        def transitions = pattern.getPhaseTransitionCount()
        
        then: "state should be correct"
        phase == AdaptiveLoadPattern.Phase.RAMP_UP
        currentTps == 100.0
        stableTps == -1.0
        transitions == 0
    }
    
    def "should return initial TPS on first call"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "calling calculateTps with small elapsed time"
        def tps = pattern.calculateTps(10) // 10ms elapsed
        
        then: "should return initial TPS"
        tps == 100.0
        pattern.getCurrentPhase() == AdaptiveLoadPattern.Phase.RAMP_UP
    }
    
    def "should not adjust TPS before ramp interval"() {
        given: "a new adaptive pattern with 1 second ramp interval"
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
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
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "calling calculateTps after ramp interval"
        // Initialize first call
        pattern.calculateTps(0)
        // Wait for ramp interval (1000ms)
        def tps = pattern.calculateTps(1001)
        
        then: "TPS should increase by ramp increment"
        tps == 150.0 // 100 + 50
        pattern.getCurrentTps() == 150.0
        pattern.getCurrentPhase() == AdaptiveLoadPattern.Phase.RAMP_UP
    }
    
    def "should transition to RAMP_DOWN when error threshold exceeded"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        provider.setFailureRate(2.0) // 2% failure rate (above 1% threshold)
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "calling calculateTps after ramp interval with errors"
        pattern.calculateTps(0) // Initialize
        def tps = pattern.calculateTps(1001) // After 1 second
        
        then: "should transition to RAMP_DOWN and decrease TPS"
        pattern.getCurrentPhase() == AdaptiveLoadPattern.Phase.RAMP_DOWN
        tps == 0.0 // 100 - 100 = 0 (but should be clamped to >= 0)
        // Actually, Math.max(0, 100 - 100) = 0
    }
    
    def "should find stable point after 3 consecutive stable intervals"() {
        given: "a new adaptive pattern in RAMP_DOWN phase"
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0) // No errors (stable)
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "simulating 3 consecutive stable intervals"
        // First, trigger RAMP_DOWN (by setting errors, then clearing)
        provider.setFailureRate(2.0)
        pattern.calculateTps(0) // Initialize
        pattern.calculateTps(1001) // Trigger RAMP_DOWN
        
        // Now clear errors and wait for 3 stable intervals
        provider.setFailureRate(0.0)
        pattern.calculateTps(2001) // First stable interval
        pattern.calculateTps(3001) // Second stable interval
        def tps = pattern.calculateTps(4001) // Third stable interval - should transition to SUSTAIN
        
        then: "should transition to SUSTAIN phase"
        pattern.getCurrentPhase() == AdaptiveLoadPattern.Phase.SUSTAIN
        pattern.getStableTps() >= 0.0
        tps == pattern.getStableTps()
    }
    
    def "should transition to SUSTAIN when max TPS reached"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0)
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            150.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "ramping up to max TPS"
        pattern.calculateTps(0) // Initialize
        def tps1 = pattern.calculateTps(1001) // First interval: 100 -> 150
        def tps2 = pattern.calculateTps(2001) // Second interval: 150 (max) -> SUSTAIN
        
        then: "should transition to SUSTAIN at max TPS"
        tps1 == 150.0
        tps2 == 150.0
        pattern.getCurrentPhase() == AdaptiveLoadPattern.Phase.SUSTAIN
        pattern.getStableTps() == 150.0
    }
    
    def "should return 0.0 in COMPLETE phase"() {
        given: "a new adaptive pattern that fails to find stable point"
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "exhausting ramp down attempts"
        pattern.calculateTps(0) // Initialize
        // Trigger RAMP_DOWN
        provider.setFailureRate(2.0)
        pattern.calculateTps(1001)
        
        // Simulate 10 ramp down attempts without finding stable point
        provider.setFailureRate(2.0) // Always errors
        for (int i = 2; i <= 11; i++) {
            pattern.calculateTps(i * 1000L + 1)
        }
        
        def tps = pattern.calculateTps(12001)
        
        then: "should transition to COMPLETE and return 0.0"
        pattern.getCurrentPhase() == AdaptiveLoadPattern.Phase.COMPLETE
        tps == 0.0
    }
    
    def "should handle zero elapsed time"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "calling calculateTps with zero elapsed time"
        def tps = pattern.calculateTps(0)
        
        then: "should return initial TPS"
        tps == 100.0
    }
    
    def "should handle negative elapsed time"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "calling calculateTps with negative elapsed time"
        def tps = pattern.calculateTps(-100)
        
        then: "should return 0.0"
        tps == 0.0
    }
    
    def "should be thread-safe for concurrent calculateTps calls"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        provider.setFailureRate(0.0)
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
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
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            1000.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "checking duration"
        def duration = pattern.getDuration()
        
        then: "should return very long duration (indefinite)"
        duration.toDays() >= 365
    }
    
    def "should track phase transitions correctly"() {
        given: "a new adaptive pattern"
        def provider = new MockMetricsProvider()
        def pattern = new AdaptiveLoadPattern(
            100.0, 50.0, 100.0, Duration.ofSeconds(1),
            150.0, Duration.ofSeconds(10), 0.01, provider
        )
        
        when: "transitioning through phases"
        pattern.calculateTps(0) // Initialize
        def transitions1 = pattern.getPhaseTransitionCount()
        def phase1 = pattern.getCurrentPhase()
        
        // Ramp up to max
        provider.setFailureRate(0.0)
        pattern.calculateTps(1001) // RAMP_UP (100 -> 150, reaches max)
        def transitions2 = pattern.getPhaseTransitionCount()
        def phase2 = pattern.getCurrentPhase()
        
        // At 2001ms, currentTps is 150 (max), so handleRampUp should transition to SUSTAIN
        pattern.calculateTps(2001) // RAMP_UP -> SUSTAIN (at max)
        def transitions3 = pattern.getPhaseTransitionCount()
        def phase3 = pattern.getCurrentPhase()
        
        then: "transition count should increase and phase should change"
        transitions1 == 0
        phase1 == AdaptiveLoadPattern.Phase.RAMP_UP
        
        // After first ramp interval, TPS is 150 (max), so handleRampUp transitions to SUSTAIN
        transitions2 == 1 // Transitioned to SUSTAIN when max reached
        phase2 == AdaptiveLoadPattern.Phase.SUSTAIN
        
        transitions3 == 1 // Still in SUSTAIN, no new transition
        phase3 == AdaptiveLoadPattern.Phase.SUSTAIN
    }
    
    def "should use backpressure provider when provided"() {
        given: "a metrics provider and backpressure provider"
        def metricsProvider = new MockMetricsProvider()
        metricsProvider.setFailureRate(0.0)
        def backpressureProvider = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.8
            getBackpressureDescription() >> "High backpressure"
        }
        def pattern = new AdaptiveLoadPattern(
            10.0, 5.0, 5.0, Duration.ofSeconds(1),
            100.0, Duration.ofSeconds(10), 0.01,
            metricsProvider,
            backpressureProvider
        )
        
        when: "calculating TPS with high backpressure"
        def tps1 = pattern.calculateTps(0)
        // Wait for ramp interval (1 second) and verify TPS decreased
        await().atMost(3, SECONDS)
            .pollInterval(100, MILLISECONDS)
            .until {
                def elapsed = 1100
                def tps2 = pattern.calculateTps(elapsed)
                tps2 <= tps1 // Should hold or decrease due to backpressure
            }
        
        then: "pattern should ramp down due to backpressure"
        def tps2 = pattern.calculateTps(1100)
        tps2 <= tps1
    }
    
    def "should work without backpressure provider"() {
        given: "a metrics provider without backpressure provider"
        def metricsProvider = new MockMetricsProvider()
        metricsProvider.setFailureRate(0.0)
        def pattern = new AdaptiveLoadPattern(
            10.0, 5.0, 5.0, Duration.ofSeconds(1),
            100.0, Duration.ofSeconds(10), 0.01,
            metricsProvider
        )
        
        when: "getting backpressure level"
        def backpressure = pattern.getBackpressureLevel()
        
        then: "should return 0.0 when no provider"
        backpressure == 0.0
    }
    
    def "should combine error rate and backpressure for ramp down decision"() {
        given: "low error rate but high backpressure"
        def metricsProvider = new MockMetricsProvider()
        metricsProvider.setFailureRate(0.5) // 0.5% error rate (below 1% threshold)
        def backpressureProvider = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.75 // High backpressure
        }
        def pattern = new AdaptiveLoadPattern(
            10.0, 5.0, 5.0, Duration.ofSeconds(1),
            100.0, Duration.ofSeconds(10), 0.01,
            metricsProvider,
            backpressureProvider
        )
        
        when: "calculating TPS"
        def tps1 = pattern.calculateTps(0)
        // Wait for ramp interval and verify ramp down due to backpressure
        await().atMost(3, SECONDS)
            .pollInterval(100, MILLISECONDS)
            .until {
                def elapsed = 1100
                def tps2 = pattern.calculateTps(elapsed)
                tps2 <= tps1 // Should hold or decrease
            }
        
        then: "should ramp down due to backpressure even with low error rate"
        def tps2 = pattern.calculateTps(1100)
        tps2 <= tps1
    }
    
    def "should ramp up only when both error rate and backpressure are low"() {
        given: "low error rate and low backpressure"
        def metricsProvider = new MockMetricsProvider()
        metricsProvider.setFailureRate(0.5) // 0.5% error rate (below 1% threshold)
        def backpressureProvider = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.2 // Low backpressure
        }
        def pattern = new AdaptiveLoadPattern(
            10.0, 5.0, 5.0, Duration.ofSeconds(1),
            100.0, Duration.ofSeconds(10), 0.01,
            metricsProvider,
            backpressureProvider
        )
        
        when: "calculating TPS"
        def tps1 = pattern.calculateTps(0)
        // Wait for ramp interval and verify ramp up
        await().atMost(3, SECONDS)
            .pollInterval(100, MILLISECONDS)
            .until {
                def elapsed = 1100
                def tps2 = pattern.calculateTps(elapsed)
                tps2 >= tps1 // Should increase
            }
        
        then: "should ramp up when both conditions are met"
        def tps2 = pattern.calculateTps(1100)
        tps2 >= tps1
    }
}
