package com.vajrapulse.core.engine;

import com.vajrapulse.api.MetricsProvider;
import com.vajrapulse.core.metrics.MetricsCollector;

/**
 * Adapter that makes MetricsCollector implement MetricsProvider interface.
 * 
 * <p>This allows AdaptiveLoadPattern to work with MetricsCollector
 * without creating a dependency from vajrapulse-api to vajrapulse-core.
 * 
 * @since 0.9.5
 */
public final class MetricsProviderAdapter implements MetricsProvider {
    
    private final MetricsCollector metricsCollector;
    
    /**
     * Creates an adapter for the given metrics collector.
     * 
     * @param metricsCollector the metrics collector to adapt
     * @throws IllegalArgumentException if metricsCollector is null
     */
    public MetricsProviderAdapter(MetricsCollector metricsCollector) {
        if (metricsCollector == null) {
            throw new IllegalArgumentException("Metrics collector must not be null");
        }
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
}

