package com.vajrapulse.core.metrics;

import com.vajrapulse.api.MetricsProvider;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

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
    
    // Cached snapshot with timestamp
    // Using volatile for snapshot to ensure visibility across threads
    private volatile CachedSnapshot cached;
    // Using AtomicLong for timestamp to ensure atomic reads and proper memory ordering
    private final AtomicLong cacheTimeNanos = new AtomicLong(0);
    
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
    
    /**
     * Gets a cached snapshot, refreshing if expired.
     * 
     * <p>This method ensures that both getFailureRate() and getTotalExecutions()
     * use the same cached snapshot, avoiding multiple calls to the delegate.
     * 
     * <p><strong>Thread Safety:</strong> This method uses double-check locking with
     * proper memory ordering guarantees:
     * <ul>
     *   <li>AtomicLong for cacheTimeNanos ensures atomic reads with proper ordering</li>
     *   <li>Volatile for cached snapshot ensures visibility across threads</li>
     *   <li>Synchronized block prevents concurrent cache refreshes</li>
     *   <li>Double-check pattern minimizes synchronization overhead</li>
     * </ul>
     * 
     * @return cached snapshot
     */
    private CachedSnapshot getCachedSnapshot() {
        long now = System.nanoTime();
        CachedSnapshot snapshot = this.cached; // Volatile read
        
        // Read cacheTimeNanos atomically to get proper memory ordering
        long cachedTime = cacheTimeNanos.get(); // Atomic read with memory ordering
        
        // Check if cache is valid (fast path - no synchronization)
        if (snapshot == null || (now - cachedTime) > ttlNanos) {
            // Synchronize to prevent multiple threads from refreshing simultaneously
            synchronized (this) {
                // Double-check after acquiring lock - re-read both values
                snapshot = this.cached; // Volatile read inside synchronized
                cachedTime = cacheTimeNanos.get(); // Atomic read inside synchronized
                
                if (snapshot == null || (now - cachedTime) > ttlNanos) {
                    // Refresh cache - call delegate methods once
                    double failureRate = delegate.getFailureRate();
                    long totalExecutions = delegate.getTotalExecutions();
                    snapshot = new CachedSnapshot(failureRate, totalExecutions);
                    
                    // Write both values with proper ordering
                    // Write timestamp first (atomic with memory ordering)
                    long freshTimestamp = System.nanoTime();
                    cacheTimeNanos.set(freshTimestamp); // Atomic write with memory ordering
                    // Then write snapshot (volatile write ensures visibility)
                    this.cached = snapshot; // Volatile write
                } else {
                    // Another thread refreshed it, use the cached value
                    snapshot = this.cached; // Volatile read
                }
            }
        }
        
        return snapshot;
    }
    
    /**
     * Cached snapshot of metrics.
     */
    private record CachedSnapshot(double failureRate, long totalExecutions) {}
}

