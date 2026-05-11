# VajraPulse ⚡

<p align="center"><img src="./vajrapulse_logo.png" alt="VajraPulse Logo" width="360"/></p>

<p align="center">
  <strong>High-performance load testing framework built on Java 21 virtual threads</strong>
</p>

<p align="center">
  <a href="https://github.com/happysantoo/vajrapulse/actions/workflows/ci.yml"><img src="https://github.com/happysantoo/vajrapulse/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://openjdk.org/projects/jdk/21/"><img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21"></a>
  <a href="https://gradle.org/"><img src="https://img.shields.io/badge/Gradle-9.0-blue.svg" alt="Gradle 9.0"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-green.svg" alt="Apache License 2.0"></a>
  <a href="https://search.maven.org/search?q=g:com.vajrapulse"><img src="https://img.shields.io/maven-central/v/com.vajrapulse/vajrapulse-core.svg" alt="Maven Central"></a>
  <a href="https://jitpack.io/#happysantoo/vajrapulse"><img src="https://jitpack.io/v/happysantoo/vajrapulse.svg" alt="JitPack"></a>
</p>

---

## 🆕 What's New in 1.0.0

Version 1.0.0 is the **first production release** of VajraPulse:

- ✅ **Stable API** - `TaskLifecycle` interface frozen; no breaking changes planned until 2.0
- ✅ **ScopedValue migration** - Better virtual thread support, replacing ThreadLocal
- ✅ **Enhanced tracing** - Proper span hierarchy with trace correlation in logs
- ✅ **Run metadata persistence** - JSON manifest file for each test run
- ✅ **CI/CD pipeline** - GitHub Actions with quality gates (coverage, SpotBugs)
- ✅ **Graceful shutdown** - 5-second drain + force-timeout, fully integration-tested
- ✅ **7 load patterns** - Static, Ramp, Step, Spike, Sine, Ramp-Sustain, Adaptive

> 🚀 **New to VajraPulse?** Check out the [Quick Start Guide](documents/guides/QUICK_START.md) to run your first load test!

See [CHANGELOG.md](CHANGELOG.md#100---2026-03-10) for complete release notes.

### Scope: what 1.0.0 is (and is not)

**1.0.0** is the first **SemVer-stable** release of the **standalone** engine: one JVM runs your load pattern, records metrics, and exports results. **Multi-worker / orchestrated distributed execution** is explicitly **out of scope** for 1.0.0 and is planned for a later minor (see [CHANGELOG.md](CHANGELOG.md) Unreleased and [`documents/roadmap/POST_1.0_BACKLOG.md`](documents/roadmap/POST_1.0_BACKLOG.md)). The public API inventory and stability categories are documented in [`documents/architecture/API_FREEZE_0.9.11.md`](documents/architecture/API_FREEZE_0.9.11.md).

---

## Why VajraPulse?

**VajraPulse** makes load testing simple, fast, and resource-efficient. Built on Java 21's virtual threads, it can handle **10,000+ requests per second** with minimal memory overhead—perfect for testing APIs, databases, message queues, and any I/O-bound service.

### Key Benefits

- ⚡ **Massive Concurrency**: Virtual threads enable millions of concurrent operations with minimal memory
- 🎯 **Simple API**: Implement one interface (`TaskLifecycle`) and you're ready to test
- 📊 **Rich Metrics**: Built-in latency percentiles, queue depth tracking, client-side metrics, and OpenTelemetry support
- 🔄 **Flexible Patterns**: 7 load patterns (static, ramp, step, spike, sine, ramp-sustain, adaptive) with warm-up/cool-down support
- 🧩 **Adaptive Intelligence**: Self-tuning adaptive pattern with event notifications and pluggable decision policies
- ✅ **Assertion Framework**: Built-in assertions for latency, error rate, throughput, and success rate validation
- 📦 **Minimal Dependencies**: ~1.6MB fat JAR, zero-dependency API module
- 🚀 **Production Ready**: OpenTelemetry integration, comprehensive metrics, graceful shutdown
- 🏗️ **Well-Architected**: Clean code organization, polymorphism-based design, excellent test coverage

> 📊 **Want to see how VajraPulse compares to JMeter, Gatling, and BlazeMeter?** Check out our [comprehensive comparison guide](documents/guides/COMPARISON.md) covering architecture, performance, enterprise scalability, and real-world use cases.

---

## Quick Start

### 1. Add Dependency

**Gradle (Kotlin DSL)** - Using BOM (Recommended):
```kotlin
dependencies {
    implementation(platform("com.vajrapulse:vajrapulse-bom:1.0.0"))
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
            <version>1.0.0</version>
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
public class ApiLoadTest implements TaskLifecycle {
    private HttpClient client;
    
    @Override
    public void init() throws Exception {
        client = HttpClient.newHttpClient();
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
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
    
    @Override
    public void teardown() throws Exception {
        // Cleanup if needed
    }
}
```

### 3. Run It

**CLI (Recommended for quick tests):**
```bash
java -jar vajrapulse-worker-1.0.0-all.jar \
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
import java.time.Duration;

TaskLifecycle task = new ApiLoadTest();
LoadPattern pattern = new StaticLoad(100.0, Duration.ofMinutes(5));
MetricsCollector collector = new MetricsCollector();

try (ExecutionEngine engine = ExecutionEngine.builder()
        .withTask(task)
        .withLoadPattern(pattern)
        .withMetricsCollector(collector)
        .build()) {
    engine.run();
    
    AggregatedMetrics metrics = collector.snapshot();
    System.out.println("Success Rate: " + metrics.successRate() + "%");
    System.out.println("P95 Latency: " + metrics.successPercentiles().get(0.95) + " ms");
}
```

---

## Core Features

### 🎯 Simple Task API

Implement the `TaskLifecycle` interface with three methods:

- **`init()`** - Initialize resources (HTTP clients, DB connections, etc.) - called once before test
- **`execute(long iteration)`** - Your test logic (called repeatedly for each iteration)
- **`teardown()`** - Clean up resources - called once after test

The framework handles timing, metrics, error handling, and thread management automatically.

> **Note**: The legacy `Task` interface is still supported but deprecated. New code should use `TaskLifecycle`.

### ⚡ Virtual Threads by Default

VajraPulse uses Java 21 virtual threads for I/O-bound tasks, enabling massive concurrency:

```java
@VirtualThreads  // For HTTP, DB, file I/O
public class IoTask implements TaskLifecycle { }

@PlatformThreads(poolSize = 8)  // For CPU-intensive work
public class CpuTask implements TaskLifecycle { }
```

**Performance**: 10,000+ TPS on typical hardware, millions of concurrent requests with minimal memory.

### 📊 Seven Load Patterns

Choose the pattern that matches your testing scenario:

| Pattern | Use Case | Example |
|---------|----------|---------|
| **Static** | Baseline performance | `--mode static --tps 100 --duration 5m` |
| **Ramp-Up** | Cold start / autoscaler warmup | `--mode ramp --tps 500 --ramp-duration 30s` |
| **Ramp-Sustain** | Sustained pressure after ramp | `--mode ramp-sustain --tps 200 --ramp-duration 30s --duration 5m` |
| **Step** | Phased testing | `--mode step --steps "50@30s,200@1m,500@2m"` |
| **Spike** | Burst absorption testing | `--mode spike --base-rate 100 --spike-rate 800 --spike-interval 60s` |
| **Sine** | Smooth oscillation | `--mode sine --mean-rate 300 --amplitude 150 --period 120s` |
| **Adaptive** | Dynamic TPS adjustment based on error rates and backpressure | See [Adaptive Pattern](#adaptive-load-pattern) section |

**Warm-up/Cool-down Support**: All patterns can be wrapped with warm-up and cool-down phases:
```java
LoadPattern basePattern = new StaticLoad(100.0, Duration.ofMinutes(5));
LoadPattern pattern = new WarmupCooldownLoadPattern(
    basePattern,
    Duration.ofSeconds(30),  // Warm-up: 30 seconds
    Duration.ofSeconds(10)   // Cool-down: 10 seconds
);
```

### 📈 Comprehensive Metrics

Built-in metrics collection with Micrometer:

- **Latency Percentiles**: P50, P95, P99 for success and failure cases
- **Queue Depth Tracking**: Monitor pending executions
- **Client-Side Metrics**: Connection pool metrics, queue depth, timeouts
- **TPS Metrics**: Request TPS, Success TPS, Failure TPS
- **Failure Count**: Absolute failure count tracking
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

Client Metrics:
  Connection Pool:
    Active:           10
    Idle:             5
    Utilization:      66.7%
  Client Queue:
    Depth:            3
    Avg Wait Time:    1.25 ms
  Client Errors:
    Connection Timeouts:  1
    Request Timeouts:     2

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
- Client-side metrics display (connection pools, queues, errors)

### ✅ Assertion Framework

Built-in assertion framework for validating test results against SLOs and requirements.

```java
// Create assertions
Assertion latencyAssertion = Assertions.latency(0.95, Duration.ofMillis(100));
Assertion errorRateAssertion = Assertions.errorRate(0.01); // 1% max
Assertion throughputAssertion = Assertions.throughput(1000.0);

// Composite assertion (all must pass)
Assertion allAssertions = Assertions.all(
    latencyAssertion,
    errorRateAssertion,
    throughputAssertion
);

// Evaluate after test
AggregatedMetrics metrics = metricsCollector.snapshot();
AssertionResult result = allAssertions.evaluate(metrics);

if (result.failed()) {
    System.err.println("Assertion failed: " + result.message());
}
```

**Available Assertions:**
- **Latency**: Validate percentile latency (e.g., P95 < 100ms)
- **Error Rate**: Validate maximum error rate (e.g., < 1%)
- **Success Rate**: Validate minimum success rate (e.g., > 99%)
- **Throughput**: Validate minimum TPS (e.g., > 1000 TPS)
- **Execution Count**: Validate minimum total executions
- **Composite**: `all()` (all must pass) or `any()` (at least one must pass)

### 🏗️ Modular Architecture

**Zero-Dependency API Module:**
- `vajrapulse-api` has **zero external dependencies**
- Clean separation of concerns
- Easy to extend and integrate
- Enhanced with polymorphism support

**Minimal Core:**
- Only Micrometer and SLF4J as dependencies
- ~150 KB core module
- ~1.6 MB fat JAR (all-in-one)
- Improved code organization

---

## Installation

### Maven Central (Recommended)

**Using BOM** - Manage all module versions in one place:

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation(platform("com.vajrapulse:vajrapulse-bom:1.0.0"))
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
    implementation platform('com.vajrapulse:vajrapulse-bom:1.0.0')
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
            <version>1.0.0</version>
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
    implementation("com.vajrapulse:vajrapulse-core:1.0.0")
    implementation("com.vajrapulse:vajrapulse-worker:1.0.0")
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
public class RestApiTest implements TaskLifecycle {
    private HttpClient client;
    
    @Override
    public void init() throws Exception {
        client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
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
    
    @Override
    public void teardown() throws Exception {
        // Cleanup if needed
    }
}
```

### Database Load Testing

```java
@VirtualThreads
public class DatabaseTest implements TaskLifecycle {
    private Connection connection;
    
    @Override
    public void init() throws SQLException {
        connection = DriverManager.getConnection("jdbc:postgresql://localhost/db", "user", "pass");
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        try (var stmt = connection.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            stmt.setInt(1, randomUserId());
            var rs = stmt.executeQuery();
            return rs.next() 
                ? TaskResult.success(rs.getString("name"))
                : TaskResult.failure(new RuntimeException("User not found"));
        }
    }
    
    @Override
    public void teardown() throws SQLException {
        if (connection != null) connection.close();
    }
}
```

### Message Queue Testing

```java
@VirtualThreads
public class KafkaProducerTest implements TaskLifecycle {
    private KafkaProducer<String, String> producer;
    
    @Override
    public void init() {
        var props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        var record = new ProducerRecord<>("test-topic", "key", "value");
        var future = producer.send(record);
        var metadata = future.get(5, TimeUnit.SECONDS);
        return TaskResult.success(metadata.topic());
    }
    
    @Override
    public void teardown() {
        if (producer != null) producer.close();
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

### Adaptive Load Pattern

The adaptive load pattern automatically finds the maximum sustainable TPS by dynamically adjusting based on error rates, backpressure, and system conditions.

**Key Features:**
- **Automatic Ramp-Up**: Increases TPS until errors occur
- **Intelligent Ramp-Down**: Decreases TPS to find stable operating point
- **Stability Detection**: Detects and sustains at optimal TPS levels (not just MAX_TPS)
- **Automatic Recovery**: Recovers from low TPS when conditions improve (uses recent window failure rate)
- **Event Notifications**: Listen to phase transitions, TPS changes, and stability events
- **Pluggable Policies**: Custom decision logic via `RampDecisionPolicy` interface
- **Continuous Operation**: Never gets stuck - continuously adapts to changing conditions
- **Backpressure Integration**: Responds to backpressure signals from queues, connection pools, etc.

**Using the Builder Pattern (Recommended):**

```java
MetricsCollector metrics = new MetricsCollector();
MetricsProvider provider = new MetricsProviderAdapter(metrics);

AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(100.0)                      // Start at 100 TPS
    .rampIncrement(50.0)                    // Increase 50 TPS per interval
    .rampDecrement(100.0)                    // Decrease 100 TPS per interval when errors occur
    .rampInterval(Duration.ofSeconds(10))   // Check/adjust every 10 seconds
    .maxTps(1000.0)                          // Max 1000 TPS
    .minTps(10.0)                            // Min 10 TPS
    .sustainDuration(Duration.ofSeconds(30)) // Sustain at stable point for 30 seconds
    .errorThreshold(0.01)                    // 1% error rate threshold
    .backpressureRampUpThreshold(0.3)       // Ramp up if backpressure < 30%
    .backpressureRampDownThreshold(0.7)     // Ramp down if backpressure > 70%
    .stableIntervalsRequired(3)              // Require 3 stable intervals
    .tpsTolerance(50.0)                      // TPS tolerance for stability
    .recoveryTpsRatio(0.5)                  // Recovery at 50% of last known good TPS
    .metricsProvider(provider)               // Metrics provider for feedback
    .backpressureProvider(backpressureProvider) // Optional: backpressure signals
    .listener(new AdaptivePatternListener() { // Optional: event notifications
        @Override
        public void onPhaseTransition(PhaseTransitionEvent event) {
            System.out.println("Phase: " + event.from() + " -> " + event.to());
        }
        
        @Override
        public void onTpsChange(TpsChangeEvent event) {
            System.out.println("TPS: " + event.previousTps() + " -> " + event.newTps());
        }
        
        @Override
        public void onStabilityDetected(StabilityDetectedEvent event) {
            System.out.println("Stable TPS detected: " + event.stableTps());
        }
        
        @Override
        public void onRecovery(RecoveryEvent event) {
            System.out.println("Recovery: " + event.recoveryTps() + " TPS");
        }
    })
    .build();
```

**Custom Decision Policy:**

```java
RampDecisionPolicy customPolicy = new RampDecisionPolicy() {
    @Override
    public boolean shouldRampUp(MetricsSnapshot metrics) {
        // Custom logic for ramping up
        return metrics.failureRate() < 0.005 && metrics.backpressure() < 0.2;
    }
    
    @Override
    public boolean shouldRampDown(MetricsSnapshot metrics) {
        // Custom logic for ramping down
        return metrics.failureRate() > 0.01 || metrics.backpressure() > 0.8;
    }
    
    @Override
    public boolean shouldSustain(MetricsSnapshot metrics) {
        // Custom logic for sustaining
        return metrics.failureRate() < 0.01 && metrics.backpressure() < 0.5;
    }
    
    @Override
    public boolean shouldRecover(MetricsSnapshot metrics) {
        // Custom logic for recovery
        return metrics.recentFailureRate() < 0.01; // Use recent window
    }
};

AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .metricsProvider(provider)
    .decisionPolicy(customPolicy) // Use custom policy
    .build();
```

**How It Works:**
1. **RAMP_UP**: Increases TPS by `rampIncrement` at each `rampInterval` until errors occur or max TPS is reached
2. **RAMP_DOWN**: Decreases TPS by `rampDecrement` when error rate exceeds threshold or backpressure is high
3. **SUSTAIN**: Maintains stable TPS after detecting stability (3 consecutive intervals with good conditions)
4. **Recovery**: When at minimum TPS, automatically recovers when conditions improve (uses recent window failure rate)

**Event Notifications:**
```java
pattern.listener(new AdaptivePatternListener() {
    @Override
    public void onPhaseTransition(PhaseTransitionEvent event) {
        // Log phase changes
    }
    
    @Override
    public void onTpsChange(TpsChangeEvent event) {
        // Track TPS changes
    }
    
    @Override
    public void onStabilityDetected(StabilityDetectedEvent event) {
        // Alert when stability is found
    }
    
    @Override
    public void onRecovery(RecoveryEvent event) {
        // Monitor recovery events
    }
});
```

**Recent Improvements:**
- ✅ **23.5% code reduction** - easier to understand and maintain
- ✅ **Better organization** - helper methods extracted for clarity
- ✅ **Simplified builder** - method chaining for better developer experience
- ✅ **Unified state transitions** - single `transitionToPhase()` method
- ✅ **Enhanced testability** - helper methods can be tested independently

### Warm-up/Cool-down Phases
Add warm-up and cool-down phases to any load pattern for accurate baseline measurements.

```java
LoadPattern basePattern = new StaticLoad(100.0, Duration.ofMinutes(5));
LoadPattern pattern = new WarmupCooldownLoadPattern(
    basePattern,
    Duration.ofSeconds(30),  // Warm-up: ramp from 0 to initial TPS
    Duration.ofSeconds(10)   // Cool-down: ramp from final TPS to 0
);
```

**Benefits:**
- Metrics are only recorded during steady-state phase
- Clean separation between initialization and measurement
- No warm-up artifacts in metrics
- Graceful shutdown with cool-down phase

The `LoadPattern` interface includes `supportsWarmupCooldown()` and `shouldRecordMetrics()` methods, enabling polymorphism over type checking.

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
│              │  - Report                 │              │
│              └───────────────────────────┘              │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

### Modules

- **`vajrapulse-api`** - Zero-dependency public API (TaskLifecycle, LoadPattern, annotations)
  - Enhanced with polymorphism support methods
- **`vajrapulse-core`** - Execution engine, metrics collection, rate control
  - Improved with better code organization
- **`vajrapulse-worker`** - CLI application with fat JAR
- **`vajrapulse-exporter-console`** - Human-readable console output
- **`vajrapulse-exporter-opentelemetry`** - OTLP metrics and tracing export
- **`vajrapulse-exporter-report`** - HTML report generation
- **`vajrapulse-bom`** - Bill of Materials for dependency management

### Architecture Improvements

- ✅ **Polymorphism over Type Checking**: Interface methods replace `instanceof` checks
- ✅ **Better Code Organization**: Extracted helper methods and top-level classes
- ✅ **Consolidated Metrics Registration**: Single method for all metrics
- ✅ **Improved Separation of Concerns**: Clear boundaries between components

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
- `vajrapulse.adaptive.tps` - Adaptive pattern current TPS (if using adaptive pattern)
- `vajrapulse.adaptive.phase` - Adaptive pattern current phase (if using adaptive pattern)

All metrics are tagged with `run_id` for test correlation.

---

## Code Quality

Version 1.0.0 delivers production-grade code quality:

- ✅ **Test Coverage**: ≥90% for all modules
- ✅ **Test Reliability**: 100% timeout coverage, 0% flakiness (validated)
- ✅ **Static Analysis**: SpotBugs passes with no issues
- ✅ **Code Organization**: Improved maintainability and readability
- ✅ **Documentation**: Complete JavaDoc on all public APIs
- ✅ **Best Practices**: Comprehensive test best practices guide

---

## Examples

See the `examples/` directory for complete working examples:

- **`http-load-test`** - HTTP API load testing with OpenTelemetry export
- **`adaptive-load-test`** - Adaptive load pattern demonstration
- **`adaptive-with-warmup`** - Adaptive pattern with warm-up/cool-down
- More examples coming soon

All examples updated to use latest APIs (builder pattern, TaskLifecycle interface).

---

## Requirements

- **Java 21+** (required for virtual threads)
- **Gradle 9.0+** or **Maven 3.6+**

---

## Breaking Changes

**Version 1.0.0 has no breaking changes from 0.9.11.** The API is now stable.

The `Task` interface (deprecated since 0.9.5) remains available but will be **removed in 1.1.0**. Migrate to `TaskLifecycle` before upgrading to 1.1.0.

For breaking changes introduced in the 0.9.x series, see [CHANGELOG.md](CHANGELOG.md) and [MIGRATION_0.9_TO_1.0.md](documents/guides/MIGRATION_0.9_TO_1.0.md).

---

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

**For Contributors**: Check out the [Test Best Practices Guide](documents/guides/TEST_BEST_PRACTICES.md) for writing reliable tests.

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
  <em>1.0.0 production release — stable API, production-ready</em>
</p>

---

## Additional Resources

- [CHANGELOG.md](CHANGELOG.md) - Complete release history
- [COMPARISON.md](documents/guides/COMPARISON.md) - Comparison with JMeter, Gatling, BlazeMeter
- [Test Best Practices](documents/guides/TEST_BEST_PRACTICES.md) - Comprehensive testing guide
- [User Guide](documents/guides/USER_GUIDE.md) - Comprehensive user guide
- [Release 0.9.9 Detailed Notes](documents/releases/RELEASE_0.9.9_DETAILED_NOTES.md) - Detailed release notes
