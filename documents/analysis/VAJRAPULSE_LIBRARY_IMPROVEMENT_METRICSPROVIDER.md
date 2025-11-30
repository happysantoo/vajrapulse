# VajraPulse Library Improvement: MetricsProvider Integration

## Issue

The `AdaptiveLoadPattern` requires a `MetricsProvider` to get real-time execution metrics (failure rate, total executions) for adaptive load adjustment. However, `MetricsPipeline` doesn't provide a way to access these metrics.

## Current Workaround

We're currently tracking metrics manually by wrapping the task:

```java
// Workaround: Track metrics ourselves
private final AtomicLong totalExecutions = new AtomicLong(0);
private final AtomicLong totalFailures = new AtomicLong(0);

TaskLifecycle trackingTask = new TaskLifecycle() {
    @Override
    public TaskResult execute(long iteration) throws Exception {
        totalExecutions.incrementAndGet();
        try {
            TaskResult result = task.execute(iteration);
            if (result instanceof TaskResult.Failure) {
                totalFailures.incrementAndGet();
            }
            return result;
        } catch (Exception e) {
            totalFailures.incrementAndGet();
            throw e;
        }
    }
    // ... init, teardown
};

MetricsProvider metricsProvider = new MetricsProvider() {
    @Override
    public double getFailureRate() {
        long executions = totalExecutions.get();
        if (executions == 0) return 0.0;
        return (double) totalFailures.get() / executions;
    }
    
    @Override
    public long getTotalExecutions() {
        return totalExecutions.get();
    }
};
```

## Problems with Current Approach

1. **Duplication**: We're duplicating metrics tracking that MetricsPipeline already does internally
2. **Maintenance**: If MetricsPipeline changes how it tracks metrics, our code breaks
3. **Inconsistency**: Our tracked metrics may differ from MetricsPipeline's internal metrics
4. **Complexity**: Requires wrapping the task, adding boilerplate code

## Proposed Library Improvements

### Option 1: MetricsPipeline.getMetricsProvider() (Recommended)

Add a method to MetricsPipeline that returns a MetricsProvider:

```java
public interface MetricsPipeline extends AutoCloseable {
    /**
     * Returns a MetricsProvider that provides real-time metrics from this pipeline.
     * 
     * <p>The MetricsProvider is updated as tasks execute, providing current
     * failure rate and total execution count for use with AdaptiveLoadPattern.
     * 
     * @return a MetricsProvider backed by this pipeline's metrics
     */
    MetricsProvider getMetricsProvider();
}
```

**Usage:**
```java
try (MetricsPipeline pipeline = createMetricsPipeline(otelExporter)) {
    MetricsProvider metricsProvider = pipeline.getMetricsProvider();
    LoadPattern loadPattern = new AdaptiveLoadPattern(..., metricsProvider);
    pipeline.run(task, loadPattern);
}
```

### Option 2: MetricsPipeline.registerMetrics(MeterRegistry)

Register metrics in the provided MeterRegistry:

```java
public interface MetricsPipeline extends AutoCloseable {
    /**
     * Registers pipeline metrics in the provided MeterRegistry.
     * 
     * <p>This allows external code to query metrics using standard Micrometer APIs.
     * Metrics are registered with names like:
     * - vajrapulse.execution.count (with status tag)
     * - vajrapulse.execution.duration
     * 
     * @param registry the MeterRegistry to register metrics in
     */
    void registerMetrics(MeterRegistry registry);
}
```

**Usage:**
```java
try (MetricsPipeline pipeline = createMetricsPipeline(otelExporter)) {
    pipeline.registerMetrics(meterRegistry);
    
    MetricsProvider metricsProvider = new MetricsProvider() {
        @Override
        public double getFailureRate() {
            var successCounter = meterRegistry.find("vajrapulse.execution.count")
                .tag("status", "success").counter();
            var failureCounter = meterRegistry.find("vajrapulse.execution.count")
                .tag("status", "failure").counter();
            // ... calculate failure rate
        }
        // ...
    };
    
    LoadPattern loadPattern = new AdaptiveLoadPattern(..., metricsProvider);
    pipeline.run(task, loadPattern);
}
```

### Option 3: MetricsPipeline.getCurrentMetrics()

Provide a method to get current metrics snapshot:

```java
public interface MetricsPipeline extends AutoCloseable {
    /**
     * Returns current execution metrics snapshot.
     * 
     * @return current metrics including total executions and failures
     */
    ExecutionMetrics getCurrentMetrics();
    
    public interface ExecutionMetrics {
        long getTotalExecutions();
        long getTotalFailures();
        double getFailureRate();
        // ... other metrics
    }
}
```

**Usage:**
```java
try (MetricsPipeline pipeline = createMetricsPipeline(otelExporter)) {
    MetricsProvider metricsProvider = new MetricsProvider() {
        @Override
        public double getFailureRate() {
            return pipeline.getCurrentMetrics().getFailureRate();
        }
        
        @Override
        public long getTotalExecutions() {
            return pipeline.getCurrentMetrics().getTotalExecutions();
        }
    };
    
    LoadPattern loadPattern = new AdaptiveLoadPattern(..., metricsProvider);
    pipeline.run(task, loadPattern);
}
```

## Recommendation

**Option 1 (`getMetricsProvider()`) is recommended** because:
- Simplest API for the common use case
- No need to understand internal metric names
- MetricsProvider is already the abstraction AdaptiveLoadPattern needs
- Clean separation of concerns

## Current Status

- **Library Version**: vajrapulse-worker:0.9.5
- **Workaround**: Manual metrics tracking (see current implementation)
- **Impact**: Functional but not ideal

## Benefits of Library Fix

1. **Cleaner API**: No need to wrap tasks or track metrics manually
2. **Consistency**: Metrics come directly from MetricsPipeline
3. **Maintainability**: Single source of truth for metrics
4. **Better Integration**: Seamless integration with AdaptiveLoadPattern
5. **Less Boilerplate**: Reduces application code complexity

## Example: Ideal Usage After Library Fix

```java
try (MetricsPipeline pipeline = createMetricsPipeline(otelExporter)) {
    // Get MetricsProvider directly from pipeline
    MetricsProvider metricsProvider = pipeline.getMetricsProvider();
    
    // Create adaptive load pattern
    LoadPattern loadPattern = new AdaptiveLoadPattern(
        100.0,                          // initialTps
        10000.0,                        // maxTps
        500.0,                          // stepSize
        Duration.ofSeconds(10),         // stepDuration
        5.0,                           // cooldownSeconds
        Duration.ofSeconds(30),        // stabilityWindow
        0.01,                          // maxFailureRate
        metricsProvider                // ✅ Direct from pipeline
    );
    
    // Run with original task (no wrapping needed)
    var result = pipeline.run(task, loadPattern);
}
```

## Next Steps

1. **Short-term**: Continue using workaround (manual tracking)
2. **Long-term**: Propose library improvement to VajraPulse maintainers
3. **Documentation**: Document this limitation in project docs

## Related Issues

- MetricsPipeline tracks metrics internally but doesn't expose them
- AdaptiveLoadPattern needs MetricsProvider but MetricsPipeline doesn't provide one
- Workaround adds complexity and potential for inconsistency

