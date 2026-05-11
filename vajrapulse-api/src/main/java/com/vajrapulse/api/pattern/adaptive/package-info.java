/**
 * Adaptive load pattern and related components.
 *
 * <p>This package contains the adaptive load pattern implementation that
 * automatically finds the maximum sustainable TPS by dynamically adjusting
 * based on error rates, backpressure, and system conditions.
 *
 * <p>Key components:
 * <ul>
 *   <li>{@link com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern} - Main adaptive pattern</li>
 *   <li>{@link com.vajrapulse.api.pattern.adaptive.AdaptiveState} - Unified state record</li>
 *   <li>{@link com.vajrapulse.api.pattern.adaptive.AdaptiveConfig} - Configuration record</li>
 *   <li>{@link com.vajrapulse.api.pattern.adaptive.AdaptivePatternListener} - Event notifications</li>
 *   <li>{@link com.vajrapulse.api.pattern.adaptive.RampDecisionPolicy} - Decision logic interface</li>
 *   <li>{@link com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy} - Default implementation</li>
 *   <li>{@link com.vajrapulse.api.pattern.adaptive.MetricsSnapshot} - Metrics snapshot for decisions</li>
 * </ul>
 *
 * <p><strong>This module has ZERO external dependencies</strong> to ensure
 * minimal overhead for user applications.
 */
@com.vajrapulse.api.annotation.Experimental(
    expectedStable = "1.1.0",
    reason = "The adaptive capacity discovery pipeline has known bugs (unit mismatch in failure rate, memory leaks, race conditions, reflection-based config extraction). Functional but incorrect — do not rely on capacity numbers in production. Full fix planned for 1.1.0."
)
package com.vajrapulse.api.pattern.adaptive;
