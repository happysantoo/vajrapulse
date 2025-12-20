/**
 * Core API for Vajra load testing framework.
 * 
 * <p>This package serves as the root package for the VajraPulse API. The API is
 * organized into logical sub-packages:
 * 
 * <ul>
 *   <li>{@link com.vajrapulse.api.task} - Task interfaces and annotations</li>
 *   <li>{@link com.vajrapulse.api.pattern} - Basic load patterns</li>
 *   <li>{@link com.vajrapulse.api.pattern.adaptive} - Adaptive load pattern</li>
 *   <li>{@link com.vajrapulse.api.metrics} - Metrics and backpressure interfaces</li>
 *   <li>{@link com.vajrapulse.api.assertion} - Assertion framework</li>
 * </ul>
 * 
 * <p><strong>This module has ZERO external dependencies</strong> to ensure
 * minimal overhead for user applications.
 */
package com.vajrapulse.api;
