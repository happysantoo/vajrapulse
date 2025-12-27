package com.vajrapulse.core.metrics;

/**
 * Statistical summary of latency measurements.
 * 
 * <p>This record provides basic statistical measures for latency data,
 * including mean, standard deviation, minimum, and maximum values.
 * All values are in nanoseconds.
 * 
 * <p>Example usage:
 * <pre>{@code
 * LatencyStats stats = new LatencyStats(1_000_000, 100_000, 500_000, 5_000_000, 100);
 * System.out.println("Mean latency: " + stats.meanMillis() + " ms");
 * System.out.println("StdDev: " + stats.stdDevMillis() + " ms");
 * }</pre>
 * 
 * @param mean the mean (average) latency in nanoseconds
 * @param stdDev the standard deviation in nanoseconds
 * @param min the minimum latency in nanoseconds
 * @param max the maximum latency in nanoseconds
 * @param count the number of samples
 * @since 0.9.12
 */
public record LatencyStats(
    double mean,
    double stdDev,
    double min,
    double max,
    long count
) {
    
    /**
     * Nanoseconds per millisecond for conversion.
     */
    private static final double NANOS_PER_MILLI = 1_000_000.0;
    
    /**
     * Creates an empty LatencyStats with zero values.
     * 
     * @return an empty LatencyStats instance
     */
    public static LatencyStats empty() {
        return new LatencyStats(0.0, 0.0, 0.0, 0.0, 0);
    }
    
    /**
     * Returns the mean latency in milliseconds.
     * 
     * @return mean latency in milliseconds
     */
    public double meanMillis() {
        return mean / NANOS_PER_MILLI;
    }
    
    /**
     * Returns the standard deviation in milliseconds.
     * 
     * @return standard deviation in milliseconds
     */
    public double stdDevMillis() {
        return stdDev / NANOS_PER_MILLI;
    }
    
    /**
     * Returns the minimum latency in milliseconds.
     * 
     * @return minimum latency in milliseconds
     */
    public double minMillis() {
        return min / NANOS_PER_MILLI;
    }
    
    /**
     * Returns the maximum latency in milliseconds.
     * 
     * @return maximum latency in milliseconds
     */
    public double maxMillis() {
        return max / NANOS_PER_MILLI;
    }
    
    /**
     * Returns the coefficient of variation (CV) as a percentage.
     * 
     * <p>CV = (stdDev / mean) * 100
     * 
     * @return coefficient of variation as percentage, or 0 if mean is 0
     */
    public double coefficientOfVariation() {
        return mean > 0 ? (stdDev / mean) * 100.0 : 0.0;
    }
    
    /**
     * Checks if this LatencyStats has valid data.
     * 
     * @return true if count > 0, false otherwise
     */
    public boolean hasData() {
        return count > 0;
    }
}
