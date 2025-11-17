# OpenTelemetry Semantic Conventions Migration

**Date**: November 16, 2025  
**Status**: ✅ COMPLETE  
**Branch**: `phase1-opentelemetry-exporter`

## Overview

Migrated VajraPulse metrics to follow OpenTelemetry semantic conventions for better standardization, interoperability, and observability platform compatibility.

## Changes Summary

### 1. Unified Metric Names

**Before** (Multiple separate metrics):
- `vajrapulse.executions.total` (Counter)
- `vajrapulse.executions.success` (Counter)
- `vajrapulse.executions.failure` (Counter)
- `vajrapulse.latency.success` (Histogram)
- `vajrapulse.latency.failure` (Histogram)
- `vajrapulse.success.rate` (Gauge)

**After** (Label-based partitioning):
- `vajrapulse.execution.count` (Counter) with `status=success|failure`
- `vajrapulse.execution.duration` (Gauge - percentile series) with `status=success|failure` and `percentile=<value>`
- `vajrapulse.success.rate` (Gauge - unchanged)

### 2. Resource Attribute Translation

User-provided resource attributes are automatically translated to semantic convention keys:

| User Key | Translated Key | Example Value |
|----------|----------------|---------------|
| `environment` | `deployment.environment` | `dev`, `staging`, `prod` |
| `region` | `cloud.region` | `us-east-1`, `eu-west-1` |
| `service.name` | `service.name` | (unchanged) |
| `service.version` | `service.version` | (unchanged) |

**Implementation**: `OpenTelemetryExporter.translateResourceKey()`

### 3. Metrics Implementation Details

#### Execution Counter
```java
executionCount = meter.counterBuilder("vajrapulse.execution.count")
    .setDescription("Count of task executions partitioned by status (success|failure)")
    .build();

// Recording with status label
executionCount.add(deltaSuccess, Attributes.of(
    AttributeKey.stringKey("status"), "success"
));
executionCount.add(deltaFailure, Attributes.of(
    AttributeKey.stringKey("status"), "failure"
));
```

**Delta Logic**: Tracks last cumulative counts per status to emit monotonic deltas, preventing double-counting in Prometheus.

#### Duration Gauge (Percentile Series)
```java
meter.gaugeBuilder("vajrapulse.execution.duration")
    .setDescription("Execution duration percentiles in milliseconds (snapshot) by status and percentile")
    .setUnit("ms")
    .buildWithCallback(measurement -> {
        var snapshot = lastMetrics.get();
        if (snapshot != null) {
            for (var entry : snapshot.successPercentiles().entrySet()) {
                String p = String.valueOf(entry.getKey());
                double ms = entry.getValue() / 1_000_000.0;
                measurement.record(ms, Attributes.builder()
                    .put(AttributeKey.stringKey("status"), "success")
                    .put(AttributeKey.stringKey("percentile"), p)
                    .build());
            }
        }
    });
```

**Rationale**: Async gauge with percentile labels instead of histogram bucket approach. Pre-aggregated percentiles from engine are exported as individual time series.

## Prometheus Metric Naming

OpenTelemetry OTLP → Prometheus exporter applies normalization:

| OTLP Metric | Prometheus Name | Notes |
|-------------|-----------------|-------|
| `vajrapulse.execution.count` | `vajrapulse_execution_count_total` | Counters get `_total` suffix |
| `vajrapulse.execution.duration` | `vajrapulse_execution_duration_milliseconds` | Unit appended |
| `vajrapulse.success.rate` | `vajrapulse_success_rate` | Gauges unchanged |

**Label Conversion**: Dots in attributes become underscores:
- `deployment.environment` → `deployment_environment`
- `cloud.region` → `cloud_region`

## Query Examples

### Execution Rates
```promql
# Total execution rate (per minute)
sum by (task_name) (rate(vajrapulse_execution_count_total[1m])) * 60

# Success rate (per minute)
rate(vajrapulse_execution_count_total{status="success"}[1m]) * 60

# Failure rate (per minute)
rate(vajrapulse_execution_count_total{status="failure"}[1m]) * 60
```

### Duration Percentiles
```promql
# P95 duration for successes
vajrapulse_execution_duration_milliseconds{status="success", percentile="0.95"}

# P99 duration for failures
vajrapulse_execution_duration_milliseconds{status="failure", percentile="0.99"}

# Compare P50 across success/failure
vajrapulse_execution_duration_milliseconds{percentile="0.5"}
```

### Success Rate
```promql
# Current success rate percentage
vajrapulse_success_rate

# Success rate by task
vajrapulse_success_rate{task_name="http-load-test"}
```

## Grafana Dashboard Updates

All panels updated to new metric names:

### Execution Rate Panel
```promql
# Before
rate(vajrapulse_executions_total{job="otel-collector"}[1m]) * 60
rate(vajrapulse_executions_success{job="otel-collector"}[1m]) * 60
rate(vajrapulse_executions_failure{job="otel-collector"}[1m]) * 60

# After
sum by (task_name) (rate(vajrapulse_execution_count_total{job="otel-collector"}[1m])) * 60
rate(vajrapulse_execution_count_total{job="otel-collector", status="success"}[1m]) * 60
rate(vajrapulse_execution_count_total{job="otel-collector", status="failure"}[1m]) * 60
```

### Latency Percentile Panels
```promql
# Before
histogram_quantile(0.95, rate(vajrapulse_latency_success_milliseconds_bucket[1m]))

# After
vajrapulse_execution_duration_milliseconds{status="success", percentile="0.95"}
```

### Metadata Table
Label renames applied:
- `environment` → `deployment_environment`
- `region` → `cloud_region`

## Verification

### Test Execution
```bash
cd examples/http-load-test
docker-compose up -d
cd ../..
./gradlew :examples:http-load-test:runOtel
```

### Collector Logs (Debug Output)
```
vajrapulse.execution.count{status=success,deployment.environment=test,...} 2998
vajrapulse.execution.count{status=failure,deployment.environment=test,...} 2
vajrapulse.success.rate{deployment.environment=test,...} 99.93333333333334
vajrapulse.execution.duration{percentile=0.5,status=success,...} 41.418752
vajrapulse.execution.duration{percentile=0.9,status=success,...} 557.580288
vajrapulse.execution.duration{percentile=0.95,status=success,...} 792.461312
vajrapulse.execution.duration{percentile=0.99,status=success,...} 1459.355648
vajrapulse.execution.duration{percentile=0.5,status=failure,...} 146.80064
```

✅ Metrics arrive correctly at collector with new schema  
✅ Status labels present (`success`, `failure`)  
✅ Percentile labels are strings (`"0.5"`, `"0.95"`, etc.)  
✅ Resource attributes translated (`deployment.environment`)

### Build Verification
```bash
./gradlew clean build --quiet
# Result: SUCCESS
```

## Benefits

### 1. Reduced Cardinality
- **Before**: 6 separate metric names
- **After**: 3 metric names with labels
- Easier to query, filter, and aggregate

### 2. Standardization
- Aligns with OpenTelemetry semantic conventions
- Better compatibility with observability platforms (Datadog, New Relic, etc.)
- Resource attributes follow standard keys

### 3. Flexibility
```promql
# Easy to aggregate across status
sum(vajrapulse_execution_count_total) by (task_name)

# Filter by any label combination
vajrapulse_execution_count_total{status="success", deployment_environment="prod"}

# Compare success vs failure
vajrapulse_execution_duration_milliseconds{percentile="0.95"}
```

### 4. Future-Proof
- Adding new statuses (e.g., `timeout`, `cancelled`) is trivial
- Adding new percentiles doesn't require code changes
- Resource attribute additions are automatic

## Migration Checklist

- [x] Update `OpenTelemetryExporter` metric creation
- [x] Replace separate counters with unified counter + status label
- [x] Replace histogram with async gauge for percentiles
- [x] Add resource attribute translation helper
- [x] Update delta tracking logic for per-status counters
- [x] Update Grafana dashboard queries
- [x] Update `OBSERVABILITY-STACK.md` documentation
- [x] Update `OBSERVABILITY-IMPLEMENTATION-SUMMARY.md`
- [x] Update `verify-stack.sh` metric checks
- [x] Verify build passes
- [x] Test with live stack

## Known Behaviors

### Prometheus Exporter Memory
The OTEL Collector's Prometheus exporter only holds metrics while the pipeline is active. After a test completes and metrics are flushed:
- Metrics disappear from the `/metrics` endpoint
- Prometheus retains scraped data in TSDB
- This is expected behavior for pull-based exporters

**Recommendation**: For persistent metrics endpoint, consider using Prometheus remote write instead of pull exporter.

### Gauge vs Histogram for Percentiles
We chose gauge over histogram because:
1. VajraPulse engine pre-computes percentiles (HdrHistogram)
2. No need for Prometheus to recompute from buckets
3. More efficient storage (6 values vs 100+ buckets)
4. Exact percentile values from engine

## Files Modified

| File | Changes |
|------|---------|
| `vajrapulse-exporter-opentelemetry/src/main/java/com/vajrapulse/exporter/otel/OpenTelemetryExporter.java` | Metric unification, resource translation, async gauges |
| `examples/http-load-test/grafana/dashboards/vajrapulse-dashboard.json` | Query updates, label renames |
| `documents/OBSERVABILITY-IMPLEMENTATION-SUMMARY.md` | Metrics table, query examples |
| `examples/http-load-test/OBSERVABILITY-STACK.md` | Queries, resource attributes, metrics reference |
| `examples/http-load-test/verify-stack.sh` | Metric name checks |

## Commits

- `0a62842`: docs+dashboard: adopt unified metrics and semantic conventions
- `900c7af`: exporter: adopt semantic conventions fully

## Next Steps

Optional enhancements:
1. **Tracing Integration**: Add distributed trace export alongside metrics
2. **Logging Correlation**: Export logs with trace/span IDs
3. **Custom Semantic Conventions**: Define VajraPulse-specific conventions document
4. **Remote Write**: Configure Prometheus remote write for long-term storage
5. **Alert Rules**: Define standard alerting rules for SLA violations

## References

- [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [OpenTelemetry Metrics API](https://opentelemetry.io/docs/specs/otel/metrics/api/)
- [Prometheus Naming Best Practices](https://prometheus.io/docs/practices/naming/)
- [OTLP Specification](https://opentelemetry.io/docs/specs/otlp/)

---

**Status**: Ready for production use with standardized metrics! 🚀
