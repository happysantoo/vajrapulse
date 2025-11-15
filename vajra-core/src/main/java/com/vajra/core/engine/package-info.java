/**
 * Core execution engine for Vajra load testing framework.
 * 
 * <p>This package contains the main orchestration components:
 * <ul>
 *   <li>{@link com.vajra.core.engine.ExecutionEngine} - Main load test coordinator</li>
 *   <li>{@link com.vajra.core.engine.TaskExecutor} - Task wrapper with automatic instrumentation</li>
 *   <li>{@link com.vajra.core.engine.RateController} - TPS control and pacing</li>
 *   <li>{@link com.vajra.core.engine.ExecutionMetrics} - Per-execution timing and result</li>
 * </ul>
 * 
 * @see com.vajra.api
 * @see com.vajra.core.metrics
 */
package com.vajra.core.engine;
