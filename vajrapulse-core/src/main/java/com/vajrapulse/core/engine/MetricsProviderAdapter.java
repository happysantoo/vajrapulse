package com.vajrapulse.core.engine;

import com.vajrapulse.api.metrics.MetricsProvider;
import com.vajrapulse.core.metrics.CachedMetricsProvider;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.util.TimeConstants;

import java.time.Duration;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Adapter that makes MetricsCollector implement MetricsProvider interface with
 * caching.
 * 
 * <p>
 * This allows AdaptiveLoadPattern to work with MetricsCollector
 * without creating a dependency from vajrapulse-api to vajrapulse-core.
 * 
 * <p>
 * This adapter uses {@link CachedMetricsProvider} internally to avoid expensive
 * snapshot operations on every metrics query. The cache has a default TTL of
 * 100ms,
 * which is optimal for high-frequency access patterns like adaptive load
 * patterns.
 * 
 * <p>
 * <strong>Implementation Note:</strong> This adapter maintains a sliding window
 * of
 * metric snapshots to calculate recent failure rates accurately.
 * 
 * @since 0.9.5
 */
public final class MetricsProviderAdapter implements MetricsProvider {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMillis(100);
    private static final long HISTORY_RETENTION_MS = 60000; // Keep 60 seconds of history

    private final MetricsProvider cachedProvider;
    private final MetricsCollector metricsCollector;

    // History for windowed calculations
    private final Deque<WindowSnapshot> history = new ConcurrentLinkedDeque<>();

    /**
     * Creates an adapter for the given metrics collector with default caching
     * (100ms TTL).
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
     * @param ttl              the time-to-live for cached values
     * @throws IllegalArgumentException if metricsCollector is null or ttl is
     *                                  null/negative
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
            return getFailureRate();
        }

        long currentTime = System.currentTimeMillis();
        var currentSnapshot = metricsCollector.snapshot();
        long currentTotal = currentSnapshot.totalExecutions();
        long currentFailures = currentSnapshot.failureCount();

        // Add current snapshot to history
        history.addLast(new WindowSnapshot(currentTime, currentTotal, currentFailures));

        // Prune old history (older than retention policy)
        long retentionCutoff = currentTime - HISTORY_RETENTION_MS;
        while (!history.isEmpty() && history.peekFirst().timestampMillis() < retentionCutoff) {
            history.removeFirst();
        }

        // Find baseline snapshot: latest snapshot <= windowCutoff
        long windowCutoff = currentTime - (long)(windowSeconds * TimeConstants.MILLISECONDS_PER_SECOND);
        WindowSnapshot baseline = findBaselineSnapshot(windowCutoff);

        // If no baseline found, use oldest available (partial window)
        if (baseline == null) {
            baseline = history.isEmpty() ? null : history.peekFirst();
            if (baseline == null) {
                return currentSnapshot.failureRate();
            }
        }

        // Calculate rate over the window
        long timeDiff = currentTime - baseline.timestampMillis();
        if (timeDiff < 100) {
            // Window too small, use all-time rate
            return currentSnapshot.failureRate();
        }

        long totalDiff = currentTotal - baseline.totalExecutions();
        long failureDiff = currentFailures - baseline.failureCount();

        if (totalDiff == 0) {
            return 0.0;
        }

        return (failureDiff * 100.0) / totalDiff;
    }
    
    /**
     * Finds the latest snapshot with timestamp at or before windowCutoff.
     * 
     * <p>Since snapshots are ordered by time (ascending), we iterate forward
     * and keep the latest snapshot that meets the criteria.
     * 
     * @param windowCutoff cutoff time in milliseconds
     * @return latest snapshot at or before windowCutoff, or null if none found
     */
    private WindowSnapshot findBaselineSnapshot(long windowCutoff) {
        WindowSnapshot baseline = null;
        for (WindowSnapshot snap : history) {
            if (snap.timestampMillis() <= windowCutoff) {
                baseline = snap; // Keep latest snapshot that meets criteria
            } else {
                break; // All subsequent snapshots are newer
            }
        }
        return baseline;
    }

    /**
     * Base provider that adapts MetricsCollector to MetricsProvider without
     * caching.
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
     */
    private record WindowSnapshot(long timestampMillis, long totalExecutions, long failureCount) {
    }
}
