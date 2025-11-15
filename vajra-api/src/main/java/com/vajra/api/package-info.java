/**
 * Core API for Vajra load testing framework.
 * 
 * <p>This package contains the public API that users interact with:
 * <ul>
 *   <li>{@link com.vajra.api.Task} - Main interface for load test tasks</li>
 *   <li>{@link com.vajra.api.TaskResult} - Sealed result type (Success/Failure)</li>
 *   <li>{@link com.vajra.api.LoadPattern} - Interface for load patterns</li>
 *   <li>{@link com.vajra.api.StaticLoad} - Constant TPS</li>
 *   <li>{@link com.vajra.api.RampUpLoad} - Linear ramp to max</li>
 *   <li>{@link com.vajra.api.RampUpToMaxLoad} - Ramp then sustain</li>
 *   <li>{@link com.vajra.api.VirtualThreads} - Annotation for I/O-bound tasks</li>
 *   <li>{@link com.vajra.api.PlatformThreads} - Annotation for CPU-bound tasks</li>
 * </ul>
 * 
 * <p><strong>This module has ZERO external dependencies</strong> to ensure
 * minimal overhead for user applications.
 */
package com.vajra.api;
