package com.vajrapulse.api.task;

/**
 * Represents a failed task execution.
 * 
 * <p>This is one of the two permitted implementations of {@link TaskResult}.
 * 
 * @param error the error that caused the failure
 * @see TaskResult
 * @since 0.9.9
 */
public record TaskResultFailure(Throwable error) implements TaskResult {
}

