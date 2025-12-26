package com.vajrapulse.api.exception;

/**
 * Exception thrown when validation of input parameters or configuration fails.
 * 
 * <p>This exception is used for:
 * <ul>
 *   <li>Invalid load pattern parameters (negative TPS, invalid durations)</li>
 *   <li>Invalid task configuration</li>
 *   <li>Missing required parameters</li>
 *   <li>Parameter value out of acceptable range</li>
 * </ul>
 * 
 * <p>Validation exceptions typically indicate user error and should be caught
 * early in the setup phase, before test execution begins.
 * 
 * @since 0.9.10
 */
public class ValidationException extends VajraPulseException {
    
    /**
     * Creates a new validation exception with the specified message.
     * 
     * @param message the error message describing the validation failure
     */
    public ValidationException(String message) {
        super(message);
    }
    
    /**
     * Creates a new validation exception with the specified message and cause.
     * 
     * @param message the error message describing the validation failure
     * @param cause the cause of this exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
