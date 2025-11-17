/**
 * Core API for Vajra load testing framework.
 * 
 * <p>This package contains the public API that users interact with:
 * <ul>
 *   <li>{@link com.vajrapulse.api.Task} - Main interface for load test tasks</li>
 *   <li>{@link com.vajrapulse.api.TaskResult} - Sealed result type (Success/Failure)</li>
 *   <li>{@link com.vajrapulse.api.LoadPattern} - Interface for load patterns</li>
 *   <li>{@link com.vajrapulse.api.StaticLoad} - Constant TPS</li>
 *   <li>{@link com.vajrapulse.api.RampUpLoad} - Linear ramp to max</li>
 *   <li>{@link com.vajrapulse.api.RampUpToMaxLoad} - Ramp then sustain</li>
 *   <li>{@link com.vajrapulse.api.VirtualThreads} - Annotation for I/O-bound tasks</li>
 *   <li>{@link com.vajrapulse.api.PlatformThreads} - Annotation for CPU-bound tasks</li>
 * </ul>
 * 
 * <p><strong>This module has ZERO external dependencies</strong> to ensure
 * minimal overhead for user applications.
 */
package com.vajrapulse.api;
