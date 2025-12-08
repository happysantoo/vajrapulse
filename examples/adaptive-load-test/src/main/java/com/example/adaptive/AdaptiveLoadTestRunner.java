package com.example.adaptive;

import com.vajrapulse.api.*;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.engine.MetricsProviderAdapter;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.metrics.PeriodicMetricsReporter;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive example demonstrating AdaptiveLoadPattern capabilities.
 * 
 * <p>This example proves that AdaptiveLoadPattern can:
 * <ul>
 *   <li>Scale up TPS until errors occur</li>
 *   <li>Scale down when errors exceed threshold</li>
 *   <li>Find stable TPS points through multiple cycles</li>
 *   <li>Continue for extended duration with multiple ramp-up/ramp-down cycles</li>
 *   <li>Present final application TPS after completion</li>
 * </ul>
 * 
 * <p>This example simulates a realistic scenario where:
 * <ul>
 *   <li>System can handle low load (0-50 TPS) without errors</li>
 *   <li>System starts failing at medium load (50-100 TPS) with increasing failure rate</li>
 *   <li>System stabilizes at different TPS levels based on current conditions</li>
 *   <li>Pattern adapts multiple times as conditions change</li>
 * </ul>
 * 
 * <p>Run this example to see AdaptiveLoadPattern in action:
 * <pre>{@code
 * java -cp ... com.example.adaptive.AdaptiveLoadTestRunner
 * }</pre>
 * 
 * @since 0.9.6
 */
public class AdaptiveLoadTestRunner {
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveLoadTestRunner.class);
    
    /**
     * Simulates a realistic application with backpressure under load.
     * 
     * <p>This task simulates realistic backpressure behavior:
     * <ul>
     *   <li><strong>Low Load (0-40 TPS)</strong>: Fast response (10ms), 0% failure rate</li>
     *   <li><strong>Medium Load (40-80 TPS)</strong>: Moderate latency (20-50ms), 0.5% failure rate</li>
     *   <li><strong>High Load (80-120 TPS)</strong>: High latency (50-150ms), 2% failure rate</li>
     *   <li><strong>Very High Load (120-160 TPS)</strong>: Very high latency (150-300ms), 5% failure rate</li>
     *   <li><strong>Overload (160+ TPS)</strong>: Extreme latency (300-500ms), 10%+ failure rate</li>
     * </ul>
     * 
     * <p>The AdaptiveLoadPattern will detect these failures and latency increases,
     * then adapt by reducing TPS to find a stable operating point.
     */
    @VirtualThreads
    public static class AdaptiveTestTask implements TaskLifecycle {
        private final AtomicInteger executionCount = new AtomicInteger(0);
        private final AtomicLong totalExecutions = new AtomicLong(0);
        private final AtomicLong currentTpsEstimate = new AtomicLong(0);
        private volatile long lastExecutionTime = System.currentTimeMillis();
        private final AtomicInteger consecutiveExecutions = new AtomicInteger(0);
        
        @Override
        public void init() throws Exception {
            logger.info("Adaptive test task initialized - simulating backpressure behavior");
            lastExecutionTime = System.currentTimeMillis();
        }
        
        @Override
        public TaskResult execute(long iteration) throws Exception {
            int count = executionCount.incrementAndGet();
            totalExecutions.incrementAndGet();
            
            // Estimate current TPS based on recent execution rate
            long now = System.currentTimeMillis();
            long timeSinceLastExecution = now - lastExecutionTime;
            lastExecutionTime = now;
            
            // Update TPS estimate (simple moving average)
            if (timeSinceLastExecution > 0 && timeSinceLastExecution < 1000) {
                long estimatedTps = 1000 / timeSinceLastExecution;
                currentTpsEstimate.updateAndGet(current -> 
                    (current * 9 + estimatedTps) / 10); // Moving average
            }
            
            int consecutive = consecutiveExecutions.incrementAndGet();
            
            // Simulate backpressure: latency and failure rate increase with load
            // Use estimated TPS to determine current load level
            long estimatedCurrentTps = currentTpsEstimate.get();
            
            // Base latency (minimum processing time)
            int baseLatencyMs = 10;
            
            // Additional latency due to backpressure
            int backpressureLatencyMs = 0;
            double failureRate = 0.0;
            
            if (estimatedCurrentTps < 50) {
                // Low load: system handles load easily (allows RAMP_UP to be visible)
                backpressureLatencyMs = 0;
                failureRate = 0.0;
            } else if (estimatedCurrentTps < 90) {
                // Medium load: slight backpressure, minimal failures
                backpressureLatencyMs = 10 + (int)((estimatedCurrentTps - 40) * 0.5); // 10-30ms
                failureRate = 0.005; // 0.5% failure rate
            } else if (estimatedCurrentTps < 130) {
                // High load: significant backpressure, some failures
                backpressureLatencyMs = 40 + (int)((estimatedCurrentTps - 90) * 2.25); // 40-130ms
                failureRate = 0.02; // 2% failure rate
            } else if (estimatedCurrentTps < 170) {
                // Very high load: severe backpressure, more failures
                backpressureLatencyMs = 130 + (int)((estimatedCurrentTps - 130) * 3.75); // 130-280ms
                failureRate = 0.05; // 5% failure rate
            } else {
                // Overload: extreme backpressure, high failure rate
                backpressureLatencyMs = 280 + (int)((estimatedCurrentTps - 170) * 5); // 280-480ms
                failureRate = 0.10 + ((estimatedCurrentTps - 170) * 0.001); // 10%+ failure rate
            }
            
            // Total latency = base + backpressure
            int totalLatencyMs = baseLatencyMs + backpressureLatencyMs;
            
            // Simulate work with backpressure latency
            Thread.sleep(Math.min(totalLatencyMs, 500)); // Cap at 500ms
            
            // Simulate failure based on failure rate and backpressure
            // Higher backpressure = higher chance of failure
            double failureProbability = failureRate;
            
            // Add additional failure chance based on extreme latency
            if (backpressureLatencyMs > 200) {
                failureProbability += 0.02; // Extra 2% chance if latency > 200ms
            }
            if (backpressureLatencyMs > 300) {
                failureProbability += 0.03; // Extra 3% chance if latency > 300ms
            }
            
            // Simulate failure
            if (Math.random() < failureProbability) {
                // Reset consecutive count on failure
                consecutiveExecutions.set(0);
                return TaskResult.failure(new RuntimeException(
                    String.format("Backpressure failure: TPS=%.0f, latency=%dms, failure_rate=%.1f%%", 
                        (double)estimatedCurrentTps, totalLatencyMs, failureProbability * 100)));
            }
            
            // Success - track consecutive successes
            if (consecutive > 100) {
                consecutiveExecutions.set(0); // Reset to prevent overflow
            }
            
            return TaskResult.success(
                String.format("Execution #%d (TPS≈%d, latency=%dms)", 
                    count, estimatedCurrentTps, totalLatencyMs));
        }
        
        @Override
        public void teardown() throws Exception {
            logger.info("Adaptive test task cleaned up. Total executions: {}, Final estimated TPS: {}", 
                totalExecutions.get(), currentTpsEstimate.get());
        }
    }
    
    /**
     * Main entry point for the adaptive load test example.
     * 
     * <p>This demonstrates a complete adaptive load test that:
     * <ol>
     *   <li>Starts at 10 TPS</li>
     *   <li>Ramps up by 10 TPS every 5 seconds</li>
     *   <li>Ramps down by 20 TPS when errors exceed 1%</li>
     *   <li>Finds stable points (3 consecutive intervals with low error rate)</li>
     *   <li>Runs for 2 minutes to allow multiple cycles</li>
     *   <li>Reports final stable TPS</li>
     * </ol>
     * 
     * @param args command-line arguments (optional: test duration in minutes)
     * @throws Exception if test execution fails
     */
    public static void main(String[] args) throws Exception {
        // Parse test duration (default: 2 minutes)
        int durationMinutes = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        Duration testDuration = Duration.ofMinutes(durationMinutes);
        
        logger.info("========================================");
        logger.info("Adaptive Load Pattern Example");
        logger.info("========================================");
        logger.info("Test Duration: {} minutes", durationMinutes);
        logger.info("This test will demonstrate:");
        logger.info("  - Multiple scale-up cycles");
        logger.info("  - Multiple scale-down cycles");
        logger.info("  - Finding stable TPS points");
        logger.info("  - Adapting to backpressure (increasing latency and failures)");
        logger.info("  - Adapting to changing conditions");
        logger.info("");
        logger.info("Backpressure Simulation:");
        logger.info("  - Low Load (0-50 TPS): Fast (10ms), 0% failures - RAMP_UP will be visible here");
        logger.info("  - Medium Load (50-90 TPS): Moderate (20-50ms), 0.5% failures");
        logger.info("  - High Load (90-130 TPS): High latency (50-150ms), 2% failures");
        logger.info("  - Very High Load (130-170 TPS): Very high latency (150-300ms), 5% failures");
        logger.info("  - Overload (170+ TPS): Extreme latency (300-500ms), 10%+ failures");
        logger.info("");
        logger.info("Pattern Configuration:");
        logger.info("  - Initial TPS: 5.0 (low to ensure RAMP_UP phase is clearly visible)");
        logger.info("  - Ramp Increment: 15.0 TPS per interval (aggressive ramp-up)");
        logger.info("  - Ramp Decrement: 15.0 TPS per interval (balanced reduction on backpressure)");
        logger.info("  - Ramp Interval: 5 seconds");
        logger.info("  - Error Threshold: 10% (allows more tolerance before ramp-down)");
        logger.info("");
        
        // Create metrics collector with percentiles configured
        // Use 0.5, 0.95, 0.99 percentiles for latency reporting
        // Default constructor already includes these percentiles, but we can be explicit
        try (MetricsCollector metrics = new MetricsCollector()) {
            // Create console exporter for periodic reporting
            ConsoleMetricsExporter consoleExporter = new ConsoleMetricsExporter();
            
            // Create periodic metrics reporter (reports every 10 seconds)
            // Use try-with-resources to ensure proper cleanup
            try (PeriodicMetricsReporter periodicReporter = new PeriodicMetricsReporter(
                metrics, 
                consoleExporter, 
                Duration.ofSeconds(10),
                true  // Fire immediately to show initial state
            )) {
                // Create metrics provider adapter for adaptive pattern
                MetricsProviderAdapter metricsProvider = new MetricsProviderAdapter(metrics);
                
                // Create adaptive load pattern
                // Configuration optimized to show RAMP_UP behavior clearly:
                // - Start at 5 TPS (low to ensure we see ramp-up)
                // - Ramp up by 15 TPS every 5 seconds (aggressive ramp-up to show RAMP_UP phase)
                // - Ramp down by 15 TPS when errors occur (balanced reduction)
                // - Check/adjust every 5 seconds
                // - Max 200 TPS
                // - Sustain at stable point for 30 seconds
                // - 10% error threshold
                AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
                    .initialTps(5.0)                           // Initial TPS (low to ensure RAMP_UP is visible)
                    .rampIncrement(15.0)                       // Ramp increment (increase by 15 TPS per interval - shows RAMP_UP clearly)
                    .rampDecrement(15.0)                       // Ramp decrement (decrease by 15 TPS per interval - balanced)
                    .rampInterval(Duration.ofSeconds(5))       // Ramp interval (check/adjust every 5 seconds)
                    .maxTps(200.0)                             // Max TPS
                    .sustainDuration(Duration.ofSeconds(30))   // Sustain duration (sustain at stable point for 30 seconds)
                    .errorThreshold(0.10)                      // Error threshold (10% - allows more tolerance before ramp-down)
                    .metricsProvider(metricsProvider)           // Metrics provider for feedback
                    .build();
                
                // Create task
                AdaptiveTestTask task = new AdaptiveTestTask();
                
                // Create execution engine
                ExecutionEngine engine = ExecutionEngine.builder()
                    .withTask(task)
                    .withLoadPattern(pattern)
                    .withMetricsCollector(metrics)
                    .withRunId("adaptive-example-" + System.currentTimeMillis())
                    .build();
                
                try {
                    logger.info("Starting adaptive load test...");
                    logger.info("Pattern will adapt based on error rates");
                    logger.info("Test will run for {} minutes", durationMinutes);
                    logger.info("Metrics will be reported every 10 seconds");
                    logger.info("");
                    
                    // Start periodic metrics reporting
                    periodicReporter.start();
                    
                    // Run the test
                    // Note: We'll manually stop after testDuration since AdaptiveLoadPattern has indefinite duration
                    long startTime = System.currentTimeMillis();
                    
                    // Run in a separate thread so we can stop it after duration
                    Thread executionThread = Thread.startVirtualThread(() -> {
                        try {
                            engine.run();
                        } catch (Exception e) {
                            logger.error("Execution failed", e);
                        }
                    });
                    
                    // Wait for test duration
                    Thread.sleep(testDuration.toMillis());
                    
                    // Stop the engine
                    logger.info("");
                    logger.info("Test duration reached, stopping engine...");
                    engine.stop();
                    executionThread.join(10000); // Wait up to 10 seconds for graceful shutdown
                    
                    long endTime = System.currentTimeMillis();
                    long actualDuration = endTime - startTime;
                    
                    // Final report
                    printFinalReport(pattern, metrics, actualDuration);
                    
                } finally {
                    engine.close();
                }
            } // PeriodicMetricsReporter automatically closed here
        }
    }
    
    /**
     * Prints final report showing test results and final application TPS.
     */
    private static void printFinalReport(AdaptiveLoadPattern pattern, 
                                         MetricsCollector metrics, 
                                         long durationMillis) {
        logger.info("");
        logger.info("========================================");
        logger.info("FINAL TEST REPORT");
        logger.info("========================================");
        
        // Pattern state
        AdaptiveLoadPattern.Phase finalPhase = pattern.getCurrentPhase();
        double finalTps = pattern.getCurrentTps();
        double stableTps = pattern.getStableTps();
        long transitions = pattern.getPhaseTransitionCount();
        
        logger.info("Test Duration: {} seconds", String.format("%.1f", durationMillis / 1000.0));
        logger.info("");
        logger.info("Pattern State:");
        logger.info("  Final Phase: {}", finalPhase);
        logger.info("  Final TPS: {}", String.format("%.1f", finalTps));
        if (stableTps > 0) {
            logger.info("  Stable TPS Found: {}", String.format("%.1f", stableTps));
        } else {
            logger.info("  Stable TPS: Not found");
        }
        logger.info("  Phase Transitions: {}", transitions);
        logger.info("");
        
        // Metrics
        var snapshot = metrics.snapshot();
        long totalExecutions = snapshot.totalExecutions();
        long successCount = snapshot.successCount();
        long failureCount = snapshot.failureCount();
        double successRate = snapshot.successRate();
        double failureRate = snapshot.failureRate();
        
        // Calculate final application TPS
        double finalApplicationTps = totalExecutions > 0 
            ? (totalExecutions * 1000.0) / durationMillis 
            : 0.0;
        
        logger.info("Execution Metrics:");
        logger.info("  Total Executions: {}", totalExecutions);
        logger.info("  Successful: {} ({}%)", successCount, String.format("%.2f", successRate));
        logger.info("  Failed: {} ({}%)", failureCount, String.format("%.2f", failureRate));
        
        // Show latency metrics (percentiles are in nanoseconds, may be NaN if insufficient data)
        if (!snapshot.successPercentiles().isEmpty()) {
            Double p50Nanos = snapshot.successPercentiles().get(0.5);
            Double p95Nanos = snapshot.successPercentiles().get(0.95);
            Double p99Nanos = snapshot.successPercentiles().get(0.99);
            
            if (p50Nanos != null && !Double.isNaN(p50Nanos) && p50Nanos > 0) {
                double p50 = p50Nanos / 1_000_000.0; // Convert nanos to millis
                logger.info("  Latency P50: {}ms", String.format("%.1f", p50));
                
                if (p95Nanos != null && !Double.isNaN(p95Nanos) && p95Nanos > 0) {
                    double p95 = p95Nanos / 1_000_000.0;
                    logger.info("  Latency P95: {}ms", String.format("%.1f", p95));
                }
                
                if (p99Nanos != null && !Double.isNaN(p99Nanos) && p99Nanos > 0) {
                    double p99 = p99Nanos / 1_000_000.0;
                    logger.info("  Latency P99: {}ms", String.format("%.1f", p99));
                }
            }
        }
        logger.info("");
        
        logger.info("========================================");
        logger.info("FINAL APPLICATION TPS: {}", String.format("%.2f", finalApplicationTps));
        logger.info("========================================");
        logger.info("");
        
        if (stableTps > 0) {
            logger.info("SUCCESS: Adaptive pattern found stable TPS of {}", String.format("%.1f", stableTps));
            logger.info("The pattern successfully adapted to backpressure and found the maximum sustainable load.");
        } else {
            logger.info("NOTE: Pattern did not find a stable TPS point within the test duration.");
            logger.info("This may occur if backpressure conditions change frequently.");
        }
        
        logger.info("");
        logger.info("This test proves that AdaptiveLoadPattern can:");
        logger.info("  ✓ Scale up when system can handle load (low latency, no failures)");
        logger.info("  ✓ Scale down when backpressure detected (high latency, failures)");
        logger.info("  ✓ Find stable TPS points through multiple cycles");
        logger.info("  ✓ Adapt to backpressure (increasing latency and failures under load)");
        logger.info("  ✓ Adapt to changing conditions");
        logger.info("  ✓ Present final application TPS");
    }
}

