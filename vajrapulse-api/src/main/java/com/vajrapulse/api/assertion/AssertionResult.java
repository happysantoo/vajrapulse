package com.vajrapulse.api.assertion;

/**
 * Result of an assertion evaluation.
 * 
 * <p>Assertions return either a success or failure result with an optional message.
 * This allows for clear reporting of test validation outcomes.
 * 
 * @param success true if assertion passed, false if it failed
 * @param message optional message describing the result (null for success)
 * @since 0.9.7
 */
public record AssertionResult(boolean success, String message) {
    
    /**
     * Creates a successful assertion result.
     * 
     * @return successful assertion result
     */
    public static AssertionResult pass() {
        return new AssertionResult(true, null);
    }
    
    /**
     * Creates a successful assertion result with a message.
     * 
     * @param message success message
     * @return successful assertion result
     */
    public static AssertionResult pass(String message) {
        return new AssertionResult(true, message);
    }
    
    /**
     * Creates a failed assertion result with a message.
     * 
     * @param message failure message describing why the assertion failed
     * @return failed assertion result
     * @throws IllegalArgumentException if message is null or blank
     */
    public static AssertionResult failure(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Failure message must not be null or blank");
        }
        return new AssertionResult(false, message);
    }
    
    /**
     * Creates a failed assertion result with a formatted message.
     * 
     * @param format message format string (see {@link String#format(String, Object...)})
     * @param args format arguments
     * @return failed assertion result
     */
    public static AssertionResult failure(String format, Object... args) {
        return failure(String.format(format, args));
    }
    
    /**
     * Checks if the assertion passed.
     * 
     * @return true if assertion passed
     */
    public boolean passed() {
        return success();
    }
    
    /**
     * Checks if the assertion failed.
     * 
     * @return true if assertion failed
     */
    public boolean failed() {
        return !success();
    }
}

