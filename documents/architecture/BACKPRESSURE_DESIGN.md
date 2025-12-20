# Backpressure Design Documentation

**Date**: 2025-01-XX  
**Version**: 0.9.9  
**Status**: Current Design (Simplified - Pattern-Only)

---

## Overview

Backpressure in VajraPulse is a mechanism to detect when the system is overloaded and cannot accept more load. This document explains how backpressure works, how it integrates with AdaptiveLoadPattern, and how to implement custom backpressure providers.

---

## Architecture

### Backpressure Flow

```
┌─────────────────────────────────────────────────────────────┐
│              AdaptiveLoadPattern                              │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  BackpressureProvider (optional)                      │  │
│  │  - QueueBackpressureProvider                          │  │
│  │  - CompositeBackpressureProvider                      │  │
│  │  - Custom implementations (HikariCP, etc.)           │  │
│  └──────────────────────────────────────────────────────┘  │
│                          │                                   │
│                          ▼                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  TPS Adjustment Logic                                 │  │
│  │                                                       │  │
│  │  1. Read backpressure level from provider            │  │
│  │  2. Combine with error rate in RampDecisionPolicy    │  │
│  │  3. Adjust TPS:                                      │  │
│  │     - Ramp Down: If backpressure > threshold         │  │
│  │     - Ramp Up: If backpressure < threshold           │  │
│  │     - Hold: If backpressure is moderate               │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    ExecutionEngine                          │
│                                                              │
│  - Executes tasks at TPS determined by pattern              │
│  - No backpressure handling (pattern makes all decisions)   │
└─────────────────────────────────────────────────────────────┘
```

---

## Components

### 1. BackpressureProvider Interface

**Location**: `com.vajrapulse.api.metrics.BackpressureProvider`

**Purpose**: Provides backpressure signals (0.0 to 1.0) indicating system load.

**Interface**:
```java
public interface BackpressureProvider {
    /**
     * Returns the current backpressure level.
     * 
     * @return backpressure level between 0.0 (none) and 1.0 (maximum)
     */
    double getBackpressureLevel();
    
    /**
     * Optional: Returns a human-readable description.
     * 
     * @return description of backpressure state, or null
     */
    default String getBackpressureDescription() {
        return null;
    }
}
```

**Value Interpretation**:
- **0.0 - 0.3**: Low backpressure (system can handle more load)
- **0.3 - 0.7**: Moderate backpressure (system is stressed)
- **0.7 - 1.0**: High backpressure (system is overloaded)

---

### 2. Built-in Backpressure Providers

#### QueueBackpressureProvider

**Location**: `com.vajrapulse.core.metrics.QueueBackpressureProvider`

**Purpose**: Calculates backpressure based on queue depth.

**Usage**:
```java
BlockingQueue<Request> queue = new LinkedBlockingQueue<>();
QueueBackpressureProvider provider = new QueueBackpressureProvider(
    () -> (long) queue.size(),  // Queue depth supplier
    1000                        // Max queue depth
);

// Backpressure = currentDepth / maxDepth
// Example: 750 items / 1000 max = 0.75 (75% backpressure)
```

**Formula**: `backpressure = min(1.0, currentDepth / maxDepth)`

#### CompositeBackpressureProvider

**Location**: `com.vajrapulse.core.metrics.CompositeBackpressureProvider`

**Purpose**: Combines multiple providers, returns maximum backpressure.

**Usage**:
```java
QueueBackpressureProvider queueProvider = new QueueBackpressureProvider(
    () -> queue.size(), 1000
);

LatencyBackpressureProvider latencyProvider = new LatencyBackpressureProvider(
    metricsProvider, Duration.ofMillis(100), 0.95
);

CompositeBackpressureProvider provider = new CompositeBackpressureProvider(
    queueProvider,
    latencyProvider
);

// Returns max(0.6, 0.8) = 0.8 (80% backpressure)
```

---

**Note**: As of version 0.9.9, backpressure handling has been simplified. Backpressure is now handled entirely within `AdaptiveLoadPattern` through TPS adjustment. The pattern reads backpressure signals and adjusts the target TPS accordingly. `ExecutionEngine` no longer handles backpressure at the request level - it simply executes tasks at the TPS determined by the pattern.

---

## Integration with AdaptiveLoadPattern

### How Backpressure is Used

1. **BackpressureProvider** (optional) is passed to `AdaptiveLoadPattern`
2. Pattern calls `getBackpressureLevel()` periodically
3. Backpressure is combined with error rate in `RampDecisionPolicy`
4. Decisions are made:
   - **Ramp Down**: If backpressure > threshold OR error rate > threshold
   - **Ramp Up**: If backpressure < threshold AND error rate < threshold
   - **Hold**: If backpressure is moderate (between thresholds)

### Configuration

```java
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(100.0)
    .rampIncrement(50.0)
    .rampDecrement(100.0)
    .rampInterval(Duration.ofSeconds(10))
    .maxTps(1000.0)
    .minTps(10.0)
    .sustainDuration(Duration.ofMinutes(10))
    .stableIntervalsRequired(3)
    .metricsProvider(metricsProvider)
    .backpressureProvider(backpressureProvider)  // Optional
    .decisionPolicy(new DefaultRampDecisionPolicy(
        0.01,   // Error threshold (1%)
        0.3,    // Backpressure ramp up threshold (30%)
        0.7     // Backpressure ramp down threshold (70%)
    ))
    .build();
```

### Decision Logic

**Ramp Down Conditions** (OR logic):
- Error rate > `errorThreshold` OR
- Backpressure > `backpressureRampDownThreshold`

**Ramp Up Conditions** (AND logic):
- Error rate < `errorThreshold` AND
- Backpressure < `backpressureRampUpThreshold`

**Hold Conditions**:
- Error rate < `errorThreshold` AND
- Backpressure between `backpressureRampUpThreshold` and `backpressureRampDownThreshold`

---

## Vortex 0.0.9 Integration (Client-Side)

### Architectural Principle

**VajraPulse provides the contract (`BackpressureProvider` interface), not framework-specific implementations.**

This keeps VajraPulse:
- **Framework-agnostic**: No dependencies on vortex, HikariCP, etc.
- **Lightweight**: Core library stays minimal
- **Extensible**: Clients implement providers for their specific infrastructure

**Pattern**: Framework-specific providers belong in client code or examples, not in core.

### Changes in Vortex 0.0.9

- **Removed**: `com.vajrapulse.vortex.backpressure` package
- **New Behavior**: Queue rejection throws exception when queue is full
- **Configuration**: `queueRejectionThreshold` in `BatcherConfig`

### Client-Side Integration Strategy

Since vortex no longer provides backpressure signals, **clients should**:

1. **Catch vortex exceptions** when queue is full
2. **Convert exceptions to backpressure signals**
3. **Implement `BackpressureProvider`** interface
4. **Feed backpressure to AdaptiveLoadPattern**

### Example Implementation (Client-Side)

Here's an example implementation that clients can use as a reference:

```java
package com.example.vortex;  // Client package, not VajraPulse core

import com.vajrapulse.api.metrics.BackpressureProvider;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Example backpressure provider for vortex 0.0.9+.
 * 
 * <p>This is an EXAMPLE implementation showing how to integrate vortex
 * queue rejection exceptions with VajraPulse's backpressure system.
 * 
 * <p><strong>Note:</strong> This example is NOT included in the core VajraPulse
 * distribution to avoid adding vortex as a dependency. Users can copy this
 * example and adapt it to their needs.
 */
public class VortexExceptionBackpressureProvider implements BackpressureProvider {
    private final AtomicLong rejectionCount = new AtomicLong(0);
    private final AtomicLong totalSubmissions = new AtomicLong(0);
    private final AtomicReference<Long> windowStartTime = 
        new AtomicReference<>(System.currentTimeMillis());
    private final long windowMillis;
    
    public VortexExceptionBackpressureProvider() {
        this(10_000L); // 10 second sliding window
    }
    
    public VortexExceptionBackpressureProvider(long windowMillis) {
        this.windowMillis = windowMillis;
    }
    
    /**
     * Records a submission attempt.
     * Call this before attempting to submit to vortex.
     */
    public void recordSubmission() {
        totalSubmissions.incrementAndGet();
        resetWindowIfNeeded();
    }
    
    /**
     * Records a queue rejection exception.
     * Call this when vortex throws a queue rejection exception.
     */
    public void recordRejection() {
        rejectionCount.incrementAndGet();
        resetWindowIfNeeded();
    }
    
    @Override
    public double getBackpressureLevel() {
        resetWindowIfNeeded();
        
        long total = totalSubmissions.get();
        if (total == 0) {
            return 0.0;
        }
        
        long rejections = rejectionCount.get();
        // Backpressure = rejection rate (0.0 to 1.0)
        return Math.min(1.0, (double) rejections / total);
    }
    
    @Override
    public String getBackpressureDescription() {
        long total = totalSubmissions.get();
        long rejections = rejectionCount.get();
        return String.format("Vortex queue rejections: %d/%d (%.1f%%)",
            rejections, total, total > 0 ? (100.0 * rejections / total) : 0.0);
    }
    
    private void resetWindowIfNeeded() {
        long now = System.currentTimeMillis();
        long start = windowStartTime.get();
        
        if (now - start > windowMillis) {
            if (windowStartTime.compareAndSet(start, now)) {
                totalSubmissions.set(0);
                rejectionCount.set(0);
            }
        }
    }
}
```

**Usage Example**:

```java
// Client code - wrap vortex submission
VortexExceptionBackpressureProvider provider = 
    new VortexExceptionBackpressureProvider();

public ItemResult<T> submitWithBackpressure(T item, Callback<T> callback) {
    provider.recordSubmission();
    try {
        return batcher.submit(item, callback);
    } catch (QueueRejectionException e) {
        provider.recordRejection();
        throw e; // Or handle gracefully
    }
}

// Use with AdaptiveLoadPattern
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .metricsProvider(metricsProvider)
    .backpressureProvider(provider)
    .build();
```

**Alternative: Queue Depth-Based Provider**

If vortex exposes queue depth metrics:

```java
public class VortexQueueBackpressureProvider implements BackpressureProvider {
    private final Supplier<Long> queueDepthSupplier;
    private final long maxQueueDepth;
    
    public VortexQueueBackpressureProvider(
        Supplier<Long> queueDepthSupplier,
        long maxQueueDepth
    ) {
        this.queueDepthSupplier = queueDepthSupplier;
        this.maxQueueDepth = maxQueueDepth;
    }
    
    @Override
    public double getBackpressureLevel() {
        long currentDepth = queueDepthSupplier.get();
        if (currentDepth <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) currentDepth / maxQueueDepth);
    }
}
```

---

## Custom Backpressure Providers

### Example: Connection Pool Backpressure

```java
public class ConnectionPoolBackpressureProvider implements BackpressureProvider {
    private final HikariDataSource dataSource;
    
    public ConnectionPoolBackpressureProvider(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public double getBackpressureLevel() {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        int active = pool.getActiveConnections();
        int total = pool.getTotalConnections();
        
        if (total == 0) {
            return 0.0;
        }
        
        // Backpressure = active connections / total connections
        return (double) active / total;
    }
    
    @Override
    public String getBackpressureDescription() {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        return String.format("Connection pool: %d/%d active",
            pool.getActiveConnections(),
            pool.getTotalConnections());
    }
}
```

### Example: Latency-Based Backpressure

```java
public class LatencyBackpressureProvider implements BackpressureProvider {
    private final MetricsProvider metricsProvider;
    private final Duration latencyThreshold;
    private final double percentile;
    
    public LatencyBackpressureProvider(
        MetricsProvider metricsProvider,
        Duration latencyThreshold,
        double percentile
    ) {
        this.metricsProvider = metricsProvider;
        this.latencyThreshold = latencyThreshold;
        this.percentile = percentile;
    }
    
    @Override
    public double getBackpressureLevel() {
        // Get P95 latency from metrics
        double p95Latency = metricsProvider.getPercentileLatency(percentile);
        double thresholdNanos = latencyThreshold.toNanos();
        
        if (p95Latency <= thresholdNanos) {
            return 0.0;
        }
        
        // Backpressure increases as latency exceeds threshold
        // Formula: min(1.0, (actual - threshold) / threshold)
        return Math.min(1.0, (p95Latency - thresholdNanos) / thresholdNanos);
    }
}
```

---

## Best Practices

### 1. Provider Thread Safety

BackpressureProvider methods may be called from multiple threads. Ensure thread-safe implementations:

```java
public class ThreadSafeBackpressureProvider implements BackpressureProvider {
    private final AtomicLong rejectionCount = new AtomicLong(0);
    
    @Override
    public double getBackpressureLevel() {
        // Use atomic operations
        long count = rejectionCount.get();
        // ... calculate backpressure
    }
}
```

### 2. Caching for Performance

Providers may be called frequently. Cache values when appropriate:

```java
public class CachedBackpressureProvider implements BackpressureProvider {
    private volatile double cachedValue = 0.0;
    private volatile long cacheTime = 0L;
    private static final long CACHE_TTL_MS = 100L; // 100ms cache
    
    @Override
    public double getBackpressureLevel() {
        long now = System.currentTimeMillis();
        if (now - cacheTime > CACHE_TTL_MS) {
            cachedValue = calculateBackpressure();
            cacheTime = now;
        }
        return cachedValue;
    }
}
```

### 3. Combining Multiple Signals

Use `CompositeBackpressureProvider` to combine multiple sources:

```java
CompositeBackpressureProvider provider = new CompositeBackpressureProvider(
    new QueueBackpressureProvider(() -> queue.size(), 1000),
    new ConnectionPoolBackpressureProvider(dataSource),
    new LatencyBackpressureProvider(metrics, Duration.ofMillis(100), 0.95)
);
```

---

## Migration from Vortex Backpressure Package

### Before (Vortex < 0.0.9)

```java
// Old: Used vortex backpressure package
import com.vajrapulse.vortex.backpressure.BackpressureHandler;
import com.vajrapulse.vortex.backpressure.BackpressureProvider;

BackpressureProvider provider = vortex.getBackpressureProvider();
```

### After (Vortex 0.0.9+)

```java
// New: Use VajraPulse backpressure providers
import com.vajrapulse.api.metrics.BackpressureProvider;
import com.vajrapulse.core.metrics.QueueBackpressureProvider;

// Option 1: Use built-in providers
BackpressureProvider provider = new QueueBackpressureProvider(
    () -> queue.size(), 1000
);

// Option 2: Create custom provider that tracks vortex exceptions
BackpressureProvider provider = new VortexExceptionBackpressureProvider();
```

---

## Troubleshooting

### Backpressure Always 0.0

**Symptoms**: `getBackpressureLevel()` always returns 0.0

**Causes**:
- Provider not configured
- Provider not tracking rejections properly
- Queue depth always 0

**Solutions**:
- Verify provider is passed to `AdaptiveLoadPattern`
- Check provider implementation
- Verify queue depth supplier is working

### Backpressure Too High

**Symptoms**: Pattern ramps down too aggressively

**Causes**:
- Thresholds too low
- Provider calculating incorrectly
- Multiple providers in composite returning high values

**Solutions**:
- Adjust `backpressureRampDownThreshold` (increase from 0.7 to 0.8)
- Review provider calculation logic
- Check individual providers in composite

### Backpressure Not Affecting Ramp Decisions

**Symptoms**: Pattern ignores backpressure

**Causes**:
- Provider not configured
- Error rate overriding backpressure
- Decision policy not using backpressure

**Solutions**:
- Verify provider is passed to pattern
- Check `RampDecisionPolicy` implementation
- Review decision logic (AND vs OR conditions)

---

## References

- `com.vajrapulse.api.metrics.BackpressureProvider` - Provider interface
- `com.vajrapulse.api.metrics.BackpressureHandler` - Handler interface
- `com.vajrapulse.core.metrics.BackpressureHandlers` - Built-in handlers
- `com.vajrapulse.core.metrics.QueueBackpressureProvider` - Queue-based provider
- `com.vajrapulse.core.metrics.CompositeBackpressureProvider` - Composite provider
- `com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern` - Pattern integration

---

## Future Enhancements

### Potential Improvements

1. **Vortex Integration**: Create `VortexBackpressureProvider` that tracks queue rejections
2. **Metrics-Based Providers**: More built-in providers (latency, throughput, etc.)
3. **Adaptive Thresholds**: Dynamic threshold adjustment based on system state
4. **Backpressure History**: Track backpressure over time for analysis
5. **Health Checks**: Integrate with health check systems

---

**Last Updated**: 2025-12-12  
**Version**: 0.9.9

