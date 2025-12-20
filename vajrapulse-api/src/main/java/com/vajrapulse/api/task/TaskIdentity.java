package com.vajrapulse.api.task;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable identifier and metadata for a task.
 * <p>Separates descriptive identity from execution logic, allowing the core
 * engine and exporters to tag metrics, logs, and future traces without
 * polluting the {@link Task} interface with naming concerns.
 * <p>Pre-1.0 design: kept deliberately small. Additional fields (e.g. group,
 * phase, scenario) can be added later without changing the {@link Task}
 * contract.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TaskIdentity identity = new TaskIdentity(
 *     "checkout-flow",
 *     Map.of(
 *         "component", "payments",
 *         "scenario", "high-tps",
 *         "dataset", "prod-sample"
 *     )
 * );
 * }
 * </pre>
 *
 * <h2>Guidelines</h2>
 * <ul>
 *   <li><b>name</b> must be stable for the duration of a test run.</li>
 *   <li><b>tags</b> should be low-cardinality (avoid user IDs, UUIDs, etc.).</li>
 *   <li>Use tags for grouping / filtering in dashboards (e.g. scenario, phase).</li>
 *   <li>Avoid more than ~10 distinct values per tag key to prevent cardinality explosions.</li>
 * </ul>
 */
public record TaskIdentity(String name, Map<String, String> tags) {

    /**
     * Canonical constructor enforcing invariants.
     * @param name non-null, non-blank task name
     * @param tags optional metadata map (null treated as empty); defensively copied
     */
    public TaskIdentity {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        tags = (tags == null || tags.isEmpty()) ? Map.of() : Map.copyOf(tags);
    }

    /**
     * Creates a {@link TaskIdentity} with only a name and no tags.
     * @param name task name
     * @return identity with empty tags
     */
    public static TaskIdentity of(String name) {
        return new TaskIdentity(name, Map.of());
    }

    /**
     * Convenience factory for a name and single tag key/value.
     * @param name task name
     * @param tagKey tag key
     * @param tagValue tag value
     * @return identity with one tag
     */
    public static TaskIdentity of(String name, String tagKey, String tagValue) {
        return new TaskIdentity(name, Map.of(tagKey, tagValue));
    }
}
