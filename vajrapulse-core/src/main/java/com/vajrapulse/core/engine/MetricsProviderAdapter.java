package com.vajrapulse.core.engine;

import com.vajrapulse.api.metrics.MetricsProvider;
import com.vajrapulse.core.metrics.CachedMetricsProvider;
import com.vajrapulse.core.metrics.MetricsCollector;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adapter that makes MetricsCollector implement MetricsProvider interface with caching.
 * 
 * <p>This allows AdaptiveLoadPattern to work with MetricsCollector
 * without creating a dependency from vajrapulse-api to vajrapulse-core.
 * 
 * <p>This adapter uses {@link CachedMetricsProvider} internally to avoid expensive
 * snapshot operations on every metrics query. The cache has a default TTL of 100ms,
 * which is optimal for high-frequency access patterns like adaptive load patterns.
 * 
 * <p><strong>Implementation Note:</strong> This adapter wraps a base provider
 * (that adapts MetricsCollector to MetricsProvider) with CachedMetricsProvider.
 * The adapter also provides time-windowed failure rate calculation via
 * {@link #getRecentFailureRate(int)}.
 * 
 * @since 0.9.5
 */
public final class MetricsProviderAdapter implements MetricsProvider {
    
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMillis(100);
    
    private final MetricsProvider cachedProvider;
    private final MetricsCollector metricsCollector;
    
    // Recent window tracking: store previous snapshot for time-windowed calculations
    private final AtomicReference<WindowSnapshot> previousSnapshot = new AtomicReference<>();
    
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
        // Create base provider (adapts MetricsCollector to MetricsProvider)
        MetricsProvider baseProvider = new BaseMetricsProvider(metricsCollector);
        // Wrap with caching
        this.cachedProvider = new CachedMetricsProvider(baseProvider, ttl);
    }
    
    @Override
    public double getFailureRate() {
        return cachedProvider.getFailureRate();
    }
    
    @Override
    public long getTotalExecutions() {
        return cachedProvider.getTotalExecutions();
    }
    
    @Override
    public long getFailureCount() {
        return cachedProvider.getFailureCount();
    }
    
    @Override
    public double getRecentFailureRate(int windowSeconds) {
        if (windowSeconds <= 0) {
            // Invalid window, return all-time rate
            return getFailureRate();
        }
        
        long windowMillis = windowSeconds * 1000L;
        
        // Get current snapshot
        var currentSnapshot = metricsCollector.snapshot();
        long currentTime = System.currentTimeMillis();
        long currentTotal = currentSnapshot.totalExecutions();
        long currentFailures = currentSnapshot.failureCount();
        
        // Get previous snapshot
        WindowSnapshot previous = previousSnapshot.get();
        
        // Update previous snapshot if needed (refresh every second to track recent changes)
        if (previous == null || (currentTime - previous.timestampMillis()) >= 1000) {
            previousSnapshot.set(new WindowSnapshot(currentTime, currentTotal, currentFailures));
            previous = previousSnapshot.get();
        }
        
        // Calculate time difference
        long timeDiff = currentTime - previous.timestampMillis();
        
        // If window is larger than available history, fall back to all-time rate
        if (timeDiff < windowMillis) {
            // Not enough history, use all-time rate
            return currentSnapshot.failureRate();
        }
        
        // Calculate failure rate from the difference
        long totalDiff = currentTotal - previous.totalExecutions();
        long failureDiff = currentFailures - previous.failureCount();
        
        if (totalDiff == 0) {
            return 0.0;  // No executions in window
        }
        
        // Calculate failure rate for the window
        return (failureDiff * 100.0) / totalDiff;
    }
    
    /**
     * Base provider that adapts MetricsCollector to MetricsProvider without caching.
     * 
     * <p>This inner class provides the adaptation layer, which is then wrapped
     * with CachedMetricsProvider for performance.
     */
    private static final class BaseMetricsProvider implements MetricsProvider {
        private final MetricsCollector metricsCollector;
        
        BaseMetricsProvider(MetricsCollector metricsCollector) {
            this.metricsCollector = metricsCollector;
        }
        
        @Override
        public double getFailureRate() {
            return metricsCollector.snapshot().failureRate();
        }
        
        @Override
        public long getTotalExecutions() {
            return metricsCollector.snapshot().totalExecutions();
        }
        
        @Override
        public long getFailureCount() {
            return metricsCollector.snapshot().failureCount();
        }
    }
    
    /**
     * Snapshot for time-windowed calculations.
     * 
     * @param timestampMillis timestamp when snapshot was taken
     * @param totalExecutions total executions at this time
     * @param failureCount failure count at this time
     */
    private record WindowSnapshot(long timestampMillis, long totalExecutions, long failureCount) {}
}

