# Multi-Exporter Example

This example demonstrates how to export metrics to multiple destinations simultaneously using VajraPulse.

## Features

- **Multiple Exporters**: Console and OpenTelemetry exporters working together
- **Composite Pattern**: Single exporter interface forwarding to multiple backends
- **Graceful Degradation**: Continues working even if one exporter fails
- **Periodic Export**: Exports metrics at regular intervals during test execution

## Quick Start

### Basic Usage (Console Only)

```bash
./gradlew :examples:multi-exporter:run
```

This will export to console. OpenTelemetry exporter will attempt to connect but won't fail if unreachable.

### With OpenTelemetry

1. **Set OTLP Endpoint**:
```bash
export OTLP_ENDPOINT="http://localhost:4318"
```

2. **Run the Test**:
```bash
./gradlew :examples:multi-exporter:run
```

## Custom Configuration

### Command-Line Arguments

```bash
# Run at 100 TPS
./gradlew :examples:multi-exporter:run --args "100"
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `OTLP_ENDPOINT` | OpenTelemetry OTLP endpoint | `http://localhost:4318` |

### System Properties

```bash
./gradlew :examples:multi-exporter:run -Dotlp.endpoint="http://localhost:4318"
```

## Architecture

The example uses a **Composite Exporter** pattern:

```
MetricsCollector
    ↓
CompositeExporter
    ├──→ ConsoleMetricsExporter (always works)
    └──→ OpenTelemetryExporter (optional, fails gracefully)
```

### Benefits

1. **Resilience**: If one exporter fails, others continue working
2. **Flexibility**: Easy to add/remove exporters
3. **Separation**: Each exporter handles its own errors
4. **Simplicity**: Single interface for multiple backends

## Export Behavior

### Periodic Exports

Metrics are exported every 5 seconds during test execution:
- Provides real-time visibility
- Shows progress during long-running tests
- Helps identify issues early

### Final Export

After test completion, a final export is performed:
- Complete metrics snapshot
- All exporters receive final data
- Useful for post-test analysis

## Error Handling

The composite exporter handles failures gracefully:

```java
for (MetricsExporter exporter : exporters) {
    try {
        exporter.export(title, metrics);
    } catch (Exception e) {
        // Log error, continue with other exporters
    }
}
```

This ensures:
- ✅ Console exporter always works (local)
- ✅ OpenTelemetry exporter failures don't break the test
- ✅ All working exporters receive metrics

## Use Cases

### Development

Use console exporter for immediate feedback:
```java
exporters.add(new ConsoleMetricsExporter());
```

### Production

Add observability platform:
```java
exporters.add(OpenTelemetryExporter.builder()
    .endpoint("https://otel.example.com")
    .build());
```

### Both

Use both for comprehensive monitoring:
```java
exporters.add(new ConsoleMetricsExporter());
exporters.add(otelExporter);
```

## Expected Output

```
Starting multi-exporter load test:
  TPS: 50.0
  Duration: PT30S
  Exporters: Console + OpenTelemetry

MultiExporterTest init completed
Starting load test runId=... pattern=StaticLoad duration=PT30S
[Console output every 5 seconds]
...
=== Final Results ===
[Console metrics]
[OpenTelemetry metrics sent to OTLP endpoint]
```

## Extending the Example

### Add More Exporters

```java
// Add custom exporter
exporters.add(new CustomMetricsExporter());

// Add file exporter (if available)
exporters.add(new FileMetricsExporter("results.json"));
```

### Custom Export Interval

Modify the periodic export thread:
```java
Thread.sleep(10000); // Export every 10 seconds
```

### Conditional Exporters

```java
if (System.getenv("ENABLE_OTEL") != null) {
    exporters.add(otelExporter);
}
```

## See Also

- [Console Exporter Documentation](../../vajrapulse-exporter-console/README.md)
- [OpenTelemetry Exporter Documentation](../../vajrapulse-exporter-opentelemetry/README.md)
- [HTTP Load Test Example](../http-load-test/README.md)
- [VajraPulse Main Documentation](../../README.md)
