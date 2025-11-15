package com.vajra.api;

/**
 * Represents the result of a task execution.
 * 
 * <p>This is a sealed interface with two permitted implementations:
 * {@link Success} for successful executions and {@link Failure} for failures.
 * 
 * <p>Use pattern matching to handle results:
 * <pre>{@code
 * switch (result) {
 *     case Success(var data) -> processSuccess(data);
 *     case Failure(var error) -> handleError(error);
 * }
 * }</pre>
 */
public sealed interface TaskResult permits TaskResult.Success, TaskResult.Failure {
    
    /**
     * Represents a successful task execution.
     * 
     * @param data optional result data from the execution
     */
    record Success(Object data) implements TaskResult {
        /**
         * Creates a success result with no data.
         */
        public Success() {
            this(null);
        }
    }
    
    /**
     * Represents a failed task execution.
     * 
     * @param error the error that caused the failure
     */
    record Failure(Throwable error) implements TaskResult {
    }
    
    /**
     * Creates a success result with the given data.
     * 
     * @param data the result data
     * @return a success result
     */
    static Success success(Object data) {
        return new Success(data);
    }
    
    /**
     * Creates a success result with no data.
     * 
     * @return a success result
     */
    static Success success() {
        return new Success();
    }
    
    /**
     * Creates a failure result with the given error.
     * 
     * @param error the error that caused the failure
     * @return a failure result
     */
    static Failure failure(Throwable error) {
        return new Failure(error);
    }
}
