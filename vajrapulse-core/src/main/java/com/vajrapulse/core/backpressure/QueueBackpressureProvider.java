package com.vajrapulse.core.backpressure;

import com.vajrapulse.api.BackpressureProvider;

import java.util.function.Supplier;

/**
 * Backpressure provider based on queue depth.
 * 
 * <p>Reports backpressure based on how full a queue is. When queue depth
 * approaches the maximum, backpressure increases proportionally.
 * 
 * <p>Example usage:
 * <pre>{@code
 * BlockingQueue<Request> queue = new LinkedBlockingQueue<>();
 * QueueBackpressureProvider provider = new QueueBackpressureProvider(
 *     () -> (long) queue.size(),
 *     1000  // Max queue depth
 * );
 * }</pre>
 * 
 * @since 0.9.6
 */
public final class QueueBackpressureProvider implements BackpressureProvider {
    private final Supplier<Long> queueDepthSupplier;
    private final long maxQueueDepth;
    
    /**
     * Creates a queue-based backpressure provider.
     * 
     * @param queueDepthSupplier supplier that returns current queue depth
     * @param maxQueueDepth maximum queue depth (backpressure = 1.0 when reached)
     * @throws IllegalArgumentException if queueDepthSupplier is null or maxQueueDepth &lt;= 0
     */
    public QueueBackpressureProvider(Supplier<Long> queueDepthSupplier, long maxQueueDepth) {
        if (queueDepthSupplier == null) {
            throw new IllegalArgumentException("Queue depth supplier must not be null");
        }
        if (maxQueueDepth <= 0) {
            throw new IllegalArgumentException("Max queue depth must be positive");
        }
        this.queueDepthSupplier = queueDepthSupplier;
        this.maxQueueDepth = maxQueueDepth;
    }
    
    @Override
    public double getBackpressureLevel() {
        long currentDepth = queueDepthSupplier.get();
        if (currentDepth <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) currentDepth / maxQueueDepth);
    }
    
    @Override
    public String getBackpressureDescription() {
        long currentDepth = queueDepthSupplier.get();
        double backpressure = getBackpressureLevel();
        return String.format("Queue depth: %d/%d (%.1f%% backpressure)",
            currentDepth,
            maxQueueDepth,
            backpressure * 100.0);
    }
}

