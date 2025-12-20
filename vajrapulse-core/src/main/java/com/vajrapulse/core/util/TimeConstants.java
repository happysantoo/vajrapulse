package com.vajrapulse.core.util;

/**
 * Shared time-related constants used throughout VajraPulse.
 * 
 * <p>This class provides centralized definitions for time conversion constants
 * to avoid duplication and ensure consistency across the codebase.
 * 
 * <p><strong>Thread Safety:</strong> All constants are thread-safe and stateless.
 * 
 * @since 0.9.9
 */
public final class TimeConstants {
    
    /**
     * Milliseconds per second (1000.0).
     * 
     * <p>Used for TPS calculations and time conversions.
     */
    public static final double MILLISECONDS_PER_SECOND = 1000.0;
    
    /**
     * Nanoseconds per millisecond (1,000,000L).
     * 
     * <p>Used for converting between nanoseconds and milliseconds.
     */
    public static final long NANOS_PER_MILLIS = 1_000_000L;
    
    /**
     * Nanoseconds per second (1,000,000,000L).
     * 
     * <p>Used for converting between nanoseconds and seconds.
     */
    public static final long NANOS_PER_SECOND = 1_000_000_000L;
    
    // Private constructor to prevent instantiation
    private TimeConstants() {
        throw new AssertionError("TimeConstants should not be instantiated");
    }
}
