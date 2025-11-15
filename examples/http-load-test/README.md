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

#### Option 1: Using the Runner (Recommended)

```bash
# Build and run directly
./gradlew run
```

This uses the `HttpLoadTestRunner` class which programmatically configures the load test.

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

#### Option 3: Using the Task's Main Method

```bash
# Run the main method in HttpLoadTest directly
./gradlew build
java -cp "build/libs/http-load-test-1.0.0-SNAPSHOT.jar:../../vajrapulse-core/build/libs/vajrapulse-core-1.0.0-SNAPSHOT.jar:../../vajrapulse-api/build/libs/vajrapulse-api-1.0.0-SNAPSHOT.jar:../../vajrapulse-exporter-console/build/libs/vajrapulse-exporter-console-1.0.0-SNAPSHOT.jar" \
  com.example.http.HttpLoadTest
```

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

The `HttpLoadTestRunner` shows how to configure and run tests programmatically:

```java
// Create task
HttpLoadTest task = new HttpLoadTest();

// Configure load pattern
LoadPattern loadPattern = new StaticLoad(100.0, Duration.ofSeconds(30));

// Create metrics collector
MetricsCollector metricsCollector = new MetricsCollector();

// Run test
try (ExecutionEngine engine = new ExecutionEngine(task, loadPattern, metricsCollector)) {
    engine.run();
}

// Export results
AggregatedMetrics metrics = metricsCollector.snapshot();
ConsoleMetricsExporter exporter = new ConsoleMetricsExporter();
exporter.export("Results", metrics);
```

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

1. **Edit** `src/main/resources/simplelogger.properties`
2. **Uncomment** this line:
   ```properties
   org.slf4j.simpleLogger.log.com.vajrapulse.core.engine.TaskExecutor=TRACE
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
