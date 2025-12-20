package com.vajrapulse.api.task;

/**
 * Represents a successful task execution.
 * 
 * <p>This is one of the two permitted implementations of {@link TaskResult}.
 * 
 * @param data optional result data from the execution
 * @see TaskResult
 * @since 0.9.9
 */
public record TaskResultSuccess(Object data) implements TaskResult {
    /**
     * Creates a success result with no data.
     */
    public TaskResultSuccess() {
        this(null);
    }
}

