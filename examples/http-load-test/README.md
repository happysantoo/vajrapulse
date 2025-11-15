# HTTP Load Test Example

This example demonstrates how to create a simple HTTP load test using Vajra with Java 21 virtual threads.

## What it does

- Sends HTTP GET requests to `https://httpbin.org/delay/0`
- Uses virtual threads for efficient I/O handling
- Runs at 10 TPS for 10 seconds (100 total requests)
- Reports latency percentiles and success/failure rates

## Quick Start

### Prerequisites

- Java 21 or later
- Built vajra-worker fat JAR

### Build and Run

```bash
# Build the example
./gradlew build

# Run the load test using the vajra-worker JAR
java -cp "build/libs/http-load-test-1.0.0-SNAPSHOT.jar:../../vajra-worker/build/libs/vajra-worker-1.0.0-SNAPSHOT-all.jar" \
  com.vajra.worker.VajraWorker \
  com.example.http.HttpLoadTest \
  --mode static \
  --tps 10 \
  --duration 10s
```

Or use the gradle task:

```bash
./gradlew runLoadTest
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

## Load Patterns

### Static Load
```bash
java -cp ... com.vajra.worker.VajraWorker \
  com.example.http.HttpLoadTest \
  --mode static \
  --tps 100 \
  --duration 5m
```

### Ramp-Up
```bash
java -cp ... com.vajra.worker.VajraWorker \
  com.example.http.HttpLoadTest \
  --mode ramp \
  --tps 200 \
  --ramp-duration 30s
```

### Ramp then Sustain
```bash
java -cp ... com.vajra.worker.VajraWorker \
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
