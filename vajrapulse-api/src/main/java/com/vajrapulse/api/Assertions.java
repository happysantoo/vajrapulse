package com.vajrapulse.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory class for creating common assertions.
 * 
 * <p>This class provides convenient factory methods for creating assertions
 * for common validation scenarios in load testing.
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Single assertion
 * Assertion latency = Assertions.latency(0.95, Duration.ofMillis(100));
 * 
 * // Composite assertion (all must pass)
 * Assertion composite = Assertions.all(
 *     Assertions.latency(0.95, Duration.ofMillis(100)),
 *     Assertions.errorRate(0.01),
 *     Assertions.throughput(1000.0)
 * );
 * 
 * // Evaluate
 * Metrics metrics = metricsCollector.snapshot();
 * AssertionResult result = composite.evaluate(metrics);
 * }</pre>
 * 
 * @since 0.9.7
 */
public final class Assertions {
    
    private Assertions() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Creates an assertion that validates latency at a specific percentile.
     * 
     * <p>The assertion fails if the latency at the specified percentile exceeds
     * the maximum allowed latency.
     * 
     * @param percentile the percentile to check (0.0 to 1.0, e.g., 0.95 for P95)
     * @param maxLatency the maximum allowed latency
     * @return latency assertion
     * @throws IllegalArgumentException if percentile is not in [0.0, 1.0] or maxLatency is negative
     */
    public static Assertion latency(double percentile, Duration maxLatency) {
        if (percentile < 0.0 || percentile > 1.0) {
            throw new IllegalArgumentException("Percentile must be in [0.0, 1.0]: " + percentile);
        }
        if (maxLatency.isNegative() || maxLatency.isZero()) {
            throw new IllegalArgumentException("Max latency must be positive: " + maxLatency);
        }
        
        double maxLatencyNanos = maxLatency.toNanos();
        return metrics -> {
            var percentiles = metrics.successPercentiles();
            if (percentiles.isEmpty()) {
                return AssertionResult.failure("No latency data available for percentile %.2f", percentile);
            }
            
            Double latencyNanos = percentiles.get(percentile);
            if (latencyNanos == null || Double.isNaN(latencyNanos)) {
                return AssertionResult.failure("Latency percentile %.2f not available in metrics", percentile);
            }
            
            if (latencyNanos > maxLatencyNanos) {
                double latencyMs = latencyNanos / 1_000_000.0;
                double maxLatencyMs = maxLatencyNanos / 1_000_000.0;
                return AssertionResult.failure(
                    "P%.0f latency %.2fms exceeds maximum %.2fms",
                    percentile * 100, latencyMs, maxLatencyMs
                );
            }
            
            return AssertionResult.pass();
        };
    }
    
    /**
     * Creates an assertion that validates the error rate.
     * 
     * <p>The assertion fails if the error rate exceeds the maximum allowed rate.
     * 
     * @param maxErrorRate the maximum allowed error rate (0.0 to 1.0, e.g., 0.01 for 1%)
     * @return error rate assertion
     * @throws IllegalArgumentException if maxErrorRate is not in [0.0, 1.0]
     */
    public static Assertion errorRate(double maxErrorRate) {
        if (maxErrorRate < 0.0 || maxErrorRate > 1.0) {
            throw new IllegalArgumentException("Max error rate must be in [0.0, 1.0]: " + maxErrorRate);
        }
        
        return metrics -> {
            double errorRate = metrics.failureRate() / 100.0; // Convert percentage to ratio
            if (errorRate > maxErrorRate) {
                return AssertionResult.failure(
                    "Error rate %.2f%% exceeds maximum %.2f%%",
                    errorRate * 100, maxErrorRate * 100
                );
            }
            return AssertionResult.pass();
        };
    }
    
    /**
     * Creates an assertion that validates the success rate.
     * 
     * <p>The assertion fails if the success rate is below the minimum required rate.
     * 
     * @param minSuccessRate the minimum required success rate (0.0 to 1.0, e.g., 0.99 for 99%)
     * @return success rate assertion
     * @throws IllegalArgumentException if minSuccessRate is not in [0.0, 1.0]
     */
    public static Assertion successRate(double minSuccessRate) {
        if (minSuccessRate < 0.0 || minSuccessRate > 1.0) {
            throw new IllegalArgumentException("Min success rate must be in [0.0, 1.0]: " + minSuccessRate);
        }
        
        return metrics -> {
            double successRate = metrics.successRate() / 100.0; // Convert percentage to ratio
            if (successRate < minSuccessRate) {
                return AssertionResult.failure(
                    "Success rate %.2f%% is below minimum %.2f%%",
                    successRate * 100, minSuccessRate * 100
                );
            }
            return AssertionResult.pass();
        };
    }
    
    /**
     * Creates an assertion that validates throughput (TPS).
     * 
     * <p>The assertion fails if the throughput is below the minimum required TPS.
     * 
     * @param minTps the minimum required transactions per second
     * @return throughput assertion
     * @throws IllegalArgumentException if minTps is negative
     */
    public static Assertion throughput(double minTps) {
        if (minTps < 0.0) {
            throw new IllegalArgumentException("Min TPS must be non-negative: " + minTps);
        }
        
        return metrics -> {
            double actualTps = metrics.responseTps();
            if (actualTps < minTps) {
                return AssertionResult.failure(
                    "Throughput %.2f TPS is below minimum %.2f TPS",
                    actualTps, minTps
                );
            }
            return AssertionResult.pass();
        };
    }
    
    /**
     * Creates an assertion that validates total execution count.
     * 
     * <p>The assertion fails if the total execution count is below the minimum required.
     * 
     * @param minExecutions the minimum required number of executions
     * @return execution count assertion
     * @throws IllegalArgumentException if minExecutions is negative
     */
    public static Assertion executionCount(long minExecutions) {
        if (minExecutions < 0) {
            throw new IllegalArgumentException("Min executions must be non-negative: " + minExecutions);
        }
        
        return metrics -> {
            long actualExecutions = metrics.totalExecutions();
            if (actualExecutions < minExecutions) {
                return AssertionResult.failure(
                    "Total executions %d is below minimum %d",
                    actualExecutions, minExecutions
                );
            }
            return AssertionResult.pass();
        };
    }
    
    /**
     * Creates a composite assertion that requires all provided assertions to pass.
     * 
     * <p>If any assertion fails, the composite assertion fails with the first failure message.
     * 
     * @param assertions the assertions to evaluate (all must pass)
     * @return composite assertion
     * @throws IllegalArgumentException if assertions is null, empty, or contains null
     */
    public static Assertion all(Assertion... assertions) {
        if (assertions == null || assertions.length == 0) {
            throw new IllegalArgumentException("Assertions array must not be null or empty");
        }
        for (Assertion assertion : assertions) {
            if (assertion == null) {
                throw new IllegalArgumentException("Assertions array must not contain null");
            }
        }
        
        return metrics -> {
            for (Assertion assertion : assertions) {
                AssertionResult result = assertion.evaluate(metrics);
                if (result.failed()) {
                    return result;
                }
            }
            return AssertionResult.pass();
        };
    }
    
    /**
     * Creates a composite assertion that requires at least one assertion to pass.
     * 
     * <p>If all assertions fail, the composite assertion fails with a combined message.
     * 
     * @param assertions the assertions to evaluate (at least one must pass)
     * @return composite assertion
     * @throws IllegalArgumentException if assertions is null, empty, or contains null
     */
    public static Assertion any(Assertion... assertions) {
        if (assertions == null || assertions.length == 0) {
            throw new IllegalArgumentException("Assertions array must not be null or empty");
        }
        for (Assertion assertion : assertions) {
            if (assertion == null) {
                throw new IllegalArgumentException("Assertions array must not contain null");
            }
        }
        
        return metrics -> {
            List<String> failures = new ArrayList<>();
            for (Assertion assertion : assertions) {
                AssertionResult result = assertion.evaluate(metrics);
                if (result.passed()) {
                    return AssertionResult.pass();
                }
                failures.add(result.message());
            }
            
            // All failed - combine messages
            return AssertionResult.failure("All assertions failed: " + String.join("; ", failures));
        };
    }
}

