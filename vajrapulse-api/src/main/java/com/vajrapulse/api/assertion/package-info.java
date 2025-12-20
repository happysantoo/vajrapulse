/**
 * Assertion framework for validating test results.
 * 
 * <p>This package contains:
 * <ul>
 *   <li>{@link com.vajrapulse.api.assertion.Assertion} - Interface for evaluating assertions</li>
 *   <li>{@link com.vajrapulse.api.assertion.AssertionResult} - Result of assertion evaluation</li>
 *   <li>{@link com.vajrapulse.api.assertion.Assertions} - Factory for creating assertions</li>
 * </ul>
 * 
 * <p>Assertions validate that test results meet expected criteria, such as:
 * <ul>
 *   <li>Latency thresholds (e.g., P95 latency &lt; 100ms)</li>
 *   <li>Error rate limits (e.g., error rate &lt; 1%)</li>
 *   <li>Throughput requirements (e.g., TPS &gt; 1000)</li>
 *   <li>Success rate requirements (e.g., success rate &gt; 99%)</li>
 * </ul>
 * 
 * <p><strong>This module has ZERO external dependencies</strong> to ensure
 * minimal overhead for user applications.
 */
package com.vajrapulse.api.assertion;

