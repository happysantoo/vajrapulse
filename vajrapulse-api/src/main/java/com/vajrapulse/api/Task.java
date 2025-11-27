package com.vajrapulse.api;

/**
 * Legacy task interface for backward compatibility.
 * 
 * <p><strong>Deprecated:</strong> This interface is deprecated. Use {@link TaskLifecycle} instead.
 * This interface extends {@link TaskLifecycle} with default implementations that map
 * the legacy methods to the new lifecycle methods:
 * <ul>
 *   <li>{@code setup()} → {@code init()}</li>
 *   <li>{@code execute()} → {@code execute(iteration)} (iteration ignored)</li>
 *   <li>{@code cleanup()} → {@code teardown()}</li>
 * </ul>
 * 
 * <p>Existing implementations continue to work, but new code should implement
 * {@link TaskLifecycle} directly.
 * 
 * @see TaskLifecycle
 * @see TaskResult
 * @see VirtualThreads
 * @see PlatformThreads
 * @deprecated Use {@link TaskLifecycle} instead. This interface will be removed in 0.9.6.
 */
@Deprecated(since = "0.9.5", forRemoval = true)
public interface Task extends TaskLifecycle {
    
    /**
     * Called once before any executions begin.
     * Use this to initialize resources like HTTP clients, database connections, etc.
     * 
     * <p>This method maps to {@link TaskLifecycle#init()}.
     * 
     * @throws Exception if setup fails
     * @deprecated Use {@link TaskLifecycle#init()} instead
     */
    @Deprecated(since = "0.9.5", forRemoval = true)
    default void setup() throws Exception {
        // Optional setup - default implementation for init()
    }
    
    /**
     * Executes one iteration of the load test.
     * This method will be called repeatedly according to the configured load pattern.
     * 
     * <p>The framework automatically captures timing and metrics.
     * Focus on implementing your test logic only.
     * 
     * <p>This method maps to {@link TaskLifecycle#execute(long)} (iteration parameter ignored).
     * 
     * @return the result of the execution
     * @throws Exception if execution fails
     * @deprecated Use {@link TaskLifecycle#execute(long)} instead
     */
    @Deprecated(since = "0.9.5", forRemoval = true)
    TaskResult execute() throws Exception;
    
    /**
     * Called once after all executions complete.
     * Use this to clean up resources.
     * 
     * <p>This method maps to {@link TaskLifecycle#teardown()}.
     * 
     * @throws Exception if cleanup fails
     * @deprecated Use {@link TaskLifecycle#teardown()} instead
     */
    @Deprecated(since = "0.9.5", forRemoval = true)
    default void cleanup() throws Exception {
        // Optional cleanup - default implementation for teardown()
    }
    
    // Default implementations mapping to TaskLifecycle
    
    /**
     * Maps setup() to init().
     */
    @Override
    default void init() throws Exception {
        setup();
    }
    
    /**
     * Maps execute() to execute(iteration) - iteration parameter ignored for legacy compatibility.
     */
    @Override
    default TaskResult execute(long iteration) throws Exception {
        return execute();
    }
    
    /**
     * Maps cleanup() to teardown().
     */
    @Override
    default void teardown() throws Exception {
        cleanup();
    }
}
