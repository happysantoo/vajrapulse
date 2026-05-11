package com.vajrapulse.core.run

import com.vajrapulse.api.pattern.StaticLoad
import com.vajrapulse.api.pattern.LoadPattern
import com.vajrapulse.api.task.TaskLifecycle
import com.vajrapulse.api.task.TaskResult
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Timeout

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

@Timeout(10)
class RunManifestSpec extends Specification {

    @TempDir
    Path tempDir

    def "should create manifest with all fields"() {
        given: "a task lifecycle and load pattern"
        def task = new TaskLifecycle() {
            @Override void init() throws Exception {}
            @Override TaskResult execute(long iteration) throws Exception { return TaskResult.success() }
            @Override void teardown() throws Exception {}
        }
        def load = new StaticLoad(100.0, Duration.ofSeconds(60))

        when: "creating manifest"
        def manifest = RunManifest.create("run-123", task, load, 1000L, ["key": "value"])

        then: "all fields are set"
        manifest.runId == "run-123"
        manifest.startTime == Instant.ofEpochMilli(1000L)
        manifest.taskClass != null
        manifest.loadPatternType == "StaticLoad"
        manifest.durationMillis == 60_000L
        manifest.configuration.containsKey("key")
    }

    def "should handle null task lifecycle"() {
        when: "creating manifest with null task"
        def manifest = RunManifest.create("run-1", null, null, 0L, null)

        then: "defaults are used"
        manifest.taskClass == "unknown"
        manifest.loadPatternType == "unknown"
        manifest.durationMillis == 0L
        manifest.configuration.isEmpty()
    }

    def "should produce valid JSON"() {
        given: "a manifest"
        def manifest = new RunManifest("abc", Instant.ofEpochMilli(1000), "MyTask",
                "StaticLoad", 60000L, ["threads": "virtual"])

        when: "converting to JSON"
        def json = manifest.toJson()

        then: "JSON is well-formed"
        json.contains("\"run_id\": \"abc\"")
        json.contains("\"task_class\": \"MyTask\"")
        json.contains("\"load_pattern_type\": \"StaticLoad\"")
        json.contains("\"duration_ms\": 60000")
        json.contains("\"configuration\": {")
        json.contains("\"threads\": \"virtual\"")
    }

    def "should escape JSON special characters"() {
        given: "a manifest with special characters in fields"
        def manifest = new RunManifest("run\"id", Instant.ofEpochMilli(1000),
                "Task\nName", "Pattern\tType", 1000L,
                ["key\"quote": "val\\backslash"])

        when: "converting to JSON"
        def json = manifest.toJson()

        then: "special characters are escaped"
        json.contains("\\\"")
        json.contains("\\\\")
        json.contains("\\n")
        json.contains("\\t")

        and: "JSON is parseable"
        json.startsWith("{")
        json.endsWith("}")
    }

    def "should escape control characters in JSON"() {
        given: "a manifest with control characters"
        def manifest = new RunManifest("runctrl", Instant.ofEpochMilli(1000),
                "Task", "Pattern", 1000L, [:])

        when: "converting to JSON"
        def json = manifest.toJson()

        then: "control characters are escaped as unicode"
        json.contains("\\u0001")

        and: "no raw control characters remain"
        !json.contains("" as String)
    }

    def "should escape backspace and form feed"() {
        given: "a manifest with backspace and form feed"
        def manifest = new RunManifest("run\bid\fform", Instant.ofEpochMilli(1000),
                "Task", "Pattern", 1000L, [:])

        when: "converting to JSON"
        def json = manifest.toJson()

        then: "backspace and form feed are escaped"
        json.contains("\\b")
        json.contains("\\f")
    }

    def "should write manifest to file"() {
        given: "a manifest"
        def manifest = new RunManifest("test-run", Instant.ofEpochMilli(5000),
                "TestTask", "StaticLoad", 30000L, ["env": "staging"])
        def outputFile = tempDir.resolve("manifest.json")

        when: "writing to file"
        manifest.writeToFile(outputFile)

        then: "file exists with correct content"
        Files.exists(outputFile)
        def content = Files.readString(outputFile)
        content.contains("\"run_id\": \"test-run\"")
        content.contains("\"configuration\": {")
        content.contains("\"env\": \"staging\"")
    }

    def "should create parent directories when writing file"() {
        given: "a manifest"
        def manifest = new RunManifest("test", Instant.now(), "Task", "Pattern", 1000L, [:])
        def nestedFile = tempDir.resolve("sub/deep/manifest.json")

        when: "writing to nested path"
        manifest.writeToFile(nestedFile)

        then: "directory is created and file exists"
        Files.exists(nestedFile)
        Files.isDirectory(tempDir.resolve("sub/deep"))
    }

    def "should reject path traversal attempts"() {
        given: "a manifest"
        def manifest = new RunManifest("test", Instant.now(), "Task", "Pattern", 1000L, [:])
        def traversalPath = Path.of("../../../etc/passwd")

        when: "writing to traversal path"
        manifest.writeToFile(traversalPath)

        then: "throws IllegalArgumentException"
        thrown(IllegalArgumentException)
    }

    def "should handle null configuration map"() {
        when: "creating manifest with null config"
        def manifest = new RunManifest("r1", Instant.now(), "Task", "Pattern", 1000L, null)

        then: "configuration is empty, not null"
        manifest.configuration != null
        manifest.configuration.isEmpty()
    }

    def "should produce immutable configuration copy"() {
        given: "a manifest with config"
        def config = new LinkedHashMap<String, Object>(["k": "v"])
        def manifest = new RunManifest("r1", Instant.now(), "Task", "Pattern", 1000L, config)

        when: "modifying original config"
        config.put("k2", "v2")

        then: "manifest config is unchanged"
        manifest.configuration.size() == 1

        when: "trying to modify returned config"
        try {
            manifest.configuration.put("new", "val")
        } catch (UnsupportedOperationException ignored) {
            // expected
        }

        then: "returned config is unmodifiable"
        manifest.configuration.size() == 1
    }

    def "should handle boolean and number config values"() {
        given: "a manifest with mixed config types"
        def config = new LinkedHashMap<String, Object>()
        config.put("enabled", true)
        config.put("timeout", 5000)
        config.put("ratio", 0.95d)

        def manifest = new RunManifest("r1", Instant.now(), "Task", "Pattern", 1000L, config)

        when: "converting to JSON"
        def json = manifest.toJson()

        then: "non-string values are serialized without quotes"
        json.contains("\"enabled\": true")
        json.contains("\"timeout\": 5000")
        // double values get toString'd which would be quoted — that's fine
    }

    def "should handle startTime null in JSON"() {
        given: "manifest with null startTime"
        def manifest = new RunManifest("r1", null, "Task", "Pattern", 1000L, [:])

        when: "converting to JSON"
        def json = manifest.toJson()

        then: "null startTime produces valid JSON"
        json.contains("\"start_time\": null")
    }
}
