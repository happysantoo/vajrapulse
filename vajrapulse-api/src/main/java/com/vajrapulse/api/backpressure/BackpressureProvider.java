package com.vajrapulse.api.backpressure;

/**
 * Provides backpressure signals for adaptive load patterns.
 * 
 * <p>Implementations can integrate with existing metrics infrastructure
 * (connection pools, queues, custom metrics) to report backpressure.
 * 
 * <p>Backpressure is reported as a value between 0.0 (no backpressure)
 * and 1.0 (maximum backpressure). The adaptive pattern uses this signal
 * along with error rate to determine when to ramp down.
 * 
 * <p>Example implementations:
 * <ul>
 *   <li>Connection pool utilization (HikariCP, Apache HttpClient)</li>
 *   <li>Queue depth thresholds</li>
 *   <li>Latency-based backpressure</li>
 *   <li>Custom business logic</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> Implementations must be thread-safe
 * as this interface may be called from multiple threads concurrently.
 * 
 * @since 0.9.6
 */
public interface BackpressureProvider {
    /**
     * Returns the current backpressure level.
     * 
     * <p>Value interpretation:
     * <ul>
     *   <li>0.0 - 0.3: Low backpressure (system can handle more load)</li>
     *   <li>0.3 - 0.7: Moderate backpressure (system is stressed)</li>
     *   <li>0.7 - 1.0: High backpressure (system is overloaded)</li>
     * </ul>
     * 
     * <p>This method may be called frequently, so implementations should
     * cache values or use efficient lookups.
     * 
     * @return backpressure level between 0.0 (none) and 1.0 (maximum)
     */
    double getBackpressureLevel();
    
    /**
     * Optional: Returns a human-readable description of current backpressure.
     * 
     * <p>Used for logging and debugging. Returns null if not available.
     * 
     * @return description of backpressure state, or null
     */
    default String getBackpressureDescription() {
        return null;
    }
}
