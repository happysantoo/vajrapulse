/**
 * Load patterns for controlling test execution rate.
 * 
 * <p>This package contains:
 * <ul>
 *   <li>{@link com.vajrapulse.api.pattern.LoadPattern} - Base interface for load patterns</li>
 *   <li>{@link com.vajrapulse.api.pattern.StaticLoad} - Constant TPS</li>
 *   <li>{@link com.vajrapulse.api.pattern.RampUpLoad} - Linear ramp to max</li>
 *   <li>{@link com.vajrapulse.api.pattern.RampUpToMaxLoad} - Ramp then sustain</li>
 *   <li>{@link com.vajrapulse.api.pattern.StepLoad} - Phased testing</li>
 *   <li>{@link com.vajrapulse.api.pattern.SpikeLoad} - Burst absorption testing</li>
 *   <li>{@link com.vajrapulse.api.pattern.SineWaveLoad} - Smooth oscillation</li>
 *   <li>{@link com.vajrapulse.api.pattern.WarmupCooldownLoadPattern} - Warm-up/cool-down wrapper</li>
 * </ul>
 * 
 * <p>For adaptive load patterns, see {@link com.vajrapulse.api.pattern.adaptive}.
 * 
 * <p><strong>This module has ZERO external dependencies</strong> to ensure
 * minimal overhead for user applications.
 */
package com.vajrapulse.api.pattern;

