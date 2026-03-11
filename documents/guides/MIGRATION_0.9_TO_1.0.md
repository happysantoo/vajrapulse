# Migration Guide: 0.9.x to 1.0.0

## Overview

This guide helps you migrate from VajraPulse 0.9.x to 1.0.0. The 1.0.0 release stabilizes the API and establishes long-term compatibility guarantees.

## Breaking Changes

### None Planned

As of 0.9.11, no breaking changes are planned for 1.0.0. The API has been reviewed and frozen.

If breaking changes are introduced, they will be documented here with migration steps.

## New Features in 1.0.0

### Enhanced Observability

- **Trace Correlation**: Logs now include `trace_id`, `span_id`, and `run_id`
- **Run Manifest**: Each run creates a `vajrapulse-run-{id}.json` manifest file
- **Structured Logging**: Use `StructuredLogger` for JSON-formatted logs

### Performance Improvements

- **ScopedValue**: Replaced ThreadLocal with ScopedValue for better virtual thread support
- **LongAdder**: High-contention counters use LongAdder for improved scalability
- **Lock-Free**: Critical paths are lock-free for maximum throughput

### Task Lifecycle Clarity

- `init()`: Called once before test starts (not measured)
- `execute()`: Called for each iteration (measured)
- `teardown()`: Called once after test ends (not measured)

## Deprecations

None in 0.9.11.

## Recommended Updates

### 1. Enable Preview Features

Java 21 preview features are now used. Add to your build:

```kotlin
// build.gradle.kts
tasks.withType<JavaCompile> {
    options.compilerArgs.add("--enable-preview")
}
tasks.withType<Test> {
    jvmArgs("--enable-preview")
}
```

### 2. Update Dependencies

```kotlin
dependencies {
    implementation("com.vajrapulse:vajrapulse-core:1.0.0")
    // Optional exporters
    implementation("com.vajrapulse:vajrapulse-exporter-console:1.0.0")
}
```

### 3. Use BOM for Version Management

```kotlin
dependencies {
    implementation(platform("com.vajrapulse:vajrapulse-bom:1.0.0"))
    implementation("com.vajrapulse:vajrapulse-core")
}
```

## API Stability

### Stable APIs (Safe to Use)

- `com.vajrapulse.api.task.Task`
- `com.vajrapulse.api.task.TaskResult`
- `com.vajrapulse.api.pattern.LoadPattern` and implementations
- `com.vajrapulse.api.metrics.AggregatedMetrics`
- `com.vajrapulse.core.engine.ExecutionEngine`

### Experimental APIs (May Change)

- `com.vajrapulse.api.pattern.adaptive.*` - Adaptive load patterns
- `com.vajrapulse.core.tracing.*` - Tracing integration

See `documents/architecture/API_FREEZE_0.9.11.md` for complete API inventory.

## Testing Your Migration

```bash
# 1. Update version in build.gradle.kts
# 2. Run tests
./gradlew test --rerun-tasks

# 3. Verify coverage
./gradlew jacocoTestCoverageVerification

# 4. Run your load tests
./gradlew run
```

## Getting Help

- GitHub Issues: Report migration problems
- Examples: See `examples/` for updated patterns
- Changelog: See `CHANGELOG.md` for detailed changes
