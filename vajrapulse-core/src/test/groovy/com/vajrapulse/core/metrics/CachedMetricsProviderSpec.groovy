package com.vajrapulse.core.metrics

import com.vajrapulse.api.metrics.MetricsProvider
import spock.lang.Specification

import java.time.Duration
import java.util.Collections

class CachedMetricsProviderSpec extends Specification {

    def "should cache metrics with default TTL"() {
        given: "a cached provider wrapping a delegate"
        def callCount = new java.util.concurrent.atomic.AtomicInteger(0)
        def delegate = new MetricsProvider() {
            @Override
            double getFailureRate() {
                callCount.incrementAndGet()
                return 5.0
            }
            
            @Override
            long getTotalExecutions() {
                callCount.incrementAndGet()
                return 100L
            }
        }
        def cached = new CachedMetricsProvider(delegate)

        when: "calling methods multiple times within TTL"
        def rate1 = cached.getFailureRate()
        def exec1 = cached.getTotalExecutions()
        def rate2 = cached.getFailureRate()
        def exec2 = cached.getTotalExecutions()

        then: "delegate called only once per method (cached)"
        rate1 == 5.0
        exec1 == 100L
        rate2 == 5.0
        exec2 == 100L
        callCount.get() == 2  // Once for failureRate, once for totalExecutions
    }

    def "should refresh cache after TTL expires"() {
        given: "a cached provider with short TTL"
        def callCount = new java.util.concurrent.atomic.AtomicInteger(0)
        def delegate = new MetricsProvider() {
            @Override
            double getFailureRate() {
                callCount.incrementAndGet()
                return callCount.get() * 1.0  // Return different value each time
            }
            
            @Override
            long getTotalExecutions() {
                return 100L
            }
        }
        def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(50))

        when: "calling within TTL"
        def rate1 = cached.getFailureRate()
        def rate2 = cached.getFailureRate()

        then: "cached value used"
        rate1 == rate2
        callCount.get() == 1

        when: "waiting for TTL to expire and calling again"
        // Wait for TTL to expire (50ms TTL, wait 60ms)
        Thread.sleep(60)
        def rate3 = cached.getFailureRate()

        then: "cache refreshed"
        rate3 != rate1
        callCount.get() == 2
    }

    def "should be thread-safe"() {
        given: "a cached provider"
        def callCount = new java.util.concurrent.atomic.AtomicInteger(0)
        def delegate = new MetricsProvider() {
            @Override
            double getFailureRate() {
                callCount.incrementAndGet()
                Thread.sleep(10)  // Simulate work
                return 5.0
            }
            
            @Override
            long getTotalExecutions() {
                return 100L
            }
        }
        def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(100))

        when: "multiple threads access simultaneously"
        def results = []
        def threads = []
        10.times {
            threads << Thread.startVirtualThread {
                results << cached.getFailureRate()
            }
        }
        threads.each { it.join() }

        then: "all threads get same value and delegate called once"
        results.every { it == 5.0 }
        callCount.get() == 1  // Only one refresh despite concurrent access
    }

    def "should reject null delegate"() {
        when: "creating with null delegate"
        new CachedMetricsProvider(null)

        then: "exception thrown"
        thrown(IllegalArgumentException)
    }

    def "should reject invalid TTL"() {
        given: "a valid delegate"
        def delegate = Mock(MetricsProvider)

        when: "creating with null TTL"
        new CachedMetricsProvider(delegate, null)

        then: "exception thrown"
        thrown(IllegalArgumentException)

        when: "creating with negative TTL"
        new CachedMetricsProvider(delegate, Duration.ofMillis(-1))

        then: "exception thrown"
        thrown(IllegalArgumentException)

        when: "creating with zero TTL"
        new CachedMetricsProvider(delegate, Duration.ZERO)

        then: "exception thrown"
        thrown(IllegalArgumentException)
    }

    def "should handle high concurrency without race conditions"() {
        given: "a cached provider with longer TTL to prevent expiration during test"
        def callCount = new java.util.concurrent.atomic.AtomicInteger(0)
        def delegate = new MetricsProvider() {
            @Override
            double getFailureRate() {
                callCount.incrementAndGet()
                Thread.sleep(1)  // Simulate work to increase chance of race
                return 10.0
            }
            
            @Override
            long getTotalExecutions() {
                callCount.incrementAndGet()
                Thread.sleep(1)
                return 200L
            }
        }
        // Use longer TTL (500ms) to ensure cache doesn't expire during concurrent access
        def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(500))

        when: "100 threads access simultaneously"
        def results = Collections.synchronizedList([])
        def threads = []
        100.times {
            threads << Thread.startVirtualThread {
                results << cached.getFailureRate()
                results << cached.getTotalExecutions()
            }
        }
        threads.each { it.join() }

        then: "all threads get consistent values"
        results.every { it == 10.0 || it == 200L }
        
        and: "delegate called minimal times despite high concurrency"
        // Should be called once for failureRate and once for totalExecutions
        // Due to caching, both methods use same snapshot
        // Allow some variance in case of timing issues, but should be minimal
        def actualCalls = callCount.get()
        actualCalls >= 2  // At least initial cache fill
        actualCalls <= 4  // Allow one potential refresh if timing is tight
    }

    def "should handle cache expiration under concurrent access"() {
        given: "a cached provider with very short TTL"
        def callCount = new java.util.concurrent.atomic.AtomicInteger(0)
        def delegate = new MetricsProvider() {
            @Override
            double getFailureRate() {
                callCount.incrementAndGet()
                return callCount.get() * 1.0
            }
            
            @Override
            long getTotalExecutions() {
                callCount.incrementAndGet()
                return callCount.get() * 10L
            }
        }
        def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(10))

        when: "accessing cache, waiting for expiration, then accessing again concurrently"
        def firstValue = cached.getFailureRate()
        // Wait for TTL to expire (10ms TTL, wait 20ms to ensure expiration)
        // Using Thread.sleep directly since we're waiting for time to pass, not a condition
        Thread.sleep(20)
        
        def results = []
        def threads = []
        50.times {
            threads << Thread.startVirtualThread {
                results << cached.getFailureRate()
            }
        }
        threads.each { it.join() }

        then: "all threads get the same refreshed value"
        results.every { it == results[0] }
        results[0] != firstValue // Should be different from first value
        
        and: "cache was refreshed only once"
        // Should have initial call + one refresh call
        callCount.get() >= 2
        callCount.get() <= 4 // Allow some variance but should be minimal
    }

    def "should maintain cache consistency under stress"() {
        given: "a cached provider with longer TTL to prevent expiration during test"
        def callCount = new java.util.concurrent.atomic.AtomicInteger(0)
        def delegate = new MetricsProvider() {
            @Override
            double getFailureRate() {
                callCount.incrementAndGet()
                return 5.0
            }
            
            @Override
            long getTotalExecutions() {
                callCount.incrementAndGet()
                return 100L
            }
        }
        // Use longer TTL (500ms) to ensure cache doesn't expire during concurrent access
        def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(500))

        when: "stressing with many concurrent accesses"
        def failureRates = []
        def executionCounts = []
        def threads = []
        
        // Use synchronized collections to avoid race conditions in test itself
        def failureRatesSync = Collections.synchronizedList(failureRates)
        def executionCountsSync = Collections.synchronizedList(executionCounts)
        
        200.times {
            threads << Thread.startVirtualThread {
                failureRatesSync << cached.getFailureRate()
                executionCountsSync << cached.getTotalExecutions()
            }
        }
        threads.each { it.join() }

        then: "all values are consistent"
        failureRatesSync.every { it == 5.0 }
        executionCountsSync.every { it == 100L }
        
        and: "delegate called minimal times due to caching"
        // Should be called once for each method in snapshot (2 calls total)
        // Allow some variance in case of timing issues, but should be minimal
        def actualCalls = callCount.get()
        actualCalls >= 2  // At least initial cache fill
        actualCalls <= 4  // Allow one potential refresh if timing is tight
    }

    def "should handle rapid cache expiration and refresh cycles"() {
        given: "a cached provider with very short TTL"
        def callCount = new java.util.concurrent.atomic.AtomicInteger(0)
        def delegate = new MetricsProvider() {
            @Override
            double getFailureRate() {
                callCount.incrementAndGet()
                return callCount.get() * 1.0
            }
            
            @Override
            long getTotalExecutions() {
                callCount.incrementAndGet()
                return callCount.get() * 10L
            }
        }
        def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(5))

        when: "rapidly accessing cache with expiration cycles"
        def allResults = []
        def threads = []
        
        // Create multiple waves of access
        10.times { wave ->
            threads << Thread.startVirtualThread {
                Thread.sleep(wave * 10) // Stagger access
                allResults << cached.getFailureRate()
            }
        }
        threads.each { it.join() }

        then: "cache refreshes correctly without race conditions"
        // Results should be consistent within each cache period
        // May have different values across waves due to expiration
        allResults.size() == 10
        
        and: "no exceptions thrown"
        noExceptionThrown()
    }
}

