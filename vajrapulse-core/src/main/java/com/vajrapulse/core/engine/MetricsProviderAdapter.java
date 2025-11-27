package com.vajrapulse.core.engine;

import com.vajrapulse.api.MetricsProvider;
import com.vajrapulse.core.metrics.MetricsCollector;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapter that makes MetricsCollector implement MetricsProvider interface with caching.
 * 
 * <p>This allows AdaptiveLoadPattern to work with MetricsCollector
 * without creating a dependency from vajrapulse-api to vajrapulse-core.
 * 
 * <p>This adapter uses caching internally to avoid expensive snapshot operations
 * on every metrics query. The cache has a default TTL of 100ms, which is optimal
 * for high-frequency access patterns like adaptive load patterns.
 * 
 * <p><strong>Implementation Note:</strong> This adapter directly implements MetricsProvider
 * with internal caching. The cache ensures that snapshot() is called at most once per
 * cache refresh period, so both {@code getFailureRate()} and {@code getTotalExecutions()}
 * use the same snapshot when the cache is refreshed.
 * 
 * @since 0.9.5
 */
public final class MetricsProviderAdapter implements MetricsProvider {
    
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMillis(100);
    private static final long DEFAULT_CACHE_TTL_NANOS = DEFAULT_CACHE_TTL.toNanos();
    
    private final MetricsCollector metricsCollector;
    private final long ttlNanos;
    
    // Cached snapshot with timestamp
    private volatile CachedSnapshot cached;
    private final AtomicLong cacheTimeNanos = new AtomicLong(0);
    
    /**
     * Creates an adapter for the given metrics collector with default caching (100ms TTL).
     * 
     * @param metricsCollector the metrics collector to adapt
     * @throws IllegalArgumentException if metricsCollector is null
     */
    public MetricsProviderAdapter(MetricsCollector metricsCollector) {
        this(metricsCollector, DEFAULT_CACHE_TTL);
    }
    
    /**
     * Creates an adapter for the given metrics collector with specified cache TTL.
     * 
     * @param metricsCollector the metrics collector to adapt
     * @param ttl the time-to-live for cached values
     * @throws IllegalArgumentException if metricsCollector is null or ttl is null/negative
     */
    public MetricsProviderAdapter(MetricsCollector metricsCollector, Duration ttl) {
        if (metricsCollector == null) {
            throw new IllegalArgumentException("Metrics collector must not be null");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("TTL must be positive: " + ttl);
        }
        this.metricsCollector = metricsCollector;
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
     * use the same cached snapshot, avoiding multiple calls to snapshot().
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
                    // Refresh cache - call snapshot() once and cache both values
                    var aggregatedMetrics = metricsCollector.snapshot();
                    snapshot = new CachedSnapshot(
                        aggregatedMetrics.failureRate(),
                        aggregatedMetrics.totalExecutions()
                    );
                    
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

