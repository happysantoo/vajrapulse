package com.vajra.core.engine;

import com.vajra.api.Task;
import com.vajra.api.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a task with automatic instrumentation and metrics capture.
 * 
 * <p>This class handles:
 * <ul>
 *   <li>Timing each execution</li>
 *   <li>Catching exceptions and wrapping in TaskResult.Failure</li>
 *   <li>Creating ExecutionMetrics for each iteration</li>
 * </ul>
 * 
 * <p>Tasks focus on business logic; this executor handles the cross-cutting concerns.
 */
public final class TaskExecutor {
    private static final Logger logger = LoggerFactory.getLogger(TaskExecutor.class);
    
    private final Task task;
    
    public TaskExecutor(Task task) {
        this.task = task;
    }
    
    /**
     * Executes the task with automatic instrumentation.
     * 
     * <p>This method:
     * <ol>
     *   <li>Captures start time</li>
     *   <li>Calls task.execute()</li>
     *   <li>Catches any exceptions</li>
     *   <li>Captures end time</li>
     *   <li>Returns ExecutionMetrics</li>
     * </ol>
     * 
     * @param iteration the iteration number (0-based)
     * @return metrics for this execution
     */
    public ExecutionMetrics executeWithMetrics(long iteration) {
        long startNanos = System.nanoTime();
        TaskResult result;
        
        try {
            result = task.execute();
        } catch (Exception e) {
            logger.debug("Task execution failed at iteration {}: {}", 
                iteration, e.getMessage());
            result = TaskResult.failure(e);
        }
        
        long endNanos = System.nanoTime();
        return new ExecutionMetrics(startNanos, endNanos, result, iteration);
    }
    
    /**
     * Returns the underlying task.
     * 
     * @return the task being executed
     */
    public Task getTask() {
        return task;
    }
}
