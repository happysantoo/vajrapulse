package com.vajrapulse.core.metrics;

import com.vajrapulse.api.backpressure.BackpressureProvider;

import java.util.List;

/**
 * Combines multiple backpressure providers (takes maximum).
 * 
 * <p>This provider reports the maximum backpressure level from all
 * constituent providers. This is useful when you want to detect
 * backpressure from multiple sources (e.g., connection pool AND queue).
 * 
 * <p>Example usage:
 * <pre>{@code
 * CompositeBackpressureProvider provider = new CompositeBackpressureProvider(
 *     new QueueBackpressureProvider(() -> queue.size(), 1000),
 *     new LatencyBackpressureProvider(metricsProvider, Duration.ofMillis(100), 0.95)
 * );
 * }</pre>
 * 
 * @since 0.9.6
 */
public final class CompositeBackpressureProvider implements BackpressureProvider {
    private final List<BackpressureProvider> providers;
    
    /**
     * Creates a composite backpressure provider.
     * 
     * @param providers backpressure providers to combine
     * @throws IllegalArgumentException if providers is null, empty, or contains null
     */
    public CompositeBackpressureProvider(BackpressureProvider... providers) {
        if (providers == null || providers.length == 0) {
            throw new IllegalArgumentException("Providers must not be null or empty");
        }
        for (BackpressureProvider provider : providers) {
            if (provider == null) {
                throw new IllegalArgumentException("Provider must not be null");
            }
        }
        this.providers = List.of(providers);
    }
    
    @Override
    public double getBackpressureLevel() {
        return providers.stream()
            .mapToDouble(BackpressureProvider::getBackpressureLevel)
            .max()
            .orElse(0.0);
    }
    
    @Override
    public String getBackpressureDescription() {
        return providers.stream()
            .map(BackpressureProvider::getBackpressureDescription)
            .filter(desc -> desc != null && !desc.isEmpty())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Composite backpressure: " + (getBackpressureLevel() * 100.0) + "%");
    }
}

