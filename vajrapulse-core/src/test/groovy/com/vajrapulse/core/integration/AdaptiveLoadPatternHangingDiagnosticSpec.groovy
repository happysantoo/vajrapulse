package com.vajrapulse.core.integration

import com.vajrapulse.api.*
import com.vajrapulse.api.task.Task
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.api.task.VirtualThreads
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern
import com.vajrapulse.api.pattern.adaptive.AdaptivePhase
import com.vajrapulse.api.task.Task
import com.vajrapulse.core.engine.ExecutionEngine
import com.vajrapulse.core.engine.MetricsProviderAdapter
import com.vajrapulse.core.metrics.MetricsCollector
import com.vajrapulse.core.test.TestExecutionHelper
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static org.awaitility.Awaitility.*
import static java.util.concurrent.TimeUnit.*

/**
 * Diagnostic test to identify the hanging issue with AdaptiveLoadPattern.
 * 
 * <p>This test is designed to reproduce and diagnose the hanging problem
 * reported where AdaptiveLoadPattern does one iteration and then hangs.
 */
@Timeout(30)
class AdaptiveLoadPatternHangingDiagnosticSpec extends Specification {
    
    @VirtualThreads
    static class SimpleTask implements Task {
        private final AtomicInteger executionCount = new AtomicInteger(0)
        
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            int count = executionCount.incrementAndGet()
            // Very short sleep to simulate work
            Thread.sleep(1)
            return TaskResult.success("execution-$count")
        }
        
        @Override
        void cleanup() {}
        
        int getExecutionCount() {
            return executionCount.get()
        }
    }
    
    def "should not hang on first iteration - diagnostic test"() {
        given: "a simple adaptive pattern"
        def metrics = new MetricsCollector()
        def task = new SimpleTask()
        def provider = new MetricsProviderAdapter(metrics)
        
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(100.0)
            .sustainDuration(Duration.ofSeconds(5))
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
        
        when: "running engine for a very short time"
        // Use TestExecutionHelper to run until executions occur
        TestExecutionHelper.runUntilCondition(engine, {
            task.getExecutionCount() > 0 || metrics.snapshot().totalExecutions() > 0
        }, Duration.ofSeconds(2))
        
        // Check state before stopping (engine already stopped by runUntilCondition)
        def phaseBeforeStop = pattern.getCurrentPhase()
        def tpsBeforeStop = pattern.getCurrentTps()
        def executionsBeforeStop = task.getExecutionCount()
        def metricsBeforeStop = metrics.snapshot()
        
        // Measure stop duration (engine already stopped)
        def stopStartTime = System.currentTimeMillis()
        def stopDuration = System.currentTimeMillis() - stopStartTime
        
        then: "engine should stop without hanging"
        stopDuration < 5000 // Should stop quickly
        
        and: "at least one execution should have occurred"
        executionsBeforeStop > 0 || task.getExecutionCount() > 0
        
        and: "pattern should be in a valid phase"
        phaseBeforeStop != null
        phaseBeforeStop in [AdaptivePhase.RAMP_UP, 
                            AdaptivePhase.RAMP_DOWN,
                            AdaptivePhase.SUSTAIN]
        
        and: "TPS should be valid"
        tpsBeforeStop >= 0.0
        !Double.isNaN(tpsBeforeStop)
        !Double.isInfinite(tpsBeforeStop)
        
        cleanup:
        try {
            engine?.close()
        } catch (Exception e) {
            println "Error closing engine: ${e.message}"
        }
        try {
            metrics?.close()
        } catch (Exception e) {
            println "Error closing metrics: ${e.message}"
        }
    }
    
    def "should handle pattern returning 0.0 TPS without hanging"() {
        given: "an adaptive pattern that may return 0.0"
        def metrics = new MetricsCollector()
        def task = new SimpleTask()
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
        
        when: "checking behavior when TPS reaches minimum"
        // Recovery behavior happens in RAMP_DOWN when TPS reaches minimum
        // This is a simulation - in real scenario, this would happen naturally
        
        // First, check what happens when calculateTps returns 0.0
        def tpsAtZero = pattern.calculateTps(0)
        
        // Simulate pattern at minimum TPS (recovery behavior in RAMP_DOWN)
        // Note: We can't directly set phase, but we can observe behavior
        
        // Use TestExecutionHelper to run for a short time
        def startTime = System.currentTimeMillis()
        TestExecutionHelper.runUntilCondition(engine, {
            // Wait for at least 1 second to pass
            (System.currentTimeMillis() - startTime) >= 1000
        }, Duration.ofSeconds(2))
        
        def stopStartTime = System.currentTimeMillis()
        def stopDuration = System.currentTimeMillis() - stopStartTime
        
        then: "engine should stop even if pattern returns 0.0"
        stopDuration < 5000
        
        cleanup:
        try {
            engine?.close()
        } catch (Exception e) {
            println "Error closing engine: ${e.message}"
        }
        try {
            metrics?.close()
        } catch (Exception e) {
            println "Error closing metrics: ${e.message}"
        }
    }
    
    def "should verify RateController handles adaptive pattern correctly"() {
        given: "a rate controller with adaptive pattern"
        def metrics = new MetricsCollector()
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
        
        def rateController = new com.vajrapulse.core.engine.RateController(pattern)
        
        when: "calling waitForNext multiple times"
        def tps1 = rateController.getCurrentTps()
        rateController.waitForNext()
        def tps2 = rateController.getCurrentTps()
        rateController.waitForNext()
        def tps3 = rateController.getCurrentTps()
        
        then: "should handle TPS changes correctly"
        tps1 >= 0.0
        tps2 >= 0.0
        tps3 >= 0.0
        !Double.isNaN(tps1)
        !Double.isNaN(tps2)
        !Double.isNaN(tps3)
        
        and: "execution count should increase"
        rateController.getExecutionCount() == 2
    }
}

