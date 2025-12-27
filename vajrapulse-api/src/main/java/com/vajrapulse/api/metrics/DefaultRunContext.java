package com.vajrapulse.api.metrics;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of {@link RunContext}.
 * 
 * <p>This record provides an immutable implementation of RunContext
 * with defensive copies of mutable collections.
 * 
 * @since 0.9.12
 */
record DefaultRunContext(
    String runId,
    Instant startTime,
    Instant endTime,
    String taskClass,
    String loadPatternType,
    Map<String, Object> configuration,
    SystemInfo systemInfo
) implements RunContext {
    
    /**
     * Empty run context for cases where context is not available.
     */
    static final RunContext EMPTY = new DefaultRunContext(
        "unknown",
        Instant.EPOCH,
        null,
        "unknown",
        "unknown",
        Collections.emptyMap(),
        SystemInfo.unknown()
    );
    
    /**
     * Compact constructor that validates and creates defensive copies.
     */
    DefaultRunContext {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(taskClass, "taskClass must not be null");
        Objects.requireNonNull(loadPatternType, "loadPatternType must not be null");
        Objects.requireNonNull(systemInfo, "systemInfo must not be null");
        
        // Create defensive copy of configuration
        configuration = configuration != null 
            ? Collections.unmodifiableMap(new LinkedHashMap<>(configuration))
            : Collections.emptyMap();
    }
}
