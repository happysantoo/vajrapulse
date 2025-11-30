# Backpressure Provider Interface - Implementation Plan

**Release**: 0.9.6  
**Priority**: HIGH  
**Source**: User Wishlist Item #1 (Revised Approach)  
**Effort**: 2-3 days

## Problem Statement

Currently, `AdaptiveLoadPattern` only uses error rate to detect backpressure. However:
- Connection pools (HikariCP, Apache HttpClient, etc.) already provide their own metrics
- Users may want to detect backpressure based on custom signals (queue depth, latency, etc.)
- We shouldn't duplicate what connection pools already do
- We need a flexible way to integrate with existing metrics infrastructure

**Key Insight**: Instead of tracking connection pool metrics ourselves, provide an interface for users to report backpressure signals from their existing infrastructure.

## Solution Overview

Create a `BackpressureProvider` interface that:
1. Allows users to report custom backpressure signals
2. Integrates with existing connection pool metrics (HikariCP, Apache HttpClient, etc.)
3. Works alongside `MetricsProvider` (error rate) in `AdaptiveLoadPattern`
4. Provides extensible way to detect backpressure from any source

**Design Philosophy**: 
- Don't reinvent the wheel - leverage existing metrics
- Provide extensibility - users define their own backpressure signals
- Framework provides the mechanism, users provide the signals
- Handle request loss gracefully - provide strategies for backpressure situations

## Design

### 1. BackpressureProvider Interface

```java
/**
 * Provides backpressure signals for adaptive load patterns.
 * 
 * <p>Implementations can integrate with existing metrics infrastructure
 * (connection pools, queues, custom metrics) to report backpressure.
 * 
 * <p>Backpressure is reported as a value between 0.0 (no backpressure)
 * and 1.0 (maximum backpressure). The adaptive pattern uses this signal
 * along with error rate to determine when to ramp down.
 * 
 * <p>Example implementations:
 * <ul>
 *   <li>Connection pool utilization (HikariCP, Apache HttpClient)</li>
 *   <li>Queue depth thresholds</li>
 *   <li>Latency-based backpressure</li>
 *   <li>Custom business logic</li>
 * </ul>
 * 
 * @since 0.9.6
 */
public interface BackpressureProvider {
    /**
     * Returns the current backpressure level.
     * 
     * <p>Value interpretation:
     * <ul>
     *   <li>0.0 - 0.3: Low backpressure (system can handle more load)</li>
     *   <li>0.3 - 0.7: Moderate backpressure (system is stressed)</li>
     *   <li>0.7 - 1.0: High backpressure (system is overloaded)</li>
     * </ul>
     * 
     * <p>This method may be called frequently, so implementations should
     * cache values or use efficient lookups.
     * 
     * @return backpressure level between 0.0 (none) and 1.0 (maximum)
     */
    double getBackpressureLevel();
    
    /**
     * Optional: Returns a human-readable description of current backpressure.
     * 
     * <p>Used for logging and debugging. Returns null if not available.
     * 
     * @return description of backpressure state, or null
     */
    default String getBackpressureDescription() {
        return null;
    }
}
```

### 1b. BackpressureHandler Interface (Request Loss Handling)

```java
/**
 * Defines how to handle requests when backpressure is detected.
 * 
 * <p>When backpressure is detected (via BackpressureProvider), the framework
 * needs to decide what to do with requests that cannot be processed immediately.
 * This interface provides extensible strategies for handling request loss.
 * 
 * <p>Built-in strategies:
 * <ul>
 *   <li>{@link BackpressureHandlers#DROP} - Skip requests silently</li>
 *   <li>{@link BackpressureHandlers#QUEUE} - Buffer requests (default)</li>
 *   <li>{@link BackpressureHandlers#REJECT} - Fail fast with error</li>
 *   <li>{@link BackpressureHandlers#RETRY} - Retry after delay</li>
 *   <li>{@link BackpressureHandlers#DEGRADE} - Reduce request quality</li>
 * </ul>
 * 
 * @since 0.9.6
 */
public interface BackpressureHandler {
    /**
     * Handles a request when backpressure is detected.
     * 
     * <p>This method is called when:
     * <ul>
     *   <li>Backpressure level exceeds threshold (configurable, default 0.7)</li>
     *   <li>Executor queue is full</li>
     *   <li>Connection pool is exhausted</li>
     *   <li>Other backpressure conditions are met</li>
     * </ul>
     * 
     * @param iteration the iteration number
     * @param backpressureLevel the current backpressure level (0.0 to 1.0)
     * @param context additional context (queue depth, connection pool state, etc.)
     * @return handling result indicating what action was taken
     */
    HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context);
    
    /**
     * Result of handling a request during backpressure.
     */
    enum HandlingResult {
        /** Request was dropped (not executed) */
        DROPPED,
        /** Request was queued (will be executed later) */
        QUEUED,
        /** Request was rejected (failed immediately) */
        REJECTED,
        /** Request will be retried */
        RETRY,
        /** Request was degraded (reduced quality) */
        DEGRADED,
        /** Request was accepted (normal processing) */
        ACCEPTED
    }
    
    /**
     * Context information available during backpressure handling.
     */
    record BackpressureContext(
        long queueDepth,
        long maxQueueDepth,
        long activeConnections,
        long maxConnections,
        double errorRate,
        Map<String, Object> customMetrics
    ) {}
}
```

### 1c. Built-in Backpressure Handlers

```java
/**
 * Factory for built-in backpressure handlers.
 */
public final class BackpressureHandlers {
    
    /**
     * DROP handler: Silently skips requests when backpressure is detected.
     * 
     * <p>Use when:
     * <ul>
     *   <li>Test accuracy is more important than request completion</li>
     *   <li>You want to measure system capacity, not overload behavior</li>
     *   <li>Dropped requests should not be counted as failures</li>
     * </ul>
     */
    public static BackpressureHandler DROP = new BackpressureHandler() {
        @Override
        public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
            // Skip this request - don't submit to executor
            return HandlingResult.DROPPED;
        }
    };
    
    /**
     * QUEUE handler: Buffers requests when backpressure is detected (default behavior).
     * 
     * <p>Use when:
     * <ul>
     *   <li>You want to test system behavior under sustained load</li>
     *   <li>All requests should eventually be processed</li>
     *   <li>Queue depth is acceptable</li>
     * </ul>
     */
    public static BackpressureHandler QUEUE = new BackpressureHandler() {
        @Override
        public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
            // Queue the request (current default behavior)
            return HandlingResult.QUEUED;
        }
    };
    
    /**
     * REJECT handler: Fails requests immediately when backpressure is detected.
     * 
     * <p>Use when:
     * <ul>
     *   <li>You want to simulate "fail fast" behavior</li>
     *   <li>You want to test client-side error handling</li>
     *   <li>You want to prevent queue buildup</li>
     * </ul>
     */
    public static BackpressureHandler REJECT = new BackpressureHandler() {
        @Override
        public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
            // Reject immediately - record as failure
            return HandlingResult.REJECTED;
        }
    };
    
    /**
     * RETRY handler: Retries requests after a delay when backpressure is detected.
     * 
     * <p>Use when:
     * <ul>
     *   <li>You want to test retry logic</li>
     *   <li>Transient backpressure is expected</li>
     *   <li>You want to maximize request completion</li>
     * </ul>
     */
    public static BackpressureHandler retry(Duration retryDelay, int maxRetries) {
        return new RetryBackpressureHandler(retryDelay, maxRetries);
    }
    
    /**
     * DEGRADE handler: Reduces request quality when backpressure is detected.
     * 
     * <p>Use when:
     * <ul>
     *   <li>You want to test graceful degradation</li>
     *   <li>You want to maintain throughput at reduced quality</li>
     *   <li>You want to test adaptive quality mechanisms</li>
     * </ul>
     */
    public static BackpressureHandler DEGRADE = new BackpressureHandler() {
        @Override
        public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
            // Mark for degraded processing
            return HandlingResult.DEGRADED;
        }
    };
    
    /**
     * THRESHOLD handler: Uses different strategies based on backpressure level.
     * 
     * <p>Example:
     * <ul>
     *   <li>0.0 - 0.5: ACCEPT (normal processing)</li>
     *   <li>0.5 - 0.7: QUEUE (buffer requests)</li>
     *   <li>0.7 - 0.9: REJECT (fail fast)</li>
     *   <li>0.9 - 1.0: DROP (skip requests)</li>
     * </ul>
     */
    public static BackpressureHandler threshold(
        double queueThreshold,
        double rejectThreshold,
        double dropThreshold
    ) {
        return new ThresholdBackpressureHandler(queueThreshold, rejectThreshold, dropThreshold);
    }
}
```

### 2. AdaptiveLoadPattern Enhancement

**Update AdaptiveLoadPattern to accept optional BackpressureProvider**:

```java
public final class AdaptiveLoadPattern implements LoadPattern {
    private final MetricsProvider metricsProvider;
    private final BackpressureProvider backpressureProvider; // Optional
    
    public AdaptiveLoadPattern(
        double initialTps,
        double rampIncrement,
        double rampDecrement,
        Duration rampInterval,
        double maxTps,
        Duration sustainDuration,
        double errorThreshold,
        MetricsProvider metricsProvider,
        BackpressureProvider backpressureProvider  // Optional, can be null
    ) {
        // ... existing validation ...
        this.metricsProvider = metricsProvider;
        this.backpressureProvider = backpressureProvider;
    }
    
    // Convenience constructor without backpressure provider
    public AdaptiveLoadPattern(
        double initialTps,
        double rampIncrement,
        double rampDecrement,
        Duration rampInterval,
        double maxTps,
        Duration sustainDuration,
        double errorThreshold,
        MetricsProvider metricsProvider
    ) {
        this(initialTps, rampIncrement, rampDecrement, rampInterval, 
             maxTps, sustainDuration, errorThreshold, metricsProvider, null);
    }
    
    private void checkAndAdjust(long elapsedMillis) {
        double errorRate = getErrorRate(elapsedMillis);
        double backpressure = getBackpressureLevel(elapsedMillis);
        
        // Combine error rate and backpressure signals
        // Ramp down if either exceeds threshold
        boolean shouldRampDown = errorRate >= errorThreshold || backpressure >= 0.7;
        
        // ... rest of logic ...
    }
    
    private double getBackpressureLevel(long elapsedMillis) {
        if (backpressureProvider == null) {
            return 0.0; // No backpressure signal available
        }
        return backpressureProvider.getBackpressureLevel();
    }
}
```

### 3. ExecutionEngine Integration

**Update ExecutionEngine to use BackpressureHandler**:

```java
public final class ExecutionEngine implements AutoCloseable {
    private final BackpressureHandler backpressureHandler; // Optional
    private final double backpressureThreshold; // Default: 0.7
    
    // In run() method:
    while (!stopRequested.get() && rateController.getElapsedMillis() < testDurationMillis) {
        rateController.waitForNext();
        
        // Check backpressure before submitting
        double backpressure = getBackpressureLevel();
        if (backpressureHandler != null && backpressure >= backpressureThreshold) {
            BackpressureContext context = new BackpressureContext(
                pendingExecutions.get(),
                maxQueueDepth,
                activeConnections,
                maxConnections,
                metricsCollector.snapshot().failureRate(),
                Map.of() // Custom metrics
            );
            
            BackpressureHandler.HandlingResult result = 
                backpressureHandler.handle(iteration, backpressure, context);
            
            switch (result) {
                case DROPPED:
                    // Skip this request - don't submit
                    logger.debug("Request {} dropped due to backpressure {:.2f}", iteration, backpressure);
                    continue; // Skip to next iteration
                    
                case REJECTED:
                    // Fail immediately - record as failure
                    metricsCollector.record(new ExecutionMetrics(
                        System.nanoTime(),
                        System.nanoTime(),
                        TaskResult.failure(new BackpressureException("Request rejected due to backpressure")),
                        iteration
                    ));
                    continue;
                    
                case RETRY:
                    // Retry after delay (implement retry logic)
                    // For now, queue it
                    // TODO: Implement retry mechanism
                    break;
                    
                case DEGRADED:
                    // Mark for degraded processing
                    // TODO: Implement degradation mechanism
                    break;
                    
                case QUEUED:
                case ACCEPTED:
                default:
                    // Normal processing - submit to executor
                    break;
            }
        }
        
        // Submit request to executor
        executor.submit(new ExecutionCallable(...));
    }
    
    private double getBackpressureLevel() {
        // Get backpressure from AdaptiveLoadPattern if available
        if (loadPattern instanceof AdaptiveLoadPattern adaptive) {
            // Access backpressure provider if available
            // This requires exposing getBackpressureLevel() in AdaptiveLoadPattern
        }
        return 0.0; // No backpressure signal
    }
}
```

### 3. Built-in Backpressure Providers

**Connection Pool Backpressure Provider** (for HikariCP):

```java
/**
 * Backpressure provider based on HikariCP connection pool metrics.
 */
public class HikariCpBackpressureProvider implements BackpressureProvider {
    private final HikariDataSource dataSource;
    private final double utilizationThreshold; // e.g., 0.8 = 80% utilization
    
    public HikariCpBackpressureProvider(HikariDataSource dataSource, double utilizationThreshold) {
        this.dataSource = dataSource;
        this.utilizationThreshold = utilizationThreshold;
    }
    
    @Override
    public double getBackpressureLevel() {
        HikariPoolMXBean poolBean = dataSource.getHikariPoolMXBean();
        if (poolBean == null) {
            return 0.0;
        }
        
        int active = poolBean.getActiveConnections();
        int total = poolBean.getTotalConnections();
        
        if (total == 0) {
            return 0.0;
        }
        
        double utilization = (double) active / total;
        
        // Return backpressure based on utilization
        // 0.0 at threshold, 1.0 at 100% utilization
        if (utilization < utilizationThreshold) {
            return 0.0;
        } else {
            return Math.min(1.0, (utilization - utilizationThreshold) / (1.0 - utilizationThreshold));
        }
    }
    
    @Override
    public String getBackpressureDescription() {
        HikariPoolMXBean poolBean = dataSource.getHikariPoolMXBean();
        if (poolBean == null) {
            return "HikariCP pool not available";
        }
        return String.format("HikariCP: %d/%d connections active (%.1f%% utilization)",
            poolBean.getActiveConnections(),
            poolBean.getTotalConnections(),
            getBackpressureLevel() * 100.0);
    }
}
```

**Queue-Based Backpressure Provider**:

```java
/**
 * Backpressure provider based on queue depth.
 */
public class QueueBackpressureProvider implements BackpressureProvider {
    private final java.util.function.Supplier<Long> queueDepthSupplier;
    private final long maxQueueDepth;
    
    public QueueBackpressureProvider(
        java.util.function.Supplier<Long> queueDepthSupplier,
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
    
    @Override
    public String getBackpressureDescription() {
        return String.format("Queue depth: %d/%d (%.1f%% backpressure)",
            queueDepthSupplier.get(),
            maxQueueDepth,
            getBackpressureLevel() * 100.0);
    }
}
```

**Composite Backpressure Provider**:

```java
/**
 * Combines multiple backpressure providers (takes maximum).
 */
public class CompositeBackpressureProvider implements BackpressureProvider {
    private final List<BackpressureProvider> providers;
    
    public CompositeBackpressureProvider(BackpressureProvider... providers) {
        this.providers = List.of(providers);
    }
    
    @Override
    public double getBackpressureLevel() {
        return providers.stream()
            .mapToDouble(BackpressureProvider::getBackpressureLevel)
            .max()
            .orElse(0.0);
    }
    
    @Override
    public String getBackpressureDescription() {
        return providers.stream()
            .map(BackpressureProvider::getBackpressureDescription)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.joining("; "));
    }
}
```

### 4. Integration with AdaptiveLoadPattern

**Enhanced Decision Logic**:

```java
private void checkAndAdjust(long elapsedMillis) {
    double errorRate = getErrorRate(elapsedMillis);
    double backpressure = getBackpressureLevel(elapsedMillis);
    
    // Decision logic:
    // - Ramp down if error rate exceeds threshold OR backpressure is high
    // - Ramp up only if both error rate is low AND backpressure is low
    boolean shouldRampDown = errorRate >= errorThreshold || backpressure >= 0.7;
    boolean canRampUp = errorRate < errorThreshold && backpressure < 0.3;
    
    state.updateAndGet(current -> {
        return switch (current.phase()) {
            case RAMP_UP -> {
                if (shouldRampDown) {
                    // Backpressure or errors detected
                    double newTps = Math.max(0, current.currentTps() - rampDecrement);
                    yield transitionPhaseInternal(current, Phase.RAMP_DOWN, elapsedMillis, current.stableTps(), newTps);
                } else if (canRampUp) {
                    // No backpressure, no errors - continue ramping up
                    double newTps = Math.min(maxTps, current.currentTps() + rampIncrement);
                    yield new AdaptiveState(/* ... */);
                } else {
                    // Moderate backpressure - hold current TPS
                    yield new AdaptiveState(/* ... */);
                }
            }
            // ... rest of phases ...
        };
    });
}
```

## Implementation Steps

### Phase 1: Core Interfaces (Day 1)

1. **Create BackpressureProvider interface**
   - Create `BackpressureProvider.java` in `vajrapulse-api/src/main/java/com/vajrapulse/api/`
   - Define `getBackpressureLevel()` method
   - Add optional `getBackpressureDescription()` method
   - Add JavaDoc with usage examples

2. **Create BackpressureHandler interface**
   - Create `BackpressureHandler.java` in `vajrapulse-api/src/main/java/com/vajrapulse/api/`
   - Define `handle()` method with `HandlingResult` enum
   - Create `BackpressureContext` record
   - Add JavaDoc with strategy explanations

3. **Update AdaptiveLoadPattern**
   - Add optional `BackpressureProvider` parameter
   - Add convenience constructor without backpressure provider
   - Update `checkAndAdjust()` to use backpressure signal
   - Combine error rate and backpressure in decision logic
   - Expose `getBackpressureLevel()` for ExecutionEngine

4. **Unit Tests**
   - Test AdaptiveLoadPattern with BackpressureProvider
   - Test decision logic with various backpressure levels
   - Test null backpressure provider (backward compatibility)
   - Test BackpressureHandler interface

### Phase 2: Built-in Providers & Handlers (Day 2)

1. **Create Built-in Providers**
   - `HikariCpBackpressureProvider` - For HikariCP connection pools
   - `QueueBackpressureProvider` - For queue depth
   - `CompositeBackpressureProvider` - Combines multiple providers
   - Add to `vajrapulse-core` module

2. **Create Built-in Handlers**
   - `DropBackpressureHandler` - DROP strategy
   - `QueueBackpressureHandler` - QUEUE strategy (default)
   - `RejectBackpressureHandler` - REJECT strategy
   - `RetryBackpressureHandler` - RETRY strategy
   - `DegradeBackpressureHandler` - DEGRADE strategy
   - `ThresholdBackpressureHandler` - Threshold-based strategy
   - `BackpressureHandlers` factory class

3. **Helper Utilities**
   - `BackpressureProviders` factory class with static methods
   - Convenience methods for common scenarios

4. **Unit Tests**
   - Test each built-in provider
   - Test each built-in handler
   - Test composite provider
   - Test threshold handler
   - Test edge cases

### Phase 3: ExecutionEngine Integration (Day 2-3)

1. **Update ExecutionEngine**
   - Add optional `BackpressureHandler` parameter to builder
   - Add `backpressureThreshold` configuration (default: 0.7)
   - Integrate backpressure checking before request submission
   - Handle different `HandlingResult` types
   - Track dropped/rejected requests in metrics

2. **Metrics Enhancement**
   - Add dropped request counter
   - Add rejected request counter
   - Add retry counter
   - Add degraded request counter
   - Include in `AggregatedMetrics`

### Phase 4: Example Integration (Day 3)

1. **Update Adaptive Load Test Example**
   - Add backpressure provider example
   - Show integration with connection pool
   - Demonstrate composite provider
   - Show different handling strategies

2. **Create Backpressure Example**
   - Standalone example showing:
     - Custom backpressure provider
     - Integration with HikariCP
     - Queue-based backpressure
     - Different handling strategies (DROP, REJECT, RETRY)
     - Threshold-based handling

3. **Documentation**
   - Update user guide
   - Add backpressure provider guide
   - Add backpressure handler guide
   - Create examples
   - Add troubleshooting section

## Example Usage

### Example 1: HikariCP Connection Pool Backpressure

```java
@VirtualThreads
public class DatabaseLoadTest implements TaskLifecycle {
    private HikariDataSource dataSource;
    
    @Override
    public void init() throws Exception {
        dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:postgresql://localhost/test");
        dataSource.setMaximumPoolSize(100);
        // ... configure dataSource ...
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        // ... database operations ...
        return TaskResult.success(result);
    }
    
    @Override
    public void teardown() throws Exception {
        dataSource.close();
    }
}

// Usage with AdaptiveLoadPattern
HikariCpBackpressureProvider backpressureProvider = 
    new HikariCpBackpressureProvider(dataSource, 0.8); // 80% utilization threshold

AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    10.0, 15.0, 15.0, Duration.ofSeconds(5),
    200.0, Duration.ofSeconds(30), 0.10,
    metricsProvider,
    backpressureProvider  // Adaptive pattern will ramp down on connection pool exhaustion
);
```

### Example 2: Queue-Based Backpressure

```java
@VirtualThreads
public class QueueLoadTest implements TaskLifecycle {
    private final BlockingQueue<Request> requestQueue = new LinkedBlockingQueue<>();
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        requestQueue.put(new Request(...));
        // ... process request ...
        return TaskResult.success();
    }
}

// Usage with AdaptiveLoadPattern
QueueBackpressureProvider backpressureProvider = 
    new QueueBackpressureProvider(
        () -> (long) requestQueue.size(),  // Queue depth supplier
        1000  // Max queue depth
    );

AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    10.0, 15.0, 15.0, Duration.ofSeconds(5),
    200.0, Duration.ofSeconds(30), 0.10,
    metricsProvider,
    backpressureProvider  // Adaptive pattern will ramp down when queue fills
);
```

### Example 3: Custom Backpressure Provider

```java
/**
 * Custom backpressure provider based on latency percentiles.
 */
public class LatencyBackpressureProvider implements BackpressureProvider {
    private final MetricsProvider metricsProvider;
    private final Duration maxLatency;
    private final double percentile; // e.g., 0.95 for P95
    
    public LatencyBackpressureProvider(
        MetricsProvider metricsProvider,
        Duration maxLatency,
        double percentile
    ) {
        this.metricsProvider = metricsProvider;
        this.maxLatency = maxLatency;
        this.percentile = percentile;
    }
    
    @Override
    public double getBackpressureLevel() {
        // Get latency percentile from metrics
        var snapshot = metricsProvider.snapshot();
        Double latencyNanos = snapshot.successPercentiles().get(percentile);
        
        if (latencyNanos == null || latencyNanos <= 0) {
            return 0.0; // No data yet
        }
        
        double latencyMs = latencyNanos / 1_000_000.0;
        double maxLatencyMs = maxLatency.toMillis();
        
        // Return backpressure based on how much latency exceeds threshold
        if (latencyMs < maxLatencyMs) {
            return 0.0;
        } else {
            // Scale: 0.0 at threshold, 1.0 at 2x threshold
            return Math.min(1.0, (latencyMs - maxLatencyMs) / maxLatencyMs);
        }
    }
    
    @Override
    public String getBackpressureDescription() {
        return String.format("Latency P%.0f: %.1fms (threshold: %.1fms)",
            percentile * 100,
            getBackpressureLevel() * 100.0,
            maxLatency.toMillis());
    }
}

// Usage
LatencyBackpressureProvider backpressureProvider = 
    new LatencyBackpressureProvider(
        metricsProvider,
        Duration.ofMillis(100),  // Max latency: 100ms
        0.95  // P95 percentile
    );
```

### Example 4: Composite Backpressure (Multiple Signals)

```java
// Combine connection pool and queue backpressure
CompositeBackpressureProvider backpressureProvider = 
    new CompositeBackpressureProvider(
        new HikariCpBackpressureProvider(dataSource, 0.8),
        new QueueBackpressureProvider(() -> queue.size(), 1000),
        new LatencyBackpressureProvider(metricsProvider, Duration.ofMillis(100), 0.95)
    );

// Adaptive pattern ramps down if ANY provider reports high backpressure
AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    10.0, 15.0, 15.0, Duration.ofSeconds(5),
    200.0, Duration.ofSeconds(30), 0.10,
    metricsProvider,
    backpressureProvider
);
```

### Example 5: Backpressure Handling Strategies

```java
// Strategy 1: Drop requests when backpressure is high
ExecutionEngine engine = ExecutionEngine.builder()
    .withTask(task)
    .withLoadPattern(pattern)
    .withMetricsCollector(metrics)
    .withBackpressureHandler(BackpressureHandlers.DROP)  // Skip requests
    .withBackpressureThreshold(0.7)  // Drop when backpressure >= 70%
    .build();

// Strategy 2: Reject requests (fail fast)
ExecutionEngine engine = ExecutionEngine.builder()
    .withTask(task)
    .withLoadPattern(pattern)
    .withMetricsCollector(metrics)
    .withBackpressureHandler(BackpressureHandlers.REJECT)  // Fail immediately
    .withBackpressureThreshold(0.8)  // Reject when backpressure >= 80%
    .build();

// Strategy 3: Threshold-based (different strategies at different levels)
BackpressureHandler handler = BackpressureHandlers.threshold(
    0.5,  // Queue when backpressure >= 50%
    0.7,  // Reject when backpressure >= 70%
    0.9   // Drop when backpressure >= 90%
);

ExecutionEngine engine = ExecutionEngine.builder()
    .withTask(task)
    .withLoadPattern(pattern)
    .withMetricsCollector(metrics)
    .withBackpressureHandler(handler)
    .build();

// Strategy 4: Retry with backoff
BackpressureHandler handler = BackpressureHandlers.retry(
    Duration.ofSeconds(1),  // Retry after 1 second
    3  // Max 3 retries
);

ExecutionEngine engine = ExecutionEngine.builder()
    .withTask(task)
    .withLoadPattern(pattern)
    .withMetricsCollector(metrics)
    .withBackpressureHandler(handler)
    .build();
```

### Example 6: Custom Backpressure Handler

```java
/**
 * Custom handler that drops requests probabilistically based on backpressure.
 */
public class ProbabilisticDropHandler implements BackpressureHandler {
    @Override
    public HandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
        // Drop requests with probability equal to backpressure level
        // e.g., 70% backpressure = 70% chance of dropping
        if (Math.random() < backpressureLevel) {
            return HandlingResult.DROPPED;
        }
        return HandlingResult.ACCEPTED;
    }
}

// Usage
ExecutionEngine engine = ExecutionEngine.builder()
    .withTask(task)
    .withLoadPattern(pattern)
    .withMetricsCollector(metrics)
    .withBackpressureHandler(new ProbabilisticDropHandler())
    .build();
```

## Backpressure Output Example

```
Adaptive Pattern State:
  Phase: RAMP_DOWN
  Current TPS: 85.0
  Error Rate: 2.5% (below 10% threshold)
  Backpressure: 0.75 (HIGH)
  Backpressure Details: HikariCP: 85/100 connections active (85.0% utilization)
  Reason: High backpressure detected - ramping down

Request Handling:
  Backpressure Handler: DROP
  Backpressure Threshold: 0.70
  Requests Dropped: 234 (12.5% of total)
  Requests Queued: 0
  Requests Rejected: 0
```

## Testing Strategy

1. **Unit Tests**
   - Test BackpressureProvider interface
   - Test AdaptiveLoadPattern with BackpressureProvider
   - Test built-in providers (HikariCP, Queue, Composite)
   - Test decision logic with various backpressure levels

2. **Integration Tests**
   - Test with real HikariCP connection pool
   - Test with real queue
   - Test composite provider
   - Test adaptive pattern behavior with backpressure

3. **Example Tests**
   - Verify examples work with backpressure providers
   - Verify adaptive pattern responds to backpressure

## Files to Create/Modify

### New Files
- `vajrapulse-api/src/main/java/com/vajrapulse/api/BackpressureProvider.java`
- `vajrapulse-api/src/main/java/com/vajrapulse/api/BackpressureHandler.java`
- `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/HikariCpBackpressureProvider.java`
- `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/QueueBackpressureProvider.java`
- `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/CompositeBackpressureProvider.java`
- `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/BackpressureProviders.java` (factory)
- `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/DropBackpressureHandler.java`
- `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/RejectBackpressureHandler.java`
- `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/RetryBackpressureHandler.java`
- `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/ThresholdBackpressureHandler.java`
- `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/BackpressureHandlers.java` (factory)
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/BackpressureProviderSpec.groovy`
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/BackpressureHandlerSpec.groovy`
- `vajrapulse-core/src/test/groovy/com/vajrapulse/core/backpressure/*Spec.groovy`

### Modified Files
- `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java` - Add backpressure support
- `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java` - Add backpressure handling
- `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/MetricsCollector.java` - Track dropped/rejected requests
- `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/AggregatedMetrics.java` - Include dropped/rejected counts
- `examples/adaptive-load-test/src/main/java/com/example/adaptive/AdaptiveLoadTestRunner.java` - Add backpressure example

## Success Criteria

- [ ] BackpressureProvider interface defined
- [ ] BackpressureHandler interface defined
- [ ] AdaptiveLoadPattern accepts optional BackpressureProvider
- [ ] ExecutionEngine accepts optional BackpressureHandler
- [ ] Built-in providers work correctly (HikariCP, Queue, Composite)
- [ ] Built-in handlers work correctly (DROP, QUEUE, REJECT, RETRY, DEGRADE, THRESHOLD)
- [ ] Adaptive pattern responds to backpressure signals
- [ ] ExecutionEngine handles requests according to handler strategy
- [ ] Decision logic combines error rate and backpressure correctly
- [ ] Dropped/rejected requests tracked in metrics
- [ ] Examples demonstrate backpressure provider and handler usage
- [ ] Documentation updated
- [ ] Tests pass with ≥90% coverage
- [ ] Backward compatible (works without BackpressureProvider/Handler)

## Benefits of This Approach

1. **No Duplication**: Leverages existing connection pool metrics
2. **Extensible**: Users can implement custom backpressure signals and handlers
3. **Flexible**: Works with any metrics source (HikariCP, Apache HttpClient, custom)
4. **Framework Philosophy**: Framework provides mechanism, users provide signals
5. **Zero Dependencies**: Interface only, no new dependencies
6. **Backward Compatible**: Optional parameters, existing code works unchanged
7. **Request Loss Handling**: Provides strategies for handling requests during backpressure
8. **Configurable**: Users choose appropriate strategy for their use case

## Request Loss Handling Strategies Comparison

| Strategy | Use Case | Pros | Cons |
|----------|----------|------|------|
| **DROP** | Capacity testing | Accurate capacity measurement, no queue buildup | Requests lost, may not reflect real behavior |
| **QUEUE** | Sustained load testing | All requests processed, realistic behavior | Queue can grow unbounded, memory pressure |
| **REJECT** | Fail-fast testing | Simulates real-world rejection, prevents queue buildup | May not reflect actual system behavior |
| **RETRY** | Transient backpressure | Maximizes request completion | Can increase load, may delay results |
| **DEGRADE** | Quality testing | Maintains throughput, tests degradation | Requires degradation logic implementation |
| **THRESHOLD** | Adaptive behavior | Different strategies at different levels | More complex configuration |

## Recommended Defaults

- **Backpressure Threshold**: 0.7 (70% backpressure triggers handling)
- **Default Handler**: QUEUE (buffer requests, current behavior)
- **Adaptive Pattern**: Uses backpressure + error rate for ramp-down decisions

## Future Enhancements (Post-0.9.6)

- More built-in providers (Apache HttpClient, OkHttp, etc.)
- Backpressure provider registry (ServiceLoader)
- Backpressure provider builder DSL
- Advanced backpressure strategies (weighted, time-windowed)
- Backpressure metrics export

