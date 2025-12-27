package com.vajrapulse.api.metrics;

import java.time.Instant;
import java.util.Map;

/**
 * Context information about a load test run.
 * 
 * <p>This interface provides metadata about the test run that can be included
 * in reports and exported metrics for correlation and reproducibility.
 * 
 * <p>Example usage:
 * <pre>{@code
 * RunContext context = RunContext.of(
 *     "run-123",
 *     Instant.now(),
 *     "HttpLoadTest",
 *     "StaticLoad",
 *     Map.of("tps", 100.0, "duration", "5m")
 * );
 * 
 * exporter.export("Test Results", metrics, context);
 * }</pre>
 * 
 * @since 0.9.11
 */
public interface RunContext {
    
    /**
     * Returns the unique run identifier.
     * 
     * <p>This ID is used to correlate metrics, traces, and logs across
     * the entire test run.
     * 
     * @return the run ID, never null
     */
    String runId();
    
    /**
     * Returns the start time of the test run.
     * 
     * @return the start time, never null
     */
    Instant startTime();
    
    /**
     * Returns the end time of the test run.
     * 
     * @return the end time, or null if the run is still in progress
     */
    Instant endTime();
    
    /**
     * Returns the task class name.
     * 
     * @return the simple class name of the task, never null
     */
    String taskClass();
    
    /**
     * Returns the load pattern type.
     * 
     * @return the simple class name of the load pattern, never null
     */
    String loadPatternType();
    
    /**
     * Returns the configuration snapshot.
     * 
     * <p>This map contains key configuration values used for the test run,
     * such as TPS, duration, pattern-specific parameters, etc.
     * 
     * @return an unmodifiable map of configuration values, never null
     */
    Map<String, Object> configuration();
    
    /**
     * Returns system information about the test environment.
     * 
     * @return system information, never null
     */
    SystemInfo systemInfo();
    
    /**
     * Creates a new RunContext with the specified values.
     * 
     * @param runId the run identifier
     * @param startTime the start time
     * @param taskClass the task class name
     * @param loadPatternType the load pattern type
     * @param configuration the configuration map
     * @return a new RunContext instance
     */
    static RunContext of(String runId, Instant startTime, String taskClass, 
                        String loadPatternType, Map<String, Object> configuration) {
        return of(runId, startTime, null, taskClass, loadPatternType, configuration, SystemInfo.current());
    }
    
    /**
     * Creates a new RunContext with the specified values including end time.
     * 
     * @param runId the run identifier
     * @param startTime the start time
     * @param endTime the end time (may be null)
     * @param taskClass the task class name
     * @param loadPatternType the load pattern type
     * @param configuration the configuration map
     * @param systemInfo the system information
     * @return a new RunContext instance
     */
    static RunContext of(String runId, Instant startTime, Instant endTime, String taskClass, 
                        String loadPatternType, Map<String, Object> configuration,
                        SystemInfo systemInfo) {
        return new DefaultRunContext(runId, startTime, endTime, taskClass, loadPatternType, 
                                    configuration, systemInfo);
    }
    
    /**
     * Returns an empty/unknown run context for cases where context is not available.
     * 
     * @return an empty run context
     */
    static RunContext empty() {
        return DefaultRunContext.EMPTY;
    }
}
