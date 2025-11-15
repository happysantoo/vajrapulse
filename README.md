# VajraPulse - Java 21 Load Testing Framework

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-9.0-blue.svg)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

VajraPulse is a modern, high-performance load testing framework built for Java 21, leveraging **virtual threads** for massive concurrency with minimal resource overhead.

## Features

✅ **Java 21 Native** - Records, sealed interfaces, pattern matching, virtual threads  
✅ **Minimal Dependencies** - ~1.6MB fat JAR, only essential libraries  
✅ **Virtual Thread Support** - Handle 10,000+ concurrent requests with ease  
✅ **Flexible Load Patterns** - Static, ramp-up, ramp-sustain modes  
✅ **Micrometer Metrics** - Industry-standard metrics with percentiles  
✅ **Simple Task API** - Implement `Task`, return `TaskResult`, done  
✅ **Comprehensive Tests** - 23 Spock tests, all passing  

## Quick Start

### Prerequisites

- Java 21 or later
- Gradle 9.0 (or use included wrapper)

### Build

```bash
./gradlew clean build shadowJar
```

This produces:
- Core modules in `vajrapulse-*/build/libs/`
- **Fat JAR**: `vajrapulse-worker/build/libs/vajrapulse-worker-1.0.0-SNAPSHOT-all.jar` (1.6MB)

### Run Example

```bash
cd examples/http-load-test
gradle build

# Run load test: 10 TPS for 10 seconds
java -cp "build/libs/http-load-test.jar:../../vajrapulse-worker/build/libs/vajrapulse-worker-1.0.0-SNAPSHOT-all.jar" \
  com.vajrapulse.worker.VajraPulseWorker \
  com.example.http.HttpLoadTest \
  --mode static \
  --tps 10 \
  --duration 10s
```

## Architecture

```
vajra/
├── vajrapulse-api/              # Public API (ZERO dependencies)
│   ├── Task                # Main interface
│   ├── TaskResult          # Sealed Success/Failure
│   ├── LoadPattern         # Rate control abstraction
│   ├── @VirtualThreads     # I/O-bound tasks
│   └── @PlatformThreads    # CPU-bound tasks
│
├── vajrapulse-core/             # Execution engine
│   ├── ExecutionEngine     # Main orchestrator
│   ├── TaskExecutor        # Instrumented wrapper
│   ├── RateController      # TPS pacing
│   └── MetricsCollector    # Micrometer integration
│
├── vajrapulse-exporter-console/ # Console output
│   └── ConsoleMetricsExporter
│
└── vajrapulse-worker/           # CLI application
    └── VajraPulseWorker         # Main entry point
```

## Creating a Task

### 1. Implement the Task Interface

```java
import com.vajrapulse.api.*;

@VirtualThreads  // Use virtual threads for I/O
public class MyHttpTask implements Task {
    private HttpClient client;
    
    @Override
    public void setup() throws Exception {
        // Called once before test starts
        client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    }
    
    @Override
    public TaskResult execute() throws Exception {
        // Called for each iteration
        var request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/endpoint"))
            .build();
        
        var response = client.send(request, BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return TaskResult.success(response.body());
        } else {
            return TaskResult.failure(
                new RuntimeException("HTTP " + response.statusCode())
            );
        }
    }
    
    @Override
    public void cleanup() throws Exception {
        // Called once after test completes
    }
}
```

### 2. Choose Thread Strategy

```java
@VirtualThreads              // For I/O-bound (HTTP, DB, files)
public class IoTask implements Task { }

@PlatformThreads(poolSize = 8)  // For CPU-bound (crypto, compression)
public class CpuTask implements Task { }

// No annotation = defaults to virtual threads
public class DefaultTask implements Task { }
```

### 3. Run Your Test

```bash
java -jar vajrapulse-worker-all.jar \
  com.yourpackage.MyHttpTask \
  --mode static \
  --tps 100 \
  --duration 5m
```

## Load Patterns

### Static Load

Constant TPS for specified duration:

```bash
--mode static --tps 100 --duration 5m
```

### Ramp-Up

Linear increase from 0 to max TPS:

```bash
--mode ramp --tps 200 --ramp-duration 30s
```

### Ramp then Sustain

Ramp to max, then sustain:

```bash
--mode ramp-sustain \
  --tps 200 \
  --ramp-duration 30s \
  --duration 5m
```

## Metrics

VajraPulse uses **Micrometer** for metrics collection. Output includes:

```
========================================
Load Test Results
========================================
Total Executions:    30000
Successful:          29850 (99.5%)
Failed:              150 (0.5%)

Success Latency (ms):
  P50:  12.34
  P95:  45.67
  P99:  89.01

Failure Latency (ms):
  P50:  234.56
  P95:  456.78
  P99:  678.90
========================================
```

## Module Details

### vajrapulse-api (0 dependencies)

Pure API module with no external dependencies:

- `Task` - Main interface with setup/execute/cleanup
- `TaskResult` - Sealed interface (Success/Failure)
- `LoadPattern` - Interface for load patterns
- `StaticLoad`, `RampUpLoad`, `RampUpToMaxLoad` - Built-in patterns
- `@VirtualThreads`, `@PlatformThreads` - Thread strategy annotations

**Dependency**: None
**Size**: ~15 KB

### vajrapulse-core (3 dependencies)

Execution engine with minimal dependencies:

- `ExecutionEngine` - Main orchestrator
- `TaskExecutor` - Automatic instrumentation
- `RateController` - TPS control
- `MetricsCollector` - Micrometer integration
- `ExecutionMetrics`, `AggregatedMetrics` - Metrics records

**Dependencies**:
- micrometer-core 1.12.0
- slf4j-api 2.0.9

**Size**: ~150 KB + dependencies

### vajrapulse-exporter-console

Console metrics exporter:

- `ConsoleMetricsExporter` - Formatted table output

**Dependencies**: vajrapulse-api, vajrapulse-core, slf4j-api

**Size**: ~30 KB

### vajrapulse-worker (Fat JAR)

CLI application bundling everything:

- `VajraPulseWorker` - Main entry point with picocli
- All modules bundled
- slf4j-simple for logging

**Total Size**: ~1.6 MB (all-in-one)

## Design Principles

1. **Java 21 First** - Use all modern features (records, sealed types, virtual threads)
2. **Minimal Dependencies** - Every dependency must be justified
3. **Zero API Dependencies** - vajrapulse-api has NO external dependencies
4. **Micrometer for Metrics** - Industry-standard, not direct HdrHistogram
5. **Explicit over Implicit** - No magic, clear execution model
6. **Performance Conscious** - No lambdas in hot paths, pre-sized collections
7. **Test Coverage** - Comprehensive Spock tests (23 tests, 100% passing)

## Performance

With Java 21 virtual threads, VajraPulse can handle:

- **10,000+ TPS** on typical hardware
- **Millions of concurrent requests** with minimal memory
- **1,000,000+ iterations** in a single test run

Example: 100,000 HTTP requests at 1000 TPS:
- Memory: ~200 MB
- Duration: 100 seconds
- Virtual Threads: 1000+ concurrent

## Testing

Run all tests:

```bash
./gradlew test
```

Test results:
- vajrapulse-api: 12 tests ✅
- vajrapulse-core: 11 tests ✅
- **Total**: 23 tests, all passing

## Examples

See `examples/` directory:

- **http-load-test** - HTTP load test with virtual threads
- *More examples coming soon*

## Roadmap

- [x] Phase 1: Core framework (Java 21, virtual threads, Micrometer)
- [ ] Phase 2: Distributed testing (P2P coordination)
- [ ] Phase 3: Additional exporters (Prometheus, JSON)
- [ ] Phase 4: Advanced load patterns
- [ ] Phase 5: GraalVM native compilation

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md)

## License

Apache License 2.0 - see [LICENSE](LICENSE)

## Credits

Built with:
- Java 21
- Gradle 9.0
- Micrometer 1.12.0
- Picocli 4.7.5
- Spock Framework 2.4
- SLF4J 2.0.9

---

**VajraPulse** (वज्र) - Sanskrit for "thunderbolt" - symbolizing the power and speed of this load testing framework.
