/**
 * Task interfaces and annotations for load test execution.
 * 
 * <p>This package contains:
 * <ul>
 *   <li>{@link com.vajrapulse.api.task.TaskLifecycle} - Main interface for load test tasks</li>
 *   <li>{@link com.vajrapulse.api.task.Task} - Legacy interface (deprecated)</li>
 *   <li>{@link com.vajrapulse.api.task.TaskResult} - Sealed result type (Success/Failure)</li>
 *   <li>{@link com.vajrapulse.api.task.TaskIdentity} - Task identifier and metadata</li>
 *   <li>{@link com.vajrapulse.api.task.VirtualThreads} - Annotation for I/O-bound tasks</li>
 *   <li>{@link com.vajrapulse.api.task.PlatformThreads} - Annotation for CPU-bound tasks</li>
 * </ul>
 * 
 * <p><strong>This module has ZERO external dependencies</strong> to ensure
 * minimal overhead for user applications.
 */
package com.vajrapulse.api.task;

