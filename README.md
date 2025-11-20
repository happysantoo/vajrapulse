# VajraPulse ⚡

<p align="center"><img src="./vajrapulse_logo.png" alt="VajraPulse Logo" width="360"/></p>

<p align="center">
  <strong>High-performance load testing framework built on Java 21 virtual threads</strong>
</p>

<p align="center">
  <a href="https://openjdk.org/projects/jdk/21/"><img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21"></a>
  <a href="https://gradle.org/"><img src="https://img.shields.io/badge/Gradle-9.0-blue.svg" alt="Gradle 9.0"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="Apache License 2.0"></a>
  <a href="https://search.maven.org/search?q=g:com.vajrapulse"><img src="https://img.shields.io/maven-central/v/com.vajrapulse/vajrapulse-core.svg" alt="Maven Central"></a>
  <a href="https://jitpack.io/#happysantoo/vajrapulse"><img src="https://jitpack.io/v/happysantoo/vajrapulse.svg" alt="JitPack"></a>
</p>

---

## Why VajraPulse?

**VajraPulse** makes load testing simple, fast, and resource-efficient. Built on Java 21's virtual threads, it can handle **10,000+ requests per second** with minimal memory overhead—perfect for testing APIs, databases, message queues, and any I/O-bound service.

### Key Benefits

- ⚡ **Massive Concurrency**: Virtual threads enable millions of concurrent operations with minimal memory
- 🎯 **Simple API**: Implement one interface (`Task`) and you're ready to test
- 📊 **Rich Metrics**: Built-in latency percentiles, queue depth tracking, and OpenTelemetry support
- 🔄 **Flexible Patterns**: 6 load patterns (static, ramp, step, spike, sine, ramp-sustain)
- 📦 **Minimal Dependencies**: ~1.6MB fat JAR, zero-dependency API module
- 🚀 **Production Ready**: OpenTelemetry integration, comprehensive metrics, graceful shutdown

> 📊 **Want to see how VajraPulse compares to JMeter, Gatling, and BlazeMeter?** Check out our [comprehensive comparison guide](COMPARISON.md) covering architecture, performance, enterprise scalability, and real-world use cases.

---

## Quick Start

### 1. Add Dependency

**Gradle (Kotlin DSL)** - Using BOM (Recommended):
```kotlin
dependencies {
    implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))
    implementation("com.vajrapulse:vajrapulse-core")
    implementation("com.vajrapulse:vajrapulse-worker") // For CLI
}
```

**Maven** - Using BOM:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.vajrapulse</groupId>
            <artifactId>vajrapulse-bom</artifactId>
            <version>0.9.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.vajrapulse</groupId>
        <artifactId>vajrapulse-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.vajrapulse</groupId>
        <artifactId>vajrapulse-worker</artifactId>
    </dependency>
</dependencies>
```

### 2. Create Your First Test

```java
import com.vajrapulse.api.*;
import java.net.http.*;

@VirtualThreads  // Use virtual threads for I/O-bound tasks
public class ApiLoadTest implements Task {
    private HttpClient client;
    
    @Override
    public void setup() throws Exception {
        client = HttpClient.newHttpClient();
    }
    
    @Override
    public TaskResult execute() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/users"))
            .GET()
            .build();
        
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return TaskResult.success(response.body());
        } else {
            return TaskResult.failure(
                new RuntimeException("HTTP " + response.statusCode())
            );
        }
    }
}
```

### 3. Run It

**CLI (Recommended for quick tests):**
```bash
java -jar vajrapulse-worker-0.9.3-all.jar \
  com.example.ApiLoadTest \
  --mode static \
  --tps 100 \
  --duration 5m
```

**Programmatic:**
```java
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.api.*;

Task task = new ApiLoadTest();
LoadPattern pattern = new StaticLoad(100.0, Duration.ofMinutes(5));
MetricsCollector collector = new MetricsCollector();

AggregatedMetrics metrics = ExecutionEngine.execute(task, pattern, collector);

System.out.println("Success Rate: " + metrics.successRate() + "%");
System.out.println("P95 Latency: " + metrics.successPercentiles().get(0.95) + " ms");
```

---

## Core Features

### 🎯 Simple Task API

Implement the `Task` interface with three optional methods:

- **`setup()`** - Initialize resources (HTTP clients, DB connections, etc.)
- **`execute()`** - Your test logic (called repeatedly)
- **`cleanup()`** - Clean up resources

The framework handles timing, metrics, error handling, and thread management automatically.

### ⚡ Virtual Threads by Default

VajraPulse uses Java 21 virtual threads for I/O-bound tasks, enabling massive concurrency:

```java
@VirtualThreads  // For HTTP, DB, file I/O
public class IoTask implements Task { }

@PlatformThreads(poolSize = 8)  // For CPU-intensive work
public class CpuTask implements Task { }
```

**Performance**: 10,000+ TPS on typical hardware, millions of concurrent requests with minimal memory.

### 📊 Six Load Patterns

Choose the pattern that matches your testing scenario:

| Pattern | Use Case | Example |
|---------|----------|---------|
| **Static** | Baseline performance | `--mode static --tps 100 --duration 5m` |
| **Ramp-Up** | Cold start / autoscaler warmup | `--mode ramp --tps 500 --ramp-duration 30s` |
| **Ramp-Sustain** | Sustained pressure after ramp | `--mode ramp-sustain --tps 200 --ramp-duration 30s --duration 5m` |
| **Step** | Phased testing | `--mode step --steps "50@30s,200@1m,500@2m"` |
| **Spike** | Burst absorption testing | `--mode spike --base-rate 100 --spike-rate 800 --spike-interval 60s` |
| **Sine** | Smooth oscillation | `--mode sine --mean-rate 300 --amplitude 150 --period 120s` |

### 📈 Comprehensive Metrics

Built-in metrics collection with Micrometer:

- **Latency Percentiles**: P50, P95, P99 for success and failure cases
- **Queue Depth Tracking**: Monitor pending executions (new in 0.9.3)
- **TPS Metrics**: Request TPS, Success TPS, Failure TPS
- **OpenTelemetry Export**: Full OTLP support for integration with observability platforms

**Example Output:**
```
========================================
Load Test Results
========================================
Total Executions:    30,000
Successful:          29,850 (99.5%)
Failed:              150 (0.5%)

Success Latency (ms):
  P50:  12.34
  P95:  45.67
  P99:  89.01

Queue Metrics:
  Current Size:       5
  Wait Time P95:     2.34 ms

Request TPS:         100.0
Success TPS:         99.5
========================================
```

### 🔍 Observability Integration

**OpenTelemetry Support:**
- Automatic metrics export via OTLP
- Distributed tracing support
- `run_id` tagging for test correlation
- Compatible with Grafana, Prometheus, Jaeger, and more

**Console Exporter:**
- Human-readable formatted output
- Real-time metrics during test execution
- Custom percentile configuration

### 🏗️ Modular Architecture

**Zero-Dependency API Module:**
- `vajrapulse-api` has **zero external dependencies**
- Clean separation of concerns
- Easy to extend and integrate

**Minimal Core:**
- Only Micrometer and SLF4J as dependencies
- ~150 KB core module
- ~1.6 MB fat JAR (all-in-one)

---

## Installation

### Maven Central (Recommended)

**Using BOM** - Manage all module versions in one place:

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))
    implementation("com.vajrapulse:vajrapulse-core")
    implementation("com.vajrapulse:vajrapulse-worker")
    // Optional exporters
    implementation("com.vajrapulse:vajrapulse-exporter-console")
    implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry")
}
```

**Gradle (Groovy DSL):**
```groovy
dependencies {
    implementation platform('com.vajrapulse:vajrapulse-bom:0.9.3')
    implementation 'com.vajrapulse:vajrapulse-core'
    implementation 'com.vajrapulse:vajrapulse-worker'
}
```

**Maven:**
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.vajrapulse</groupId>
            <artifactId>vajrapulse-bom</artifactId>
            <version>0.9.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.vajrapulse</groupId>
        <artifactId>vajrapulse-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.vajrapulse</groupId>
        <artifactId>vajrapulse-worker</artifactId>
    </dependency>
</dependencies>
```

**Without BOM** - Specify versions individually:
```kotlin
dependencies {
    implementation("com.vajrapulse:vajrapulse-core:0.9.3")
    implementation("com.vajrapulse:vajrapulse-worker:0.9.3")
}
```

### Requirements

- **Java 21+** (required for virtual threads)
- **Gradle 9.0+** or **Maven 3.6+**

---

## Usage Examples

### HTTP API Testing

```java
@VirtualThreads
public class RestApiTest implements Task {
    private HttpClient client;
    
    @Override
    public void setup() {
        client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    }
    
    @Override
    public TaskResult execute() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/data"))
            .header("Authorization", "Bearer " + getToken())
            .POST(HttpRequest.BodyPublishers.ofString("{\"key\":\"value\"}"))
            .build();
        
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200 
            ? TaskResult.success(response.body())
            : TaskResult.failure(new RuntimeException("HTTP " + response.statusCode()));
    }
}
```

### Database Load Testing

```java
@VirtualThreads
public class DatabaseTest implements Task {
    private Connection connection;
    
    @Override
    public void setup() throws SQLException {
        connection = DriverManager.getConnection("jdbc:postgresql://localhost/db", "user", "pass");
    }
    
    @Override
    public TaskResult execute() throws Exception {
        try (var stmt = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            stmt.setInt(1, randomUserId());
            var rs = stmt.executeQuery();
            return rs.next() 
                ? TaskResult.success(rs.getString("name"))
                : TaskResult.failure(new RuntimeException("User not found"));
        }
    }
    
    @Override
    public void cleanup() throws SQLException {
        if (connection != null) connection.close();
    }
}
```

### Message Queue Testing

```java
@VirtualThreads
public class KafkaProducerTest implements Task {
    private KafkaProducer<String, String> producer;
    
    @Override
    public void setup() {
        var props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }
    
    @Override
    public TaskResult execute() throws Exception {
        var record = new ProducerRecord<>("test-topic", "key", "value");
        var future = producer.send(record);
        var metadata = future.get(5, TimeUnit.SECONDS);
        return TaskResult.success(metadata.topic());
    }
}
```

---

## Load Patterns in Detail

### Static Load
Constant TPS for the entire duration. Best for baseline performance measurement.

```bash
--mode static --tps 100 --duration 5m
```

### Ramp-Up Load
Linear increase from 0 to target TPS. Perfect for observing cold starts and autoscaler behavior.

```bash
--mode ramp --tps 500 --ramp-duration 30s
```

### Ramp-Up to Max Load
Ramp to target TPS, then sustain. Tests resource contention after stabilization.

```bash
--mode ramp-sustain --tps 200 --ramp-duration 30s --duration 5m
```

### Step Load
Discrete phases with different TPS values. Great for phased capacity testing.

```bash
--mode step --steps "50@30s,200@1m,500@2m,100@30s"
```

### Spike Load
Baseline TPS with periodic spikes. Tests burst absorption and queue behavior.

```bash
--mode spike \
  --base-rate 100 \
  --spike-rate 800 \
  --spike-interval 60s \
  --spike-duration 5s \
  --duration 10m
```

### Sine Wave Load
Smooth oscillation around a mean. Reveals latency drift and GC sensitivity.

```bash
--mode sine \
  --mean-rate 300 \
  --amplitude 150 \
  --period 120s \
  --duration 15m
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    VajraPulse                            │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌──────────────┐    ┌──────────────┐    ┌─────────────┐ │
│  │   API        │    │   Core      │    │  Worker    │ │
│  │ (Zero deps)  │───▶│ (Engine)    │───▶│  (CLI)     │ │
│  └──────────────┘    └──────────────┘    └─────────────┘ │
│         │                    │                            │
│         │                    ▼                            │
│         │            ┌──────────────┐                     │
│         │            │  Metrics     │                     │
│         │            │  Collector   │                     │
│         │            └──────────────┘                     │
│         │                    │                            │
│         │                    ▼                            │
│         │    ┌───────────────────────────┐              │
│         └───▶│  Exporters                 │              │
│              │  - Console                 │              │
│              │  - OpenTelemetry           │              │
│              └───────────────────────────┘              │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

### Modules

- **`vajrapulse-api`** - Zero-dependency public API (Task, LoadPattern, annotations)
- **`vajrapulse-core`** - Execution engine, metrics collection, rate control
- **`vajrapulse-worker`** - CLI application with fat JAR
- **`vajrapulse-exporter-console`** - Human-readable console output
- **`vajrapulse-exporter-opentelemetry`** - OTLP metrics and tracing export
- **`vajrapulse-bom`** - Bill of Materials for dependency management

---

## Performance

VajraPulse leverages Java 21 virtual threads for exceptional performance:

- **10,000+ TPS** on typical hardware
- **Millions of concurrent requests** with minimal memory
- **~200 MB memory** for 100k HTTP requests @ 1000 TPS
- **1,000,000+ iterations** in a single test run

Virtual threads enable massive concurrency without the overhead of traditional thread pools.

---

## Observability

### OpenTelemetry Integration

Export metrics and traces to any OpenTelemetry-compatible backend:

```java
var exporter = new OpenTelemetryExporter.Builder()
    .endpoint("http://localhost:4318")
    .runId("test-run-001")
    .build();

var collector = new MetricsCollector(exporter);
// ... run test ...
```

Compatible with:
- **Grafana** (via OTEL Collector)
- **Prometheus** (via OTEL Collector)
- **Jaeger** (distributed tracing)
- **Any OTEL-compatible backend**

### Metrics Available

- `vajrapulse.execution.total` - Total executions counter
- `vajrapulse.execution.success` - Success counter
- `vajrapulse.execution.failure` - Failure counter
- `vajrapulse.execution.latency` - Latency histogram (success/failure)
- `vajrapulse.execution.queue.size` - Queue depth gauge
- `vajrapulse.execution.queue.wait_time` - Queue wait time histogram
- `vajrapulse.request.tps` - Request TPS gauge
- `vajrapulse.response.tps` - Response TPS gauge

All metrics are tagged with `run_id` for test correlation.

---

## Examples

See the `examples/` directory for complete working examples:

- **`http-load-test`** - HTTP API load testing with OpenTelemetry export
- More examples coming soon

---

## Requirements

- **Java 21+** (required for virtual threads)
- **Gradle 9.0+** or **Maven 3.6+**

---

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## License

Apache License 2.0 - see [LICENSE](LICENSE)

---

## Credits

Built with:
- Java 21 (Virtual Threads)
- Gradle 9.0
- Micrometer 1.12.0
- OpenTelemetry 1.41.0
- Spock Framework 2.4
- SLF4J 2.0.9

---

<p align="center">
  <strong>VajraPulse</strong> (वज्र) – "thunderbolt" ⚡
</p>

<p align="center">
  <em>Pre-1.0: Breaking changes may occur as we refine the API</em>
</p>
