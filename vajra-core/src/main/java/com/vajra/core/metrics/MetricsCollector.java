package com.vajra.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.vajra.core.engine.ExecutionMetrics;

import java.util.concurrent.TimeUnit;

/**
 * Collects and aggregates execution metrics using Micrometer.
 * 
 * <p>This class records:
 * <ul>
 *   <li>Success/failure counts</li>
 *   <li>Latency distribution with percentiles</li>
 *   <li>Total execution count</li>
 * </ul>
 * 
 * <p>Thread-safe for concurrent metric recording.
 */
public final class MetricsCollector {
    private final MeterRegistry registry;
    private final Timer successTimer;
    private final Timer failureTimer;
    private final Counter totalCounter;
    
    /**
     * Creates a collector with default SimpleMeterRegistry.
     */
    public MetricsCollector() {
        this(new SimpleMeterRegistry());
    }
    
    /**
     * Creates a collector with the specified registry.
     * 
     * @param registry the meter registry to use
     */
    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;
        
        this.successTimer = Timer.builder("vajra.execution.duration")
            .tag("status", "success")
            .description("Successful task execution duration")
            .publishPercentileHistogram()
            .register(registry);
        
        this.failureTimer = Timer.builder("vajra.execution.duration")
            .tag("status", "failure")
            .description("Failed task execution duration")
            .publishPercentileHistogram()
            .register(registry);
        
        this.totalCounter = Counter.builder("vajra.execution.total")
            .description("Total task executions")
            .register(registry);
    }
    
    /**
     * Records metrics from a task execution.
     * 
     * @param metrics the execution metrics to record
     */
    public void record(ExecutionMetrics metrics) {
        totalCounter.increment();
        
        long durationNanos = metrics.durationNanos();
        if (metrics.isSuccess()) {
            successTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        } else {
            failureTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }
    
    /**
     * Creates a snapshot of current metrics.
     * 
     * @return aggregated metrics snapshot
     */
    public AggregatedMetrics snapshot() {
        long successCount = successTimer.count();
        long failureCount = failureTimer.count();
        long totalCount = (long) totalCounter.count();
        
        double successP50 = successTimer.percentile(0.50, TimeUnit.NANOSECONDS);
        double successP95 = successTimer.percentile(0.95, TimeUnit.NANOSECONDS);
        double successP99 = successTimer.percentile(0.99, TimeUnit.NANOSECONDS);
        
        double failureP50 = failureTimer.percentile(0.50, TimeUnit.NANOSECONDS);
        double failureP95 = failureTimer.percentile(0.95, TimeUnit.NANOSECONDS);
        double failureP99 = failureTimer.percentile(0.99, TimeUnit.NANOSECONDS);
        
        return new AggregatedMetrics(
            totalCount,
            successCount,
            failureCount,
            successP50,
            successP95,
            successP99,
            failureP50,
            failureP95,
            failureP99
        );
    }
    
    /**
     * Returns the underlying meter registry.
     * 
     * @return the meter registry
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}
