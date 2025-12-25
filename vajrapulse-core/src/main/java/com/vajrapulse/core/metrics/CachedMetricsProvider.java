package com.vajrapulse.core.metrics;

import com.vajrapulse.api.metrics.MetricsProvider;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Caches metrics provider results to reduce expensive snapshot operations.
 * 
 * <p>This wrapper caches the results of {@link MetricsProvider} calls with a
 * configurable time-to-live (TTL). This is critical for high-frequency access
 * patterns, such as adaptive load patterns that query metrics on every TPS calculation.
 * 
 * <p>Thread-safe for concurrent access from multiple threads.
 * 
 * <p>Example usage:
 * <pre>{@code
 * MetricsProvider baseProvider = new MetricsProviderAdapter(metricsCollector);
 * MetricsProvider cachedProvider = new CachedMetricsProvider(baseProvider, Duration.ofMillis(100));
 * 
 * AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(..., cachedProvider);
 * }</pre>
 * 
 * @since 0.9.5
 */
public final class CachedMetricsProvider implements MetricsProvider {
    
    private static final Duration DEFAULT_TTL = Duration.ofMillis(100);
    
    private final MetricsProvider delegate;
    private final long ttlNanos;
    
    // Cached snapshot with timestamp - using AtomicReference for lock-free updates
    private final AtomicReference<CachedSnapshot> cached = new AtomicReference<>();
    // Using AtomicLong for timestamp to ensure atomic reads and proper memory ordering
    private final AtomicLong cacheTimeNanos = new AtomicLong(0);
    // Flag to prevent concurrent cache refreshes (lock-free coordination)
    private final AtomicLong refreshInProgress = new AtomicLong(0);
    
    /**
     * Creates a cached metrics provider with default TTL (100ms).
     * 
     * @param delegate the underlying metrics provider to cache
     * @throws IllegalArgumentException if delegate is null
     */
    public CachedMetricsProvider(MetricsProvider delegate) {
        this(delegate, DEFAULT_TTL);
    }
    
    /**
     * Creates a cached metrics provider with specified TTL.
     * 
     * @param delegate the underlying metrics provider to cache
     * @param ttl the time-to-live for cached values
     * @throws IllegalArgumentException if delegate is null or ttl is null/negative
     */
    public CachedMetricsProvider(MetricsProvider delegate, Duration ttl) {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate metrics provider must not be null");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("TTL must be positive: " + ttl);
        }
        this.delegate = delegate;
        this.ttlNanos = ttl.toNanos();
    }
    
    @Override
    public double getFailureRate() {
        return getCachedSnapshot().failureRate();
    }
    
    @Override
    public long getTotalExecutions() {
        return getCachedSnapshot().totalExecutions();
    }
    
    @Override
    public long getFailureCount() {
        return getCachedSnapshot().failureCount();
    }
    
    /**
     * Gets a cached snapshot, refreshing if expired.
     * 
     * <p>This method ensures that getFailureRate(), getTotalExecutions(), and
     * getFailureCount() use the same cached snapshot, avoiding multiple calls to the delegate.
     * 
     * <p><strong>Thread Safety:</strong> This method uses a lock-free approach with
     * compare-and-swap operations and a refresh coordination flag:
     * <ul>
     *   <li>AtomicReference for cached snapshot ensures atomic updates</li>
     *   <li>AtomicLong for cacheTimeNanos ensures atomic reads with proper ordering</li>
     *   <li>AtomicLong refreshInProgress coordinates cache refreshes</li>
     *   <li>Compare-and-swap prevents concurrent cache refreshes without blocking</li>
     *   <li>Double-check pattern minimizes expensive delegate calls</li>
     * </ul>
     * 
     * @return cached snapshot
     */
    private CachedSnapshot getCachedSnapshot() {
        long now = System.nanoTime();
        CachedSnapshot snapshot = cached.get(); // Atomic read
        
        // Read cacheTimeNanos atomically to get proper memory ordering
        long cachedTime = cacheTimeNanos.get(); // Atomic read with memory ordering
        
        // Check if cache is valid (fast path - no synchronization)
        if (snapshot == null || (now - cachedTime) > ttlNanos) {
            // Try to become the refresh coordinator (lock-free)
            long expected = 0;
            if (refreshInProgress.compareAndSet(expected, now)) {
                // We're the coordinator - refresh the cache
                try {
                    // Re-check cache (another thread might have refreshed while we were waiting)
                    CachedSnapshot current = cached.get();
                    cachedTime = cacheTimeNanos.get();
                    
                    if (current == null || (now - cachedTime) > ttlNanos) {
                        // Refresh cache - call delegate methods once
                        double failureRate = delegate.getFailureRate();
                        long totalExecutions = delegate.getTotalExecutions();
                        long failureCount = delegate.getFailureCount();
                        CachedSnapshot newSnapshot = new CachedSnapshot(failureRate, totalExecutions, failureCount);
                        
                        // Update cache atomically
                        cached.set(newSnapshot); // Atomic write
                        cacheTimeNanos.set(now); // Atomic write with memory ordering
                        snapshot = newSnapshot;
                    } else {
                        // Cache was refreshed by another coordinator
                        snapshot = current;
                    }
                } finally {
                    // Release coordination flag
                    refreshInProgress.set(0);
                }
            } else {
                // Another thread is coordinating refresh - spin-wait briefly then read
                // Use a short spin loop to avoid blocking (lock-free retry)
                int spins = 0;
                while (spins < 100 && refreshInProgress.get() != 0) {
                    Thread.onSpinWait(); // CPU-friendly spin wait
                    spins++;
                }
                
                // Read the refreshed cache value
                snapshot = cached.get(); // Atomic read
                cachedTime = cacheTimeNanos.get();
                
                // If still expired after waiting, the coordinator should have refreshed it
                // If not, we'll get a slightly stale value which is acceptable for performance
                if (snapshot == null || (now - cachedTime) > ttlNanos) {
                    // Final attempt - coordinator should be done by now
                    snapshot = cached.get();
                }
            }
        }
        
        return snapshot;
    }
    
    /**
     * Cached snapshot of metrics.
     */
    private record CachedSnapshot(double failureRate, long totalExecutions, long failureCount) {}
}

