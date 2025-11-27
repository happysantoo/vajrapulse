package com.vajrapulse.core.util;

/**
 * Utility class for adding structured context to exceptions.
 * 
 * <p>This class provides methods to create exceptions with consistent,
 * structured context information that aids in debugging and monitoring.
 * 
 * <p><strong>Context Information:</strong>
 * <ul>
 *   <li>Run ID - Correlates exceptions with specific test runs</li>
 *   <li>Iteration number - Identifies which execution failed</li>
 *   <li>Phase/State - Current execution phase when error occurred</li>
 *   <li>Timing information - When the error occurred</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> All methods are thread-safe and stateless.
 * 
 * @since 0.9.5
 */
public final class ExceptionContext {
    
    // Private constructor to prevent instantiation
    private ExceptionContext() {
        throw new AssertionError("ExceptionContext should not be instantiated");
    }
    
    /**
     * Creates a RuntimeException with structured context.
     * 
     * <p>Format: {@code [runId=<runId>] <message>}
     * 
     * @param runId the run identifier (may be null)
     * @param message the error message
     * @return RuntimeException with context
     */
    public static RuntimeException withContext(String runId, String message) {
        String contextMessage = formatMessage(runId, null, null, null, message);
        return new RuntimeException(contextMessage);
    }
    
    /**
     * Creates a RuntimeException with structured context and cause.
     * 
     * <p>Format: {@code [runId=<runId>] <message>}
     * 
     * @param runId the run identifier (may be null)
     * @param message the error message
     * @param cause the cause exception
     * @return RuntimeException with context
     */
    public static RuntimeException withContext(String runId, String message, Throwable cause) {
        String contextMessage = formatMessage(runId, null, null, null, message);
        return new RuntimeException(contextMessage, cause);
    }
    
    /**
     * Creates a RuntimeException with full structured context.
     * 
     * <p>Format: {@code [runId=<runId>] [iteration=<iteration>] [phase=<phase>] <message>}
     * 
     * @param runId the run identifier (may be null)
     * @param iteration the iteration number (may be null)
     * @param phase the current phase/state (may be null)
     * @param message the error message
     * @return RuntimeException with context
     */
    public static RuntimeException withContext(String runId, Long iteration, String phase, String message) {
        String contextMessage = formatMessage(runId, iteration, phase, null, message);
        return new RuntimeException(contextMessage);
    }
    
    /**
     * Creates a RuntimeException with full structured context and cause.
     * 
     * <p>Format: {@code [runId=<runId>] [iteration=<iteration>] [phase=<phase>] <message>}
     * 
     * @param runId the run identifier (may be null)
     * @param iteration the iteration number (may be null)
     * @param phase the current phase/state (may be null)
     * @param message the error message
     * @param cause the cause exception
     * @return RuntimeException with context
     */
    public static RuntimeException withContext(String runId, Long iteration, String phase, String message, Throwable cause) {
        String contextMessage = formatMessage(runId, iteration, phase, null, message);
        return new RuntimeException(contextMessage, cause);
    }
    
    /**
     * Formats a structured error message with context.
     * 
     * @param runId the run identifier (may be null)
     * @param iteration the iteration number (may be null)
     * @param phase the current phase/state (may be null)
     * @param elapsedMillis the elapsed time in milliseconds (may be null)
     * @param message the error message
     * @return formatted message with context
     */
    private static String formatMessage(String runId, Long iteration, String phase, Long elapsedMillis, String message) {
        StringBuilder sb = new StringBuilder();
        
        // Add runId context
        if (runId != null && !runId.isBlank()) {
            sb.append("[runId=").append(runId).append("] ");
        }
        
        // Add iteration context
        if (iteration != null) {
            sb.append("[iteration=").append(iteration).append("] ");
        }
        
        // Add phase context
        if (phase != null && !phase.isBlank()) {
            sb.append("[phase=").append(phase).append("] ");
        }
        
        // Add timing context
        if (elapsedMillis != null) {
            sb.append("[elapsed=").append(elapsedMillis).append("ms] ");
        }
        
        // Add message
        sb.append(message);
        
        return sb.toString();
    }
    
    /**
     * Wraps an existing exception with context.
     * 
     * @param runId the run identifier (may be null)
     * @param message additional context message
     * @param cause the exception to wrap
     * @return RuntimeException with context
     */
    public static RuntimeException wrapWithContext(String runId, String message, Throwable cause) {
        String contextMessage = formatMessage(runId, null, null, null, message);
        return new RuntimeException(contextMessage, cause);
    }
}

