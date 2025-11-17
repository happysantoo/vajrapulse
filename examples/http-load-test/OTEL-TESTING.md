# OpenTelemetry Integration Testing Guide

This directory contains everything needed to test the HTTP Load Test example with a real OpenTelemetry Collector.

## Overview

The test setup includes:
- **Docker Compose**: Runs OpenTelemetry Collector with metrics pipeline
- **Collector Config**: Receives OTLP metrics and exports to logging, file, and Prometheus
- **Validation Script**: Verifies metrics were received by the collector
- **Example Runner**: VajraPulse OTLP example that sends metrics to collector

## Prerequisites

- Docker and Docker Compose installed
- Java 21
- VajraPulse built (`./gradlew build` from project root)

## Quick Start

### 1. Start OpenTelemetry Collector

```bash
cd examples/http-load-test

# Start collector in background (or use separate terminal)
docker-compose up -d

# Verify it's healthy
docker-compose ps
# Should show: otel-collector ... (healthy)

# Wait for health check to pass (usually 10-15s)
sleep 15
```

### 2. Run the OTLP Example

```bash
cd examples/http-load-test

# Run the OTEL variant (30 second test at 100 TPS)
./gradlew runOtel
```

You should see:
- "OpenTelemetry exporter initialized"
- Load test metrics output
- "Successfully exported metrics to OTLP"
- Final metrics summary

### 3. Validate Metrics Were Received

```bash
cd examples/http-load-test

# Make the validation script executable (first time only)
chmod +x validate-otel-metrics.sh

# Run validation (default 30s timeout)
./validate-otel-metrics.sh

# Or with custom timeout
./validate-otel-metrics.sh 60
```

Expected output:
```
🔍 Validating OpenTelemetry metrics reception...
✅ Metrics file found
📊 Checking for expected metrics...
   ✅ Found: vajrapulse.executions.total
   ✅ Found: vajrapulse.executions.success
   ✅ Found: vajrapulse.executions.failure
   ✅ Found: vajrapulse.success.rate
   ✅ Found: vajrapulse.latency.success
   ✅ Found: vajrapulse.latency.failure

✅ SUCCESS: All expected metrics received!
```

### 4. View Collected Metrics (Optional)

```bash
# See metrics in JSON format
cat /tmp/otel-collector-data/metrics.json | jq . | head -50

# Or view Prometheus format metrics
curl http://localhost:8888/metrics | grep vajrapulse
```

### 5. Stop the Collector

```bash
docker-compose down

# Clean up volumes (optional)
docker-compose down -v
```

## Test Scenarios

### Scenario 1: Verify gRPC Protocol (Default)

The default example uses HTTP protocol for broader compatibility. To test gRPC:

**Modify `HttpLoadTestOtelRunner.java`**:
```java
.protocol(Protocol.GRPC)  // Change from Protocol.HTTP
// And optionally use port 4317:
.endpoint("http://localhost:4317")
```

Then run:
```bash
./gradlew runOtel
./validate-otel-metrics.sh
```

### Scenario 2: Test with Custom Resource Attributes

**Modify `HttpLoadTestOtelRunner.java`** to add more attributes:
```java
.resourceAttributes(Map.of(
    "service.name", "checkout-flow-test",
    "service.version", "2.1.0",
    "environment", "staging",
    "region", "us-west-2",
    "deployment.id", "deploy-" + System.currentTimeMillis()
))
```

Then verify attributes appear in collected metrics:
```bash
./validate-otel-metrics.sh
cat /tmp/otel-collector-data/metrics.json | jq '.resourceMetrics[0].resource.attributes'
```

### Scenario 3: Different Load Patterns

Modify `HttpLoadTestOtelRunner.java` to use different patterns:

```java
// Ramp-up pattern
LoadPattern loadPattern = new RampUpLoad(200.0, Duration.ofSeconds(30));

// Ramp then sustain
LoadPattern loadPattern = new RampUpToMaxLoad(
    200.0, 
    Duration.ofSeconds(30), 
    Duration.ofMinutes(5)
);
```

### Scenario 4: High-Load Stress Test

Modify for higher TPS to verify exporter handles load:
```java
LoadPattern loadPattern = new StaticLoad(500.0, Duration.ofSeconds(60));
```

Monitor collector resource usage:
```bash
docker stats otel-collector
```

### Scenario 5: Network Failure Recovery

Stop collector mid-test:
```bash
# Terminal 1: Start collector
docker-compose up

# Terminal 2: Start test
./gradlew runOtel

# Terminal 3 (while test running): Stop collector
docker-compose stop

# Observe:
# - Exporter logs connection errors but continues
# - No exceptions thrown
# - Test completes successfully
# - Restart collector to capture final metrics
```

## Troubleshooting

### Collector won't start

```bash
# Check logs
docker-compose logs otel-collector

# Verify config file exists and is valid
ls -la otel-collector-config.yml

# Try pulling fresh image
docker pull otel/opentelemetry-collector-contrib:0.104.0
docker-compose down -v
docker-compose up
```

### Metrics file not created

```bash
# Verify collector is running
docker-compose ps

# Check health status
docker-compose exec otel-collector curl http://localhost:13133/healthz

# Check collector logs for errors
docker-compose logs --follow otel-collector

# Verify metrics directory permissions
ls -la /tmp/otel-collector-data/
```

### Metrics file is empty

```bash
# Verify VajraPulse actually sent metrics
docker-compose logs otel-collector | grep "received"

# Check if exporter metrics show up (may take a few seconds)
sleep 5
./validate-otel-metrics.sh

# View raw logs from collector
docker-compose logs --follow otel-collector
```

### Can't connect to collector from example

```bash
# Verify collector is listening on both protocols
docker-compose exec otel-collector netstat -tlnp | grep 4317
docker-compose exec otel-collector netstat -tlnp | grep 4318

# Test connectivity from host
curl http://localhost:4318/healthz
telnet localhost 4318

# Check Docker network
docker network inspect http-load-test_otel-network
```

## Performance Baseline

Typical test results on a modern laptop:

| Metric | Value |
|--------|-------|
| TPS | 100 |
| Duration | 30s |
| Total Requests | ~3,000 |
| Success Rate | 99-100% |
| P95 Latency | 100-500ms |
| Metrics Export | < 1s |
| Collector CPU | 10-20% |
| Collector Memory | 50-100MB |

## CI/CD Integration

To use this in automated testing:

```yaml
# Example GitHub Actions workflow
- name: Start OpenTelemetry Collector
  run: |
    cd examples/http-load-test
    docker-compose up -d
    sleep 15

- name: Run OTLP Example Test
  run: |
    cd examples/http-load-test
    ./gradlew runOtel --no-daemon

- name: Validate Metrics
  run: |
    cd examples/http-load-test
    chmod +x validate-otel-metrics.sh
    ./validate-otel-metrics.sh 60

- name: Cleanup
  if: always()
  run: |
    cd examples/http-load-test
    docker-compose down -v
```

## Next Steps

- Test with different OTLP backends (Grafana Cloud, Datadog, etc.)
- Add tracing export (traces not yet supported)
- Implement log export (logs not yet supported)
- Create performance benchmarks with different load patterns
- Add Prometheus scraping validation
- Set up alerts for metric anomalies
