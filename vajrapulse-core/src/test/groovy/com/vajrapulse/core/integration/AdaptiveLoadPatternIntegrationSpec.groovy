package com.vajrapulse.core.integration

import com.vajrapulse.api.*
import com.vajrapulse.core.engine.ExecutionEngine
import com.vajrapulse.core.engine.MetricsProviderAdapter
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration tests for AdaptiveLoadPattern with ExecutionEngine.
 * 
 * <p>Tests verify:
 * <ul>
 *   <li>Adaptive pattern integrates with ExecutionEngine</li>
 *   <li>Metrics are registered correctly</li>
 *   <li>Pattern state is accessible</li>
 * </ul>
 * 
 * <p>Note: Full execution tests are limited due to adaptive pattern's indefinite duration.
 * These tests focus on integration aspects that can be verified without full execution.
 */
@Timeout(30)
class AdaptiveLoadPatternIntegrationSpec extends Specification {
    
    @VirtualThreads
    static class SuccessTask implements Task {
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(10) // Simulate I/O
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    @VirtualThreads
    static class FailingTask implements Task {
        private final AtomicInteger counter = new AtomicInteger(0)
        private final int failureRatePercent
        
        FailingTask(int failureRatePercent) {
            this.failureRatePercent = failureRatePercent
        }
        
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(10)
            int count = counter.incrementAndGet()
            // Fail based on failure rate
            if (count % (100 / failureRatePercent) == 0) {
                return TaskResult.failure(new RuntimeException("Simulated failure"))
            }
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    def "should integrate with ExecutionEngine and register metrics"() {
        given: "an adaptive pattern"
        def metrics = new MetricsCollector()
        def task = new SuccessTask()
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = new AdaptiveLoadPattern(
            10.0, 5.0, 10.0, Duration.ofSeconds(1),
            50.0, Duration.ofSeconds(2), 0.01, provider
        )
        
        when: "creating ExecutionEngine with adaptive pattern"
        def engine = new ExecutionEngine(task, pattern, metrics)
        
        then: "adaptive pattern metrics should be registered"
        def registry = metrics.getRegistry()
        def phaseGauge = registry.find("vajrapulse.adaptive.phase").gauge()
        def currentTpsGauge = registry.find("vajrapulse.adaptive.current_tps").gauge()
        def stableTpsGauge = registry.find("vajrapulse.adaptive.stable_tps").gauge()
        def transitionsGauge = registry.find("vajrapulse.adaptive.phase_transitions").gauge()
        
        phaseGauge != null
        currentTpsGauge != null
        stableTpsGauge != null
        transitionsGauge != null
        
        and: "pattern should be initialized in RAMP_UP phase"
        pattern.getCurrentPhase() == AdaptiveLoadPattern.Phase.RAMP_UP
        pattern.getCurrentTps() == 10.0
    }
    
    def "should have indefinite duration"() {
        given: "an adaptive pattern"
        def metrics = new MetricsCollector()
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = new AdaptiveLoadPattern(
            10.0, 5.0, 10.0, Duration.ofSeconds(1),
            50.0, Duration.ofSeconds(1), 0.01, provider
        )
        
        when: "checking pattern duration"
        def duration = pattern.getDuration()
        
        then: "pattern duration should be very long (indefinite)"
        duration.toDays() >= 365
    }
    
    def "should provide access to pattern state"() {
        given: "an adaptive pattern"
        def metrics = new MetricsCollector()
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = new AdaptiveLoadPattern(
            10.0, 5.0, 10.0, Duration.ofSeconds(1),
            50.0, Duration.ofSeconds(1), 0.01, provider
        )
        
        when: "querying pattern state"
        def phase = pattern.getCurrentPhase()
        def currentTps = pattern.getCurrentTps()
        def stableTps = pattern.getStableTps()
        def transitions = pattern.getPhaseTransitionCount()
        
        then: "state should be accessible"
        phase != null
        currentTps == 10.0 // Initial TPS
        stableTps == -1.0 // Not found yet
        transitions == 0 // No transitions yet
    }
}

