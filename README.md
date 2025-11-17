# VajraPulse – Java 21 Load Testing

<p align="center"><img src="./vajrapulse_logo.png" alt="VajraPulse Logo" width="360"/></p>

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Gradle](https://img.shields.io/badge/Gradle-9.0-blue.svg)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.vajrapulse/vajrapulse-core.svg)](https://search.maven.org/search?q=g:com.vajrapulse)
[![JitPack](https://jitpack.io/v/happysantoo/vajrapulse.svg)](https://jitpack.io/#happysantoo/vajrapulse)

High-concurrency load testing using Java 21 virtual threads. Minimal dependencies, clear API.

Pre-1.0: breaking changes allowed (clean architecture over compatibility).

## Highlights (0.9)
• Patterns: static, ramp, ramp-sustain, step, spike, sine
• Auto/override `run_id` tagging (metrics & traces)
• Micrometer + optional OpenTelemetry (OTLP)
• YAML/JSON config + env overrides
• Virtual vs platform thread annotations
• Performance harness
• ≥90% coverage gate (Jacoco)
• Console + OTEL exporters
• Zero-dependency API module
• Spock BDD tests

## Features

✅ Java 21 features (records, sealed types, virtual threads)
✅ Minimal dependencies (~1.6MB fat JAR)
✅ Flexible load patterns (static, ramp, sustain, step, spike, sine)
✅ Micrometer metrics + percentiles
✅ Simple Task API (implement `Task`)
✅ ≥90% coverage enforced

## Installation

### Maven Central (Recommended)

Gradle (Kotlin DSL):
```kotlin
dependencies {
  implementation("com.vajrapulse:vajrapulse-core:0.9.1")
  implementation("com.vajrapulse:vajrapulse-worker:0.9.1") // For CLI runnable
  // Optional exporters
  implementation("com.vajrapulse:vajrapulse-exporter-console:0.9.1")
  implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry:0.9.1")
}
```

Gradle (Groovy DSL):
```groovy
dependencies {
  implementation 'com.vajrapulse:vajrapulse-core:0.9.1'
  implementation 'com.vajrapulse:vajrapulse-worker:0.9.1'
}
```

Maven:
```xml
<dependency>
  <groupId>com.vajrapulse</groupId>
  <artifactId>vajrapulse-core</artifactId>
  <version>0.9.1</version>
</dependency>
<dependency>
  <groupId>com.vajrapulse</groupId>
  <artifactId>vajrapulse-worker</artifactId>
  <version>0.9.1</version>
</dependency>
```

### JitPack (Latest tag, immediate)

If you want to consume the latest Git tag via JitPack now:

Gradle (Groovy DSL):
```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies {
  implementation 'com.github.happysantoo.vajrapulse:vajrapulse-core:v0.9.1'
  // optional exporters
  implementation 'com.github.happysantoo.vajrapulse:vajrapulse-exporter-opentelemetry:v0.9.1'
}
```

Gradle (Kotlin DSL):
```kotlin
repositories { maven { url = uri("https://jitpack.io") } }
dependencies {
  implementation("com.github.happysantoo.vajrapulse:vajrapulse-core:v0.9.1")
  // optional exporters
  implementation("com.github.happysantoo.vajrapulse:vajrapulse-exporter-opentelemetry:v0.9.1")
}
```

Maven:
```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
<dependencies>
  <dependency>
    <groupId>com.github.happysantoo.vajrapulse</groupId>
    <artifactId>vajrapulse-core</artifactId>
    <version>v0.9.1</version>
  </dependency>
</dependencies>
```

## Quick Start

Requires: Java 21+, Gradle 9 (wrapper included).

### Build

```bash
./gradlew clean build shadowJar
```

Produces module JARs + fat worker JAR under `vajrapulse-worker/build/libs/`.

### Run Static Pattern

```bash
cd examples/http-load-test
gradle build

# Run load test: 10 TPS for 10 seconds
java -cp "build/libs/http-load-test.jar:../../vajrapulse-worker/build/libs/vajrapulse-worker-0.9.1-all.jar" \
  com.vajrapulse.worker.VajraPulseWorker \
  com.example.http.HttpLoadTest \
  --mode static \
  --tps 10 \
  --duration 10s \
  --run-id demo-static

```

### Run Sine Pattern

```bash
java -cp "build/libs/http-load-test.jar:../../vajrapulse-worker/build/libs/vajrapulse-worker-0.9.1-all.jar" \
  com.vajrapulse.worker.VajraPulseWorker \
  com.example.http.HttpLoadTest \
  --mode sine \
  --mean-rate 150 \
  --amplitude 75 \
  --period 60s \
  --duration 5m \
  --run-id demo-sine
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
Implement `Task` with optional setup/cleanup and use virtual threads for I/O:

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

Thread strategy annotations:

```java
@VirtualThreads              // For I/O-bound (HTTP, DB, files)
public class IoTask implements Task { }

@PlatformThreads(poolSize = 8)  // For CPU-bound (crypto, compression)
public class CpuTask implements Task { }

// No annotation = defaults to virtual threads
public class DefaultTask implements Task { }
```

Run test:

```bash
java -jar vajrapulse-worker-all.jar \
  com.yourpackage.MyHttpTask \
  --mode static \
  --tps 100 \
  --duration 5m
```

## Load Patterns

### Static

Constant TPS for specified duration:

```bash
--mode static --tps 100 --duration 5m
```

### Ramp-Up

Linear increase from 0 to max TPS:

```bash
--mode ramp --tps 200 --ramp-duration 30s
```

### Ramp-Sustain

Ramp to max, then sustain:

```bash
--mode ramp-sustain \
  --tps 200 \
  --ramp-duration 30s \
  --duration 5m
```
### Step

Discrete phases with different target TPS values:

```bash
--mode step \
  --steps "50@30s,200@1m,500@2m,100@30s"
```

### Spike

Baseline TPS plus periodic spikes:

```bash
--mode spike \
  --base-rate 100 \
  --spike-rate 800 \
  --spike-interval 60s \
  --spike-duration 5s \
  --duration 10m
```

### Sine

Smooth oscillation around a mean:

```bash
--mode sine \
  --mean-rate 300 \
  --amplitude 150 \
  --period 120s \
  --duration 15m
```

## Metrics & Observability

VajraPulse uses **Micrometer** for metrics; optional OpenTelemetry exporter attaches `run_id`, task identity, and pattern info. Output includes:

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

## Modules (Summary)

### API (no deps)

Pure API module with no external dependencies:

- `Task` - Main interface with setup/execute/cleanup
- `TaskResult` - Sealed interface (Success/Failure)
- `LoadPattern` - Interface for load patterns
- `StaticLoad`, `RampUpLoad`, `RampUpToMaxLoad` - Built-in patterns
- `@VirtualThreads`, `@PlatformThreads` - Thread strategy annotations

**Dependency**: None
**Size**: ~15 KB

### Core

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

### Console Exporter

Console metrics exporter:

- `ConsoleMetricsExporter` - Formatted table output

**Dependencies**: vajrapulse-api, vajrapulse-core, slf4j-api

**Size**: ~30 KB

### Worker CLI

CLI application bundling everything:

- `VajraPulseWorker` - Main entry point with picocli
- All modules bundled
- slf4j-simple for logging

**Total Size**: ~1.6 MB (all-in-one)

## Principles

1. **Java 21 First** - Use all modern features (records, sealed types, virtual threads)
2. **Minimal Dependencies** - Every dependency must be justified
3. **Zero API Dependencies** - vajrapulse-api has NO external dependencies
4. **Micrometer for Metrics** - Industry-standard, not direct HdrHistogram
5. **Explicit over Implicit** - No magic, clear execution model
6. **Performance Conscious** - No lambdas in hot paths, pre-sized collections
7. **Test Coverage** - Comprehensive Spock tests (23 tests, 100% passing)

## Performance

Handles high TPS with virtual threads:

- **10,000+ TPS** on typical hardware
- **Millions of concurrent requests** with minimal memory
- **1,000,000+ iterations** in a single test run

Harness example (100k HTTP @1000 TPS):
- Memory: ~200 MB
- Duration: 100 seconds
- Virtual Threads: 1000+ concurrent

## Testing

Run all tests:

```bash
./gradlew test
```

Coverage gate ensures ≥90%; run `./gradlew test`.

## Examples

See `examples/` directory:

- **http-load-test** - HTTP load test with virtual threads
- *More examples coming soon*

## Roadmap (Excerpt)

- [x] Core + Patterns + Observability (0.9 scope)
- [ ] Distributed execution layer
- [ ] Additional exporters (Prometheus / JSON / CSV)
- [ ] Adaptive patterns (feedback-based)
- [ ] Native image (GraalVM) validation
- [ ] Scenario scripting DSL

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

**VajraPulse** (वज्र) – "thunderbolt". Pre‑1.0: expect iteration.
