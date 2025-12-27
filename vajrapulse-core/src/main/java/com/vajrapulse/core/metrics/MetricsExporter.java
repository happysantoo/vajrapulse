package com.vajrapulse.core.metrics;

import com.vajrapulse.api.metrics.RunContext;

/**
 * Exporter interface for emitting aggregated metrics snapshots.
 * 
 * <p>Implementations should be lightweight and avoid heavy allocations in the hot
 * path. Formatting concerns (pretty tables, JSON, etc.) belong in exporter modules.
 * 
 * <p>Example usage:
 * <pre>{@code
 * MetricsExporter exporter = new HtmlReportExporter(Path.of("report.html"));
 * 
 * // Simple export (backward compatible)
 * exporter.export("Test Results", metrics);
 * 
 * // Export with run context (recommended)
 * RunContext context = RunContext.of(runId, startTime, taskClass, patternType, config);
 * exporter.export("Test Results", metrics, context);
 * }</pre>
 * 
 * @since 0.9.0
 */
public interface MetricsExporter {
    
    /**
     * Exports the given metrics snapshot.
     * 
     * <p>This method provides backward compatibility for exporters that don't
     * need run context. Implementations should delegate to 
     * {@link #export(String, AggregatedMetrics, RunContext)} with an empty context.
     * 
     * @param title human-readable title describing the snapshot context
     * @param metrics aggregated metrics data
     */
    void export(String title, AggregatedMetrics metrics);
    
    /**
     * Exports the given metrics snapshot with run context.
     * 
     * <p>This method provides additional context about the test run, including
     * the run ID, task class, load pattern type, and configuration. This
     * information can be included in reports for correlation and reproducibility.
     * 
     * <p>The default implementation delegates to {@link #export(String, AggregatedMetrics)}
     * for backward compatibility with existing exporters.
     * 
 * @param title human-readable title describing the snapshot context
 * @param metrics aggregated metrics data
 * @param context run context with metadata (runId, task class, pattern, etc.)
 * @since 0.9.11
 */
    default void export(String title, AggregatedMetrics metrics, RunContext context) {
        // Default implementation for backward compatibility
        export(title, metrics);
    }
}
