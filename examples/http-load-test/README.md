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
========================================
Load Test Results
========================================
Total Executions:    100
Successful:          98 (98.0%)
Failed:              2 (2.0%)

Success Latency (ms):
  P50:  125.45
  P95:  245.67
  P99:  389.12

========================================
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
