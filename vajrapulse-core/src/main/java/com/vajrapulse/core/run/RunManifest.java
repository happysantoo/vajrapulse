package com.vajrapulse.core.run;

import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.task.TaskLifecycle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents metadata for a load test run.
 * 
 * <p>This class captures essential information about a test run for correlation
 * across metrics, traces, and logs. The manifest can be persisted to disk for
 * later analysis and debugging.
 * 
 * <p>Example manifest content:
 * <pre>
 * {
 *   "run_id": "abc123",
 *   "start_time": "2025-12-26T10:00:00Z",
 *   "task_class": "HttpLoadTest",
 *   "load_pattern_type": "StaticLoad",
 *   "duration_ms": 60000,
 *   "configuration": {
 *     "thread_strategy": "VIRTUAL",
 *     "percentiles": [0.50, 0.95, 0.99]
 *   }
 * }
 * </pre>
 * 
 * @since 1.0.0
 */
public final class RunManifest {
    private final String runId;
    private final Instant startTime;
    private final String taskClass;
    private final String loadPatternType;
    private final long durationMillis;
    private final Map<String, Object> configuration;
    
    /**
     * Creates a new run manifest.
     * 
     * @param runId the run identifier
     * @param startTime the start time of the run
     * @param taskClass the task class name
     * @param loadPatternType the load pattern type
     * @param durationMillis the duration in milliseconds
     * @param configuration the configuration snapshot
     */
    public RunManifest(String runId, Instant startTime, String taskClass, 
                      String loadPatternType, long durationMillis, 
                      Map<String, Object> configuration) {
        this.runId = runId;
        this.startTime = startTime;
        this.taskClass = taskClass;
        this.loadPatternType = loadPatternType;
        this.durationMillis = durationMillis;
        this.configuration = configuration != null ? new TreeMap<>(configuration) : new TreeMap<>();
    }
    
    /**
     * Returns the run identifier.
     * 
     * @return the run ID
     */
    public String getRunId() {
        return runId;
    }
    
    /**
     * Returns the start time of the run.
     * 
     * @return the start time
     */
    public Instant getStartTime() {
        return startTime;
    }
    
    /**
     * Returns the task class name.
     * 
     * @return the task class
     */
    public String getTaskClass() {
        return taskClass;
    }
    
    /**
     * Returns the load pattern type.
     * 
     * @return the load pattern type
     */
    public String getLoadPatternType() {
        return loadPatternType;
    }
    
    /**
     * Returns the duration in milliseconds.
     * 
     * @return the duration
     */
    public long getDurationMillis() {
        return durationMillis;
    }
    
    /**
     * Returns the configuration snapshot.
     * 
     * @return the configuration (immutable copy)
     */
    public Map<String, Object> getConfiguration() {
        return Map.copyOf(configuration);
    }
    
    /**
     * Writes this manifest to a JSON file.
     *
     * <p>The file will be written as a simple JSON object with all manifest fields.
     * If the file already exists, it will be overwritten.
     *
     * @param filePath the path to write the manifest file
     * @throws UncheckedIOException if writing fails
     * @throws IllegalArgumentException if the path contains parent directory traversal
     */
    public void writeToFile(Path filePath) {
        try {
            String json = toJson();
            Path resolved = filePath.normalize();
            for (int i = 0; i < resolved.getNameCount(); i++) {
                if (resolved.getName(i).toString().equals("..")) {
                    throw new IllegalArgumentException(
                        "Path must not escape via parent directory references: " + filePath);
                }
            }
            Path parent = resolved.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(resolved, json, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write run manifest to " + filePath, e);
        }
    }
    
    /**
     * Converts this manifest to a JSON string.
     * 
     * @return JSON representation of the manifest
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\n");
        appendJsonField(sb, "run_id", runId, true);
        appendJsonField(sb, "start_time", startTime != null ? startTime.toString() : null, true);
        appendJsonField(sb, "task_class", taskClass, true);
        appendJsonField(sb, "load_pattern_type", loadPatternType, true);
        appendJsonField(sb, "duration_ms", durationMillis, true);
        
        // Configuration object
        sb.append("  \"configuration\": {\n");
        boolean first = true;
        for (var entry : configuration.entrySet()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            appendJsonField(sb, entry.getKey(), entry.getValue(), false, "    ");
        }
        sb.append("\n  }");
        
        sb.append("\n}");
        return sb.toString();
    }
    
    private void appendJsonField(StringBuilder sb, String key, Object value, boolean trailingComma) {
        appendJsonField(sb, key, value, trailingComma, "  ");
    }
    
    private void appendJsonField(StringBuilder sb, String key, Object value, boolean trailingComma, String indent) {
        sb.append(indent).append('"').append(escape(key)).append('"').append(": ");
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
        if (trailingComma) {
            sb.append(',');
        }
        sb.append('\n');
    }
    
    private String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"'  -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
    
    /**
     * Creates a run manifest from execution context.
     *
     * <p><strong>Warning:</strong> Do not include secrets, credentials, or sensitive
     * data in {@code configuration}. Configuration values are serialized as plaintext
     * JSON via {@code String.valueOf()} and written to disk by {@link #writeToFile(Path)}.
     *
     * @param runId the run identifier
     * @param taskLifecycle the task lifecycle instance
     * @param loadPattern the load pattern
     * @param startTimeMillis the start time in milliseconds
     * @param configuration additional configuration to include (must not contain secrets)
     * @return a new run manifest
     */
    public static RunManifest create(String runId, TaskLifecycle taskLifecycle, 
                                     LoadPattern loadPattern, long startTimeMillis,
                                     Map<String, Object> configuration) {
        return new RunManifest(
            runId,
            Instant.ofEpochMilli(startTimeMillis),
            taskLifecycle != null ? taskLifecycle.getClass().getSimpleName() : "unknown",
            loadPattern != null ? loadPattern.getClass().getSimpleName() : "unknown",
            loadPattern != null ? loadPattern.getDuration().toMillis() : 0,
            configuration
        );
    }
}
