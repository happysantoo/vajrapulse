package com.vajrapulse.api.pattern.adaptive;

/**
 * Stability tracking state for an adaptive load pattern.
 * 
 * <p>This record tracks stability detection, including:
 * <ul>
 *   <li>Found stable TPS (if any)</li>
 *   <li>Current candidate TPS being evaluated</li>
 *   <li>Stability interval count</li>
 * </ul>
 * 
 * @param stableTps the found stable TPS (-1 if not found)
 * @param candidateTps the current candidate TPS (-1 if not tracking)
 * @param candidateStartTime when candidate tracking started (-1 if not tracking)
 * @param stableIntervalsCount consecutive stable intervals
 * 
 * @see AdaptiveLoadPattern
 * @since 0.9.9
 */
public record AdaptiveStabilityTracking(
    double stableTps,
    double candidateTps,
    long candidateStartTime,
    int stableIntervalsCount
) {
    /**
     * Creates a stability tracking record with validation.
     */
    public AdaptiveStabilityTracking {
        if (stableTps < -1 || candidateTps < -1) {
            throw new IllegalArgumentException("TPS values must be >= -1");
        }
        if (stableIntervalsCount < 0) {
            throw new IllegalArgumentException("Stable intervals count must be non-negative");
        }
    }
    
    /**
     * Checks if currently tracking a candidate TPS.
     * 
     * @return true if tracking a candidate
     */
    public boolean isTracking() {
        return candidateTps >= 0;
    }
    
    /**
     * Checks if a stable TPS has been found.
     * 
     * @return true if stable TPS found
     */
    public boolean hasStableTps() {
        return stableTps >= 0;
    }
    
    /**
     * Creates an empty stability tracking record.
     * 
     * @return empty stability tracking
     */
    public static AdaptiveStabilityTracking empty() {
        return new AdaptiveStabilityTracking(-1, -1, -1, 0);
    }
}

