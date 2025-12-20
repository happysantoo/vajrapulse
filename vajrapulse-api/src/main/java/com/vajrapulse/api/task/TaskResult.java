package com.vajrapulse.api.task;

/**
 * Represents the result of a task execution.
 * 
 * <p>This is a sealed interface with two permitted implementations:
 * {@link TaskResultSuccess} for successful executions and {@link TaskResultFailure} for failures.
 * 
 * <p>Use pattern matching to handle results:
 * <pre>{@code
 * switch (result) {
 *     case TaskResultSuccess(var data) -> processSuccess(data);
 *     case TaskResultFailure(var error) -> handleError(error);
 * }
 * }</pre>
 * 
 * @see TaskResultSuccess
 * @see TaskResultFailure
 */
public sealed interface TaskResult permits TaskResultSuccess, TaskResultFailure {
    
    /**
     * Creates a success result with the given data.
     * 
     * @param data the result data
     * @return a success result
     */
    static TaskResultSuccess success(Object data) {
        return new TaskResultSuccess(data);
    }
    
    /**
     * Creates a success result with no data.
     * 
     * @return a success result
     */
    static TaskResultSuccess success() {
        return new TaskResultSuccess();
    }
    
    /**
     * Creates a failure result with the given error.
     * 
     * @param error the error that caused the failure
     * @return a failure result
     */
    static TaskResultFailure failure(Throwable error) {
        return new TaskResultFailure(error);
    }
}
