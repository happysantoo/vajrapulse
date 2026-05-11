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
    
    private final AtomicReference<CachedSnapshot> cached = new AtomicReference<>();
    private final AtomicLong cacheTimeNanos = new AtomicLong(0);
    private final Object refreshLock = new Object();
    
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
     * Gets a cached snapshot, refreshing if expired under a synchronized lock.
     * Uses double-checked locking: fast-path reads outside the lock, refresh inside it.
     */
    private CachedSnapshot getCachedSnapshot() {
        long now = System.nanoTime();
        CachedSnapshot snapshot = cached.get();
        long cachedTime = cacheTimeNanos.get();

        if (snapshot != null && (now - cachedTime) <= ttlNanos) {
            return snapshot;
        }

        synchronized (refreshLock) {
            // Re-read now inside lock for accurate double-check
            now = System.nanoTime();
            snapshot = cached.get();
            cachedTime = cacheTimeNanos.get();
            if (snapshot != null && (now - cachedTime) <= ttlNanos) {
                return snapshot;
            }

            double failureRate = delegate.getFailureRate();
            long totalExecutions = delegate.getTotalExecutions();
            long failureCount = delegate.getFailureCount();
            CachedSnapshot newSnapshot = new CachedSnapshot(failureRate, totalExecutions, failureCount);

            cached.set(newSnapshot);
            cacheTimeNanos.set(System.nanoTime());
            return newSnapshot;
        }
    }
    
    /**
     * Cached snapshot of metrics.
     */
    private record CachedSnapshot(double failureRate, long totalExecutions, long failureCount) {}
}

