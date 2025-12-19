package com.vajrapulse.core.engine;

import com.vajrapulse.core.metrics.MetricsCollector;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Callable for executing a task and recording metrics.
 * 
 * <p>This class wraps task execution with metrics collection, avoiding lambda
 * allocation in the hot path. It handles queue wait time tracking and metrics
 * recording based on whether the current phase should record metrics.
 * 
 * @since 0.9.9
 */
final class ExecutionCallable implements Callable<Void> {
    private final TaskExecutor taskExecutor;
    private final MetricsCollector metricsCollector;
    private final long iteration;
    private final long queueStartNanos;
    private final AtomicLong pendingExecutions;
    private final boolean shouldRecordMetrics;
    
    /**
     * Creates a new execution callable.
     * 
     * @param taskExecutor the task executor
     * @param metricsCollector the metrics collector
     * @param iteration the current iteration number
     * @param queueStartNanos the nano time when task was queued
     * @param pendingExecutions atomic counter for pending executions
     * @param shouldRecordMetrics whether metrics should be recorded for this execution
     */
    ExecutionCallable(
            TaskExecutor taskExecutor,
            MetricsCollector metricsCollector,
            long iteration,
            long queueStartNanos,
            AtomicLong pendingExecutions,
            boolean shouldRecordMetrics) {
        this.taskExecutor = taskExecutor;
        this.metricsCollector = metricsCollector;
        this.iteration = iteration;
        this.queueStartNanos = queueStartNanos;
        this.pendingExecutions = pendingExecutions;
        this.shouldRecordMetrics = shouldRecordMetrics;
    }
    
    @Override
    public Void call() {
        // Record queue wait time (time from submission to actual execution start)
        // Only record during steady-state phase
        if (shouldRecordMetrics) {
            long queueWaitNanos = System.nanoTime() - queueStartNanos;
            metricsCollector.recordQueueWait(queueWaitNanos);
        }
        
        // Decrement pending count when execution starts (before actual execution)
        // This ensures queue size metric reflects only tasks waiting in queue,
        // not tasks that have started executing
        pendingExecutions.decrementAndGet();
        metricsCollector.updateQueueSize(pendingExecutions.get());
        
        try {
            ExecutionMetrics metrics = taskExecutor.executeWithMetrics(iteration);
            // Only record metrics during steady-state phase (warm-up/cool-down excluded)
            if (shouldRecordMetrics) {
                metricsCollector.record(metrics);
            }
        } finally {
            // No cleanup needed - queue size already updated above
        }
        return null;
    }
}
