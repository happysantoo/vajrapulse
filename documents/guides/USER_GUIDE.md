# VajraPulse User Guide

**Version**: 0.9.10  
**Last Updated**: 2025-12-14

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Writing Your First Task](#writing-your-first-task)
3. [Load Patterns Explained](#load-patterns-explained)
4. [Thread Strategy Selection](#thread-strategy-selection)
5. [Metrics and Exporters](#metrics-and-exporters)
6. [Common Patterns & Best Practices](#common-patterns--best-practices)
7. [Advanced Topics](#advanced-topics)
8. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Installation

#### Gradle (Kotlin DSL) - Using BOM (Recommended)

```kotlin
dependencies {
    implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.10"))
    implementation("com.vajrapulse:vajrapulse-core")
    implementation("com.vajrapulse:vajrapulse-exporter-console")
}
```

#### Maven - Using BOM

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.vajrapulse</groupId>
            <artifactId>vajrapulse-bom</artifactId>
            <version>0.9.10</version>
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
        <artifactId>vajrapulse-exporter-console</artifactId>
    </dependency>
</dependencies>
```

### Requirements

- **Java 21+** (required for virtual threads)
- **Gradle 9+** or **Maven 3.8+**

---

## Writing Your First Task

### Basic Task Structure

A VajraPulse task implements the `TaskLifecycle` interface with three methods:

```java
import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.TaskResult;
import com.vajrapulse.api.task.VirtualThreads;

@VirtualThreads
public class MyLoadTest implements TaskLifecycle {
    
    @Override
    public void init() throws Exception {
        // Initialize resources (HTTP clients, DB connections, etc.)
        // Called once before test execution
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        // Your test logic here
        // Called repeatedly for each iteration
        // Return TaskResult.success() or TaskResult.failure()
    }
    
    @Override
    public void teardown() throws Exception {
        // Clean up resources
        // Called once after test execution
    }
}
```

### Complete Example

```java
import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.TaskResult;
import com.vajrapulse.api.task.VirtualThreads;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

@VirtualThreads
public class HttpLoadTest implements TaskLifecycle {
    
    private HttpClient client;
    private HttpRequest request;
    
    @Override
    public void init() throws Exception {
        client = HttpClient.newHttpClient();
        request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/endpoint"))
            .GET()
            .build();
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        HttpResponse<String> response = client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
        
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
        // HttpClient doesn't need explicit cleanup
    }
}
```

### Running Your Task

```java
import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.pattern.StaticLoad;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import java.time.Duration;

public class MyTestRunner {
    public static void main(String[] args) throws Exception {
        // Create task
        HttpLoadTest task = new HttpLoadTest();
        
        // Create load pattern (100 TPS for 5 minutes)
        LoadPattern pattern = new StaticLoad(100.0, Duration.ofMinutes(5));
        
        // Create metrics collector
        try (MetricsCollector metrics = new MetricsCollector()) {
            ConsoleMetricsExporter exporter = new ConsoleMetricsExporter();
            
            // Create and run engine
            ExecutionEngine engine = ExecutionEngine.builder()
                .withTask(task)
                .withLoadPattern(pattern)
                .withMetricsCollector(metrics)
                .build();
            
            try {
                engine.run();
                
                // Print results
                exporter.export("Final", metrics.snapshot());
            } finally {
                engine.close();
            }
        }
    }
}
```

---

## Load Patterns Explained

VajraPulse provides 7 load patterns for different testing scenarios:

### 1. Static Load

Constant TPS for a fixed duration.

```java
LoadPattern pattern = new StaticLoad(100.0, Duration.ofMinutes(5));
```

**Use Case**: Baseline performance testing, sustained load

### 2. Ramp-Up Load

Gradually increases TPS from 0 to target.

```java
LoadPattern pattern = new RampUpLoad(500.0, Duration.ofMinutes(2));
```

**Use Case**: Cold start testing, autoscaler warmup

### 3. Ramp-Up to Max Load

Ramps up then sustains at maximum.

```java
LoadPattern pattern = new RampUpToMaxLoad(
    500.0,                    // max TPS
    Duration.ofMinutes(1),    // ramp duration
    Duration.ofMinutes(5)     // sustain duration
);
```

**Use Case**: Find max capacity, then test stability

### 4. Step Load

Phased testing with multiple TPS levels.

```java
List<StepLoad.Step> steps = List.of(
    new StepLoad.Step(50.0, Duration.ofSeconds(30)),
    new StepLoad.Step(100.0, Duration.ofMinutes(1)),
    new StepLoad.Step(200.0, Duration.ofMinutes(2))
);
LoadPattern pattern = new StepLoad(steps);
```

**Use Case**: Phased capacity testing, identify breaking points

### 5. Spike Load

Sudden spikes of high load.

```java
LoadPattern pattern = new SpikeLoad(
    100.0,                    // base TPS
    500.0,                    // spike TPS
    Duration.ofSeconds(10),   // spike duration
    Duration.ofSeconds(30),   // interval between spikes
    Duration.ofMinutes(5)    // total duration
);
```

**Use Case**: Testing system resilience to sudden load increases

### 6. Sine Wave Load

Sinusoidal TPS variation.

```java
LoadPattern pattern = new SineWaveLoad(
    100.0,                    // base TPS
    50.0,                     // amplitude
    Duration.ofSeconds(60),   // period
    Duration.ofMinutes(5)     // total duration
);
```

**Use Case**: Simulating realistic traffic patterns

### 7. Adaptive Load Pattern

Self-tuning pattern that finds maximum sustainable TPS.

```java
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(100.0)
    .rampIncrement(50.0)
    .rampDecrement(100.0)
    .rampInterval(Duration.ofMinutes(1))
    .maxTps(5000.0)
    .minTps(10.0)
    .sustainDuration(Duration.ofMinutes(10))
    .stableIntervalsRequired(3)
    .metricsProvider(metricsProvider)
    .build();
```

**Use Case**: Finding maximum sustainable throughput automatically

### Warm-Up and Cool-Down

Add warm-up and cool-down phases to any pattern:

```java
LoadPattern basePattern = new StaticLoad(100.0, Duration.ofMinutes(5));
LoadPattern withWarmup = new WarmupCooldownLoadPattern(
    basePattern,
    Duration.ofSeconds(30),  // warm-up
    Duration.ofSeconds(30)   // cool-down
);
```

---

## Thread Strategy Selection

VajraPulse supports two thread strategies:

### Virtual Threads (Default)

Use for **I/O-bound** operations (HTTP, database, file I/O).

```java
@VirtualThreads
public class HttpTask implements TaskLifecycle {
    // HTTP requests, database queries, file operations
}
```

**Benefits**:
- Massive concurrency (millions of threads)
- Minimal memory overhead
- Perfect for I/O-bound workloads

### Platform Threads

Use for **CPU-bound** operations (encryption, compression, calculations).

```java
@PlatformThreads(poolSize = 8)
public class CpuTask implements TaskLifecycle {
    // Encryption, compression, heavy computations
}
```

**Benefits**:
- True parallelism on multiple CPU cores
- Better for CPU-intensive work
- Configurable pool size

### When to Use Each

| Operation Type | Thread Strategy | Example |
|----------------|----------------|---------|
| HTTP requests | `@VirtualThreads` | REST API calls |
| Database queries | `@VirtualThreads` | JDBC, JPA |
| File I/O | `@VirtualThreads` | Reading/writing files |
| Network I/O | `@VirtualThreads` | gRPC, WebSocket |
| Encryption | `@PlatformThreads` | AES, RSA |
| Compression | `@PlatformThreads` | Deflate, GZIP |
| Calculations | `@PlatformThreads` | Mathematical computations |
| Image processing | `@PlatformThreads` | Filters, transformations |

---

## Metrics and Exporters

### MetricsCollector

The `MetricsCollector` automatically tracks:

- **Execution counts** (success/failure)
- **Latency percentiles** (p50, p75, p95, p99)
- **Throughput** (TPS)
- **Success rate**
- **Queue depth**
- **Queue wait time**

```java
try (MetricsCollector metrics = new MetricsCollector()) {
    // Run test...
    
    AggregatedMetrics snapshot = metrics.snapshot();
    System.out.println("Success Rate: " + snapshot.successRate() + "%");
    System.out.println("P95 Latency: " + snapshot.successPercentiles().get(0.95) + " ms");
}
```

### Exporters

Export metrics to various destinations:

#### Console Exporter

```java
ConsoleMetricsExporter exporter = new ConsoleMetricsExporter();
exporter.export("Test Results", metrics.snapshot());
```

#### OpenTelemetry Exporter

```java
try (OpenTelemetryExporter exporter = OpenTelemetryExporter.builder()
        .endpoint("http://localhost:4318")
        .resourceAttributes(Map.of(
            "service.name", "my-load-test",
            "service.version", "0.9.10"
        ))
        .build()) {
    exporter.export("Test Results", metrics.snapshot());
}
```

#### Multiple Exporters

```java
List<MetricsExporter> exporters = List.of(
    new ConsoleMetricsExporter(),
    otelExporter
);

// Export to all
for (MetricsExporter exporter : exporters) {
    exporter.export("Results", metrics.snapshot());
}
```

---

## Common Patterns & Best Practices

### Pattern 1: Resource Initialization

**Good**: Initialize in `init()`, reuse in `execute()`

```java
private HttpClient client;

@Override
public void init() throws Exception {
    client = HttpClient.newHttpClient();  // Create once
}

@Override
public TaskResult execute(long iteration) throws Exception {
    // Reuse client for all iterations
    return performRequest(client);
}
```

**Bad**: Creating resources in `execute()`

```java
@Override
public TaskResult execute(long iteration) throws Exception {
    HttpClient client = HttpClient.newHttpClient();  // ❌ Creates new client each time
    return performRequest(client);
}
```

### Pattern 2: Error Handling

**Good**: Return `TaskResult.failure()` for errors

```java
@Override
public TaskResult execute(long iteration) throws Exception {
    try {
        return performOperation();
    } catch (Exception e) {
        return TaskResult.failure(e);  // Framework handles it
    }
}
```

**Bad**: Throwing exceptions or swallowing errors

```java
@Override
public TaskResult execute(long iteration) throws Exception {
    performOperation();  // ❌ What if it fails?
    return TaskResult.success();
}
```

### Pattern 3: Thread Strategy Selection

**Good**: Choose based on operation type

```java
@VirtualThreads  // ✅ For HTTP I/O
public class ApiTask implements TaskLifecycle { }

@PlatformThreads  // ✅ For CPU work
public class EncryptionTask implements TaskLifecycle { }
```

**Bad**: Using wrong strategy

```java
@VirtualThreads  // ❌ CPU-bound work blocks carrier thread
public class CpuIntensiveTask implements TaskLifecycle { }
```

### Pattern 4: Metrics Collection

**Good**: Use try-with-resources for MetricsCollector

```java
try (MetricsCollector metrics = new MetricsCollector()) {
    // Run test
    AggregatedMetrics snapshot = metrics.snapshot();
}
```

**Bad**: Not closing MetricsCollector

```java
MetricsCollector metrics = new MetricsCollector();  // ❌ Memory leak
// Run test
// Never closed!
```

### Pattern 5: Load Pattern Selection

**Good**: Match pattern to testing goal

```java
// Finding max capacity
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()...build();

// Sustained load
StaticLoad pattern = new StaticLoad(100.0, Duration.ofMinutes(10));

// Phased testing
StepLoad pattern = new StepLoad(steps);
```

---

## Advanced Topics

### Configuration System

VajraPulse supports configuration via YAML files or environment variables:

**`vajrapulse.conf.yml`**:
```yaml
execution:
  drainTimeout: 5s
  forceTimeout: 10s
  defaultThreadPool: VIRTUAL
  platformThreadPoolSize: 8

observability:
  tracingEnabled: false
```

**Environment Variables**:
```bash
export VAJRAPULSE_EXECUTION_DRAIN_TIMEOUT=5s
export VAJRAPULSE_EXECUTION_DEFAULT_THREAD_POOL=VIRTUAL
```

### Custom Metrics Providers

For AdaptiveLoadPattern, provide custom metrics:

```java
MetricsProvider provider = new MetricsProvider() {
    @Override
    public double getFailureRate() {
        return calculateFailureRate();
    }
    
    // ... implement other methods
};

AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .metricsProvider(provider)
    .build();
```

### Event Listeners

Listen to AdaptiveLoadPattern events:

```java
AdaptivePatternListener listener = new AdaptivePatternListener() {
    @Override
    public void onPhaseTransition(PhaseTransitionEvent event) {
        System.out.println("Phase: " + event.oldPhase() + " -> " + event.newPhase());
    }
    
    @Override
    public void onTpsChange(TpsChangeEvent event) {
        System.out.println("TPS: " + event.oldTps() + " -> " + event.newTps());
    }
};

AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .listener(listener)
    .build();
```

### Backpressure Handling

Handle backpressure signals:

```java
BackpressureProvider provider = new BackpressureProvider() {
    @Override
    public double getBackpressureLevel() {
        return calculateBackpressure();
    }
};

AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .backpressureProvider(provider)
    .build();
```

---

## Troubleshooting

### Issue: Tests Hang or Timeout

**Solution**: Ensure all tests have `@Timeout` annotations:

```java
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class MyTestSpec extends Specification {
    // ...
}
```

### Issue: Low Throughput

**Possible Causes**:
1. Using `@PlatformThreads` for I/O-bound work → Use `@VirtualThreads`
2. Creating resources in `execute()` → Move to `init()`
3. Synchronized blocks → Use lock-free structures
4. Network latency → Check target system performance

### Issue: High Memory Usage

**Possible Causes**:
1. Not closing `MetricsCollector` → Use try-with-resources
2. Storing large objects in task → Use object pooling
3. Too many platform threads → Reduce pool size

### Issue: Metrics Not Appearing

**Checklist**:
- [ ] `MetricsCollector` is created before `ExecutionEngine`
- [ ] `export()` is called after test completion
- [ ] Exporter is properly configured
- [ ] Network connectivity (for remote exporters)

### Issue: Connection Pool Exhausted

**Solution**: Increase pool size or reduce TPS:

```java
// For database connections
config.setMaximumPoolSize(50);

// For HTTP clients
// Use connection pooling or increase limits
```

---

## Examples

Comprehensive examples are available in the `examples/` directory:

- **[HTTP Load Test](../examples/http-load-test/README.md)** - REST API testing
- **[Database Load Test](../examples/database-load-test/README.md)** - JDBC testing
- **[CPU-Bound Test](../examples/cpu-bound-test/README.md)** - Platform threads
- **[gRPC Load Test](../examples/grpc-load-test/README.md)** - gRPC services
- **[Multi-Exporter](../examples/multi-exporter/README.md)** - Multiple exporters
- **[Adaptive Load Test](../examples/adaptive-load-test/README.md)** - Adaptive pattern

---

## Next Steps

- Read the [API Documentation](#api-documentation) (coming soon)
- Explore [Examples](../examples/README.md)
- Check [CHANGELOG.md](../../CHANGELOG.md) for latest features
- Review [Architecture Documentation](../architecture/DESIGN.md)

---

**Questions?** Open an issue on [GitHub](https://github.com/happysantoo/vajrapulse/issues)
