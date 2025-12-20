/**
 * Metrics interfaces for VajraPulse.
 * 
 * <p>This package contains two main interfaces for accessing execution metrics:
 * 
 * <ul>
 *   <li>{@link com.vajrapulse.api.metrics.Metrics} - Comprehensive metrics snapshot
 *       for assertion evaluation and reporting. Provides detailed metrics including
 *       percentiles, TPS breakdown (response/success/failure), and latency distributions.
 *       Used by the assertion framework and metrics exporters.</li>
 *   <li>{@link com.vajrapulse.api.metrics.MetricsProvider} - Lightweight metrics provider
 *       optimized for high-frequency queries. Provides essential metrics (failure rate,
 *       execution count, failure count) with optional time-windowed calculations.
 *       Used by AdaptiveLoadPattern for real-time decision-making.</li>
 * </ul>
 * 
 * <p><strong>Key Differences:</strong>
 * <table border="1">
 *   <caption>Comparison of Metrics and MetricsProvider</caption>
 *   <tr>
 *     <th>Feature</th>
 *     <th>Metrics</th>
 *     <th>MetricsProvider</th>
 *   </tr>
 *   <tr>
 *     <td>Purpose</td>
 *     <td>Comprehensive snapshot for analysis</td>
 *     <td>Lightweight queries for decision-making</td>
 *   </tr>
 *   <tr>
 *     <td>Data Provided</td>
 *     <td>Percentiles, TPS breakdown, latency distributions</td>
 *     <td>Failure rate, execution count, failure count</td>
 *   </tr>
 *   <tr>
 *     <td>Query Frequency</td>
 *     <td>Low (periodic snapshots)</td>
 *     <td>High (every TPS calculation)</td>
 *   </tr>
 *   <tr>
 *     <td>Time Windows</td>
 *     <td>All-time metrics</td>
 *     <td>All-time + recent window support</td>
 *   </tr>
 *   <tr>
 *     <td>Units</td>
 *     <td>Failure rate as percentage (0.0-100.0)</td>
 *     <td>Failure rate as percentage (0.0-100.0)</td>
 *   </tr>
 * </table>
 * 
 * <p><strong>When to Use:</strong>
 * <ul>
 *   <li><strong>Use {@code Metrics}</strong> for:
 *     <ul>
 *       <li>Assertion evaluation (comprehensive metrics needed)</li>
 *       <li>Metrics reporting and export</li>
 *       <li>Post-execution analysis</li>
 *       <li>Dashboard and visualization</li>
 *     </ul>
 *   </li>
 *   <li><strong>Use {@code MetricsProvider}</strong> for:
 *     <ul>
 *       <li>Adaptive pattern decision-making (high-frequency queries)</li>
 *       <li>Real-time TPS adjustments</li>
 *       <li>Time-windowed failure rate calculations</li>
 *       <li>Any scenario requiring lightweight, fast metrics access</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p><strong>Note:</strong> Backpressure-related interfaces have been moved to
 * {@link com.vajrapulse.api.backpressure} package for better separation of concerns.
 * 
 * <p><strong>This module has ZERO external dependencies</strong> to ensure
 * minimal overhead for user applications.
 */
package com.vajrapulse.api.metrics;

