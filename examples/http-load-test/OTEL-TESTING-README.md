# OpenTelemetry Testing Setup - Quick Reference

This folder contains tools for testing VajraPulse's OpenTelemetry integration with a real OTLP collector.

## Files Included

- **docker-compose.yml** - OpenTelemetry Collector configuration
- **otel-collector-config.yml** - Metrics pipeline and exporters
- **validate-otel-metrics.sh** - Metrics validation script
- **OTEL-TESTING.md** - Complete testing guide

## Two Testing Paths

### Path A: Local Docker Setup (Recommended)

**Requirements:** Docker and Docker Compose installed

**Steps:**
```bash
# 1. Start collector
docker-compose up -d

# 2. Run example (30s at 100 TPS)
./gradlew runOtel

# 3. Validate metrics received
chmod +x validate-otel-metrics.sh
./validate-otel-metrics.sh

# 4. Cleanup
docker-compose down
```

See **OTEL-TESTING.md** for complete details, troubleshooting, and advanced scenarios.

### Path B: Remote Collector (No Docker Required)

Point the example at an existing OTLP collector:

**Modify HttpLoadTestOtelRunner.java:**
```java
.endpoint("http://collector-hostname:4318")  // Your collector endpoint
```

**Then run:**
```bash
./gradlew runOtel
```

### Path C: Manual Validation (No Collector)

Test the exporter resilience to connection errors:

```bash
# Run example (will log connection errors but not crash)
./gradlew runOtel
```

Verify:
- No exceptions thrown
- Metrics still collected locally
- Shows: "Failed to export metrics" (expected without real collector)

## What Gets Tested

✅ **Exporter Configuration**
- Endpoint configuration
- Protocol selection (gRPC/HTTP)
- Resource attributes
- Custom headers
- Export intervals

✅ **Metric Types**
- Counters (total, success, failure)
- Gauges (success rate)
- Histograms (latency percentiles)

✅ **Resource Attributes**
- Service identification
- Environment context
- Custom attributes

✅ **Resilience**
- Handles unreachable endpoints gracefully
- Doesn't crash on network errors
- Recovers after collector restart
- Works with try-with-resources lifecycle

## Expected Outputs

### Without Collector (logs show):
```
[main] INFO OpenTelemetryExporter - OpenTelemetry exporter initialized
[main] ERROR OpenTelemetryExporter - Failed to export metrics to OTLP endpoint
```

### With Collector (logs show):
```
[main] INFO OpenTelemetryExporter - OpenTelemetry exporter initialized
[main] DEBUG OpenTelemetryExporter - Exporting metrics to OTLP
[main] DEBUG OpenTelemetryExporter - Successfully exported metrics to OTLP
```

## Validation Output

**With collector running:**
```
✅ SUCCESS: All expected metrics received!
   Found 6/6 metrics:
   ✅ vajrapulse.executions.total
   ✅ vajrapulse.executions.success
   ✅ vajrapulse.executions.failure
   ✅ vajrapulse.success.rate
   ✅ vajrapulse.latency.success
   ✅ vajrapulse.latency.failure
```

**Without collector:**
```
❌ FAILED: Metrics file not created within 30s
   Collector may not be running or not receiving metrics
```

## Performance Baseline

Typical metrics on 100 TPS for 30 seconds:

| Metric | Value |
|--------|-------|
| Total Requests | ~3,000 |
| Success Rate | 99-100% |
| P50 Latency | 100-200ms |
| P95 Latency | 200-500ms |
| P99 Latency | 500-1000ms |

## Next Steps

1. **Quick Test** - Run without Docker: `./gradlew runOtel`
2. **Full Integration** - Set up Docker and use validation script
3. **Production Ready** - Test against real Grafana Cloud or Datadog endpoint
4. **Stress Test** - Increase TPS and duration in example runner

See **OTEL-TESTING.md** for comprehensive guide including troubleshooting and advanced scenarios.
