package com.vajrapulse.core.engine;

import com.vajrapulse.api.metrics.MetricsProvider;
import com.vajrapulse.core.metrics.CachedMetricsProvider;
import com.vajrapulse.core.metrics.MetricsCollector;

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
        WindowSnapshot newSnapshot = new WindowSnapshot(currentTime, currentTotal, currentFailures);
        history.addLast(newSnapshot);

        // Prune old history (older than retention policy)
        long retentionCutoff = currentTime - HISTORY_RETENTION_MS;
        while (!history.isEmpty() && history.peekFirst().timestampMillis() < retentionCutoff) {
            history.removeFirst();
        }

        // Find the snapshot that is at least windowSeconds ago
        long windowCutoff = currentTime - (windowSeconds * 1000L);
        WindowSnapshot baseline = null;

        // Iterate backwards finding the first snapshot <= windowCutoff
        // Actually best to iterate forward or pick the one closest to windowCutoff?
        // We want the snapshot that represents the "start" of the window.
        // So we want a snapshot with timestamp <= windowCutoff.
        // The one closest to windowCutoff from the LEFT (older side) or RIGHT?
        // Ideally we want (Current - Baseline) ~ Window.
        // So Baseline ~ Current - Window.

        Iterator<WindowSnapshot> it = history.iterator();
        while (it.hasNext()) {
            WindowSnapshot snap = it.next();
            if (snap.timestampMillis() <= windowCutoff) {
                baseline = snap;
                // Keep looking? The snapshots are ordered by time (ascending).
                // The first one we find might be VERY old if we have gaps.
                // But we want the one closest to windowCutoff but <= windowCutoff.
                // Since they are ascending, 'snap' is older than next 'snap'.
                // If snap <= windowCutoff, it's a candidate.
                // The next one might also be <= windowCutoff.
                // We want the LATEST snapshot that is <= windowCutoff (closest to the edge).
            } else {
                // snap.timestamp > windowCutoff.
                // Stop, because all subsequent will be > windowCutoff.
                break;
            }
        }

        // If we found no baseline (all snapshots are newer than window),
        // fallback to the oldest available if it's "reasonably" close?
        // Or just fallback to all-time (metrics behavior).
        // If oldest is newer than window, it means we don't have full window history.
        // Default behavior: return accumulated stats since oldest?
        // Or all-time.

        if (baseline == null) {
            // Check oldest
            if (!history.isEmpty()) {
                baseline = history.peekFirst();
                // If even the oldest is too new, we can calculate rate based on what we have
                // (Partial window). This is often better than all-time.
            } else {
                return currentSnapshot.failureRate(); // Should not happen as we just added one
            }
        }

        // Evaluate effective window duration
        long timeDiff = currentTime - baseline.timestampMillis();

        // If the effective window is too small (e.g. < 1s), statistics are noisy.
        // But if that's all we have, we use it.
        // Exception: if just started, timeDiff might be 0.
        if (timeDiff < 100) {
            return currentSnapshot.failureRate();
        }

        // Calculate rate over the window
        long totalDiff = currentTotal - baseline.totalExecutions();
        long failureDiff = currentFailures - baseline.failureCount();

        if (totalDiff == 0) {
            return 0.0;
        }

        return (failureDiff * 100.0) / totalDiff;
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
