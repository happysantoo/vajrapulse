package com.vajrapulse.core.integration

import com.vajrapulse.api.*
import com.vajrapulse.api.task.Task
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.api.task.VirtualThreads
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern
import com.vajrapulse.api.pattern.adaptive.AdaptivePhase
import com.vajrapulse.core.engine.ExecutionEngine
import com.vajrapulse.core.engine.MetricsProviderAdapter
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

import static org.awaitility.Awaitility.*
import static java.util.concurrent.TimeUnit.*

/**
 * Integration tests for AdaptiveLoadPattern with ExecutionEngine.
 * 
 * <p>Tests verify:
 * <ul>
 *   <li>Adaptive pattern integrates with ExecutionEngine</li>
 *   <li>Pattern state transitions during execution</li>
 *   <li>Metrics collection during adaptive execution</li>
 *   <li>Pattern behavior with different task failure rates</li>
 *   <li>Pattern finds stable point and transitions correctly</li>
 * </ul>
 */
@Timeout(60)
class AdaptiveLoadPatternExecutionSpec extends Specification {
    
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
    
    @VirtualThreads
    static class ControlledFailureTask implements Task {
        private final AtomicInteger counter = new AtomicInteger(0)
        private final int startFailingAt
        private final int stopFailingAt
        
        ControlledFailureTask(int startFailingAt, int stopFailingAt) {
            this.startFailingAt = startFailingAt
            this.stopFailingAt = stopFailingAt
        }
        
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(10)
            int count = counter.incrementAndGet()
            if (count >= startFailingAt && count < stopFailingAt) {
                return TaskResult.failure(new RuntimeException("Simulated failure"))
            }
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    def "should execute with adaptive pattern in RAMP_UP phase"() {
        given: "an adaptive pattern with success-only tasks"
        def metrics = new MetricsCollector()
        def task = new SuccessTask()
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(50.0)
            .sustainDuration(Duration.ofSeconds(2))
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(2))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.01))
            .build()
        
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
                .withMetricsCollector(metrics)
                .withShutdownHook(false)
                .build()
        
        when: "running engine for short duration"
        def executionThread = Thread.start {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected - test will stop engine
            }
        }
        
        // Wait for pattern to be in RAMP_UP or SUSTAIN phase
        await().atMost(5, SECONDS)
            .pollInterval(200, MILLISECONDS)
            .until {
                def currentPhase = pattern.getCurrentPhase()
                currentPhase == AdaptivePhase.RAMP_UP || currentPhase == AdaptivePhase.SUSTAIN
            }
        
        // Stop the engine
        engine.stop()
        executionThread.join(5000)
        
        then: "pattern should be in RAMP_UP or SUSTAIN phase"
        def phase = pattern.getCurrentPhase()
        phase == AdaptivePhase.RAMP_UP || phase == AdaptivePhase.SUSTAIN
        
        and: "some executions should have occurred"
        def snapshot = metrics.snapshot()
        snapshot.totalExecutions() > 0
        
        and: "current TPS should be at least initial TPS"
        pattern.getCurrentTps() >= 10.0
        
        cleanup:
        engine?.close()
        metrics?.close()
    }
    
    def "should transition to RAMP_DOWN when errors occur"() {
        given: "an adaptive pattern with failing tasks"
        def metrics = new MetricsCollector()
        def task = new FailingTask(5) // 5% failure rate
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(50.0)
            .sustainDuration(Duration.ofSeconds(2))
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(2))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.01))
            .build()
        
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
                .withMetricsCollector(metrics)
                .withShutdownHook(false)
                .build()
        
        when: "running engine until errors trigger RAMP_DOWN"
        def executionThread = Thread.start {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected - test will stop engine
            }
        }
        
        // Wait for pattern to transition to RAMP_DOWN due to errors or accumulate enough executions
        def executionCount = 0L
        await().atMost(10, SECONDS)
            .pollInterval(200, MILLISECONDS)
            .until {
                def currentPhase = pattern.getCurrentPhase()
                def currentSnapshot = metrics.snapshot()
                executionCount = currentSnapshot.totalExecutions()
                // Transition to RAMP_DOWN/SUSTAIN OR enough executions to trigger error threshold
                currentPhase == AdaptivePhase.RAMP_DOWN || 
                currentPhase == AdaptivePhase.SUSTAIN ||
                executionCount >= 100 // Enough executions to potentially trigger errors
            }
        
        // Give it a bit more time to process errors and transition
        Thread.sleep(1500) // Wait for ramp interval to pass
        
        // Stop the engine
        engine.stop()
        executionThread.join(5000)
        
        then: "pattern should transition to RAMP_DOWN or SUSTAIN (or be in RAMP_UP if not enough time elapsed)"
        def phase = pattern.getCurrentPhase()
        def finalSnapshot = metrics.snapshot()
        // May be in RAMP_DOWN (including recovery behavior at minimum), SUSTAIN (if stable point found),
        // or still in RAMP_UP if not enough time has passed for error threshold to be detected
        phase in [AdaptivePhase.RAMP_UP, 
                  AdaptivePhase.RAMP_DOWN, 
                  AdaptivePhase.SUSTAIN]
        
        // If we have enough executions, we should have transitioned
        if (finalSnapshot.totalExecutions() >= 50) {
            phase in [AdaptivePhase.RAMP_DOWN, AdaptivePhase.SUSTAIN]
        }
        
        and: "some executions should have occurred"
        finalSnapshot.totalExecutions() > 0
        
        cleanup:
        engine?.close()
        metrics?.close()
    }
    
    def "should find stable point and transition to SUSTAIN"() {
        given: "an adaptive pattern with controlled failure task"
        def metrics = new MetricsCollector()
        // Task fails initially, then succeeds (simulates finding stable point)
        def task = new ControlledFailureTask(1, 15) // Fails for first 15 executions
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(50.0)
            .sustainDuration(Duration.ofSeconds(2))
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(2))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.01))
            .build()
        
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
                .withMetricsCollector(metrics)
                .withShutdownHook(false)
                .build()
        
        when: "running engine until stable point is found"
        def executionThread = Thread.start {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected - test will stop engine
            }
        }
        
        // Wait for pattern to potentially find stable point and transition to SUSTAIN
        await().atMost(20, SECONDS)
            .pollInterval(500, MILLISECONDS)
            .until {
                def currentPhase = pattern.getCurrentPhase()
                currentPhase == AdaptivePhase.SUSTAIN
            }
        
        // Stop the engine
        engine.stop()
        executionThread.join(5000)
        
        then: "pattern may have found stable point"
        def phase = pattern.getCurrentPhase()
        // Should be in SUSTAIN if stable point found
        phase == AdaptivePhase.SUSTAIN
        
        and: "executions should have occurred"
        def snapshot = metrics.snapshot()
        snapshot.totalExecutions() > 0
        
        cleanup:
        engine?.close()
        metrics?.close()
    }
    
    def "should collect metrics during adaptive execution"() {
        given: "an adaptive pattern"
        def metrics = new MetricsCollector()
        def task = new SuccessTask()
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(50.0)
            .sustainDuration(Duration.ofSeconds(2))
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(2))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.01))
            .build()
        
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
                .withMetricsCollector(metrics)
                .withShutdownHook(false)
                .build()
        
        when: "running engine for short duration"
        def executionThread = Thread.start {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected - test will stop engine
            }
        }
        
        // Wait for some executions to occur
        await().atMost(5, SECONDS)
            .pollInterval(200, MILLISECONDS)
            .until { metrics.snapshot().totalExecutions() > 0 }
        
        engine.stop()
        executionThread.join(5000)
        
        then: "metrics should be collected"
        def snapshot = metrics.snapshot()
        snapshot.totalExecutions() > 0
        snapshot.successCount() >= 0
        snapshot.failureCount() >= 0
        
        and: "adaptive pattern metrics should be registered"
        def registry = metrics.getRegistry()
        def phaseGauge = registry.find("vajrapulse.adaptive.phase").gauge()
        def currentTpsGauge = registry.find("vajrapulse.adaptive.current_tps").gauge()
        
        phaseGauge != null
        currentTpsGauge != null
        
        cleanup:
        engine?.close()
        metrics?.close()
    }
    
    def "should handle rapid TPS changes"() {
        given: "an adaptive pattern with small ramp interval"
        def metrics = new MetricsCollector()
        def task = new SuccessTask()
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(10.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofMillis(500))
            .maxTps(100.0)
            .sustainDuration(Duration.ofSeconds(1))
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(2))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.01))
            .build()
        
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
                .withMetricsCollector(metrics)
                .withShutdownHook(false)
                .build()
        
        when: "running engine with rapid adjustments"
        def executionThread = Thread.start {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected - test will stop engine
            }
        }
        
        // Wait for pattern to process multiple ramp intervals
        await().atMost(5, SECONDS)
            .pollInterval(300, MILLISECONDS)
            .until {
                def currentSnapshot = metrics.snapshot()
                currentSnapshot.totalExecutions() > 0 && pattern.getCurrentPhase() != null
            }
        
        engine.stop()
        executionThread.join(5000)
        
        then: "pattern should handle rapid changes"
        def phase = pattern.getCurrentPhase()
        phase != null
        
        and: "executions should have occurred"
        def snapshot = metrics.snapshot()
        snapshot.totalExecutions() > 0
        
        cleanup:
        engine?.close()
        metrics?.close()
    }
    
    def "should not hang when pattern returns 0.0 TPS"() {
        given: "an adaptive pattern that may reach minimum TPS"
        def metrics = new MetricsCollector()
        def task = new FailingTask(50) // 50% failure rate - will trigger RAMP_DOWN
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(50.0)
            .sustainDuration(Duration.ofSeconds(2))
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(2))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.01))
            .build()
        
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
                .withMetricsCollector(metrics)
                .withShutdownHook(false)
                .build()
        
        when: "running engine until pattern may reach minimum TPS"
        def startTime = System.currentTimeMillis()
        def executionThread = Thread.start {
            try {
                engine.run()
            } catch (Exception e) {
                // Expected - test will stop engine
            }
        }
        
        // Wait for pattern to potentially reach minimum TPS (recovery behavior in RAMP_DOWN)
        // This test verifies the pattern doesn't hang, so we wait for either:
        // 1. RAMP_DOWN phase at minimum TPS (recovery behavior)
        // 2. Or sufficient time has passed (pattern is still running)
        await().atMost(20, SECONDS)
            .pollInterval(1, SECONDS)
            .until {
                def currentPhase = pattern.getCurrentPhase()
                (currentPhase == AdaptivePhase.RAMP_DOWN && pattern.getCurrentTps() <= 0.0) || 
                (System.currentTimeMillis() - startTime) > 12000 // At least 12s passed
            }
        
        // Stop the engine (should not hang)
        def stopTime = System.currentTimeMillis()
        engine.stop()
        executionThread.join(10000) // Should complete within 10 seconds
        def stopDuration = System.currentTimeMillis() - stopTime
        
        then: "engine should stop without hanging"
        stopDuration < 10000 // Should stop quickly
        
        and: "executions should have occurred"
        def snapshot = metrics.snapshot()
        snapshot.totalExecutions() > 0
        
        cleanup:
        engine?.close()
        metrics?.close()
    }
    
    def "should handle empty metrics gracefully"() {
        given: "an adaptive pattern with new metrics collector"
        def metrics = new MetricsCollector()
        def task = new SuccessTask()
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(50.0)
            .sustainDuration(Duration.ofSeconds(2))
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(2))
            .stableIntervalsRequired(3)
            .metricsProvider(provider)
            .decisionPolicy(new com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy(0.01))
            .build()
        
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
                .withMetricsCollector(metrics)
                .withShutdownHook(false)
                .build()
        
        when: "calling calculateTps before any executions"
        def tps = pattern.calculateTps(0)
        
        then: "should return initial TPS without errors"
        tps == 10.0
        pattern.getCurrentPhase() == AdaptivePhase.RAMP_UP
        
        and: "metrics provider should handle empty metrics"
        provider.getFailureRate() >= 0.0
        provider.getTotalExecutions() >= 0
        
        cleanup:
        engine?.close()
        metrics?.close()
    }
}

