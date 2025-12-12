package com.vajrapulse.api.pattern.adaptive;

/**
 * Recovery tracking state for an adaptive load pattern.
 * 
 * <p>This record tracks recovery state, including:
 * <ul>
 *   <li>Last known good TPS (highest TPS before recovery)</li>
 *   <li>Recovery start time</li>
 * </ul>
 * 
 * @param lastKnownGoodTps the highest TPS achieved before recovery
 * @param recoveryStartTime when recovery started (-1 if not in recovery)
 * 
 * @see AdaptiveLoadPattern
 * @since 0.9.9
 */
public record AdaptiveRecoveryTracking(
    double lastKnownGoodTps,
    long recoveryStartTime
) {
    /**
     * Creates a recovery tracking record with validation.
     */
    public AdaptiveRecoveryTracking {
        if (lastKnownGoodTps < 0) {
            throw new IllegalArgumentException("Last known good TPS must be non-negative");
        }
    }
    
    /**
     * Checks if currently in recovery mode.
     * 
     * @return true if in recovery
     */
    public boolean isInRecovery() {
        return recoveryStartTime >= 0;
    }
    
    /**
     * Creates an empty recovery tracking record.
     * 
     * @return empty recovery tracking
     */
    public static AdaptiveRecoveryTracking empty() {
        return new AdaptiveRecoveryTracking(0, -1);
    }
}

