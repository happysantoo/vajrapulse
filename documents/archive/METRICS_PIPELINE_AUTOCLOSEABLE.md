# MetricsPipeline AutoCloseable Lifecycle Management

**Date**: November 15, 2025  
**Status**: Completed  
**Related**: Phase 1 OpenTelemetry Exporter

## Problem Statement

Users had to manually close exporters (especially `OpenTelemetryExporter`) after running tests, which was:
1. Error-prone (easy to forget)
2. Verbose (extra boilerplate)
3. Risky (final metrics might not be exported if close() wasn't called)

### Before
```java
OpenTelemetryExporter exporter = OpenTelemetryExporter.builder()...build();
MetricsPipeline pipeline = MetricsPipeline.builder()
    .addExporter(exporter)
    .build();

pipeline.run(task, pattern);

// User must remember to close!
exporter.close();
```

## Solution

Made `MetricsPipeline` implement `AutoCloseable` with the following guarantees:

1. **Final metrics are always exported** before closing exporters (in `run()`)
2. **AutoCloseable exporters are automatically closed** when pipeline is closed
3. **Try-with-resources pattern** handles lifecycle automatically
4. **Close failures don't propagate** – logged but don't crash the application

### After
```java
OpenTelemetryExporter exporter = OpenTelemetryExporter.builder()...build();

try (MetricsPipeline pipeline = MetricsPipeline.builder()
        .addExporter(exporter)
        .build()) {
    pipeline.run(task, pattern);
} // Automatic final export + cleanup
```

## Implementation Details

### MetricsPipeline Changes

1. **Implemented AutoCloseable**
   ```java
   public final class MetricsPipeline implements AutoCloseable
   ```

2. **Added close() method**
   - Iterates through all exporters
   - Checks if exporter implements `AutoCloseable`
   - Calls `close()` on closeable exporters
   - Catches and logs exceptions (resilient cleanup)

3. **Execution order guarantee**
   - `run()` exports final metrics to all exporters
   - `close()` is called after `run()` completes
   - Try-with-resources ensures `close()` even on exceptions

### Example Updates

Both example runners updated to use try-with-resources:

- `HttpLoadTestRunner` (Console export)
- `HttpLoadTestOtelRunner` (OTLP export)

### Testing

Created comprehensive test suite: `MetricsPipelineAutoCloseableSpec`

**Tests cover**:
- Basic close functionality
- Try-with-resources pattern
- Export before close ordering
- Multiple exporters
- Close failure handling
- Non-AutoCloseable exporters (backward compatible)

**Results**: All 6 lifecycle tests passing + all existing tests passing

## Benefits

1. **User Experience**: Simpler, cleaner code with less boilerplate
2. **Reliability**: Guaranteed final metrics export before shutdown
3. **Safety**: No resource leaks from forgotten close() calls
4. **Consistency**: Standard Java idiom (try-with-resources)
5. **Backward Compatible**: Non-AutoCloseable exporters still work

## Migration Guide

### For Users

**Old Pattern** (still works, but not recommended):
```java
MetricsPipeline pipeline = MetricsPipeline.builder()...build();
pipeline.run(task, pattern);
// Optional: manually close exporters if needed
```

**New Pattern** (recommended):
```java
try (MetricsPipeline pipeline = MetricsPipeline.builder()...build()) {
    pipeline.run(task, pattern);
}
```

### For Exporter Developers

If your exporter needs cleanup (flush buffers, close connections, etc.), implement `AutoCloseable`:

```java
public class MyExporter implements MetricsExporter, AutoCloseable {
    @Override
    public void export(String title, AggregatedMetrics metrics) {
        // Export logic
    }
    
    @Override
    public void close() {
        // Cleanup: flush buffers, close connections, etc.
        // Note: Final metrics already exported before this is called
    }
}
```

## Files Changed

### Core Changes
- `vajrapulse-worker/src/main/java/com/vajrapulse/worker/pipeline/MetricsPipeline.java`
  - Added `AutoCloseable` interface
  - Added `close()` method with resilient cleanup
  - Updated JavaDoc with lifecycle examples

### Example Updates
- `examples/http-load-test/src/main/java/com/example/http/HttpLoadTestRunner.java`
- `examples/http-load-test/src/main/java/com/example/http/HttpLoadTestOtelRunner.java`
- `examples/http-load-test/README.md`

### Tests
- `vajrapulse-worker/src/test/groovy/com/vajrapulse/worker/pipeline/MetricsPipelineAutoCloseableSpec.groovy` (new)

## Related Issues

This change complements the OpenTelemetry exporter implementation (Phase 1) by ensuring:
- Metrics are always flushed to OTLP endpoints
- SDK resources are properly cleaned up
- Users don't need to manage low-level exporter lifecycle

## Future Considerations

- Consider adding explicit flush timeout configuration
- May want shutdown hook for non-try-with-resources usage
- Could expose close() result (success/failure) for advanced users
