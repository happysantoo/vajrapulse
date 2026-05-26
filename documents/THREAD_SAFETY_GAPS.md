# Multi-Threaded Access Pattern Gaps

Analysis of concurrency hazards in the VajraPulse load testing framework. Generated 2026-05-16.

## Summary

| Gap | Component | Severity | Root Cause | Fix Approach |
|-----|-----------|----------|-----------|--------------|
| 1 | AdaptiveLoadPattern | **High** (contract bug) | No CAS on state transition | `compareAndSet` + retry, or fix Javadoc |
| 2 | RateController | Low (single-threaded today) | Two separate volatile stores | Bundle into `AtomicReference` record |
| 3 | OpenTelemetryExporter | Low | Non-atomic multi-field write | Bundle into `AtomicReference` record |
| 4 | Tracing | Medium | Check-then-act on static volatile | `synchronized` or `AtomicReference` CAS |
| 5 | AdaptivePatternMetrics | Medium | Non-atomic compound map ops | `ConcurrentHashMap.compute()` |
| 6 | ExecutionEngine.engineState | Low | Plain volatile write for state machine | `compareAndSet` for transitions |
| 7 | MetricsProviderAdapter | Medium | Lock contention under load | `ConcurrentLinkedDeque` |
| 8 | ShutdownManager | Low | Volatile check-then-use | Read into local variable |

---

## GAP 1 — AdaptiveLoadPattern: get-then-set without CAS (API contract mismatch)

**Severity:** High (contract bug)

**File:** `vajrapulse-api/src/main/java/com/vajrapulse/api/pattern/adaptive/AdaptiveLoadPattern.java`

**Lines:** 107–176

The `calculateTps()` method does `state.get()` → decision → `state.set(newState)` without compare-and-swap. Two concurrent calls could read the same state, both make independent decisions, and the second `set()` silently overwrites the first.

The Javadoc at line 44 claims *"This class is thread-safe for concurrent access"*, but the implementation is not atomic under concurrent access.

**Current risk:** Low — only called from the single orchestration thread in `RateController`. But the API contract is wrong.

**Fix:** Either switch to `state.compareAndSet(current, newState)` with a retry loop, or correct the Javadoc to reflect single-threaded usage only.

---

## GAP 2 — RateController: volatile pair written non-atomically

**Severity:** Low (single-threaded today)

**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/RateController.java`

**Lines:** 58–59

```java
private volatile long cachedElapsedNanos;
private volatile long cachedElapsedTimeNanos;
```

These two volatiles are written as two separate stores (~lines 179–180). A reader could see a new `cachedElapsedNanos` paired with a stale `cachedElapsedTimeNanos`, producing a time inconsistency.

**Current risk:** None — `RateController` is single-threaded in practice. But if `RateController` were ever shared across threads, this would be a data race.

**Fix:** Combine into a single `AtomicReference<ElapsedTimeCache>` record to guarantee atomic reads and writes of the pair.

---

## GAP 3 — OpenTelemetryExporter: three volatile doubles split from AtomicReference

**Severity:** Low

**File:** `vajrapulse-exporter-opentelemetry/src/main/java/com/vajrapulse/exporter/otel/OpenTelemetryExporter.java`

**Lines:** 78–82

```java
AtomicReference<AggregatedMetrics> lastMetrics    // atomic
volatile double lastResponseTps                     // separate write
volatile double lastSuccessTps                      // separate write
volatile double lastFailureTps                      // separate write
```

In `export()`, `lastMetrics.set()` and the three volatile double writes happen non-atomically (~lines 302–308). A reader calling `getLastResponseTps()` may see TPS values from a different export cycle than `lastMetrics.get()`.

**Current risk:** Low — these getters are for testing/observability only.

**Fix:** Bundle all four into a single `AtomicReference<ExportSnapshot>` record.

---

## GAP 4 — Tracing.initIfEnabled(): check-then-act initialization race

**Severity:** Medium

**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/tracing/Tracing.java`

**Lines:** 38–39

```java
if (tracer != null) return;  // not synchronized
```

Two threads calling `initIfEnabled()` concurrently could both see `tracer == null` and both initialize the OpenTelemetry SDK. The second init overwrites the first, leaking the first SDK instance — its `SdkTracerProvider` is never closed and holds a thread pool and span exporter.

**Current risk:** Medium — the leaked `SdkTracerProvider` holds resources that are never released.

**Fix:** Use `synchronized` on the method or `AtomicReference<OpenTelemetry>` with `compareAndSet` to ensure single initialization.

---

## GAP 5 — AdaptivePatternMetrics.unregister(): non-atomic compound map + meter removal

**Severity:** Medium

**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/AdaptivePatternMetrics.java`

**Lines:** 270–279

```java
trackers.remove(pattern);
MeterRegistry registry = registries.remove(pattern);
registry.getMeters().stream()...forEach(registry::remove);
```

The `remove()` from both `ConcurrentHashMap`s and the subsequent meter cleanup are not atomic. If `register()` is called concurrently for the same pattern between lines 272 and 277, a new tracker is created while meters are being removed from the old registry. The `@Experimental` annotation already acknowledges a gauge registration memory leak.

**Current risk:** Medium — can leak meters during rapid register/unregister cycles.

**Fix:** Use `ConcurrentHashMap.compute()` to atomically remove and cleanup in a single critical section.

---

## GAP 6 — ExecutionEngine.engineState: volatile state transitions without CAS

**Severity:** Low

**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java`

**Lines:** 95, ~535, ~597, ~658

```java
volatile EngineState engineState = EngineState.STOPPED;
```

State transitions (`STOPPED → RUNNING → STOPPING → STOPPED`) are plain volatile writes with no compare-and-swap. If `stop()` runs between transitions, the engine could enter an inconsistent state (e.g., writing `RUNNING` after `STOPPING` was already set).

**Current risk:** Low — `stopRequested` AtomicBoolean acts as a guard in practice.

**Fix:** Use `compareAndSet` for state transitions to enforce the state machine and fail fast on invalid transitions.

---

## GAP 7 — MetricsProviderAdapter: synchronized(ArrayDeque) is a contention bottleneck

**Severity:** Medium

**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/MetricsProviderAdapter.java`

**Lines:** 43, 107–124

```java
Deque<WindowSnapshot> history = new ArrayDeque<>();
synchronized (history) { ... }
```

Every `getRecentFailureRate()` call contends on this lock. Under adaptive load patterns with high TPS, this synchronized block is hit on every metrics poll from the scheduler thread while potentially being called from worker threads too.

**Current risk:** Medium under high TPS — lock contention degrades throughput.

**Fix:** Replace `ArrayDeque` with `ConcurrentLinkedDeque` and lock-free pruning. This is already proposed in `documents/THREAD_SAFETY_MODERNIZATION_JAVA25.md`.

---

## GAP 8 — ShutdownManager.shutdownHookThread: check-then-act on volatile

**Severity:** Low

**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ShutdownManager.java`

**Lines:** ~221–223

`removeShutdownHook()` checks `if (shutdownHookThread != null)` then uses it. Another thread could null the reference between the check and the use (TOCTOU).

**Current risk:** Low — only called during controlled shutdown.

**Fix:** Read into a local variable before the null check:

```java
Thread hook = shutdownHookThread;
if (hook != null) {
    Runtime.getRuntime().removeShutdownHook(hook);
}
```

---

## Existing Concurrency Primitives Inventory

For reference, these are the concurrency primitives currently in use:

| Component | Primitive | Field | Line |
|-----------|-----------|-------|------|
| ExecutionEngine | AtomicBoolean | stopRequested | 86 |
| ExecutionEngine | AtomicBoolean | executorShutdown | 87 |
| ExecutionEngine | volatile Span | scenarioSpan | 88 |
| ExecutionEngine | LongAdder | pendingExecutions | 91 |
| ExecutionEngine | AtomicLong | startTimeMillis | 94 |
| ExecutionEngine | volatile EngineState | engineState | 95 |
| RateController | AtomicLong | executionCount | 55 |
| RateController | volatile long | cachedElapsedNanos | 58 |
| RateController | volatile long | cachedElapsedTimeNanos | 59 |
| MetricsCollector | AtomicLong | queueSizeHolder | 59 |
| CachedMetricsProvider | AtomicReference | cached | 35 |
| CachedMetricsProvider | AtomicLong | cacheTimeNanos | 36 |
| CachedMetricsProvider | synchronized | refreshLock | 95 |
| MetricsProviderAdapter | synchronized | history (ArrayDeque) | 107 |
| ShutdownManager | AtomicBoolean | shutdownRequested | 84 |
| ShutdownManager | CountDownLatch | shutdownComplete | 85 |
| ShutdownManager | volatile Thread | shutdownHookThread | 86 |
| ShutdownManager | CopyOnWriteArrayList | callbackExceptions | 87 |
| AdaptiveLoadPattern | CopyOnWriteArrayList | listeners | 93 |
| AdaptiveLoadPattern | AtomicReference | state | 69 |
| AdaptivePatternMetrics | ConcurrentHashMap | trackers | 37 |
| AdaptivePatternMetrics | ConcurrentHashMap | registries | 38 |
| AdaptivePatternMetrics | AtomicReference | lastPhase | 47 |
| AdaptivePatternMetrics | AtomicLong | lastPhaseStartTime | 49 |
| PeriodicMetricsReporter | AtomicBoolean | running | 25 |
| OpenTelemetryExporter | AtomicReference | lastCounters | 73 |
| OpenTelemetryExporter | AtomicReference | lastMetrics | 78 |
| OpenTelemetryExporter | volatile double | lastResponseTps | 80 |
| OpenTelemetryExporter | volatile double | lastSuccessTps | 81 |
| OpenTelemetryExporter | volatile double | lastFailureTps | 82 |
| Tracing | static volatile OpenTelemetry | openTelemetry | 26 |
| Tracing | static volatile Tracer | tracer | 27 |

## Recommendations

### Immediate (correctness)

1. **GAP 1** — Fix the `AdaptiveLoadPattern` thread-safety contract. Either implement CAS-based state transitions or correct the Javadoc. The current mismatch between contract and implementation is a correctness debt.
2. **GAP 4** — Add synchronization to `Tracing.initIfEnabled()` to prevent double initialization and resource leaks.
3. **GAP 5** — Use `ConcurrentHashMap.compute()` for atomic register/unregister in `AdaptivePatternMetrics`.

### Near-term (performance under load)

4. **GAP 7** — Replace `synchronized(ArrayDeque)` in `MetricsProviderAdapter` with `ConcurrentLinkedDeque`. Already proposed in the modernization doc.

### Defensive hardening (future-proofing)

5. **GAP 2** — Bundle the `RateController` volatile pair into an `AtomicReference` record.
6. **GAP 3** — Bundle the `OpenTelemetryExporter` volatile triple + AtomicReference into a single `AtomicReference` record.
7. **GAP 6** — Use `compareAndSet` for `ExecutionEngine.engineState` transitions.
8. **GAP 8** — Read `ShutdownManager.shutdownHookThread` into a local variable before the null check.