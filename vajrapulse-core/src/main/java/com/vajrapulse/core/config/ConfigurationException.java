package com.vajrapulse.core.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when configuration loading or validation fails.
 * 
 * <p>Contains structured error messages with field paths for easy debugging.
 * 
 * @since 0.9.0
 */
public class ConfigurationException extends RuntimeException {
    
    private final List<String> errors;
    
    public ConfigurationException(String message) {
        super(message);
        this.errors = Collections.emptyList();
    }
    
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
        this.errors = Collections.emptyList();
    }
    
    public ConfigurationException(String message, List<String> errors) {
        super(formatMessage(message, errors));
        this.errors = new ArrayList<>(errors);
    }
    
    /**
     * Returns list of validation errors.
     * 
     * @return unmodifiable list of error messages
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
    
    private static String formatMessage(String message, List<String> errors) {
        if (errors.isEmpty()) {
            return message;
        }
        
        StringBuilder sb = new StringBuilder(message);
        sb.append("\n  Errors:");
        for (String error : errors) {
            sb.append("\n    - ").append(error);
        }
        return sb.toString();
    }
}
