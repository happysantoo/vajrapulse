package com.vajrapulse.api.pattern;

import java.time.Duration;

/**
 * Utility class for validating load pattern parameters.
 * 
 * <p>Provides consistent validation logic and error messages
 * across all load pattern implementations.
 * 
 * <p><strong>Thread Safety:</strong> All methods are thread-safe and stateless.
 * 
 * @since 0.9.9
 */
public final class LoadPatternValidator {
    
    // Private constructor to prevent instantiation
    private LoadPatternValidator() {
        throw new AssertionError("LoadPatternValidator should not be instantiated");
    }
    
    /**
     * Validates that TPS is positive.
     * 
     * @param name parameter name (e.g., "TPS", "Max TPS")
     * @param tps the TPS value to validate
     * @throws IllegalArgumentException if TPS is not positive
     */
    public static void validateTps(String name, double tps) {
        if (tps <= 0) {
            throw new IllegalArgumentException(
                String.format("%s must be positive, got: %s", name, tps)
            );
        }
    }
    
    /**
     * Validates that TPS is non-negative (allows 0.0).
     * 
     * @param name parameter name
     * @param tps the TPS value to validate
     * @throws IllegalArgumentException if TPS is negative
     */
    public static void validateTpsNonNegative(String name, double tps) {
        if (tps < 0) {
            throw new IllegalArgumentException(
                String.format("%s must be non-negative, got: %s", name, tps)
            );
        }
    }
    
    /**
     * Validates that duration is positive.
     * 
     * @param name parameter name (e.g., "Duration", "Ramp duration")
     * @param duration the duration to validate
     * @throws IllegalArgumentException if duration is null, negative, or zero
     */
    public static void validateDuration(String name, Duration duration) {
        if (duration == null) {
            throw new IllegalArgumentException(
                String.format("%s must not be null", name)
            );
        }
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException(
                String.format("%s must be positive, got: %s", name, duration)
            );
        }
    }
    
    /**
     * Validates that duration is non-negative (allows zero).
     * 
     * @param name parameter name
     * @param duration the duration to validate
     * @throws IllegalArgumentException if duration is null or negative
     */
    public static void validateDurationNonNegative(String name, Duration duration) {
        if (duration == null) {
            throw new IllegalArgumentException(
                String.format("%s must not be null", name)
            );
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException(
                String.format("%s must be non-negative, got: %s", name, duration)
            );
        }
    }
}
