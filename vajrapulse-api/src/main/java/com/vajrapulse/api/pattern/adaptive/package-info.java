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
package com.vajrapulse.api.pattern.adaptive;
