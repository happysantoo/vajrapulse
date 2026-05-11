# VajraPulse 1.1.0 Release Plan

**Target release:** 1.1.0
**Baseline:** 1.0.0 (core load testing framework, adaptive features marked @Experimental)
**Theme:** Stabilize the adaptive capacity discovery pipeline and harden the core framework

---

## Release Goal

1.0.0 ships a solid core load testing framework with adaptive features marked `@Experimental`. 1.1.0 removes those annotations and delivers a **correct, production-ready adaptive capacity discovery engine**. The release also addresses all critical bugs, hardens concurrency, eliminates code duplication, and closes gaps deferred from 1.0.0.

---

## Summary

| Category | Item Count |
|----------|------------|
| Critical fixes | 4 |
| High-priority fixes | 13 |
| Medium-priority improvements | 18 |
| Low-priority polish | 10 |
| Deferred features (from TODOS.md) | 8 |
| Architecture refactors | 3 |
| **Total work items** | **56** |

---

## Phase 1 — Stabilize (critical + high-risk fixes, 2-3 weeks)

These bugs corrupt data, leak resources, or silently produce wrong results. They must ship in 1.1.0.

### 1.1 Fix AdaptivePatternMetrics memory leak
- **File:** `vajrapulse-core/.../AdaptivePatternMetrics.java`
- **Bug:** `unregister()` removes the tracker from the static map but does not remove Micrometer gauges from the registry. Gauges hold references to `AdaptiveLoadPattern` via callback lambdas, preventing GC.
- **Fix:** In `unregister()`, iterate and remove all gauges matching `vajrapulse.adaptive.*` from the `MeterRegistry`.
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 1.2 Add Tracing.shutdown() and wire into ExecutionEngine
- **File:** `vajrapulse-core/.../Tracing.java`, `ExecutionEngine.java`
- **Bug:** `SdkTracerProvider`, `OtlpGrpcSpanExporter`, and `OpenTelemetrySdk` are created but never shut down. Pending spans are lost; gRPC connections and threads leak for the JVM lifetime.
- **Fix:** Add `Tracing.shutdown()` that calls `SdkTracerProvider.shutdown()` and `OpenTelemetrySdk.shutdown()`. Wire into `ExecutionEngine.close()`.
- **Effort:** S (~2 hours)
- **Dependencies:** None

### 1.3 Remove gauge-with-side-effects anti-pattern
- **File:** `vajrapulse-core/.../AdaptivePatternMetrics.java`
- **Bug:** The `vajrapulse.adaptive.phase` gauge callback calls `tracker.update(pattern)` as a side effect. Tracker state only updates when Micrometer polls (every ~60s by default). In non-Micrometer environments, the tracker never updates.
- **Fix:** Call `tracker.update(pattern)` directly from a scheduled task or from `calculateTps()` on each invocation. The gauge becomes a pure read of `.getCurrentPhase().ordinal()`.
- **Effort:** S (~2 hours)
- **Dependencies:** None

### 1.4 Fix negative rate handling in MetricsProviderAdapter
- **File:** `vajrapulse-core/.../MetricsProviderAdapter.java`
- **Bug:** If cumulative counters reset, `failureDiff` and `totalDiff` become negative, producing incorrect failure rates. Only `totalDiff == 0` is guarded, not `< 0`.
- **Fix:** Add `if (failureDiff < 0 || totalDiff < 0) return currentSnapshot.failureRate()` as a safety fallback.
- **Effort:** S (~30 min)
- **Dependencies:** None

### 1.5 Add null/negative guards to ConfigLoader
- **File:** `vajrapulse-core/.../ConfigLoader.java`
- **Bug:** `Double.parseDouble(sampleRate.toString())` throws uncaught `NumberFormatException` on non-numeric YAML values.
- **Fix:** Wrap in try-catch, add structured error to errors list.
- **Effort:** S (~30 min)
- **Dependencies:** None

### 1.6 Fix ramp-sustain negative duration bug
- **File:** `vajrapulse-worker/.../LoadPatternFactory.java`
- **Bug:** `testDuration.minus(ramp)` doesn't check whether `ramp > testDuration`. User gets negative sustain duration with `--ramp 60s --duration 30s`.
- **Fix:** Validate and throw `IllegalArgumentException` if ramp >= testDuration.
- **Effort:** S (~30 min)
- **Dependencies:** None

### 1.7 Add null check to parseDuration
- **File:** `vajrapulse-worker/.../LoadPatternFactory.java`
- **Bug:** NPE on null input: `.toLowerCase().trim()` called on null argument.
- **Fix:** Add null check at method entry.
- **Effort:** S (~15 min)
- **Dependencies:** None

### 1.8 Fix CpuBoundTest AES-GCM IV and deprecated constructors
- **File:** `examples/cpu-bound-test/.../CpuBoundTest.java`
- **Bug:** Decrypt cipher initialized without `GCMParameterSpec` (IV), causing intermittent failure. `new Deflater()` / `new Inflater()` deprecated since Java 13.
- **Fix:** Store IV from encryption, pass to decrypt cipher. Use `new Deflater(Deflater.DEFAULT_COMPRESSION)` and keep `new Inflater()` (not deprecated).
- **Effort:** S (~1 hour)
- **Dependencies:** None

**Phase 1 checkpoint:** All 4 critical bugs fixed. 4 high-priority input-validation bugs fixed. JMH benchmarks pass. Full test suite green.

---

## Phase 2 — Harden (unit standardization + concurrency, 3-4 weeks)

These are correctness and performance issues that affect behavior under load or across repeated runs.

### 2.1 Standardize metric units on ratio (0.0-1.0)
- **Files:** `Metrics.java`, `MetricsProvider.java`, `MetricsSnapshot.java`, `DefaultRampDecisionPolicy.java`, `Assertions.java`
- **Bug:** `Metrics.successRate()` and `MetricsProvider.getFailureRate()` return **percentages** (0.0–100.0). `MetricsSnapshot.failureRate()` returns a **ratio** (0.0–1.0). `AdaptiveLoadPattern.captureMetricsSnapshot()` manually divides by 100.0. A single wrong unit silently corrupts all decisions.
- **Fix:** Standardize on ratio (0.0–1.0) everywhere. Add `@Range(from=0.0, to=1.0)` Javadoc tags. Consider a value type (`FailureRate`) for compile-time safety.
- **Effort:** M (~2 days)
- **Dependencies:** Must complete before 2.2, 2.3, and all adaptive fixes

### 2.2 Fix Assertions errorRate/successRate
- **File:** `vajrapulse-api/.../Assertions.java`
- **Bug:** Divides `Metrics` percentage return by 100.0. After 2.1 standardization on ratios, this division is no longer needed and would silently produce 100x-wrong results.
- **Fix:** Remove the division after 2.1 is complete. Add a runtime assertion invariant until then.
- **Effort:** S (~30 min)
- **Dependencies:** Depends on 2.1

### 2.3 Replace System.err with proper logging in AdaptiveLoadPattern
- **File:** `vajrapulse-api/.../AdaptiveLoadPattern.java`
- **Bug:** `notifyListeners()` catches exceptions and calls `e.printStackTrace()` to `System.err`. In a library, errors are invisible to callers.
- **Fix:** Use `java.util.logging.Logger` (zero-dependency constraint) or collect exceptions and expose them via `AdaptivePatternListener`.
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 2.4 Fix or remove dead ScopedValue optimization in MetricsCollector
- **File:** `vajrapulse-core/.../MetricsCollector.java`
- **Bug:** Static `ScopedValue` fields are declared but never bound. `snapshot()` always allocates new maps. The `AutoCloseable` implementation is misleading.
- **Fix:** Bind ScopedValues in `ExecutionEngine.run()` and use `ScopedValue.getWhere()` in `MetricsCollector.snapshot()`, or remove the ScopedValue fields entirely.
- **Effort:** S (~2 hours)
- **Dependencies:** None

### 2.5 Replace CachedMetricsProvider spin-wait with synchronized
- **File:** `vajrapulse-core/.../CachedMetricsProvider.java`
- **Bug:** Lock-free spin-wait loop (up to 100 iterations with `Thread.onSpinWait()`) for low-contention cache refresh. Pure CPU waste — 1-2 threads access this cache.
- **Fix:** Replace with `synchronized` block. JDK 24+ eliminates virtual thread pinning, making this safe.
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 2.6 Convert WarmupCooldownLoadPattern to record
- **File:** `vajrapulse-api/.../WarmupCooldownLoadPattern.java`
- **Bug:** All other load patterns are records. This one is a plain `final class`. Nested `Phase` enum couples phase logic to this class unnecessarily.
- **Fix:** Convert to `record (LoadPattern basePattern, Duration warmupDuration, Duration cooldownDuration)`. Extract `Phase` as a top-level enum. Remove `@Experimental` annotation after refactoring.
- **Effort:** S (~2 hours)
- **Dependencies:** None

### 2.7 Add getConfiguration() to LoadPattern interface
- **Files:** `LoadPattern.java`, `LoadTestRunner.java`, report exporters
- **Bug:** `LoadTestRunner` uses reflection on hardcoded class names (`"RampSustainLoad"` never matches — actual class is `RampUpToMaxLoad`). Report exporters use string-based gauge name lookups.
- **Fix:** Add `Map<String, Object> getConfiguration()` with a default empty-map implementation to `LoadPattern`. Replace reflection and string lookups with this method. Fix the class name bug as part of this change.
- **Effort:** M (~2 days)
- **Dependencies:** None

### 2.8 Fix ConcurrentLinkedDeque race in MetricsProviderAdapter
- **File:** `vajrapulse-core/.../MetricsProviderAdapter.java`
- **Bug:** `ConcurrentLinkedDeque` iteration is weakly consistent. Concurrent `addLast()`/`removeFirst()` produces slightly incorrect sliding-window failure rates.
- **Fix:** Replace with `synchronized` `ArrayDeque` (window is small — <100 entries for 60s at 1-second snapshots).
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 2.9 Add virtual-thread executor metrics
- **File:** `vajrapulse-core/.../EngineMetricsRegistrar.java`
- **Bug:** `registerExecutorMetrics()` checks `instanceof ThreadPoolExecutor`, which only matches platform threads. Virtual-thread engines have zero executor visibility.
- **Fix:** For virtual threads, register metrics using `ThreadMXBean` (thread count, peak thread count) or JFR events.
- **Effort:** S (~2 hours)
- **Dependencies:** None

### 2.10 Replace manual JSON with library-based serialization
- **Files:** `StructuredLogger.java`, `RunManifest.java`
- **Bug:** Manual JSON via `StringBuilder` doesn't escape control characters (`\b`, `\f`, `/`, `\uXXXX`). Always builds full JSON string even when log level is disabled.
- **Fix:** Use Jackson (already a dependency) or Gson for serialization. For StructuredLogger, use SLF4J's fluent API with structured arguments.
- **Effort:** M (~1 day)
- **Dependencies:** None

### 2.11 Fix RateController cumulative drift
- **File:** `vajrapulse-core/.../RateController.java`
- **Bug:** Integer division for sleep interval loses fractional nanoseconds. Over long tests, cumulative drift accumulates.
- **Fix:** Use `Math.round()` or track the error term (Bresenham-style accumulator).
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 2.12 Fix OTel exporter broad synchronized block
- **File:** `vajrapulse-exporter-opentelemetry/.../OpenTelemetryExporter.java`
- **Bug:** Full `export()` method is `synchronized`, serializing all callers. Delta computation could use `AtomicLong` operations.
- **Fix:** Use `AtomicLong.getAndSet(0)` for delta counters, remove broad `synchronized`.
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 2.13 Add unbounded history size cap
- **File:** `vajrapulse-core/.../MetricsProviderAdapter.java`
- **Bug:** History `ConcurrentLinkedDeque` prunes entries older than 60s, but only when `getRecentFailureRate()` is called. If only all-time rate is queried, history grows unboundedly.
- **Fix:** Add a size cap (e.g., 1000 entries) or prune on every addition.
- **Effort:** S (~30 min)
- **Dependencies:** Related to 2.8

**Phase 2 checkpoint:** 13 high-priority bugs fixed. All metric calculations produce correct, unit-consistent results. No manual JSON or reflection-based config extraction remains.

---

## Phase 3 — Polish (code quality + developer experience, 3-4 weeks)

### 3.1 Centralize duplicated exporter utilities
- **Files:** All 4 exporters
- **Duplicated code:**
  - `nanosToMillis(double)` — 4 copies
  - `formatPercentileLabel(double)` — 4 copies (uppercase/lowercase inconsistency)
  - `getPhaseName(int)` — 3 copies
  - `toDouble(Object)` — 3 copies
  - Adaptive Micrometer gauge name strings — 3 copies
- **Fix:** Create shared `ExportUtils` class in `vajrapulse-core` or add default methods to `MetricsExporter`.
- **Effort:** M (~1 day)
- **Dependencies:** None

### 3.2 Refactor LoadPatternFactory.create() with Builder
- **File:** `vajrapulse-worker/.../LoadPatternFactory.java`
- **Bug:** `create()` takes 20 parameters. Every new pattern adds more.
- **Fix:** Create `LoadPatternSpec` record or Builder. This also enables future CLI improvements.
- **Effort:** M (~1 day)
- **Dependencies:** None

### 3.3 Embed Chart.js in HTML reports
- **File:** `vajrapulse-exporter-report/.../HtmlReportExporter.java`
- **Bug:** Chart.js loaded from CDN at open time — reports break offline, supply-chain risk, CDN dependency.
- **Fix:** Embed as base64 string, or clearly document online-only requirement in the report UI.
- **Effort:** S (~2 hours)
- **Dependencies:** None

### 3.4 Clean up dead code
- **Items:**
  - `RunManifest.create()` — never called anywhere
  - `ExceptionContext.elapsedMillis` — dead parameter, always called with `-1`
  - `examples/hikaricp-backpressure-example/` — orphan directory, no build.gradle.kts, not in settings.gradle.kts
- **Fix:** Delete `create()`, remove dead parameter, move orphan example to `documents/integrations/` as code snippet or add proper build file.
- **Effort:** S (~2 hours)
- **Dependencies:** None

### 3.5 Add missing package-info.java
- **File:** `vajrapulse-api/.../exception/package-info.java`
- **Bug:** Only package in API module without package-info.java.
- **Fix:** Add one for consistency.
- **Effort:** S (~15 min)
- **Dependencies:** None

### 3.6 Add startup validation for @VirtualThreads/@PlatformThreads
- **Files:** `ExecutionEngine.java`, annotation Javadoc
- **Bug:** User can annotate a class with both `@VirtualThreads` and `@PlatformThreads`. Framework must detect and reject at runtime.
- **Fix:** Document precedence in Javadoc. Add startup validation in `ExecutionEngine`.
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 3.7 Support multiple exporters in VajraPulseWorker CLI
- **File:** `vajrapulse-worker/.../VajraPulseWorker.java`
- **Bug:** CLI always uses `ConsoleMetricsExporter`. Users can't use JSON/CSV/HTML/OTel exporters from CLI without modifying source code.
- **Fix:** Add `--exporter json,csv,html` flag support. The `LoadTestRunner` pipeline already supports multiple exporters.
- **Effort:** M (~2 days)
- **Dependencies:** Depends on 3.2 (LoadPatternSpec makes CLI parsing cleaner)

### 3.8 Refactor OTelExporter constructor
- **File:** `vajrapulse-exporter-opentelemetry/.../OpenTelemetryExporter.java`
- **Bug:** Constructor is ~160 lines. Builds resource, exporter, reader, meter, counter, and 5 gauges inline.
- **Fix:** Extract `initResource()`, `initMeterProvider()`, `initCounters()`, `initGauges()`.
- **Effort:** S (~2 hours)
- **Dependencies:** Should not conflict with 2.12

### 3.9 Fix PeriodicMetricsReporter schedule race
- **File:** `vajrapulse-core/.../PeriodicMetricsReporter.java`
- **Bug:** `scheduleAtFixedRate` queues next execution even if previous hasn't finished. Slow reporting could cause concurrent `reportSafe()` calls.
- **Fix:** Use `scheduleWithFixedDelay` or guard with semaphore.
- **Effort:** S (~30 min)
- **Dependencies:** None

### 3.10 Standardize example package naming
- **Files:** All examples
- **Bug:** Examples use both `com.example.*` and `com.vajrapulse.examples.*` without convention.
- **Fix:** Standardize on `com.vajrapulse.examples.*`.
- **Effort:** S (~2 hours)
- **Dependencies:** None

### 3.11 Add README.md to examples missing them
- **Examples:** `adaptive-with-warmup/`, `assertion-framework/`
- **Fix:** Add README.md following the pattern from `http-load-test/`.
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 3.12 Update stale Javadoc on LoadPattern
- **File:** `vajrapulse-api/.../LoadPattern.java`
- **Bug:** Javadoc lists 3 implementations but there are 7+. Package-info for root mentions backpressure as part of metrics.
- **Fix:** List all implementations or reference `package-info.java`. Fix stale cross-references.
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 3.13 Add OTel service version from build
- **File:** `vajrapulse-core/.../Tracing.java`
- **Bug:** Hardcoded `"0.9.0-SNAPSHOT"` as OTel service version.
- **Fix:** Derive from build via resource filtering or system property.
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 3.14 Fix shutdown hook waits full force timeout
- **File:** `vajrapulse-core/.../ShutdownManager.java`
- **Bug:** JVM shutdown hook waits full `forceTimeout` (default 10s) even when all work completes in 100ms.
- **Fix:** Use `awaitTermination` with tighter timeout in the hook, or signal from main thread.
- **Effort:** S (~1 hour)
- **Dependencies:** None

### 3.15 Clean up redundant shutdownNow in ExecutionEngine
- **File:** `vajrapulse-core/.../ExecutionEngine.java`
- **Bug:** After init failure, calls `shutdown()`, then `awaitTermination()`, then `shutdownNow()` — redundant.
- **Fix:** Call `shutdownNow()` immediately if init failed.
- **Effort:** S (~15 min)
- **Dependencies:** None

### 3.16 Add PrintStream.checkError() to ConsoleMetricsExporter
- **File:** `vajrapulse-exporter-console/.../ConsoleMetricsExporter.java`
- **Bug:** Broken pipe / I/O errors silently ignored.
- **Fix:** Call `checkError()` at end of `export()` and log warning.
- **Effort:** S (~15 min)
- **Dependencies:** None

### 3.17 Fix Static mutable state in AdaptivePatternMetrics
- **File:** `vajrapulse-core/.../AdaptivePatternMetrics.java`
- **Bug:** Static global `ConcurrentHashMap` — tests could leak state between runs.
- **Fix:** Make instance field or clear in `@AfterEach` hooks.
- **Effort:** S (~1 hour)
- **Dependencies:** Related to 1.1, 1.3

### 3.18 Increase generateRunId() entropy
- **File:** `vajrapulse-worker/.../LoadTestRunner.java`
- **Bug:** 8-char UUID truncation reduces collision resistance from 2^122 to ~2^32.
- **Fix:** Use at least 12 characters or full UUID.
- **Effort:** S (~15 min)
- **Dependencies:** None

**Phase 3 checkpoint:** All medium-priority issues resolved. No duplicated code across exporters. Examples consistent and documented. CLI supports all exporter types.

---

## Phase 4 — Features deferred from 1.0.0 (4-6 weeks)

These are the 8 items in TODOS.md plus new capabilities enabled by the stabilized core.

### 4.1 ServiceLoader-based exporter plugin mechanism
- **Goal:** Drop-in exporter JARs on classpath. `MetricsExporter` interface via `ServiceLoader`.
- **Effort:** M (~2 days)
- **Dependencies:** None (MetricsExporter interface is already well-suited)

### 4.2 TaskResult<T> redesign
- **Goal:** Type-safe `TaskResult<T>` and `TaskResultSuccess<T>`. Eliminate `Object data` → `ClassCastException` risk.
- **Risk:** API-breaking change. Needs its own design doc. Evaluate whether this can be additive (default methods, new interface) or must wait for 2.0.0.
- **Effort:** L (~3-5 days for design + implementation)
- **Dependencies:** Separate design doc required before implementation

### 4.3 Fat JAR CLI — `vajrapulse capacity`
- **Goal:** Single command: `java -jar vajrapulse.jar capacity --url <url> --max-tps 1000`. Prints discovered capacity number to stdout.
- **Effort:** M (~3 days)
- **Dependencies:** Depends on Phase 1+2 adaptive fixes being complete

### 4.4 JUnit 5 extension — `@VajraPulseTest`
- **Goal:** Declarative load tests: `@VajraPulseTest(tps = 100, duration = "30s")`. Framework starts engine, injects metrics, runs assertions.
- **Effort:** M (~3 days)
- **Dependencies:** Requires stable 1.1.0 core

### 4.5 Spring Boot starter
- **Goal:** Auto-configuration for embedded load testing in Spring apps. `VajraPulseAutoConfiguration`, health indicators, actuator endpoints.
- **Effort:** M (~3 days)
- **Dependencies:** Requires stable 1.1.0 core; separate module

### 4.6 Implement missing internal-test subprojects
- **Goal:** `high-throughput/` and `long-running/` tests (documented in README but don't exist).
- **Effort:** S (~1 day)
- **Dependencies:** None

### 4.7 Add benchmarks regression gate to CI
- **File:** `.github/workflows/benchmarks.yml`
- **Goal:** The benchmarks workflow currently has `TODO: Add regression gate`. Implement failure if JMH scores degrade beyond threshold.
- **Effort:** M (~1 day)
- **Dependencies:** JMH baseline values from 1.0.0

### 4.8 Add smoke tests for all examples in CI
- **Goal:** Run each example for 5 seconds, verify non-zero exit code. Prevent silent example breakage.
- **Effort:** M (~1 day)
- **Dependencies:** None

---

## Phase 5 — Architecture Refactors (2-3 weeks)

### 5.1 Move AdaptiveLoadPattern implementation to core
- **Goal:** Keep `AdaptiveLoadPattern` interface/contract in `vajrapulse-api`. Move ~820-line implementation to `vajrapulse-core`.
- **Rationale:** API module is for contracts. Current implementation carries state transitions, listener management, and snapshot capture in the "API" module.
- **Risk:** API-breaking. May require deprecation cycle or wait for 2.0.0. Evaluate additive approach first.
- **Effort:** L (~3-5 days for design + implementation)
- **Dependencies:** Separate design doc required

### 5.2 Add Architecture Decision Records (ADRs)
- **Goal:** Lightweight ADRs for key decisions: unchecked exceptions, zero-dependency API, virtual threads, no generics on TaskResult.
- **Effort:** S (~2 hours)
- **Dependencies:** None

### 5.3 Define consistent exporter error handling policy
- **Goal:** All exporters return `ExportResult` (success/failure). Console errors logged, OTel errors surfaced, report errors wrapped properly.
- **Effort:** S (~2 hours)
- **Dependencies:** None

---

## Phase 6 — Documentation (1-2 weeks)

### 6.1 Add troubleshooting guide
- **Goal:** Cover common issues: test hangs during shutdown, virtual threads not creating, OTLP export not working, adaptive pattern never stabilizes.
- **Effort:** M (~2 days)
- **Dependencies:** Phase 1+2 fixes provide the authoritative answers

### 6.2 Add protocol-level OTLP validation tests
- **Goal:** `OpenTelemetryExporterSpec` validates actual OTLP wire format, not just builder config.
- **Effort:** M (~1 day)
- **Dependencies:** None

### 6.3 Add E2E report validation tests
- **Goal:** Automated tests parse generated JSON/CSV/HTML reports and verify data correctness.
- **Effort:** M (~2 days)
- **Dependencies:** None

### 6.4 Add unit tests for CPU-bound test crypto logic
- **Goal:** Verify AES-GCM encrypt/decrypt correctness.
- **Effort:** S (~1 hour)
- **Dependencies:** None

---

## Dependency Graph

```
Phase 1 (Stabilize)
├── 1.1 AdaptivePatternMetrics leak ────┬── 1.3 Gauge anti-pattern
│                                        │
├── 1.2 Tracing.shutdown()              │
├── 1.4 Negative rates guard            │
├── 1.5 ConfigLoader guards             │
├── 1.6 Ramp-sustain validation         │
├── 1.7 parseDuration null check        │
└── 1.8 CpuBoundTest fixes              │
                                        │
Phase 2 (Harden)                        │
├── 2.1 Unit standardization ◄──────────┼── Blocks 2.2, 2.3, all adaptive decisions
├── 2.2 Assertions fix ◄────────────────┤
├── 2.3 System.err → logging            │
├── 2.4 ScopedValue fix                 │
├── 2.5 CachedMetrics spin-wait         │
├── 2.6 WarmupCooldown → record         │
├── 2.7 getConfiguration() interface    │
├── 2.8 MetricsProviderAdapter race     │
├── 2.9 Virtual thread executor metrics │
├── 2.10 JSON serialization             │
├── 2.11 RateController drift           │
├── 2.12 OTel synchronized              │
└── 2.13 History size cap               │
                                        │
Phase 3 (Polish)                        │
├── 3.1 Shared ExportUtils              │
├── 3.2 LoadPatternSpec                 │
├── 3.3 Embed Chart.js                  │
├── 3.4 Dead code cleanup               │
├── 3.5 missing package-info            │
├── 3.6 Thread annotation validation    │
├── 3.7 Multiple exporters CLI ◄────────┼── Depends on 3.2
└── ... (3.8–3.18)                      │
                                        │
Phase 4 (Features)                      │
├── 4.1 ServiceLoader plugins           │
├── 4.2 TaskResult<T> redesign ◄────────┼── Needs design doc
├── 4.3 Fat JAR CLI ◄───────────────────┼── Depends on Phase 1+2 adaptive fixes
├── 4.4 JUnit 5 extension               │
├── 4.5 Spring Boot starter             │
├── 4.6 Missing internal tests          │
├── 4.7 Benchmarks regression gate      │
└── 4.8 Example smoke tests             │
                                        │
Phase 5 (Architecture)                  │
├── 5.1 AdaptiveLoadPattern to core ◄───┼── Needs design doc
├── 5.2 ADRs                            │
└── 5.3 Exporter error handling policy  │
                                        │
Phase 6 (Documentation)                 │
├── 6.1 Troubleshooting guide           │
├── 6.2 OTLP wire-format tests          │
├── 6.3 E2E report validation           │
└── 6.4 CpuBound crypto tests           │
```

---

## Release Checklist

### Required for 1.1.0 (blockers)
- [ ] All 4 critical bugs fixed (Phase 1)
- [ ] All 13 high-priority bugs fixed (Phase 2)
- [ ] Unit standardization complete (2.1) — single source of truth for metric units
- [ ] `getConfiguration()` on LoadPattern interface (2.7) — eliminates reflection
- [ ] `@Experimental` annotation removed from `AdaptiveLoadPattern` and related types
- [ ] `@Experimental` annotation removed from `WarmupCooldownLoadPattern`
- [ ] `@Experimental` annotation removed from `AdaptivePatternMetrics`
- [ ] `@Experimental` annotation removed from `package-info.java` for `adaptive` package
- [ ] Full test suite green with JDK 25
- [ ] JMH benchmarks show no regression vs 1.0.0 baselines

### Strongly recommended (should ship)
- [ ] All 18 medium-priority issues resolved (Phase 3)
- [ ] Exporter utility deduplication (3.1)
- [ ] Multiple exporters in CLI (3.7)
- [ ] LoadPatternFactory refactored (3.2)
- [ ] HTML reports embed Chart.js (3.3)

### Optional (can defer to 1.1.x or 1.2.0)
- [ ] ServiceLoader exporter plugin (4.1)
- [ ] TaskResult<T> redesign (4.2)
- [ ] Fat JAR CLI (4.3)
- [ ] JUnit 5 extension (4.4)
- [ ] Spring Boot starter (4.5)
- [ ] AdaptiveLoadPattern move to core (5.1) — may require 2.0.0
- [ ] ADRs (5.2)

---

## Effort Summary

| Phase | Description | Estimated Effort |
|-------|-------------|-----------------|
| Phase 1 | Critical + input-validation fixes (8 items) | ~1 week |
| Phase 2 | Unit standardization + concurrency (13 items) | ~3-4 weeks |
| Phase 3 | Code quality + polish (18 items) | ~3-4 weeks |
| Phase 4 | Deferred features (8 items) | ~4-6 weeks |
| Phase 5 | Architecture refactors (3 items) | ~2-3 weeks |
| Phase 6 | Documentation + test gaps (4 items) | ~1-2 weeks |
| **Total** | **56 items across 6 phases** | **~14-20 weeks** |

### Recommended minimum viable timeline: ~7 weeks
- Phase 1: 1 week (critical fixes)
- Phase 2: 3 weeks (unit standardization + concurrency)
- Phase 3: 3 weeks (code quality — can ship without full polish)

This delivers a **correct, production-ready adaptive engine** plus significantly improved code quality, without requiring the Phase 4-6 deferred features or architectural refactors.

---

## Success Criteria

1. **Correctness:** AdaptiveLoadPattern produces capacity numbers that match actual system limits (±5% tolerance verified via controlled-environment tests)
2. **No resource leaks:** 100 consecutive test runs in the same JVM show stable memory (no upward trend in heap usage)
3. **Consistent units:** No production code path mixes percentage and ratio representations
4. **No reflection:** No production code uses reflection to extract configuration from load patterns
5. **No manual JSON:** StructuredLogger and RunManifest use library-based serialization
6. **Observability:** All executor types (platform, virtual) report metrics. Tracing shuts down cleanly.
7. **CI green:** Full build including JMH benchmarks, coverage verification, and example smoke tests
