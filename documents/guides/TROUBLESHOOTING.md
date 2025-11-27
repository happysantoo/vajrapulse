# VajraPulse Troubleshooting Guide

**Version**: 0.9.5  
**Status**: Troubleshooting Guide

---

## Overview

This guide helps diagnose and resolve common issues when using VajraPulse for load testing.

---

## Common Issues

### Issue 1: Test Never Starts

**Symptoms**:
- ExecutionEngine.run() hangs or throws exception immediately
- No task executions occur
- Logs show initialization but no iterations

**Possible Causes**:
1. Task initialization (`init()`) throws exception
2. Load pattern duration is zero or negative
3. Thread pool creation fails

**Diagnosis**:
```java
// Check logs for initialization errors
logger.error("Task initialization failed for runId={}: {}", runId, e.getMessage(), e);
```

**Solutions**:
1. **Check task initialization**:
   ```java
   @Override
   public void init() throws Exception {
       // Ensure all resources are initialized correctly
       // Check for null pointers, connection failures, etc.
   }
   ```

2. **Verify load pattern**:
   ```java
   LoadPattern pattern = new StaticLoad(100.0, Duration.ofMinutes(5));
   // Ensure duration is positive
   ```

3. **Check thread annotations**:
   ```java
   @VirtualThreads  // or @PlatformThreads
   public class MyTask implements TaskLifecycle {
       // ...
   }
   ```

---

### Issue 2: Low Actual TPS vs Target TPS

**Symptoms**:
- Target TPS is 1000, but actual TPS is only 200
- Rate controller accuracy metrics show large error
- System appears underutilized

**Possible Causes**:
1. Task execution is too slow (bottleneck in task code)
2. Thread pool size too small (platform threads)
3. System resource limits (CPU, memory, network)
4. External service is slow (API, database)

**Diagnosis**:
```java
// Check rate controller accuracy metrics
double targetTps = rateController.getCurrentTps();
double actualTps = metrics.responseTps();
double error = targetTps - actualTps;
System.out.println("TPS Error: " + error);
```

**Solutions**:
1. **Profile task execution**:
   ```java
   // Enable trace logging
   logger.trace("Iteration={} Duration={}ms", iteration, durationMs);
   ```

2. **Increase thread pool** (for platform threads):
   ```java
   @PlatformThreads(poolSize = 100)  // Increase from default
   public class MyTask implements TaskLifecycle {
   ```

3. **Check system resources**:
   - Monitor CPU usage
   - Check memory usage
   - Verify network bandwidth

4. **Optimize task code**:
   - Remove unnecessary operations
   - Use connection pooling
   - Batch operations where possible

---

### Issue 3: High Error Rate

**Symptoms**:
- Many task executions fail
- Failure rate > 5%
- Errors in logs

**Possible Causes**:
1. External service is down or slow
2. Network timeouts
3. Invalid test data
4. Resource exhaustion (connections, memory)

**Diagnosis**:
```java
// Check failure rate
var snapshot = metrics.snapshot();
double failureRate = snapshot.failureRate();
System.out.println("Failure rate: " + failureRate + "%");
```

**Solutions**:
1. **Check external services**:
   ```bash
   # Verify service is accessible
   curl https://api.example.com/health
   ```

2. **Increase timeouts**:
   ```java
   HttpClient client = HttpClient.newBuilder()
       .connectTimeout(Duration.ofSeconds(30))  // Increase from default
       .build();
   ```

3. **Validate test data**:
   ```java
   @Override
   public TaskResult execute(long iteration) throws Exception {
       // Validate data before use
       if (testData == null || testData.isEmpty()) {
           return TaskResult.failure(new IllegalArgumentException("Invalid test data"));
       }
       // ...
   }
   ```

4. **Check resource limits**:
   - Connection pool size
   - Memory usage
   - File descriptors

---

### Issue 4: Adaptive Pattern Never Finds Stable Point

**Symptoms**:
- Pattern stays in RAMP_DOWN phase
- TPS keeps decreasing
- Eventually reaches 0 or COMPLETE phase

**Possible Causes**:
1. Error threshold too low (system always has errors)
2. System fundamentally unstable
3. Ramp decrement too small

**Diagnosis**:
```java
// Check current phase and TPS
AdaptiveLoadPattern.Phase phase = pattern.getCurrentPhase();
double currentTps = pattern.getCurrentTps();
double errorRate = metricsProvider.getFailureRate();

System.out.println("Phase: " + phase);
System.out.println("Current TPS: " + currentTps);
System.out.println("Error rate: " + errorRate + "%");
```

**Solutions**:
1. **Increase error threshold**:
   ```java
   new AdaptiveLoadPattern(
       // ... other params ...
       0.05,  // Increase from 0.01 (1%) to 0.05 (5%)
       metricsProvider
   );
   ```

2. **Check system stability**:
   - Run with static load pattern first
   - Verify system can handle any TPS without errors
   - Check for intermittent issues

3. **Increase ramp decrement**:
   ```java
   new AdaptiveLoadPattern(
       // ... other params ...
       200.0,  // Increase from 100.0 for faster recovery
       metricsProvider
   );
   ```

---

### Issue 5: Memory Leaks or High Memory Usage

**Symptoms**:
- Memory usage grows continuously
- OutOfMemoryError after long runs
- GC pressure visible in metrics

**Possible Causes**:
1. Resources not closed (connections, files)
2. Accumulating data structures
3. ThreadLocal not cleaned up

**Diagnosis**:
```java
// Check memory metrics
var snapshot = metrics.snapshot();
// Check JVM memory metrics in exporters
```

**Solutions**:
1. **Use try-with-resources**:
   ```java
   try (HttpClient client = HttpClient.newHttpClient()) {
       // Use client
   } // Automatically closed
   ```

2. **Clean up in teardown**:
   ```java
   @Override
   public void teardown() throws Exception {
       if (client != null) {
           client.close();
       }
       // Clean up all resources
   }
   ```

3. **Avoid accumulating data**:
   ```java
   // ❌ Bad: Accumulates data
   private final List<String> allResults = new ArrayList<>();
   
   // ✅ Good: Process and discard
   private void processResult(String result) {
       // Process immediately, don't store
   }
   ```

---

### Issue 6: Shutdown Takes Too Long

**Symptoms**:
- Shutdown takes > 10 seconds
- Warnings about timeout
- Tasks still running after shutdown

**Possible Causes**:
1. Tasks take too long to complete
2. Drain timeout too short
3. Tasks don't respect shutdown signal

**Diagnosis**:
```java
// Check shutdown logs
logger.warn("Executor did not terminate gracefully within {}ms", timeout);
```

**Solutions**:
1. **Increase drain timeout**:
   ```java
   ShutdownManager.builder()
       .withDrainTimeout(Duration.ofSeconds(30))  // Increase from 5s
       .build();
   ```

2. **Make tasks interruptible**:
   ```java
   @Override
   public TaskResult execute(long iteration) throws Exception {
       // Check for interruption
       if (Thread.currentThread().isInterrupted()) {
           throw new InterruptedException("Task interrupted");
       }
       // ...
   }
   ```

3. **Set timeouts on operations**:
   ```java
   HttpRequest request = HttpRequest.newBuilder()
       .timeout(Duration.ofSeconds(5))  // Don't wait forever
       .build();
   ```

---

### Issue 7: Metrics Not Accurate

**Symptoms**:
- Percentiles seem wrong
- TPS calculations don't match expectations
- Metrics inconsistent across exporters

**Possible Causes**:
1. Metrics collected during ramp-up (not steady state)
2. Percentile configuration incorrect
3. Metrics snapshot timing issues

**Diagnosis**:
```java
// Check metrics after steady state
// Wait for ramp-up to complete
var snapshot = metrics.snapshot();
System.out.println("Total: " + snapshot.totalExecutions());
System.out.println("Success: " + snapshot.successCount());
System.out.println("Failure: " + snapshot.failureCount());
```

**Solutions**:
1. **Wait for steady state**:
   ```java
   engine.run();
   Thread.sleep(5000); // Wait for metrics to stabilize
   var snapshot = metrics.snapshot();
   ```

2. **Verify percentile configuration**:
   ```java
   MetricsCollector collector = new MetricsCollector(
       new double[]{0.50, 0.95, 0.99}  // Standard percentiles
   );
   ```

3. **Check snapshot timing**:
   ```java
   // Take snapshot after all executions complete
   engine.run();
   var snapshot = metrics.snapshot(); // After run() completes
   ```

---

### Issue 8: Virtual Threads Not Working

**Symptoms**:
- Using @VirtualThreads but seeing platform threads
- No performance improvement
- Thread pool metrics show platform threads

**Possible Causes**:
1. Java version < 21 (virtual threads require Java 21+)
2. Annotation not on task class
3. Configuration overrides annotation

**Diagnosis**:
```java
// Check Java version
System.out.println("Java version: " + System.getProperty("java.version"));

// Check logs
logger.info("Using virtual threads for task: {}", taskClass.getSimpleName());
```

**Solutions**:
1. **Verify Java version**:
   ```bash
   java -version  # Should be 21 or higher
   ```

2. **Check annotation placement**:
   ```java
   @VirtualThreads  // Must be on class, not method
   public class MyTask implements TaskLifecycle {
   ```

3. **Check configuration**:
   ```yaml
   execution:
     defaultThreadPool: VIRTUAL  # Should match annotation
   ```

---

## Debugging Tips

### Enable Trace Logging

```xml
<!-- In logback.xml -->
<logger name="com.vajrapulse.core.engine.TaskExecutor" level="TRACE"/>
<logger name="com.vajrapulse.core.engine.ExecutionEngine" level="DEBUG"/>
```

### Monitor Metrics in Real-Time

```java
// Periodic metrics reporting
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(() -> {
    var snapshot = metrics.snapshot();
    System.out.println("TPS: " + snapshot.responseTps());
    System.out.println("Success rate: " + snapshot.successRate() + "%");
}, 0, 5, TimeUnit.SECONDS);
```

### Use ExecutionEngine.execute() Helper

```java
// Simpler execution with automatic cleanup
AggregatedMetrics results = ExecutionEngine.execute(
    task,
    loadPattern,
    metricsCollector
);
// No need to manage engine lifecycle
```

---

## Performance Tuning

### For High TPS (>10,000 TPS)

1. **Use virtual threads**:
   ```java
   @VirtualThreads
   public class MyTask implements TaskLifecycle {
   ```

2. **Minimize logging**:
   ```java
   // Disable trace logging in production
   // Use INFO level only
   ```

3. **Optimize task code**:
   - Avoid object allocations in hot paths
   - Use connection pooling
   - Batch operations

4. **Tune JVM**:
   ```bash
   -XX:+UseZGC  # For low latency
   -Xmx4g        # Adequate memory
   ```

### For Low Latency Requirements

1. **Use platform threads** (if CPU-bound):
   ```java
   @PlatformThreads(poolSize = Runtime.getRuntime().availableProcessors())
   ```

2. **Reduce ramp intervals**:
   ```java
   Duration.ofSeconds(10)  // Faster adjustments
   ```

3. **Monitor queue depth**:
   ```java
   long queueDepth = engine.getQueueDepth();
   if (queueDepth > 1000) {
       // Queue backing up - may need to reduce TPS
   }
   ```

---

## Getting Help

### Logs

Check logs for error messages:
```bash
# Look for ERROR and WARN level messages
grep -i "error\|warn" logs/vajrapulse.log
```

### Metrics

Export metrics to analyze:
```java
// Export to console
ConsoleMetricsExporter exporter = new ConsoleMetricsExporter();
exporter.export("Final Results", metrics.snapshot());

// Export to OpenTelemetry
OpenTelemetryExporter otelExporter = new OpenTelemetryExporter(...);
otelExporter.export("Final Results", metrics.snapshot());
```

### Reproduce Issue

Create minimal reproduction:
```java
// Minimal test case
public class MinimalRepro {
    public static void main(String[] args) throws Exception {
        TaskLifecycle task = new SimpleTask();
        LoadPattern load = new StaticLoad(10.0, Duration.ofSeconds(5));
        MetricsCollector metrics = new MetricsCollector();
        
        ExecutionEngine.execute(task, load, metrics);
    }
}
```

---

## See Also

- [Adaptive Pattern Usage Guide](ADAPTIVE_PATTERN_USAGE.md)
- [MetricsProvider Implementation Guide](METRICS_PROVIDER_IMPLEMENTATION.md)
- [Logging Strategy](LOGGING_STRATEGY.md)

---

**Last Updated**: 2025-01-XX  
**Next Review**: Before 1.0 release

