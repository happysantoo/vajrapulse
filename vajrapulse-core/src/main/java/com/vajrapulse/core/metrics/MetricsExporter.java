package com.vajrapulse.core.metrics;

/**
 * Exporter interface for emitting aggregated metrics snapshots.
 * <p>Implementations should be lightweight and avoid heavy allocations in the hot
 * path. Formatting concerns (pretty tables, JSON, etc.) belong in exporter modules.
 */
public interface MetricsExporter {
    /**
     * Exports the given metrics snapshot.
     * @param title human-readable title describing the snapshot context
     * @param metrics aggregated metrics data
     */
    void export(String title, AggregatedMetrics metrics);
}
