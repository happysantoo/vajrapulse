# VajraPulse OpenTelemetry Exporter

Export VajraPulse load testing metrics to OpenTelemetry-compatible backends via the OTLP (OpenTelemetry Protocol).

## Overview

The OpenTelemetry exporter sends metrics to any OTLP-compatible observability platform:

- **OpenTelemetry Collector** - Standard open-source collector
- **Prometheus** - With OTLP receiver enabled
- **Grafana Cloud** - Native OTLP support
- **Datadog, New Relic, Honeycomb** - Via OTLP endpoints
- **Any OTLP-compatible backend**

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry:1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'com.vajrapulse:vajrapulse-exporter-opentelemetry:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vajrapulse-exporter-opentelemetry</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

### Basic Usage

```java
import com.vajrapulse.exporter.otel.OpenTelemetryExporter;

// Create exporter with defaults
try (var exporter = OpenTelemetryExporter.builder()
        .endpoint("http://localhost:4318")  // OTLP endpoint
        .serviceName("my-load-test")
        .build()) {
    
    // Use with MetricsPipeline
    MetricsPipeline.builder()
        .addExporter(exporter)
        .withPeriodic(Duration.ofSeconds(10))
        .build()
        .run(task, loadPattern);
}
```

### Custom Configuration

```java
OpenTelemetryExporter exporter = OpenTelemetryExporter.builder()
    .endpoint("https://otlp.example.com:4318")
    .serviceName("checkout-load-test")
    .exportInterval(5)  // Export every 5 seconds
    .headers(Map.of(
        "Authorization", "Bearer " + apiKey,
        "X-Org-ID", orgId
    ))
    .build();
```

## Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `endpoint` | String | `http://localhost:4318` | OTLP gRPC endpoint URL |
| `serviceName` | String | `vajrapulse-load-test` | Service name for resource attribution |
| `exportInterval` | int | `10` | Export interval in seconds |
| `headers` | Map<String, String> | `{}` | Custom headers (e.g., auth tokens) |

## Exported Metrics

The exporter sends the following metrics to OTLP:

### Counters

| Metric Name | Type | Description |
|-------------|------|-------------|
| `vajrapulse.executions.total` | Counter | Total number of task executions |
| `vajrapulse.executions.success` | Counter | Number of successful executions |
| `vajrapulse.executions.failure` | Counter | Number of failed executions |

### Gauges

| Metric Name | Type | Description |
|-------------|------|-------------|
| `vajrapulse.success.rate` | Gauge | Success rate percentage (0-100) |

### Histograms

| Metric Name | Type | Unit | Description |
|-------------|------|------|-------------|
| `vajrapulse.latency.success` | Histogram | ms | Success latency distribution with percentiles |
| `vajrapulse.latency.failure` | Histogram | ms | Failure latency distribution with percentiles |

All histograms include percentile attributes (p50, p75, p95, p99).

## Integration Examples

### OpenTelemetry Collector

**docker-compose.yml**:
```yaml
version: '3.8'
services:
  otel-collector:
    image: otel/opentelemetry-collector:latest
    command: ["--config=/etc/otel-collector-config.yaml"]
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector-config.yaml
    ports:
      - "4318:4318"  # OTLP gRPC
      - "8889:8889"  # Prometheus exporter
```

**otel-collector-config.yaml**:
```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4318

exporters:
  prometheus:
    endpoint: "0.0.0.0:8889"
  logging:
    loglevel: debug

service:
  pipelines:
    metrics:
      receivers: [otlp]
      exporters: [prometheus, logging]
```

**Java code**:
```java
OpenTelemetryExporter exporter = OpenTelemetryExporter.builder()
    .endpoint("http://localhost:4318")
    .serviceName("api-load-test")
    .build();
```

### Grafana Cloud

```java
OpenTelemetryExporter exporter = OpenTelemetryExporter.builder()
    .endpoint("https://otlp-gateway-prod-us-central-0.grafana.net/otlp")
    .serviceName("production-load-test")
    .headers(Map.of(
        "Authorization", "Basic " + base64Encode(instanceId + ":" + apiKey)
    ))
    .build();
```

### Prometheus with OTLP

**prometheus.yml**:
```yaml
scrape_configs:
  - job_name: 'otel-collector'
    static_configs:
      - targets: ['localhost:8889']
```

Start Collector with Prometheus exporter, then configure VajraPulse to send to Collector.

## Multi-Exporter Setup

Use multiple exporters simultaneously:

```java
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import com.vajrapulse.exporter.otel.OpenTelemetryExporter;

var consoleExporter = new ConsoleMetricsExporter();
var otelExporter = OpenTelemetryExporter.builder()
    .endpoint("http://localhost:4318")
    .build();

// Combine exporters
var compositeExporter = new MetricsExporter() {
    @Override
    public void export(String title, AggregatedMetrics metrics) {
        consoleExporter.export(title, metrics);
        otelExporter.export(title, metrics);
    }
};

// Or use MetricsPipeline (when available in future version)
MetricsPipeline.builder()
    .addExporter(consoleExporter)
    .addExporter(otelExporter)
    .withPeriodic(Duration.ofSeconds(10))
    .build();
```

## Error Handling

The exporter is **resilient by design**:

- **Network failures**: Logged but don't interrupt tests
- **Unreachable endpoints**: Gracefully degraded
- **Invalid configuration**: Fails fast at build time

```java
// Exporter won't throw even if endpoint is down
exporter.export("Test", metrics);  // Logs error, continues
```

## Performance Considerations

### Resource Usage

- **Minimal overhead**: ~2-3 MB additional JAR size
- **Async export**: Non-blocking metric transmission
- **Virtual thread safe**: No carrier thread pinning

### Export Intervals

| Interval | Use Case | Trade-off |
|----------|----------|-----------|
| 5s | Real-time monitoring | Higher network traffic |
| 10s | Balanced (default) | Good for most cases |
| 30s | Long-running tests | Lower overhead |

## Troubleshooting

### Connection Issues

**Problem**: "Failed to export metrics to OTLP endpoint"

**Solutions**:
1. Verify endpoint is reachable: `curl http://localhost:4318`
2. Check firewall rules
3. Enable debug logging:
   ```java
   System.setProperty("org.slf4j.simpleLogger.log.com.vajrapulse", "debug");
   ```

### Authentication Failures

**Problem**: HTTP 401/403 responses

**Solution**: Add authentication headers:
```java
.headers(Map.of("Authorization", "Bearer YOUR_TOKEN"))
```

### Metrics Not Appearing

**Problem**: No metrics in backend

**Checklist**:
- [ ] Exporter created successfully
- [ ] `export()` method called periodically
- [ ] OTLP Collector running and configured
- [ ] Backend scraping from Collector
- [ ] Service name matches your query filters

## Architecture

### Design Principles

1. **Zero runtime dependencies** (beyond OpenTelemetry SDK)
2. **Implements `MetricsExporter` interface** from vajrapulse-core
3. **Fail-safe**: Never interrupts load tests
4. **AutoCloseable**: Proper resource cleanup

### Metrics Flow

```
VajraPulse Test
    ↓
ExecutionEngine
    ↓
MetricsCollector (AggregatedMetrics)
    ↓
OpenTelemetryExporter
    ↓
OTLP gRPC Protocol
    ↓
OpenTelemetry Collector
    ↓
Backend (Prometheus, Grafana, etc.)
```

## Dependencies

- **OpenTelemetry API**: 1.43.0
- **OpenTelemetry SDK**: 1.43.0
- **OTLP Exporter**: 1.43.0
- **Semantic Conventions**: 1.37.0
- **SLF4J**: 2.0.13

## License

Apache License 2.0 - Same as VajraPulse core project.

## See Also

- [VajraPulse Main Documentation](../../README.md)
- [Console Exporter](../vajrapulse-exporter-console/README.md)
- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/otel/)
- [OTLP Protocol](https://opentelemetry.io/docs/specs/otlp/)
