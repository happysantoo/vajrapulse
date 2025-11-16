# HTTP Load Test Example

This example demonstrates how to create a simple HTTP load test using VajraPulse with Java 21 virtual threads.

## What it does

- Sends HTTP GET requests to `https://httpbin.org/delay/0`
- Uses virtual threads for efficient I/O handling
- Runs at 100 TPS for 30 seconds (3,000 total requests)
- Reports latency percentiles and success/failure rates

## Quick Start

### Prerequisites

- Java 21 or later
- Built VajraPulse modules (run `./gradlew build` from project root)

### Build and Run

There are three ways to run this example:

#### Option 1: Using the Console Runner (Console Export)

```bash
# Build and run directly
./gradlew run
```

This uses the `HttpLoadTestRunner` class which leverages the high-level `MetricsPipeline` abstraction for minimal boilerplate and prints metrics to the console.

#### Option 1b: Using the OpenTelemetry Runner (OTLP Export)

Run the variant that exports metrics to an OpenTelemetry Collector:

```bash
./gradlew runOtel
```

Requirements:
1. Running OpenTelemetry Collector (default ports) – simplest via Docker:
   ```bash
   docker run --name otel-collector -p 4317:4317 -p 4318:4318 \
     -e OTEL_EXPORTER_OTLP_LOGS_ENABLED=false \
     otel/opentelemetry-collector:latest
   ```
2. Endpoint used: `http://localhost:4318` (HTTP protocol) – configured in `HttpLoadTestOtelRunner`.
3. Service/resource attributes applied: `environment=dev`, `example.type=http-load-test`, `team=platform`.

The OTLP exporter is automatically closed when the pipeline completes, ensuring final metrics are flushed before shutdown.

You can switch to gRPC by changing:
```java
    .protocol(Protocol.GRPC)
```
and (optionally) using port 4317.

#### Option 2: Using VajraPulse Worker CLI

```bash
# Build VajraPulse modules first (from project root)
cd ../..
./gradlew build

# Build the example
cd examples/http-load-test
./gradlew build

# Run using the worker CLI
java -cp "build/libs/http-load-test-1.0.0-SNAPSHOT.jar:../../vajrapulse-worker/build/libs/vajrapulse-worker-1.0.0-SNAPSHOT-all.jar" \
  com.vajrapulse.worker.VajraPulseWorker \
  com.example.http.HttpLoadTest \
  --mode static \
  --tps 100 \
  --duration 30s
```

#### Option 3: (Deprecated) Direct Task Main
Direct invocation via a task `main` method has been deprecated in favor of the `MetricsPipeline` or the worker CLI. Keep tests focused on task logic only.

## Code Walkthrough

### Task Implementation

```java
@VirtualThreads  // Use virtual threads for I/O operations
public class HttpLoadTest implements Task {
    private HttpClient client;
    
    @Override
    public void setup() {
        // Called once before test starts
        client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    }
    
    @Override
    public TaskResult execute() throws Exception {
        // Called for each iteration (100 times)
        HttpResponse<String> response = client.send(request, ...);
        
        if (response.statusCode() == 200) {
            return TaskResult.success(response.body());
        } else {
            return TaskResult.failure(new RuntimeException(...));
        }
    }
    
    @Override
    public void cleanup() {
        // Called once after test completes
    }
}
```

### Key Features

1. **@VirtualThreads** - Annotation tells Vajra to use virtual threads for this I/O-bound task
2. **setup()** - Initialize HttpClient once, reuse for all requests
3. **execute()** - Test logic executed repeatedly
4. **TaskResult** - Return Success or Failure explicitly

## Programmatic Configuration

The `HttpLoadTestRunner` now uses the `MetricsPipeline` for concise orchestration:

```java
HttpLoadTest task = new HttpLoadTest();
LoadPattern pattern = new StaticLoad(100.0, Duration.ofSeconds(30));

// Pipeline implements AutoCloseable for automatic cleanup
try (MetricsPipeline pipeline = MetricsPipeline.builder()
    .addExporter(new ConsoleMetricsExporter())       // final + live exports
    .withPeriodic(Duration.ofSeconds(5))             // live updates every 5s
    .build()) {                                      // creates MetricsCollector internally
    
    pipeline.run(task, pattern);
} // Automatic final export + cleanup
```

Internals handled by the pipeline:
1. Task setup/cleanup
2. Thread strategy annotation resolution
3. Rate-controlled submission
4. Metrics collection & aggregation
5. Optional periodic live snapshot export
6. Final snapshot export (guaranteed before close)
7. Automatic exporter cleanup (AutoCloseable exporters)

## OpenTelemetry Export Details

### Metrics Sent
- `vajrapulse.executions.total` – Counter
- `vajrapulse.executions.success` – Counter
- `vajrapulse.executions.failure` – Counter
- `vajrapulse.success.rate` – Gauge (0-100)
- `vajrapulse.latency.success` – Histogram (ms) with `percentile` attribute
- `vajrapulse.latency.failure` – Histogram (ms) with `percentile` attribute (if failures > 0)

### Custom Resource Attributes
Configured in the OTLP runner:
```java
resourceAttributes(Map.of(
  "environment", "dev",
  "example.type", "http-load-test",
  "team", "platform"
))
```
Use these for filtering and grouping in your observability backend.

### Switching Protocol
Default is gRPC. In the example we explicitly use HTTP:
```java
protocol(Protocol.HTTP)
```
Change to `Protocol.GRPC` when collector supports gRPC or for improved efficiency.

### Manual Endpoint Override
Update `.endpoint("http://collector-host:4318")` to point at remote collectors. For gRPC use typical port `4317`.

## Load Patterns via CLI

### Static Load
```bash
java -cp ... com.vajrapulse.worker.VajraPulseWorker \
  com.example.http.HttpLoadTest \
  --mode static \
  --tps 100 \
  --duration 5m
```

### Ramp-Up
```bash
java -cp ... com.vajrapulse.worker.VajraPulseWorker \
  com.example.http.HttpLoadTest \
  --mode ramp \
  --tps 200 \
  --ramp-duration 30s
```

### Ramp then Sustain
```bash
java -cp ... com.vajrapulse.worker.VajraPulseWorker \
  com.example.http.HttpLoadTest \
  --mode ramp-sustain \
  --tps 200 \
  --ramp-duration 30s \
  --duration 5m
```

## Expected Output

```
╔════════════════════════════════════════════════════════╗
║        VajraPulse HTTP Load Test Example              ║
╚════════════════════════════════════════════════════════╝

Configuration:
  Task:     HttpLoadTest
  Pattern:  Static Load
  TPS:      100
  Duration: 30 seconds
  Endpoint: https://httpbin.org/delay/0

Starting load test...

[Live metrics updates every 5 seconds...]

Load test completed!

========================================
HTTP Load Test Results
========================================
Total Executions:    3000
Successful:          2985 (99.5%)
Failed:              15 (0.5%)

Success Latency (ms):
  P50:  125.45
  P95:  245.67
  P99:  389.12

========================================
```

## Debugging and Manual Validation

### Enable Trace Logging

To see detailed per-request logs for manual validation of percentile calculations:

1. **Edit** `src/main/resources/logback.xml`
2. **Uncomment** this line:
   ```xml
   <logger name="com.vajrapulse.core.engine.TaskExecutor" level="TRACE"/>
   ```
3. **Run** the test with low TPS for manageable output:
   ```bash
   # Modify HttpLoadTestRunner.java to use lower TPS temporarily:
   # LoadPattern loadPattern = new StaticLoad(10.0, Duration.ofSeconds(10));
   ./gradlew run
   ```

### Trace Log Format

With TRACE logging enabled, you'll see output like:
```
2025-11-15 10:30:45.123 TRACE TaskExecutor - Iteration=0 Status=SUCCESS Duration=245678912ns (245.679ms)
2025-11-15 10:30:45.234 TRACE TaskExecutor - Iteration=1 Status=SUCCESS Duration=156234567ns (156.235ms)
2025-11-15 10:30:45.345 TRACE TaskExecutor - Iteration=2 Status=SUCCESS Duration=189456789ns (189.457ms)
...
```

### Manual Percentile Validation

1. **Extract durations** from trace logs:
   ```bash
   ./gradlew run 2>&1 | grep "TRACE TaskExecutor" | awk '{print $9}' | sed 's/ms)//' | sed 's/(//g' > durations.txt
   ```

2. **Sort and calculate** percentiles:
   ```bash
   # Sort durations
   sort -n durations.txt > sorted_durations.txt
   
   # Count total
   total=$(wc -l < sorted_durations.txt)
   
   # Calculate P50 (50th percentile)
   p50_line=$((total * 50 / 100))
   p50=$(sed -n "${p50_line}p" sorted_durations.txt)
   
   # Calculate P95 (95th percentile)
   p95_line=$((total * 95 / 100))
   p95=$(sed -n "${p95_line}p" sorted_durations.txt)
   
   # Calculate P99 (99th percentile)
   p99_line=$((total * 99 / 100))
   p99=$(sed -n "${p99_line}p" sorted_durations.txt)
   
   echo "Manual Calculations:"
   echo "P50: ${p50}ms"
   echo "P95: ${p95}ms"
   echo "P99: ${p99}ms"
   ```

3. **Compare** with VajraPulse reported metrics

⚠️ **WARNING**: TRACE logging generates massive output at high TPS! Only use for validation with:
- Low TPS (10-50 TPS)
- Short duration (10-30 seconds)
- Total requests < 1000

### Python Script for Validation

For easier validation, use this Python script:

```python
#!/usr/bin/env python3
import sys
import re

durations = []
with open(sys.argv[1] if len(sys.argv) > 1 else 'durations.txt', 'r') as f:
    for line in f:
        match = re.search(r'\((\d+\.\d+)ms\)', line)
        if match:
            durations.append(float(match.group(1)))

durations.sort()
n = len(durations)

print(f"Total requests: {n}")
print(f"P50: {durations[int(n * 0.50)]:.2f}ms")
print(f"P95: {durations[int(n * 0.95)]:.2f}ms")
print(f"P99: {durations[int(n * 0.99)]:.2f}ms")
```

Save as `validate_percentiles.py` and run:
```bash
./gradlew run 2>&1 | tee test_output.log
python3 validate_percentiles.py test_output.log
```

## Customization

To test your own HTTP endpoint:

1. Change the URI in `setup()`:
   ```java
   request = HttpRequest.newBuilder()
       .uri(URI.create("https://your-api.com/endpoint"))
       .build();
   ```

2. Add headers, authentication, POST body, etc.

3. Customize success criteria in `execute()`

## Performance

With virtual threads, this example can easily handle:
- **1,000+ TPS** on a typical laptop
- **10,000+ concurrent requests** with minimal memory overhead
- **Millions of requests** in a single test run
