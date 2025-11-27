# Thread Safety Guide - VajraPulse

**Version**: 0.9.5  
**Status**: Implementation Guide

---

## Overview

This guide documents the thread safety guarantees and concurrency patterns used throughout VajraPulse. Understanding these guarantees is essential for correct usage in multi-threaded environments.

---

## Thread Safety Guarantees

### Levels of Thread Safety

VajraPulse classes fall into the following categories:

1. **Thread-Safe**: Safe for concurrent use from multiple threads without external synchronization
2. **Conditionally Thread-Safe**: Thread-safe when used correctly (e.g., with proper initialization)
3. **Not Thread-Safe**: Requires external synchronization for concurrent access

---

## Core Classes

### ExecutionEngine

**Thread Safety**: Thread-safe for concurrent access

**Guarantees**:
- Multiple threads can call `run()`, `stop()`, and `close()` concurrently
- Internal state is protected by atomic operations and volatile fields
- Metrics collection is thread-safe via Micrometer
- ExecutorService operations are thread-safe

**Usage**:
```java
// Safe for concurrent access
ExecutionEngine engine = ExecutionEngine.builder()
    .withTask(task)
    .withLoadPattern(load)
    .withMetricsCollector(collector)
    .build();

// Multiple threads can call these safely
engine.run();  // Thread 1
engine.stop(); // Thread 2
engine.close(); // Thread 3
```

**Memory Ordering**: Uses volatile fields and atomic operations for state visibility.

---

### MetricsCollector

**Thread Safety**: Thread-safe for concurrent metric recording

**Guarantees**:
- `record()` can be called concurrently from multiple threads
- `snapshot()` can be called concurrently from multiple threads
- Uses ThreadLocal for map reuse (must be cleaned up via `close()`)
- Micrometer meters are thread-safe

**Usage**:
```java
try (MetricsCollector collector = new MetricsCollector()) {
    // Multiple threads can record metrics concurrently
    executor.submit(() -> collector.record(metrics1));
    executor.submit(() -> collector.record(metrics2));
    
    // snapshot() is safe to call from any thread
    AggregatedMetrics snapshot = collector.snapshot();
} // ThreadLocal cleanup happens automatically
```

**Memory Ordering**: Micrometer handles all synchronization internally.

---

### AdaptiveLoadPattern

**Thread Safety**: Thread-safe for concurrent TPS calculations

**Guarantees**:
- `calculateTps()` can be called concurrently from multiple threads
- State updates use `AtomicReference.updateAndGet()` with sequential consistency
- State reads use `AtomicReference.get()` with volatile read semantics
- All state transitions are atomic and immediately visible

**Memory Ordering**:
- **Sequential Consistency**: State updates use `updateAndGet()` which provides the strongest memory ordering
- **Volatile Reads**: State reads use `get()` which provides volatile read semantics
- **Immediate Visibility**: All state changes are immediately visible to all threads

**Usage**:
```java
AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(...);

// Multiple threads can call calculateTps() concurrently
double tps1 = pattern.calculateTps(elapsed1); // Thread 1
double tps2 = pattern.calculateTps(elapsed2); // Thread 2
```

**Implementation Details**:
- Uses `AtomicReference<AdaptiveState>` for lock-free state management
- Immutable `AdaptiveState` record ensures no partial state visibility
- Lock-free compare-and-swap operations for state updates

---

### CachedMetricsProvider

**Thread Safety**: Thread-safe for concurrent access

**Guarantees**:
- `getFailureRate()` and `getTotalExecutions()` can be called concurrently
- Uses double-check locking with proper memory ordering
- `AtomicLong` for cache timestamp ensures atomic reads with memory ordering
- Volatile for cached snapshot ensures visibility across threads

**Memory Ordering**:
- **AtomicLong**: `cacheTimeNanos` uses atomic operations with proper memory ordering
- **Volatile**: `cached` snapshot uses volatile for visibility
- **Synchronized**: Double-check locking prevents concurrent cache refreshes

**Usage**:
```java
CachedMetricsProvider cached = new CachedMetricsProvider(provider, Duration.ofMillis(100));

// Multiple threads can access concurrently
double rate1 = cached.getFailureRate(); // Thread 1
long exec1 = cached.getTotalExecutions(); // Thread 2
```

---

### RateController

**Thread Safety**: Thread-safe for concurrent rate control

**Guarantees**:
- `waitForNext()` can be called concurrently from multiple threads
- Uses `AtomicLong` for execution count
- Caches elapsed time to reduce `System.nanoTime()` calls

**Usage**:
```java
RateController controller = new RateController(loadPattern);

// Multiple threads can call waitForNext() concurrently
controller.waitForNext(); // Thread 1
controller.waitForNext(); // Thread 2
```

---

## Common Patterns

### Immutable State Pattern

**Used In**: `AdaptiveLoadPattern`, `AggregatedMetrics`

**Pattern**:
```java
// Immutable state record
private record State(int value, String phase) {}

// Atomic reference for thread-safe updates
private final AtomicReference<State> state = new AtomicReference<>(new State(0, "INIT"));

// Update atomically
state.updateAndGet(current -> new State(current.value() + 1, "UPDATED"));
```

**Benefits**:
- No partial state visibility
- Lock-free updates
- Thread-safe by design

---

### Double-Check Locking Pattern

**Used In**: `CachedMetricsProvider`

**Pattern**:
```java
private volatile CachedSnapshot cached;
private final AtomicLong cacheTimeNanos = new AtomicLong(0);

private CachedSnapshot getCachedSnapshot() {
    long now = System.nanoTime();
    CachedSnapshot snapshot = this.cached; // Volatile read
    long cachedTime = cacheTimeNanos.get(); // Atomic read
    
    if (snapshot == null || (now - cachedTime) > ttlNanos) {
        synchronized (this) {
            // Double-check after acquiring lock
            snapshot = this.cached;
            cachedTime = cacheTimeNanos.get();
            
            if (snapshot == null || (now - cachedTime) > ttlNanos) {
                // Refresh cache
                snapshot = computeSnapshot();
                cacheTimeNanos.set(System.nanoTime());
                this.cached = snapshot; // Volatile write
            }
        }
    }
    return snapshot;
}
```

**Benefits**:
- Minimizes synchronization overhead
- Ensures proper memory ordering
- Prevents concurrent cache refreshes

---

### ThreadLocal Cleanup Pattern

**Used In**: `MetricsCollector`

**Pattern**:
```java
private final ThreadLocal<Map> reusableMap = ThreadLocal.withInitial(HashMap::new);

// Implement AutoCloseable
@Override
public void close() {
    reusableMap.remove(); // Clean up ThreadLocal
}

// Usage with try-with-resources
try (MetricsCollector collector = new MetricsCollector()) {
    // Use collector
} // Automatically cleaned up
```

**Benefits**:
- Prevents memory leaks in thread pools
- Automatic cleanup with try-with-resources
- No manual cleanup required

---

## Best Practices

### 1. Always Use Try-With-Resources

```java
// ✅ Good
try (MetricsCollector collector = new MetricsCollector()) {
    // Use collector
}

// ❌ Bad - ThreadLocal not cleaned up
MetricsCollector collector = new MetricsCollector();
// Use collector
// Memory leak if threads are reused!
```

### 2. Don't Share Mutable State

```java
// ✅ Good - Immutable state
private final AtomicReference<ImmutableState> state;

// ❌ Bad - Mutable state
private final AtomicReference<MutableState> state; // Requires external synchronization
```

### 3. Use Atomic Operations for Counters

```java
// ✅ Good
private final AtomicLong counter = new AtomicLong(0);
counter.incrementAndGet();

// ❌ Bad - Not thread-safe
private long counter = 0;
counter++; // Race condition!
```

### 4. Document Thread Safety in JavaDoc

```java
/**
 * Thread-safe for concurrent access from multiple threads.
 * 
 * <p>Memory Ordering: Uses AtomicReference with sequential consistency.
 */
public class MyClass {
    // ...
}
```

---

## Testing Thread Safety

### Concurrent Test Pattern

```groovy
def "should be thread-safe under concurrent access"() {
    given: "a shared instance"
    def instance = new MyThreadSafeClass()
    
    when: "multiple threads access concurrently"
    def results = []
    def threads = []
    100.times {
        threads << Thread.startVirtualThread {
            results << instance.doSomething()
        }
    }
    threads.each { it.join() }
    
    then: "all operations complete without errors"
    results.size() == 100
    noExceptionThrown()
}
```

### Stress Test Pattern

```groovy
def "should handle high concurrency stress test"() {
    given: "a shared instance"
    def instance = new MyThreadSafeClass()
    
    when: "stressing with many concurrent operations"
    def threads = []
    1000.times {
        threads << Thread.startVirtualThread {
            100.times {
                instance.doSomething()
            }
        }
    }
    threads.each { it.join() }
    
    then: "no race conditions or deadlocks"
    noExceptionThrown()
}
```

---

## Memory Ordering Reference

### Java Memory Model Guarantees

1. **Sequential Consistency** (strongest)
   - `AtomicReference.updateAndGet()`
   - All operations appear to execute in a single total order
   - Used in: `AdaptiveLoadPattern`

2. **Volatile**
   - `volatile` fields
   - Read/write operations are visible across threads
   - Used in: `CachedMetricsProvider`, `RateController`

3. **Atomic Operations**
   - `AtomicLong`, `AtomicInteger`, etc.
   - Individual operations are atomic
   - Used in: `RateController`, `ExecutionEngine`

---

## See Also

- [Java Memory Model](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html)
- [AtomicReference JavaDoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html)
- [Thread Safety in Java](https://docs.oracle.com/javase/tutorial/essential/concurrency/sync.html)

