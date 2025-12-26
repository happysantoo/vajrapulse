package com.vajrapulse.api.exception;

/**
 * Exception thrown when errors occur during test execution.
 * 
 * <p>This exception is used for:
 * <ul>
 *   <li>Task execution failures that cannot be handled gracefully</li>
 *   <li>Engine initialization failures</li>
 *   <li>Resource allocation failures</li>
 *   <li>Unexpected errors during test execution</li>
 * </ul>
 * 
 * <p>Execution exceptions typically indicate system errors or unexpected conditions
 * that prevent the test from continuing. These should be logged and may require
 * investigation.
 * 
 * @since 0.9.10
 */
public class ExecutionException extends VajraPulseException {
    
    /**
     * Creates a new execution exception with the specified message.
     * 
     * @param message the error message describing the execution failure
     */
    public ExecutionException(String message) {
        super(message);
    }
    
    /**
     * Creates a new execution exception with the specified message and cause.
     * 
     * @param message the error message describing the execution failure
     * @param cause the cause of this exception
     */
    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
