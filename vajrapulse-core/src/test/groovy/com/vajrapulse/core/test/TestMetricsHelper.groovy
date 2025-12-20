package com.vajrapulse.core.test

import com.vajrapulse.core.metrics.MetricsCollector
import com.vajrapulse.core.metrics.CachedMetricsProvider
import java.time.Duration

import static org.awaitility.Awaitility.*
import static java.util.concurrent.TimeUnit.*

/**
 * Test utilities for working with metrics in tests.
 * 
 * <p>This helper class provides common patterns for waiting on metrics conditions
 * in a reliable way, eliminating the need for Thread.sleep() calls.
 * 
 * <p><strong>Example usage:</strong>
 * <pre>{@code
 * def collector = new MetricsCollector()
 * 
 * // Wait for minimum number of executions
 * TestMetricsHelper.waitForExecutions(collector, 100, Duration.ofSeconds(5))
 * 
 * // Wait for cache expiration
 * def cached = new CachedMetricsProvider(provider, Duration.ofMillis(50))
 * TestMetricsHelper.waitForCacheExpiration(cached, Duration.ofMillis(50))
 * }</pre>
 * 
 * @since 0.9.9
 */
class TestMetricsHelper {
    
    /**
     * Waits for a MetricsCollector to record at least the minimum number of executions.
     * 
     * <p>This method polls the collector's snapshot until the total execution count
     * reaches or exceeds the minimum, or the timeout is reached.
     * 
     * @param collector the MetricsCollector to check
     * @param minExecutions minimum number of executions to wait for
     * @param timeout maximum time to wait
     * @throws AssertionError if minimum executions aren't reached within timeout
     */
    static void waitForExecutions(MetricsCollector collector, long minExecutions, Duration timeout) {
        await().atMost(timeout.toSeconds(), SECONDS)
            .pollInterval(100, MILLISECONDS)
            .until {
                collector.snapshot().totalExecutions() >= minExecutions
            }
    }
    
    /**
     * Waits for a CachedMetricsProvider's cache to expire and refresh.
     * 
     * <p>This method waits for the cache TTL to expire by checking if the cached
     * value changes (indicating a refresh). This is more reliable than using
     * Thread.sleep() with a fixed delay.
     * 
     * <p>The method works by:
     * 1. Recording the initial cached value
     * 2. Waiting for enough time to pass (TTL + buffer)
     * 3. Verifying the value has changed (cache refreshed)
     * 
     * @param provider the CachedMetricsProvider to check
     * @param ttl the TTL duration of the cache
     * @throws AssertionError if cache doesn't expire within reasonable time (2x TTL)
     */
    static void waitForCacheExpiration(CachedMetricsProvider provider, Duration ttl) {
        // Get initial value (this will be cached)
        def initialValue = provider.getFailureRate()
        
        // Wait for cache to expire (check if value changes, indicating refresh)
        // Use 2x TTL as maximum wait time to account for timing variations
        def maxWait = ttl.multipliedBy(2)
        def pollIntervalMillis = Math.max(10L, ttl.toMillis() / 10L)
        
        await().atMost(maxWait.toSeconds(), SECONDS)
            .pollInterval(pollIntervalMillis, MILLISECONDS)
            .until {
                // Call getFailureRate() which will refresh if TTL expired
                def currentValue = provider.getFailureRate()
                // Value should be different if cache expired and refreshed
                // Use double comparison to handle floating point precision
                Math.abs(currentValue - initialValue) > 0.0001
            }
    }
    
    /**
     * Waits for a metrics condition to become true.
     * 
     * <p>This is a convenience method for waiting on custom metrics conditions.
     * 
     * @param condition closure that checks metrics and returns true when condition is met
     * @param timeout maximum time to wait
     * @param pollInterval how often to check the condition (default: 100ms)
     */
    static void waitForMetricsCondition(Closure<Boolean> condition, Duration timeout, Duration pollInterval = Duration.ofMillis(100)) {
        await().atMost(timeout.toSeconds(), SECONDS)
            .pollInterval(pollInterval.toMillis(), MILLISECONDS)
            .until(condition)
    }
}
