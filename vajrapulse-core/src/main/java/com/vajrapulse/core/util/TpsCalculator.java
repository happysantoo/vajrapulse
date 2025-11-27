package com.vajrapulse.core.util;

/**
 * Utility class for calculating transactions per second (TPS) metrics.
 * 
 * <p>This class provides static methods for common TPS calculations used throughout
 * the VajraPulse framework. All calculations use consistent formulas to ensure
 * accuracy and avoid code duplication.
 * 
 * <p><strong>Thread Safety:</strong> All methods are thread-safe and stateless.
 * 
 * @since 0.9.5
 */
public final class TpsCalculator {
    
    /**
     * Milliseconds per second constant for TPS calculations.
     */
    private static final double MILLISECONDS_PER_SECOND = 1000.0;
    
    // Private constructor to prevent instantiation
    private TpsCalculator() {
        throw new AssertionError("TpsCalculator should not be instantiated");
    }
    
    /**
     * Calculates actual TPS from execution count and elapsed time.
     * 
     * <p>Formula: {@code TPS = (executionCount * 1000) / elapsedMillis}
     * 
     * <p>Edge cases:
     * <ul>
     *   <li>If {@code elapsedMillis <= 0}, returns 0.0</li>
     *   <li>If {@code executionCount < 0}, result may be negative (caller should validate)</li>
     * </ul>
     * 
     * @param executionCount the number of executions completed
     * @param elapsedMillis the elapsed time in milliseconds
     * @return transactions per second (TPS), or 0.0 if elapsed time is invalid
     */
    public static double calculateActualTps(long executionCount, long elapsedMillis) {
        if (elapsedMillis <= 0) {
            return 0.0;
        }
        return (executionCount * MILLISECONDS_PER_SECOND) / elapsedMillis;
    }
    
    /**
     * Calculates TPS error (target - actual).
     * 
     * <p>This is useful for rate control accuracy metrics. A positive error indicates
     * the system is running slower than target, while a negative error indicates
     * it's running faster than target.
     * 
     * @param targetTps the target transactions per second
     * @param executionCount the number of executions completed
     * @param elapsedMillis the elapsed time in milliseconds
     * @return TPS error (target - actual), or targetTps if elapsed time is invalid
     */
    public static double calculateTpsError(double targetTps, long executionCount, long elapsedMillis) {
        double actualTps = calculateActualTps(executionCount, elapsedMillis);
        return targetTps - actualTps;
    }
    
    /**
     * Calculates expected execution count based on target TPS and elapsed time.
     * 
     * <p>Formula: {@code expectedCount = (targetTps * elapsedMillis) / 1000}
     * 
     * <p>This is useful for rate control to determine if the system is ahead or
     * behind schedule.
     * 
     * @param targetTps the target transactions per second
     * @param elapsedMillis the elapsed time in milliseconds
     * @return expected execution count, or 0 if targetTps &lt;= 0 or elapsedMillis &lt;= 0
     */
    public static long calculateExpectedCount(double targetTps, long elapsedMillis) {
        if (targetTps <= 0 || elapsedMillis <= 0) {
            return 0;
        }
        return (long) ((targetTps * elapsedMillis) / MILLISECONDS_PER_SECOND);
    }
}

