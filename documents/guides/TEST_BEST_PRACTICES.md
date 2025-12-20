# Test Best Practices Guide

**Version**: 0.9.9  
**Last Updated**: 2025-01-XX  
**Status**: Active

---

## Table of Contents

1. [Introduction](#introduction)
2. [Awaitility vs Thread.sleep() Guidelines](#awaitility-vs-threadsleep-guidelines)
3. [Async Testing Patterns](#async-testing-patterns)
4. [Test Timeout Guidelines](#test-timeout-guidelines)
5. [Common Patterns and Anti-patterns](#common-patterns-and-anti-patterns)
6. [Good vs Bad Test Examples](#good-vs-bad-test-examples)
7. [Test Utility Usage](#test-utility-usage)
8. [Quick Reference](#quick-reference)
9. [Troubleshooting](#troubleshooting)

---

## Introduction

### Purpose

This guide provides best practices for writing reliable, maintainable tests in the VajraPulse project. It covers:

- When and how to use Awaitility vs `Thread.sleep()`
- Proper patterns for testing asynchronous behavior
- Test timeout strategies
- Common patterns and anti-patterns
- Usage of test utilities (`TestExecutionHelper`, `TestMetricsHelper`)

### Scope

This guide applies to all test files in:
- `vajrapulse-core/src/test`
- `vajrapulse-api/src/test`
- `vajrapulse-worker/src/test`

### Key Principles

1. **Reliability First**: Tests should be deterministic and never flaky
2. **Fail Fast**: Use timeouts to prevent hanging tests
3. **Condition-Based Waiting**: Wait for conditions, not fixed time periods
4. **Proper Synchronization**: Use explicit synchronization primitives
5. **Maintainability**: Use utilities to reduce code duplication

---

## Awaitility vs Thread.sleep() Guidelines

### Decision Tree

```
Need to wait for a condition to become true?
├─ YES → Use Awaitility
│   ├─ Waiting for metrics to reach a value? → TestMetricsHelper
│   ├─ Waiting for ExecutionEngine state? → TestExecutionHelper
│   └─ Custom condition? → Awaitility.await()
│
└─ NO → Is it intentional delay?
    ├─ Simulating work (I/O, processing)? → Thread.sleep() ✅
    ├─ Testing rate control/timing behavior? → Thread.sleep() ✅
    ├─ Testing shutdown behavior? → Thread.sleep() ✅
    └─ Waiting for GC? → Thread.sleep() ✅
```

### When to Use Awaitility

**Use Awaitility when waiting for conditions to become true:**

1. **Waiting for state changes**
   - ExecutionEngine to reach a certain state
   - Metrics to reach a threshold
   - Pattern phase transitions
   - Cache expiration

2. **Waiting for async operations**
   - Background threads to complete
   - Counters to reach a value
   - Collections to be populated

3. **Waiting for external conditions**
   - Network responses
   - File system changes
   - Database updates

**Example - Good (Awaitility):**
```groovy
def "should wait for metrics to reach threshold"() {
    given: "a metrics collector"
    def collector = new MetricsCollector()
    
    when: "executing tasks"
    // ... execute tasks ...
    
    then: "wait for minimum executions"
    await().atMost(5, SECONDS)
        .pollInterval(100, MILLISECONDS)
        .until {
            collector.snapshot().totalExecutions() >= 100
        }
}
```

**Example - Bad (Thread.sleep()):**
```groovy
def "should wait for metrics to reach threshold"() {
    given: "a metrics collector"
    def collector = new MetricsCollector()
    
    when: "executing tasks"
    // ... execute tasks ...
    
    then: "wait for minimum executions"
    Thread.sleep(2000)  // ❌ BAD: Fixed delay, may be too short or too long
    assert collector.snapshot().totalExecutions() >= 100
}
```

### When to Use Thread.sleep()

**Use `Thread.sleep()` for intentional delays (NOT waiting for conditions):**

1. **Simulating work** (in task implementations)
   ```groovy
   @Override
   TaskResult execute() throws Exception {
       Thread.sleep(10)  // ✅ OK: Simulating I/O work
       return TaskResult.success()
   }
   ```

2. **Testing rate control/timing behavior**
   ```groovy
   def "should respect rate limit"() {
       // ... setup ...
       Thread.sleep(100)  // ✅ OK: Testing timing behavior
       // ... verify rate ...
   }
   ```

3. **Testing shutdown behavior**
   ```groovy
   def "should shutdown gracefully"() {
       // ... setup ...
       engine.stop()
       Thread.sleep(50)  // ✅ OK: Testing shutdown timing
       // ... verify shutdown ...
   }
   ```

4. **GC-related delays** (non-deterministic)
   ```groovy
   def "should handle memory pressure"() {
       // ... setup ...
       System.gc()
       Thread.sleep(100)  // ✅ OK: Allow GC to run
       // ... verify ...
   }
   ```

### Awaitility Best Practices

1. **Set appropriate timeouts**
   ```groovy
   await().atMost(5, SECONDS)  // ✅ Good: Reasonable timeout
   await().atMost(1, HOURS)    // ❌ Bad: Too long, test will hang
   ```

2. **Use appropriate poll intervals**
   ```groovy
   await().atMost(5, SECONDS)
       .pollInterval(100, MILLISECONDS)  // ✅ Good: Frequent enough
       .until { condition }
   
   await().atMost(5, SECONDS)
       .pollInterval(1, SECONDS)  // ⚠️ OK: Less frequent, but acceptable
       .until { condition }
   ```

3. **Make conditions idempotent**
   ```groovy
   // ✅ Good: Idempotent condition
   await().until {
       collector.snapshot().totalExecutions() >= 100
   }
   
   // ❌ Bad: Non-idempotent (may fail if called multiple times)
   await().until {
       collector.snapshot().totalExecutions() == 100  // Exact match may fail
   }
   ```

4. **Use descriptive error messages**
   ```groovy
   await().atMost(5, SECONDS)
       .until {
           def snapshot = collector.snapshot()
           snapshot.totalExecutions() >= 100
       }
   // Awaitility will show helpful error message on timeout
   ```

---

## Async Testing Patterns

### Testing ExecutionEngine

**Problem**: `ExecutionEngine.run()` is blocking and runs indefinitely. How do we test it?

**Solution**: Use `TestExecutionHelper` utilities.

#### Pattern 1: Run with Timeout

Use when you want to run the engine until it completes naturally (e.g., load pattern ends).

```groovy
def "should complete load pattern execution"() {
    given: "an engine with a finite load pattern"
    def pattern = new StaticLoad(10.0, Duration.ofSeconds(2))
    def engine = ExecutionEngine.builder()
        .withTask(task)
        .withLoadPattern(pattern)
        .withShutdownHook(false)
        .build()
    
    when: "running engine"
    TestExecutionHelper.runWithTimeout(engine, Duration.ofSeconds(5))
    
    then: "engine completed"
    // Verify results
}
```

#### Pattern 2: Run Until Condition

Use when you want to stop the engine after a condition is met.

```groovy
def "should transition to RAMP_DOWN when errors occur"() {
    given: "an adaptive pattern with failing task"
    def pattern = AdaptiveLoadPattern.builder()
        .initialTps(10.0)
        // ... configuration ...
        .build()
    def engine = ExecutionEngine.builder()
        .withTask(failingTask)
        .withLoadPattern(pattern)
        .withShutdownHook(false)
        .build()
    
    when: "running until pattern transitions"
    TestExecutionHelper.runUntilCondition(engine, {
        pattern.getCurrentPhase() == AdaptivePhase.RAMP_DOWN
    }, Duration.ofSeconds(10))
    
    then: "pattern transitioned"
    pattern.getCurrentPhase() == AdaptivePhase.RAMP_DOWN
}
```

#### Anti-pattern: Manual Thread Management

**❌ Bad:**
```groovy
def "should run engine"() {
    given: "an engine"
    def engine = ExecutionEngine.builder()...
    
    when: "running in background thread"
    def thread = Thread.startVirtualThread {
        engine.run()
    }
    Thread.sleep(2000)  // ❌ BAD: Fixed delay
    engine.stop()
    thread.join()  // ❌ BAD: May hang indefinitely
    
    then: "engine ran"
    // ...
}
```

**✅ Good:**
```groovy
def "should run engine"() {
    given: "an engine"
    def engine = ExecutionEngine.builder()...
    
    when: "running until condition"
    TestExecutionHelper.runUntilCondition(engine, {
        // condition
    }, Duration.ofSeconds(5))
    
    then: "engine ran"
    // ...
}
```

### Testing with CountDownLatch

**When to use**: For explicit synchronization when utilities don't fit.

```groovy
def "should handle concurrent access"() {
    given: "a shared resource"
    def resource = new SharedResource()
    def latch = new CountDownLatch(1)
    
    when: "accessing from multiple threads"
    Thread.startVirtualThread {
        resource.doSomething()
        latch.countDown()
    }
    
    then: "wait for completion"
    assert latch.await(5, TimeUnit.SECONDS) : "Operation should complete"
    // Verify results
}
```

**Best Practices**:
1. Always set a timeout: `latch.await(5, TimeUnit.SECONDS)`
2. Use descriptive assertion messages
3. Prefer `TestExecutionHelper` when testing `ExecutionEngine`

### Testing Metrics Collection

**Problem**: Metrics are collected asynchronously. How do we wait for them?

**Solution**: Use `TestMetricsHelper` utilities.

#### Pattern 1: Wait for Executions

```groovy
def "should collect metrics during execution"() {
    given: "a metrics collector"
    def collector = new MetricsCollector()
    def engine = ExecutionEngine.builder()
        .withTask(task)
        .withMetricsCollector(collector)
        .withShutdownHook(false)
        .build()
    
    when: "running engine"
    TestExecutionHelper.runUntilCondition(engine, {
        collector.snapshot().totalExecutions() >= 100
    }, Duration.ofSeconds(10))
    
    then: "metrics collected"
    def snapshot = collector.snapshot()
    snapshot.totalExecutions() >= 100
}
```

**Or use TestMetricsHelper:**
```groovy
def "should collect metrics during execution"() {
    given: "a metrics collector"
    def collector = new MetricsCollector()
    // ... setup engine and run ...
    
    when: "waiting for executions"
    TestMetricsHelper.waitForExecutions(collector, 100, Duration.ofSeconds(5))
    
    then: "metrics collected"
    collector.snapshot().totalExecutions() >= 100
}
```

#### Pattern 2: Wait for Cache Expiration

```groovy
def "should refresh cache after TTL expires"() {
    given: "a cached metrics provider"
    def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(50))
    def initialValue = cached.getFailureRate()
    
    when: "waiting for cache to expire"
    TestMetricsHelper.waitForCacheExpiration(cached, Duration.ofMillis(50))
    def newValue = cached.getFailureRate()
    
    then: "cache refreshed"
    newValue != initialValue
}
```

**Anti-pattern: Fixed Sleep for TTL**
```groovy
// ❌ BAD: Fixed sleep may be too short or too long
Thread.sleep(100)  // What if TTL is 50ms? What if system is slow?
def newValue = cached.getFailureRate()
```

---

## Test Timeout Guidelines

### Timeout Strategy

All test classes **MUST** have `@Timeout` annotations to prevent hanging tests.

### Timeout Values

| Test Type | Timeout | Example |
|-----------|---------|---------|
| **Unit Tests** | `@Timeout(10)` | Simple component tests, no I/O |
| **Integration Tests** | `@Timeout(30)` | Tests with ExecutionEngine, metrics |
| **Complex Integration** | `@Timeout(60)` | Full adaptive pattern cycles, E2E tests |
| **Metrics Tests** | `@Timeout(30)` | Tests involving metrics collection |

### Examples

```groovy
// Unit test - simple component
@Timeout(10)
class MetricsCollectorSpec extends Specification {
    // ...
}

// Integration test - ExecutionEngine
@Timeout(30)
class ExecutionEngineSpec extends Specification {
    // ...
}

// Complex integration - adaptive patterns
@Timeout(60)
class AdaptiveLoadPatternE2ESpec extends Specification {
    // ...
}
```

### Determining Appropriate Timeout

1. **Measure actual execution time** of the test
2. **Add 2-3x buffer** for system load variations
3. **Consider slowest operation**:
   - Network calls: Add network latency
   - File I/O: Add disk I/O time
   - Complex computations: Add processing time

### Timeout Best Practices

1. **Always set timeouts** - Never leave tests without `@Timeout`
2. **Be generous but reasonable** - 10s for unit, 30s for integration, 60s for complex
3. **Adjust based on actual needs** - If test consistently takes 5s, use 10s timeout
4. **Document exceptions** - If a test needs longer timeout, document why

---

## Common Patterns and Anti-patterns

### Pattern 1: Using TestExecutionHelper.runUntilCondition()

**✅ Good:**
```groovy
def "should transition phase"() {
    given: "an adaptive pattern"
    def pattern = AdaptiveLoadPattern.builder()...
    def engine = ExecutionEngine.builder()...
    
    when: "running until phase transition"
    TestExecutionHelper.runUntilCondition(engine, {
        pattern.getCurrentPhase() == AdaptivePhase.SUSTAIN
    }, Duration.ofSeconds(20))
    
    then: "phase transitioned"
    pattern.getCurrentPhase() == AdaptivePhase.SUSTAIN
}
```

**❌ Bad:**
```groovy
def "should transition phase"() {
    given: "an adaptive pattern"
    def pattern = AdaptiveLoadPattern.builder()...
    def engine = ExecutionEngine.builder()...
    
    when: "running engine"
    def thread = Thread.startVirtualThread { engine.run() }
    Thread.sleep(5000)  // ❌ BAD: Fixed delay, may be too short
    engine.stop()
    thread.join()  // ❌ BAD: May hang
    
    then: "phase transitioned"
    pattern.getCurrentPhase() == AdaptivePhase.SUSTAIN  // May fail
}
```

### Pattern 2: Using TestMetricsHelper.waitForExecutions()

**✅ Good:**
```groovy
def "should collect metrics"() {
    given: "a metrics collector"
    def collector = new MetricsCollector()
    // ... setup and run engine ...
    
    when: "waiting for executions"
    TestMetricsHelper.waitForExecutions(collector, 100, Duration.ofSeconds(5))
    
    then: "metrics collected"
    collector.snapshot().totalExecutions() >= 100
}
```

**❌ Bad:**
```groovy
def "should collect metrics"() {
    given: "a metrics collector"
    def collector = new MetricsCollector()
    // ... setup and run engine ...
    
    when: "waiting for executions"
    Thread.sleep(2000)  // ❌ BAD: Fixed delay
    
    then: "metrics collected"
    collector.snapshot().totalExecutions() >= 100  // May fail
}
```

### Pattern 3: Using TestMetricsHelper.waitForCacheExpiration()

**✅ Good:**
```groovy
def "should refresh cache"() {
    given: "a cached provider"
    def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(50))
    def initial = cached.getFailureRate()
    
    when: "waiting for cache expiration"
    TestMetricsHelper.waitForCacheExpiration(cached, Duration.ofMillis(50))
    
    then: "cache refreshed"
    cached.getFailureRate() != initial
}
```

**❌ Bad:**
```groovy
def "should refresh cache"() {
    given: "a cached provider"
    def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(50))
    def initial = cached.getFailureRate()
    
    when: "waiting for cache expiration"
    Thread.sleep(100)  // ❌ BAD: Fixed delay, may be too short or too long
    
    then: "cache refreshed"
    cached.getFailureRate() != initial  // May fail
}
```

### Anti-pattern 1: Missing @Timeout Annotations

**❌ Bad:**
```groovy
class MySpec extends Specification {
    // ❌ BAD: No @Timeout - test may hang indefinitely
    def "should do something"() {
        // ...
    }
}
```

**✅ Good:**
```groovy
@Timeout(10)  // ✅ GOOD: Prevents hanging
class MySpec extends Specification {
    def "should do something"() {
        // ...
    }
}
```

### Anti-pattern 2: Hard-coded Sleep Durations

**❌ Bad:**
```groovy
def "should wait for condition"() {
    // ... setup ...
    Thread.sleep(2000)  // ❌ BAD: Why 2000ms? What if it takes longer?
    // ... verify ...
}
```

**✅ Good:**
```groovy
def "should wait for condition"() {
    // ... setup ...
    await().atMost(5, SECONDS)  // ✅ GOOD: Condition-based, with timeout
        .until { condition }
    // ... verify ...
}
```

### Anti-pattern 3: Thread.start/join Without Synchronization

**❌ Bad:**
```groovy
def "should run async"() {
    def thread = Thread.startVirtualThread {
        doSomething()
    }
    thread.join()  // ❌ BAD: May hang if doSomething() never completes
    // ... verify ...
}
```

**✅ Good:**
```groovy
def "should run async"() {
    def latch = new CountDownLatch(1)
    Thread.startVirtualThread {
        try {
            doSomething()
        } finally {
            latch.countDown()
        }
    }
    assert latch.await(5, TimeUnit.SECONDS) : "Should complete"
    // ... verify ...
}
```

---

## Good vs Bad Test Examples

### Example 1: Testing ExecutionEngine Execution

**❌ Bad:**
```groovy
@Timeout(30)
class ExecutionEngineSpec extends Specification {
    def "should execute tasks"() {
        given: "an engine"
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
            .withShutdownHook(false)
            .build()
        
        when: "running engine"
        def thread = Thread.startVirtualThread {
            engine.run()
        }
        Thread.sleep(2000)  // ❌ Fixed delay
        engine.stop()
        thread.join()  // ❌ May hang
        
        then: "tasks executed"
        // Verify results
    }
}
```

**✅ Good:**
```groovy
@Timeout(30)
class ExecutionEngineSpec extends Specification {
    def "should execute tasks"() {
        given: "an engine"
        def engine = ExecutionEngine.builder()
            .withTask(task)
            .withLoadPattern(pattern)
            .withShutdownHook(false)
            .build()
        
        when: "running until condition"
        TestExecutionHelper.runUntilCondition(engine, {
            collector.snapshot().totalExecutions() >= 100
        }, Duration.ofSeconds(10))
        
        then: "tasks executed"
        collector.snapshot().totalExecutions() >= 100
    }
}
```

### Example 2: Testing Metrics Collection

**❌ Bad:**
```groovy
@Timeout(10)
class MetricsCollectorSpec extends Specification {
    def "should collect metrics"() {
        given: "a collector"
        def collector = new MetricsCollector()
        // ... execute tasks ...
        
        when: "waiting for metrics"
        Thread.sleep(1000)  // ❌ Fixed delay
        
        then: "metrics collected"
        collector.snapshot().totalExecutions() > 0  // May fail
    }
}
```

**✅ Good:**
```groovy
@Timeout(10)
class MetricsCollectorSpec extends Specification {
    def "should collect metrics"() {
        given: "a collector"
        def collector = new MetricsCollector()
        // ... execute tasks ...
        
        when: "waiting for executions"
        TestMetricsHelper.waitForExecutions(collector, 10, Duration.ofSeconds(5))
        
        then: "metrics collected"
        collector.snapshot().totalExecutions() >= 10
    }
}
```

### Example 3: Testing Cache Expiration

**❌ Bad:**
```groovy
@Timeout(10)
class CachedMetricsProviderSpec extends Specification {
    def "should refresh cache"() {
        given: "a cached provider with 50ms TTL"
        def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(50))
        def initial = cached.getFailureRate()
        
        when: "waiting for TTL"
        Thread.sleep(100)  // ❌ Fixed delay, may be too short or too long
        
        then: "cache refreshed"
        cached.getFailureRate() != initial  // May fail
    }
}
```

**✅ Good:**
```groovy
@Timeout(10)
class CachedMetricsProviderSpec extends Specification {
    def "should refresh cache"() {
        given: "a cached provider with 50ms TTL"
        def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(50))
        def initial = cached.getFailureRate()
        
        when: "waiting for cache expiration"
        TestMetricsHelper.waitForCacheExpiration(cached, Duration.ofMillis(50))
        
        then: "cache refreshed"
        cached.getFailureRate() != initial
    }
}
```

### Example 4: Testing Async Behavior

**❌ Bad:**
```groovy
@Timeout(10)
class AsyncSpec extends Specification {
    def "should handle async operation"() {
        given: "a resource"
        def resource = new Resource()
        
        when: "performing async operation"
        def thread = Thread.startVirtualThread {
            resource.doSomething()
        }
        thread.join()  // ❌ May hang
        
        then: "operation completed"
        resource.isDone()
    }
}
```

**✅ Good:**
```groovy
@Timeout(10)
class AsyncSpec extends Specification {
    def "should handle async operation"() {
        given: "a resource"
        def resource = new Resource()
        def latch = new CountDownLatch(1)
        
        when: "performing async operation"
        Thread.startVirtualThread {
            try {
                resource.doSomething()
            } finally {
                latch.countDown()
            }
        }
        assert latch.await(5, TimeUnit.SECONDS) : "Operation should complete"
        
        then: "operation completed"
        resource.isDone()
    }
}
```

---

## Test Utility Usage

### TestExecutionHelper

Located at: `com.vajrapulse.core.test.TestExecutionHelper`

#### Method 1: `runWithTimeout(ExecutionEngine engine, Duration timeout)`

**Purpose**: Run an engine until it completes naturally (e.g., load pattern ends).

**When to use**:
- Testing finite load patterns (e.g., `StaticLoad` with duration)
- Testing engine completion behavior
- When you don't need to stop the engine manually

**Example**:
```groovy
def "should complete static load pattern"() {
    given: "an engine with finite pattern"
    def pattern = new StaticLoad(10.0, Duration.ofSeconds(2))
    def engine = ExecutionEngine.builder()
        .withTask(task)
        .withLoadPattern(pattern)
        .withShutdownHook(false)
        .build()
    
    when: "running engine"
    TestExecutionHelper.runWithTimeout(engine, Duration.ofSeconds(5))
    
    then: "engine completed"
    // Verify results
}
```

#### Method 2: `runUntilCondition(ExecutionEngine engine, Closure<Boolean> condition, Duration timeout)`

**Purpose**: Run an engine until a condition is met, then stop it.

**When to use**:
- Testing adaptive patterns (waiting for phase transitions)
- Testing metrics collection (waiting for execution count)
- When you need to stop the engine based on a condition

**Example**:
```groovy
def "should transition to SUSTAIN phase"() {
    given: "an adaptive pattern"
    def pattern = AdaptiveLoadPattern.builder()...
    def engine = ExecutionEngine.builder()...
    
    when: "running until phase transition"
    TestExecutionHelper.runUntilCondition(engine, {
        pattern.getCurrentPhase() == AdaptivePhase.SUSTAIN
    }, Duration.ofSeconds(20))
    
    then: "phase transitioned"
    pattern.getCurrentPhase() == AdaptivePhase.SUSTAIN
}
```

**Best Practices**:
1. Make conditions idempotent (safe to call multiple times)
2. Use descriptive conditions (clear what you're waiting for)
3. Set appropriate timeouts (consider worst-case scenario)

#### Method 3: `awaitCondition(Closure<Boolean> condition, Duration timeout, Duration pollInterval)`

**Purpose**: Wait for a condition to become true (convenience wrapper for Awaitility).

**When to use**:
- Waiting for conditions that don't involve ExecutionEngine
- Custom waiting scenarios
- When you need fine-grained control over polling

**Example**:
```groovy
def "should wait for external condition"() {
    given: "an external resource"
    def resource = new ExternalResource()
    
    when: "waiting for condition"
    TestExecutionHelper.awaitCondition({
        resource.isReady()
    }, Duration.ofSeconds(5), Duration.ofMillis(100))
    
    then: "condition met"
    resource.isReady()
}
```

### TestMetricsHelper

Located at: `com.vajrapulse.core.test.TestMetricsHelper`

#### Method 1: `waitForExecutions(MetricsCollector collector, long minExecutions, Duration timeout)`

**Purpose**: Wait for a MetricsCollector to record at least the minimum number of executions.

**When to use**:
- Testing metrics collection
- Waiting for sufficient test data
- Verifying execution counts

**Example**:
```groovy
def "should collect metrics"() {
    given: "a metrics collector"
    def collector = new MetricsCollector()
    // ... setup and run engine ...
    
    when: "waiting for executions"
    TestMetricsHelper.waitForExecutions(collector, 100, Duration.ofSeconds(5))
    
    then: "metrics collected"
    collector.snapshot().totalExecutions() >= 100
}
```

#### Method 2: `waitForCacheExpiration(CachedMetricsProvider provider, Duration ttl)`

**Purpose**: Wait for a CachedMetricsProvider's cache to expire and refresh.

**When to use**:
- Testing cache TTL behavior
- Verifying cache refresh logic
- Testing time-based cache invalidation

**Example**:
```groovy
def "should refresh cache after TTL"() {
    given: "a cached provider"
    def cached = new CachedMetricsProvider(delegate, Duration.ofMillis(50))
    def initial = cached.getFailureRate()
    
    when: "waiting for cache expiration"
    TestMetricsHelper.waitForCacheExpiration(cached, Duration.ofMillis(50))
    
    then: "cache refreshed"
    cached.getFailureRate() != initial
}
```

**How it works**:
1. Records initial cached value
2. Waits for TTL to expire (checks if value changes)
3. Uses 2x TTL as maximum wait time
4. Polls at appropriate intervals

#### Method 3: `waitForMetricsCondition(Closure<Boolean> condition, Duration timeout, Duration pollInterval)`

**Purpose**: Wait for a custom metrics condition to become true.

**When to use**:
- Custom metrics conditions
- Complex metrics checks
- When other helper methods don't fit

**Example**:
```groovy
def "should wait for failure rate threshold"() {
    given: "a metrics collector"
    def collector = new MetricsCollector()
    // ... setup ...
    
    when: "waiting for failure rate"
    TestMetricsHelper.waitForMetricsCondition({
        def snapshot = collector.snapshot()
        snapshot.failureRate() > 0.1 && snapshot.totalExecutions() >= 50
    }, Duration.ofSeconds(10))
    
    then: "condition met"
    collector.snapshot().failureRate() > 0.1
}
```

---

## Quick Reference

### Decision Matrix: When to Use What?

| Scenario | Solution | Example |
|----------|----------|---------|
| **Wait for ExecutionEngine to complete** | `TestExecutionHelper.runWithTimeout()` | Finite load pattern |
| **Wait for ExecutionEngine condition** | `TestExecutionHelper.runUntilCondition()` | Phase transition |
| **Wait for metrics execution count** | `TestMetricsHelper.waitForExecutions()` | Minimum executions |
| **Wait for cache expiration** | `TestMetricsHelper.waitForCacheExpiration()` | TTL refresh |
| **Wait for custom condition** | `TestExecutionHelper.awaitCondition()` or `Awaitility.await()` | Custom logic |
| **Simulate work (in task)** | `Thread.sleep()` | I/O simulation |
| **Test rate control** | `Thread.sleep()` | Timing behavior |
| **Test shutdown** | `Thread.sleep()` | Shutdown timing |
| **Allow GC** | `Thread.sleep()` | Memory tests |

### Timeout Guidelines

| Test Type | `@Timeout` Value |
|-----------|------------------|
| Unit tests | `@Timeout(10)` |
| Integration tests | `@Timeout(30)` |
| Complex integration | `@Timeout(60)` |
| Metrics tests | `@Timeout(30)` |

### Common Patterns Quick Reference

```groovy
// Pattern 1: Run engine until condition
TestExecutionHelper.runUntilCondition(engine, {
    condition
}, Duration.ofSeconds(10))

// Pattern 2: Wait for executions
TestMetricsHelper.waitForExecutions(collector, 100, Duration.ofSeconds(5))

// Pattern 3: Wait for cache expiration
TestMetricsHelper.waitForCacheExpiration(cached, Duration.ofMillis(50))

// Pattern 4: Custom condition with Awaitility
await().atMost(5, SECONDS)
    .pollInterval(100, MILLISECONDS)
    .until { condition }
```

### Anti-patterns to Avoid

1. ❌ `Thread.sleep()` for waiting on conditions
2. ❌ Missing `@Timeout` annotations
3. ❌ `Thread.start/join` without synchronization
4. ❌ Hard-coded sleep durations
5. ❌ Fixed delays for TTL expiration
6. ❌ Non-idempotent conditions in Awaitility

---

## Troubleshooting

### Problem: Test Hangs Indefinitely

**Symptoms**: Test runs but never completes, no timeout error.

**Causes**:
1. Missing `@Timeout` annotation
2. `Thread.join()` without timeout
3. Condition in `await()` never becomes true
4. `CountDownLatch.await()` without timeout

**Solutions**:
1. Add `@Timeout` annotation to test class
2. Use `latch.await(5, TimeUnit.SECONDS)` instead of `latch.await()`
3. Review condition logic - ensure it can become true
4. Use `TestExecutionHelper` instead of manual thread management

### Problem: Test Fails with Timeout

**Symptoms**: Test fails with `ConditionTimeoutException` or timeout assertion error.

**Causes**:
1. Condition never becomes true
2. Timeout too short
3. System under load (slow execution)

**Solutions**:
1. Review condition logic - verify it can become true
2. Increase timeout if test legitimately needs more time
3. Check system load - may need to run on less loaded system
4. Add logging to understand what's happening

### Problem: Test is Flaky

**Symptoms**: Test passes sometimes, fails other times.

**Causes**:
1. Using `Thread.sleep()` with fixed delays
2. Race conditions in test logic
3. Non-deterministic conditions

**Solutions**:
1. Replace `Thread.sleep()` with Awaitility
2. Use proper synchronization (CountDownLatch)
3. Make conditions deterministic
4. Use `TestExecutionHelper` and `TestMetricsHelper` utilities

### Problem: Cache Expiration Test Fails

**Symptoms**: Test waiting for cache expiration times out.

**Causes**:
1. Using fixed `Thread.sleep()` instead of checking value change
2. TTL too short for system timing
3. Cache not refreshing as expected

**Solutions**:
1. Use `TestMetricsHelper.waitForCacheExpiration()` instead of `Thread.sleep()`
2. Verify cache implementation is correct
3. Increase TTL if test environment is slow

### Problem: ExecutionEngine Test Hangs

**Symptoms**: Test with ExecutionEngine never completes.

**Causes**:
1. Not calling `engine.stop()`
2. Using `Thread.join()` without timeout
3. Missing `withShutdownHook(false)` in builder

**Solutions**:
1. Use `TestExecutionHelper.runUntilCondition()` which handles stop automatically
2. Always use `withShutdownHook(false)` in tests
3. Use `CountDownLatch` with timeout instead of `Thread.join()`

---

## References

- **TestExecutionHelper**: `vajrapulse-core/src/test/groovy/com/vajrapulse/core/test/TestExecutionHelper.groovy`
- **TestMetricsHelper**: `vajrapulse-core/src/test/groovy/com/vajrapulse/core/test/TestMetricsHelper.groovy`
- **Awaitility Documentation**: https://github.com/awaitility/awaitility
- **Spock Framework**: http://spockframework.org/
- **Test Reliability Improvement Plan**: `documents/analysis/TEST_RELIABILITY_IMPROVEMENT_PLAN.md`

---

**Last Updated**: 2025-01-XX  
**Version**: 0.9.9  
**Maintained By**: VajraPulse Team
