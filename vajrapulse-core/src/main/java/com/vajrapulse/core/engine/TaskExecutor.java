package com.vajrapulse.core.engine;

import com.vajrapulse.api.TaskLifecycle;
import com.vajrapulse.api.TaskResult;
import com.vajrapulse.core.tracing.Tracing;
import io.opentelemetry.api.trace.Span;
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
 *   <li>Logging detailed execution traces for debugging</li>
 * </ul>
 * 
 * <p>Tasks focus on business logic; this executor handles the cross-cutting concerns.
 * 
 * <p>Enable trace logging to see detailed execution information:
 * <pre>
 * # In src/main/resources/logback.xml, uncomment:
 * &lt;logger name="com.vajrapulse.core.engine.TaskExecutor" level="TRACE"/&gt;
 * </pre>
 */
public final class TaskExecutor {
    private static final Logger logger = LoggerFactory.getLogger(TaskExecutor.class);
    
    private final TaskLifecycle taskLifecycle;
    
    public TaskExecutor(TaskLifecycle taskLifecycle) {
        this.taskLifecycle = taskLifecycle;
    }
    
    /**
     * Executes the task with automatic instrumentation.
     * 
     * <p>This method:
     * <ol>
     *   <li>Captures start time</li>
     *   <li>Calls taskLifecycle.execute(iteration)</li>
     *   <li>Catches any exceptions</li>
     *   <li>Captures end time</li>
     *   <li>Logs execution details at TRACE level</li>
     *   <li>Returns ExecutionMetrics</li>
     * </ol>
     * 
     * @param iteration the iteration number (0-based)
     * @return metrics for this execution
     */
    public ExecutionMetrics executeWithMetrics(long iteration) {
        long startNanos = System.nanoTime();
        TaskResult result;
        Span execSpan = Span.getInvalid();
        // Parent scenario span not tracked here yet; execution spans stand-alone for now.
        if (Tracing.isEnabled()) {
            execSpan = Tracing.startExecutionSpan(null, "unknown", iteration); // runId injected by caller soon
        }
        
        try {
            result = taskLifecycle.execute(iteration);
            long endNanos = System.nanoTime();
            long durationNanos = endNanos - startNanos;
            
            // TRACE logging for manual validation
            if (logger.isTraceEnabled()) {
                double durationMs = durationNanos / 1_000_000.0;
                logger.trace("Iteration={} Status=SUCCESS Duration={}ns ({}ms)", 
                    iteration, 
                    durationNanos,
                    durationMs);
            }
            
            Tracing.markSuccess(execSpan);
            execSpan.end();
            return new ExecutionMetrics(startNanos, endNanos, result, iteration);
            
        } catch (Exception e) {
            long endNanos = System.nanoTime();
            long durationNanos = endNanos - startNanos;
            
            logger.debug("Task execution failed at iteration {}: {}", 
                iteration, e.getMessage());
            
            // TRACE logging for failures
            if (logger.isTraceEnabled()) {
                double durationMs = durationNanos / 1_000_000.0;
                logger.trace("Iteration={} Status=FAILURE Duration={}ns ({}ms) Error={}", 
                    iteration, 
                    durationNanos,
                    durationMs,
                    e.getMessage());
            }
            
            result = TaskResult.failure(e);
            Tracing.markFailure(execSpan, e);
            execSpan.end();
            return new ExecutionMetrics(startNanos, endNanos, result, iteration);
        }
    }
    
    /**
     * Returns the underlying task lifecycle.
     * 
     * @return the task lifecycle being executed
     */
    public TaskLifecycle getTaskLifecycle() {
        return taskLifecycle;
    }
}
