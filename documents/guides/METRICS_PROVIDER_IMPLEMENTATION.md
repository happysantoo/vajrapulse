# MetricsProvider Implementation Guide

**Version**: 0.9.5  
**Status**: Implementation Guide

---

## Overview

The `MetricsProvider` interface allows adaptive load patterns to query execution metrics without creating a dependency on the metrics implementation. This guide explains how to implement custom `MetricsProvider` instances.

---

## Interface Contract

```java
public interface MetricsProvider {
    /**
     * Gets the current failure rate as a percentage.
     * 
     * @return failure rate (0.0 to 100.0)
     */
    double getFailureRate();
    
    /**
     * Gets the total number of executions.
     * 
     * @return total execution count
     */
    long getTotalExecutions();
}
```

### Requirements

1. **Thread Safety**: Methods may be called concurrently from multiple threads
2. **Performance**: Methods are called frequently (every `rampInterval`), should be fast
3. **Accuracy**: Values should reflect current execution state
4. **Failure Rate Range**: Must return 0.0 to 100.0 (percentage)

---

## Built-in Implementations

### MetricsProviderAdapter

**Purpose**: Adapts `MetricsCollector` to `MetricsProvider` interface.

**Usage**:
```java
MetricsCollector collector = new MetricsCollector();
MetricsProviderAdapter adapter = new MetricsProviderAdapter(collector);

AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    // ... parameters ...
    adapter
);
```

**Features**:
- Automatic caching (100ms TTL) to avoid expensive `snapshot()` calls
- Thread-safe
- Uses `CachedMetricsProvider` internally

**When to Use**: Standard use case - use this unless you need custom behavior.

### CachedMetricsProvider

**Purpose**: Wraps another `MetricsProvider` with caching to reduce expensive operations.

**Usage**:
```java
MetricsProvider baseProvider = new MyCustomProvider();
MetricsProvider cachedProvider = new CachedMetricsProvider(
    baseProvider, 
    Duration.ofMillis(100)  // TTL
);

AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    // ... parameters ...
    cachedProvider
);
```

**Features**:
- Configurable TTL (default: 100ms)
- Thread-safe double-check locking
- Reduces calls to underlying provider

**When to Use**: When your custom provider has expensive operations (e.g., database queries, network calls).

---

## Custom Implementation Examples

### Example 1: Simple Wrapper

Wrap an existing metrics source:

```java
public class SimpleMetricsProvider implements MetricsProvider {
    private final MetricsCollector collector;
    
    public SimpleMetricsProvider(MetricsCollector collector) {
        this.collector = Objects.requireNonNull(collector);
    }
    
    @Override
    public double getFailureRate() {
        var snapshot = collector.snapshot();
        return snapshot.failureRate();
    }
    
    @Override
    public long getTotalExecutions() {
        return collector.snapshot().totalExecutions();
    }
}
```

**Note**: This is inefficient - calls `snapshot()` twice. Use `CachedMetricsProvider` instead.

### Example 2: Cached Implementation

Implement caching directly:

```java
public class CachedSimpleProvider implements MetricsProvider {
    private final MetricsCollector collector;
    private volatile CachedSnapshot cached;
    private volatile long cacheTimeNanos;
    private static final long CACHE_TTL_NANOS = 100_000_000L; // 100ms
    
    public CachedSimpleProvider(MetricsCollector collector) {
        this.collector = Objects.requireNonNull(collector);
    }
    
    @Override
    public double getFailureRate() {
        return getCachedSnapshot().failureRate();
    }
    
    @Override
    public long getTotalExecutions() {
        return getCachedSnapshot().totalExecutions();
    }
    
    private CachedSnapshot getCachedSnapshot() {
        long now = System.nanoTime();
        CachedSnapshot snapshot = this.cached;
        
        if (snapshot == null || (now - cacheTimeNanos) > CACHE_TTL_NANOS) {
            synchronized (this) {
                snapshot = this.cached;
                if (snapshot == null || (now - cacheTimeNanos) > CACHE_TTL_NANOS) {
                    var metrics = collector.snapshot();
                    snapshot = new CachedSnapshot(metrics.failureRate(), metrics.totalExecutions());
                    this.cached = snapshot;
                    this.cacheTimeNanos = System.nanoTime();
                } else {
                    snapshot = this.cached;
                }
            }
        }
        
        return snapshot;
    }
    
    private record CachedSnapshot(double failureRate, long totalExecutions) {}
}
```

### Example 3: Custom Calculation

Implement custom failure rate calculation:

```java
public class CustomFailureRateProvider implements MetricsProvider {
    private final MetricsCollector collector;
    private final double customThreshold;
    
    public CustomFailureRateProvider(MetricsCollector collector, double customThreshold) {
        this.collector = Objects.requireNonNull(collector);
        this.customThreshold = customThreshold;
    }
    
    @Override
    public double getFailureRate() {
        var snapshot = collector.snapshot();
        
        // Custom calculation: include timeouts as failures
        long total = snapshot.totalExecutions();
        if (total == 0) {
            return 0.0;
        }
        
        long failures = snapshot.failureCount();
        // Add custom logic here (e.g., count slow requests as failures)
        // ...
        
        return (failures * 100.0) / total;
    }
    
    @Override
    public long getTotalExecutions() {
        return collector.snapshot().totalExecutions();
    }
}
```

### Example 4: Aggregated Metrics Provider

Aggregate metrics from multiple sources:

```java
public class AggregatedMetricsProvider implements MetricsProvider {
    private final List<MetricsProvider> providers;
    
    public AggregatedMetricsProvider(List<MetricsProvider> providers) {
        this.providers = new ArrayList<>(providers);
    }
    
    @Override
    public double getFailureRate() {
        // Weighted average of all providers
        double totalFailures = 0.0;
        long totalExecutions = 0;
        
        for (MetricsProvider provider : providers) {
            long executions = provider.getTotalExecutions();
            double failureRate = provider.getFailureRate();
            
            totalFailures += (executions * failureRate / 100.0);
            totalExecutions += executions;
        }
        
        if (totalExecutions == 0) {
            return 0.0;
        }
        
        return (totalFailures * 100.0) / totalExecutions;
    }
    
    @Override
    public long getTotalExecutions() {
        return providers.stream()
            .mapToLong(MetricsProvider::getTotalExecutions)
            .sum();
    }
}
```

---

## Performance Considerations

### Caching Strategy

**Problem**: `snapshot()` is expensive (percentile calculations, map allocations)

**Solution**: Use caching with appropriate TTL

**Recommended TTL**:
- **100ms**: Default, good for most cases
- **50ms**: For very responsive systems
- **200ms**: For slower systems or when performance is critical

**Example**:
```java
// Use built-in cached provider
MetricsProvider cached = new CachedMetricsProvider(
    baseProvider,
    Duration.ofMillis(100)  // 100ms TTL
);
```

### Thread Safety

**Requirement**: Methods may be called concurrently

**Pattern**: Use `volatile` + `synchronized` for caching

```java
private volatile CachedSnapshot cached;
private volatile long cacheTimeNanos;

private CachedSnapshot getCachedSnapshot() {
    long now = System.nanoTime();
    CachedSnapshot snapshot = this.cached;
    
    if (snapshot == null || (now - cacheTimeNanos) > TTL_NANOS) {
        synchronized (this) {
            // Double-check after acquiring lock
            snapshot = this.cached;
            if (snapshot == null || (now - cacheTimeNanos) > TTL_NANOS) {
                // Refresh cache
                snapshot = computeSnapshot();
                this.cached = snapshot;
                this.cacheTimeNanos = System.nanoTime();
            }
        }
    }
    
    return snapshot;
}
```

---

## Testing

### Unit Test Example

```java
public class CustomMetricsProviderTest {
    @Test
    public void testFailureRate() {
        // Setup
        MetricsCollector collector = new MetricsCollector();
        MetricsProvider provider = new MetricsProviderAdapter(collector);
        
        // Record some executions
        collector.record(new ExecutionMetrics(/* success */));
        collector.record(new ExecutionMetrics(/* failure */));
        collector.record(new ExecutionMetrics(/* success */));
        
        // Verify
        double failureRate = provider.getFailureRate();
        assertEquals(33.33, failureRate, 0.1); // ~33%
        
        long total = provider.getTotalExecutions();
        assertEquals(3, total);
    }
}
```

### Thread Safety Test

```java
@Test
public void testConcurrentAccess() throws Exception {
    MetricsProvider provider = new MetricsProviderAdapter(collector);
    int threads = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    
    List<Future<Double>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
        futures.add(executor.submit(() -> provider.getFailureRate()));
    }
    
    // All should return same value (or very close due to caching)
    Set<Double> results = futures.stream()
        .map(f -> {
            try { return f.get(); }
            catch (Exception e) { throw new RuntimeException(e); }
        })
        .collect(Collectors.toSet());
    
    // Results should be consistent
    assertTrue(results.size() <= 2); // Allow for cache refresh
}
```

---

## Best Practices

### 1. Always Use Caching

**✅ Good**:
```java
MetricsProvider cached = new CachedMetricsProvider(baseProvider);
```

**❌ Bad**:
```java
// Calls snapshot() on every access - expensive!
MetricsProvider direct = new DirectProvider(collector);
```

### 2. Ensure Thread Safety

**✅ Good**:
```java
private volatile CachedSnapshot cached;
// Use synchronized for updates
```

**❌ Bad**:
```java
private CachedSnapshot cached; // Not thread-safe!
```

### 3. Handle Edge Cases

**✅ Good**:
```java
@Override
public double getFailureRate() {
    long total = getTotalExecutions();
    if (total == 0) {
        return 0.0; // No executions yet
    }
    // ... calculation
}
```

**❌ Bad**:
```java
@Override
public double getFailureRate() {
    // Division by zero if total == 0!
    return (failures * 100.0) / total;
}
```

### 4. Document Performance Characteristics

**✅ Good**:
```java
/**
 * Cached metrics provider with 100ms TTL.
 * 
 * <p>Performance: O(1) for cached values, O(n) for cache refresh
 * where n is the number of percentile calculations.
 */
public class CachedMetricsProvider implements MetricsProvider {
```

---

## Common Pitfalls

### Pitfall 1: Calling snapshot() Multiple Times

**Problem**:
```java
@Override
public double getFailureRate() {
    return collector.snapshot().failureRate(); // Expensive!
}

@Override
public long getTotalExecutions() {
    return collector.snapshot().totalExecutions(); // Expensive again!
}
```

**Solution**: Cache the snapshot
```java
private CachedSnapshot getSnapshot() {
    // Cache logic here
    var snapshot = collector.snapshot();
    return new CachedSnapshot(snapshot.failureRate(), snapshot.totalExecutions());
}
```

### Pitfall 2: Not Handling Zero Executions

**Problem**:
```java
return (failures * 100.0) / total; // Division by zero!
```

**Solution**: Check for zero
```java
if (total == 0) {
    return 0.0;
}
return (failures * 100.0) / total;
```

### Pitfall 3: Race Conditions in Caching

**Problem**:
```java
if (cached == null) {
    cached = compute(); // Not thread-safe!
}
```

**Solution**: Use synchronized
```java
if (cached == null) {
    synchronized (this) {
        if (cached == null) {
            cached = compute();
        }
    }
}
```

---

## Integration with AdaptiveLoadPattern

### Standard Integration

```java
// Create metrics collector
MetricsCollector collector = new MetricsCollector();

// Create provider (with caching)
MetricsProvider provider = new MetricsProviderAdapter(collector);

// Use with adaptive pattern
AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    // ... parameters ...
    provider
);
```

### Custom Integration

```java
// Create custom provider
MetricsProvider customProvider = new MyCustomProvider(collector);

// Wrap with caching if needed
MetricsProvider cached = new CachedMetricsProvider(
    customProvider,
    Duration.ofMillis(100)
);

// Use with adaptive pattern
AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    // ... parameters ...
    cached
);
```

---

## See Also

- [Adaptive Pattern Usage Guide](ADAPTIVE_PATTERN_USAGE.md)
- [Troubleshooting Guide](TROUBLESHOOTING.md)
- [MetricsProvider JavaDoc](../vajrapulse-api/src/main/java/com/vajrapulse/api/MetricsProvider.java)

---

**Last Updated**: 2025-01-XX  
**Next Review**: Before 1.0 release

