/**
 * Core execution engine for Vajra load testing framework.
 * 
 * <p>This package contains the main orchestration components:
 * <ul>
 *   <li>{@link com.vajrapulse.core.engine.ExecutionEngine} - Main load test coordinator</li>
 *   <li>{@link com.vajrapulse.core.engine.TaskExecutor} - Task wrapper with automatic instrumentation</li>
 *   <li>{@link com.vajrapulse.core.engine.RateController} - TPS control and pacing</li>
 *   <li>{@link com.vajrapulse.core.engine.ExecutionMetrics} - Per-execution timing and result</li>
 * </ul>
 * 
 * @see com.vajrapulse.api
 * @see com.vajrapulse.core.metrics
 */
package com.vajrapulse.core.engine;
