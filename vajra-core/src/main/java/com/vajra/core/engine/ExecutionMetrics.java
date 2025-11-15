package com.vajra.core.engine;

import com.vajra.api.TaskResult;

/**
 * Captures metrics for a single task execution.
 * 
 * <p>This record holds timing information and the result of one task iteration.
 * The framework automatically creates these during execution and passes them
 * to the metrics collector.
 * 
 * @param startNanos start time in nanoseconds (from System.nanoTime())
 * @param endNanos end time in nanoseconds (from System.nanoTime())
 * @param result the task execution result (Success or Failure)
 * @param iteration the iteration number (0-based)
 */
public record ExecutionMetrics(
    long startNanos,
    long endNanos,
    TaskResult result,
    long iteration
) {
    /**
     * Calculates the execution duration in nanoseconds.
     * 
     * @return duration in nanoseconds
     */
    public long durationNanos() {
        return endNanos - startNanos;
    }
    
    /**
     * Checks if this execution was successful.
     * 
     * @return true if result is Success, false otherwise
     */
    public boolean isSuccess() {
        return result instanceof TaskResult.Success;
    }
    
    /**
     * Checks if this execution failed.
     * 
     * @return true if result is Failure, false otherwise
     */
    public boolean isFailure() {
        return result instanceof TaskResult.Failure;
    }
}
