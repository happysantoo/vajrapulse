# End-to-End Load Test Flow with MetricsPipeline

This guide shows the complete, minimal flow to run a VajraPulse load test programmatically by linking:
1. Task (work to execute)
2. LoadPattern (rate & duration definition)
3. MetricsPipeline (execution + metrics + periodic & final export)

All examples assume Java 21 and follow VajraPulse design principles (virtual threads for I/O, minimal dependencies, records/sealed types, no lambdas in hot path).

---
## 1. Define Your Task

Implement `Task` in the `vajrapulse-api` style. Use annotations to select threading strategy.

```java
@VirtualThreads // I/O-bound example
public final class MyHttpTask implements Task {
    private HttpClient client;
    private HttpRequest request;

    @Override
    public void setup() {
        client = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
        request = HttpRequest.newBuilder()
            .uri(URI.create("https://httpbin.org/delay/0"))
            .GET()
            .build();
    }

    @Override
    public TaskResult execute() throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return TaskResult.success(response.body());
        }
        return TaskResult.failure(new RuntimeException("HTTP " + response.statusCode()));
    }

    @Override
    public void cleanup() {
        // No explicit cleanup needed here
    }
}
```

Use `@PlatformThreads(poolSize = -1)` for CPU-bound tasks instead.

---
## 2. Choose a Load Pattern

Select a built-in pattern (examples):

```java
LoadPattern pattern = new StaticLoad(250.0, Duration.ofSeconds(45));
// or new RampUpLoad(500.0, Duration.ofSeconds(30));
// or new RampUpToMaxLoad(500.0, Duration.ofSeconds(30), Duration.ofMinutes(2));
```

Patterns expose `calculateTps(elapsedMillis)` and `getDuration()`; the engine performs rate control internally.

---
## 3. Build the MetricsPipeline

The pipeline lives in the worker layer (`vajrapulse-worker`). It wires together:
* Thread strategy (via task annotations)
* ExecutionEngine
* MetricsCollector (auto-created unless provided)
* Optional periodic live exporter
* Final snapshot export

```java
MetricsPipeline pipeline = MetricsPipeline.builder()
    .addExporter(new ConsoleMetricsExporter())    // final + live exports
    .withPeriodic(Duration.ofSeconds(5))          // live updates cadence
    .withImmediateLive(true)                      // fire first live snapshot immediately (optional)
    .withPercentiles(0.50, 0.75, 0.90, 0.95, 0.99) // custom latency percentiles
    .withSloBuckets(Duration.ofMillis(10),        // optional SLO histogram buckets
                     Duration.ofMillis(50),
                     Duration.ofMillis(100))
    .build();                                     // creates MetricsCollector
```

You can add multiple exporters; the first is used for live periodic updates. Custom percentiles must be in (0,1]; they are rounded to 3 decimals, sorted, and deduplicated automatically.

The `ConsoleMetricsExporter` displays all configured percentiles, sorted ascending, with labels up to 3 decimal places (e.g., P97.125). Example output with extended percentiles:
```
Success Latency (ms):
  P50:  12.50
  P75:  23.80
  P90:  38.40
  P95:  45.20
  P99:  78.90
```

---
## 4. Run the Flow

```java
MyHttpTask task = new MyHttpTask();
AggregatedMetrics results = pipeline.run(task, pattern);

System.out.println("Total Executions: " + results.totalExecutions());
System.out.println("Success Rate: " + String.format("%.2f%%", results.successRate()));
Double p95Nanos = results.successPercentiles().get(0.95d);
if (p95Nanos != null) {
    System.out.println("P95 Success (ms): " + (p95Nanos / 1_000_000.0));
}

// Iterate configured percentiles (success), formatting label up to 3 decimals
results.successPercentiles().entrySet().stream()
    .sorted(java.util.Map.Entry.comparingByKey())
    .forEach(e -> {
        double pct = e.getKey() * 100.0;
        java.math.BigDecimal bd = new java.math.BigDecimal(pct).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        String label = "P" + (bd.scale() <= 0 ? bd.toBigInteger().toString() : bd.toPlainString());
        System.out.printf("Success %s: %.3fms%n", label, e.getValue()/1_000_000.0);
    });
```

Behind the scenes:
1. `setup()` invoked once.
2. Rate-controlled submissions for full duration.
3. Each iteration wrapped by `TaskExecutor` (timing + exception handling).
4. Metrics recorded in `MetricsCollector` (Micrometer timers + counters).
5. Periodic reporter (if configured) snapshots and exports live metrics.
6. After duration, executor shutdown + `cleanup()` invoked.
7. Final snapshot exported via all exporters.

---
## 5. Alternative: Direct Static Helper

For one-off runs without periodic reporting, bypass the pipeline:

```java
MetricsCollector collector = new MetricsCollector();
AggregatedMetrics snap = ExecutionEngine.execute(task, pattern, collector);
new ConsoleMetricsExporter().export("Final Results", snap);
```

Use this when you need full manual control or custom orchestration.

---
## 6. Enabling Trace Logging (Optional Debug)

Edit `logback.xml` and add:
```xml
<logger name="com.vajrapulse.core.engine.TaskExecutor" level="TRACE"/>
```
Then reduce TPS (e.g., 10) and inspect per-iteration durations to validate latency percentiles.

---
## 7. Custom Exporter Example

Implement `MetricsExporter` for JSON output:
```java
public final class JsonMetricsExporter implements MetricsExporter {
    @Override
    public void export(String title, AggregatedMetrics m) {
        System.out.printf("{\n  \"title\": \"%s\",\n  \"total\": %d,\n  \"successRate\": %.2f,\n  \"p95_ms\": %.2f\n}%n",
            title,
            m.totalExecutions(),
            m.successRate(),
            m.successP95() / 1_000_000.0);
    }
}
```

Add to pipeline:
```java
MetricsPipeline pipeline = MetricsPipeline.builder()
    .addExporter(new ConsoleMetricsExporter())
    .addExporter(new JsonMetricsExporter())
    .withPeriodic(Duration.ofSeconds(10))
    .build();
```

---
## 8. Common Pitfalls

| Issue | Cause | Fix |
|-------|-------|-----|
| No live updates | Forgot `.withPeriodic(...)` | Add periodic interval |
| High memory usage | Using synchronized blocks | Remove; rely on Micrometer + lock-free structures |
| Percentiles NaN | Insufficient samples yet | Wait for enough executions; use longer duration |
| Slow throughput | CPU-bound task on virtual threads | Switch to `@PlatformThreads(poolSize = -1)` |

---
## 9. Verification Checklist

Before committing:
- Task annotated correctly (`@VirtualThreads` or `@PlatformThreads`).
- No lambdas in hot path (executor submissions use concrete callables).
- MetricsPipeline used (unless intentional manual control).
- Logback configured; trace only for debugging.
- Percentiles present (P50/P95/P99) after enough executions.
- No direct HdrHistogram usage (Micrometer only).

---
## 10. Minimal Runner Template

```java
public final class Runner {
    public static void main(String[] args) throws Exception {
        Task task = new MyHttpTask();
        LoadPattern pattern = new StaticLoad(200.0, Duration.ofSeconds(20));
        MetricsPipeline pipeline = MetricsPipeline.builder()
            .addExporter(new ConsoleMetricsExporter())
            .withPeriodic(Duration.ofSeconds(5))
            .build();
        pipeline.run(task, pattern);
    }
}
```

---
## 11. Next Extensions

Potential future exporters:
- JSON file writer
- Prometheus push (gateway)
- CSV summary
- OpenTelemetry bridge

All plug in via `addExporter(...)` with no core changes.

---
## 12. Summary

The `MetricsPipeline` provides a concise, extensible orchestration point. Users assemble Task + LoadPattern + Pipeline → run → receive live + final metrics with minimal boilerplate while retaining direct access to lower-level APIs when needed.
