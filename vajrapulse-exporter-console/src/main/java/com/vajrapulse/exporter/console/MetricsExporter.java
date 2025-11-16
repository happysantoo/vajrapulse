package com.vajrapulse.exporter.console;

import com.vajrapulse.core.metrics.AggregatedMetrics;

/**
 * Simple exporter interface for emitting aggregated metrics.
 */
public interface MetricsExporter {
    void export(String title, AggregatedMetrics metrics);
}
