package com.vajrapulse.api.exception;

/**
 * Base exception for all VajraPulse framework errors.
 * 
 * <p>This exception serves as the root of the VajraPulse exception hierarchy,
 * allowing clients to catch all framework-related exceptions with a single catch block.
 * 
 * <p>Subclasses provide more specific error types:
 * <ul>
 *   <li>{@link ValidationException} - Invalid input parameters or configuration</li>
 *   <li>{@link ExecutionException} - Errors during test execution</li>
 * </ul>
 * 
 * @since 0.9.10
 */
public class VajraPulseException extends RuntimeException {
    
    /**
     * Creates a new VajraPulse exception with the specified message.
     * 
     * @param message the error message
     */
    public VajraPulseException(String message) {
        super(message);
    }
    
    /**
     * Creates a new VajraPulse exception with the specified message and cause.
     * 
     * @param message the error message
     * @param cause the cause of this exception
     */
    public VajraPulseException(String message, Throwable cause) {
        super(message, cause);
    }
}
