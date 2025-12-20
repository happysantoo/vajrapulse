package com.vajrapulse.api.assertion;

import com.vajrapulse.api.metrics.Metrics;

/**
 * Interface for evaluating assertions against aggregated metrics.
 * 
 * <p>Assertions validate that test results meet expected criteria, such as:
 * <ul>
 *   <li>Latency thresholds (e.g., P95 latency &lt; 100ms)</li>
 *   <li>Error rate limits (e.g., error rate &lt; 1%)</li>
 *   <li>Throughput requirements (e.g., TPS &gt; 1000)</li>
 *   <li>Success rate requirements (e.g., success rate &gt; 99%)</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * Assertion latencyAssertion = Assertions.latency(0.95, Duration.ofMillis(100));
 * Assertion errorRateAssertion = Assertions.errorRate(0.01); // 1% max
 * 
 * AggregatedMetrics metrics = metricsCollector.snapshot();
 * AssertionResult latencyResult = latencyAssertion.evaluate(metrics);
 * AssertionResult errorResult = errorRateAssertion.evaluate(metrics);
 * 
 * if (latencyResult.failed()) {
 *     System.err.println("Latency assertion failed: " + latencyResult.message());
 * }
 * }</pre>
 * 
 * @since 0.9.7
 */
public interface Assertion {
    
    /**
     * Evaluates the assertion against the provided metrics.
     * 
     * @param metrics the metrics to evaluate
     * @return assertion result (success or failure with message)
     * @throws IllegalArgumentException if metrics is null
     */
    AssertionResult evaluate(Metrics metrics);
}

