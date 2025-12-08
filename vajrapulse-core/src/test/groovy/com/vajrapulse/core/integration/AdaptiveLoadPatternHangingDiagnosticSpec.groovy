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
        
        def pattern = new AdaptiveLoadPattern(
            10.0,  // Initial TPS
            5.0,   // Ramp increment
            10.0,  // Ramp decrement
            Duration.ofSeconds(1),  // Ramp interval
            100.0, // Max TPS
            Duration.ofSeconds(5), // Sustain duration
            0.01,  // Error threshold (1%)
            provider
        )
        
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
            .withMetricsCollector(metrics)
            .build()
        
        when: "running engine for a very short time"
        def executionThread = Thread.start {
            try {
                engine.run()
            } catch (Exception e) {
                println "Engine exception: ${e.message}"
                e.printStackTrace()
            }
        }
        
        // Wait just long enough for a few iterations
        Thread.sleep(500)
        
        // Check state before stopping
        def phaseBeforeStop = pattern.getCurrentPhase()
        def tpsBeforeStop = pattern.getCurrentTps()
        def executionsBeforeStop = task.getExecutionCount()
        def metricsBeforeStop = metrics.snapshot()
        
        // Stop the engine
        def stopStartTime = System.currentTimeMillis()
        engine.stop()
        executionThread.join(5000) // Should complete within 5 seconds
        def stopDuration = System.currentTimeMillis() - stopStartTime
        
        then: "engine should stop without hanging"
        stopDuration < 5000 // Should stop quickly
        
        and: "at least one execution should have occurred"
        executionsBeforeStop > 0 || task.getExecutionCount() > 0
        
        and: "pattern should be in a valid phase"
        phaseBeforeStop != null
        phaseBeforeStop in [AdaptiveLoadPattern.Phase.RAMP_UP, 
                            AdaptiveLoadPattern.Phase.RAMP_DOWN,
                            AdaptiveLoadPattern.Phase.SUSTAIN]
        
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
        
        def pattern = new AdaptiveLoadPattern(
            10.0, 5.0, 10.0, Duration.ofSeconds(1),
            50.0, Duration.ofSeconds(2), 0.01, provider
        )
        
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
            .withMetricsCollector(metrics)
            .build()
        
        when: "checking behavior when TPS reaches minimum"
        // Recovery behavior happens in RAMP_DOWN when TPS reaches minimum
        // This is a simulation - in real scenario, this would happen naturally
        
        // First, check what happens when calculateTps returns 0.0
        def tpsAtZero = pattern.calculateTps(0)
        
        // Simulate pattern at minimum TPS (recovery behavior in RAMP_DOWN)
        // Note: We can't directly set phase, but we can observe behavior
        
        def executionThread = Thread.start {
            try {
                engine.run()
            } catch (Exception e) {
                println "Engine exception: ${e.message}"
            }
        }
        
        Thread.sleep(1000)
        engine.stop()
        def stopDuration = System.currentTimeMillis()
        executionThread.join(5000)
        stopDuration = System.currentTimeMillis() - stopDuration
        
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
        
        def pattern = new AdaptiveLoadPattern(
            10.0, 5.0, 10.0, Duration.ofSeconds(1),
            50.0, Duration.ofSeconds(2), 0.01, provider
        )
        
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

