# VajraPulse 1.0.0 — Thorough Review & Action Report

**Date:** 2026-05-09
**Review scope:** Full codebase — all modules, examples, tests, build, CI/CD, documentation
**Total files reviewed:** 118 Java sources, 79 Groovy tests, build/config files

---

## Severity Legend

| Symbol | Meaning |
|--------|---------|
| 🔴 CRITICAL | Memory leak, data loss, JVM crash, security vulnerability |
| 🟠 HIGH | Functional bug, thread-safety defect, silent data corruption |
| 🟡 MEDIUM | Code quality, maintainability, test gap, design inconsistency |
| 🟢 LOW | Convention, cleanup, minor duplication, documentation |

---

## 1. Critical Issues (🔴)

### 1.1 AdaptivePatternMetrics — Memory Leak via Underegistered Gauges
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/AdaptivePatternMetrics.java:272-276`

`unregister()` removes the `PatternStateTracker` from the static `ConcurrentHashMap` but **does not remove the 5 Micrometer gauges** from the `MeterRegistry`. The gauges hold references to the `AdaptiveLoadPattern` instance through their callback lambdas, preventing GC. Over multiple test runs in the same JVM, this leaks both memory and the pattern objects indefinitely.

**Fix:** Call `meterRegistry.remove(gaugeId)` for each gauge in `unregister()`.

### 1.2 Tracing.java — OpenTelemetry SDK Never Shut Down
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/tracing/Tracing.java`

`initIfEnabled()` creates `SdkTracerProvider`, `OtlpGrpcSpanExporter`, and `OpenTelemetrySdk` but there is **no `close()` or `shutdown()` method anywhere**. The gRPC exporter holds a managed channel and thread pool. On JVM exit, pending spans are lost. The SDK resource (threads, connections) leaks for the JVM lifetime.

**Fix:** Add a `Tracing.shutdown()` method that calls `SdkTracerProvider.shutdown()` and `OpenTelemetrySdk.shutdown()`, and wire it into `ExecutionEngine.close()`.

### 1.3 AdaptivePatternMetrics — Gauge With Side Effects Anti-Pattern
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/AdaptivePatternMetrics.java:242-254`

The `vajrapulse.adaptive.metrics_update` gauge returns a dummy value (1.0) and uses the gauge callback **solely for its side effect** of calling `t.update(pattern)`. This means:
- The tracker state only updates when Micrometer polls (typically every 60s by default).
- In non-Micrometer environments, the tracker **never updates**.
- The gauge exists to mutate state, violating the Gauge contract.

**Fix:** Separate the update mechanism from the gauge. Call `tracker.update(pattern)` directly from a scheduled task or from `calculateTps()`.

### 1.4 MetricsProviderAdapter.getRecentFailureRate() — Negative Rates on Counter Reset
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/MetricsProviderAdapter.java:132`

If cumulative counters are ever reset (rare but possible), `failureDiff` and `totalDiff` become negative, producing incorrect failure rates. The method guards `totalDiff == 0` but not `totalDiff < 0`.

**Fix:** Add `if (failureDiff < 0 || totalDiff < 0) return currentSnapshot.failureRate()` as a safety fallback.

---

## 2. High-Priority Issues (🟠)

### 2.1 Dual Unit Representation (Percentage vs. Ratio) — Bug Surface
**Files:** `vajrapulse-api/src/main/java/com/vajrapulse/api/metrics/Metrics.java` + `MetricsProvider.java` vs. `MetricsSnapshot.java` + `DefaultRampDecisionPolicy.java`

`Metrics.successRate()` and `MetricsProvider.getFailureRate()` return **percentages** (0.0–100.0). `MetricsSnapshot.failureRate()` returns a **ratio** (0.0–1.0). `AdaptiveLoadPattern.captureMetricsSnapshot()` manually divides by 100.0. `Assertions.errorRate()` also divides by 100.0. A single implementation returning the wrong unit silently corrupts all dependent calculations.

**Fix:** Standardize on ratio (0.0–1.0) everywhere. Add `@Range(from=0.0, to=1.0)` Javadoc tags. Consider a value type (`FailureRate extends Double`) for compile-time safety.

### 2.2 Assertions.errorRate/successRate — Silent Wrong Results
**File:** `vajrapulse-api/src/main/java/com/vajrapulse/api/assertion/Assertions.java`

These methods divide the `Metrics` percentage return by 100.0. If an implementation returns ratios (violating the percentage contract), assertions silently produce results that are off by 100x. There is no runtime check.

**Fix:** After standardizing on ratios (2.1), remove the division. Until then, add an assertion invariant check.

### 2.3 AdaptiveLoadPattern — System.err for Listener Errors
**File:** `vajrapulse-api/src/main/java/com/vajrapulse/api/pattern/adaptive/AdaptiveLoadPattern.java:472-473`

`notifyListeners()` catches exceptions and calls `e.printStackTrace()` to `System.err`. In a library, this is unacceptable — errors disappear into stderr, invisible to the caller and to logging infrastructure.

**Fix:** Use `java.util.logging.Logger` (consistent with the zero-dependency constraint) or collect exceptions and expose them via the `AdaptivePatternListener` interface.

### 2.4 ConfigLoader — Uncaught NumberFormatException on YAML Values
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/config/ConfigLoader.java:224`

`Double.parseDouble(sampleRate.toString())` will throw an uncaught `NumberFormatException` if the YAML value is a non-numeric string. This crashes configuration loading with a raw stack trace.

**Fix:** Wrap in try-catch and add a structured error to the `errors` list.

### 2.5 ScopedValue Optimization — Dead Code in MetricsCollector
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/MetricsCollector.java:75-83, 382`

Static `ScopedValue` fields are declared for `SUCCESS_PERCENTILES` and `FAILURE_PERCENTILES`, but **nothing in the codebase ever binds them**. The `snapshot()` method checks `isBound()` and falls back to creating new maps — the fallback is always taken. The `AutoCloseable` implementation is misleading (the comment claims ScopedValue handles cleanup, but there is nothing to clean).

**Fix:** Either bind the ScopedValues in `run()` and remove them in `close()`, or remove the ScopedValue fields entirely as dead code.

### 2.6 CachedMetricsProvider — Wasteful Spin-Wait Loop
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/CachedMetricsProvider.java:144-147`

A lock-free spin-wait loop (up to 100 iterations with `Thread.onSpinWait()`) is used for cache refresh coordination. This is a low-contention cache (1-2 threads typically), making the spin-wait pure CPU waste. A simple `synchronized` block or `ReentrantLock` would be simpler and more CPU-friendly.

**Fix:** Replace with `synchronized` or `StampedLock` for the cache refresh.

### 2.7 WarmupCooldownLoadPattern — Inconsistent With Record Pattern
**File:** `vajrapulse-api/src/main/java/com/vajrapulse/api/pattern/WarmupCooldownLoadPattern.java`

All other load patterns are records. This one is a plain `final class` with mutable-looking builder state (though fields are `final`). The nested `Phase` enum couples phase logic to this class unnecessarily.

**Fix:** Convert to a record `(LoadPattern basePattern, Duration warmupDuration, Duration cooldownDuration)`. Extract `Phase` as a top-level enum.

### 2.8 ConcurrentLinkedDeque Iteration Race in MetricsProviderAdapter
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/MetricsProviderAdapter.java:108-111, 153-161`

`ConcurrentLinkedDeque` iteration is weakly consistent. `findBaselineSnapshot()` iterates while `getRecentFailureRate()` may concurrently `addLast()` and `removeFirst()`. This produces slightly incorrect sliding-window failure rates.

**Fix:** Use a `synchronized` `ArrayDeque` (the window is small — 60s worth of snapshots, typically <100 entries) or a ring buffer.

### 2.9 LoadTestRunner — Reflection on Wrong Class Name
**File:** `vajrapulse-worker/src/main/java/com/vajrapulse/worker/pipeline/LoadTestRunner.java:183`

The `switch` on `"RampSustainLoad"` never matches because the actual class is `RampUpToMaxLoad`. Configuration for ramp-sustain mode is silently never extracted.

**Fix:** Fix the class name, and replace string-based reflection with a `getConfiguration()` method on `LoadPattern`.

### 2.10 OTel Exporter — Synchronized export() Causes Contention
**File:** `vajrapulse-exporter-opentelemetry/src/main/java/com/vajrapulse/exporter/otel/OpenTelemetryExporter.java:295`

The full `export()` method is `synchronized`, serializing all callers including the periodic OTLP reader thread. For any concurrent access pattern, this is a bottleneck. The delta computation could be done with `AtomicLong` operations instead.

**Fix:** Use `AtomicLong.getAndSet(0)` for delta counters and remove the broad `synchronized`.

### 2.11 RateController — Cumulative Integer Division Drift
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/RateController.java:121`

`(long)(1_000_000_000.0 / targetTps)` uses integer division for the sleep interval. For non-round TPS values over long tests, cumulative drift accumulates. At TPS=3 over 24 hours: ~8.6ms drift. At TPS=7 over 1 hour: ~0.4ms. Minor but measurable.

**Fix:** Use `Math.round(1_000_000_000.0 / targetTps)` to minimize drift, or track the error term (Bresenham-style).

### 2.12 StructuredLogger — Fragile Manual JSON Construction
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/logging/StructuredLogger.java`

Manual JSON building with `StringBuilder` does not escape all JSON control characters (missing: `\b`, `\f`, `/`, `\uXXXX` for other control chars). It also always builds the full JSON string even when the log level is disabled, defeating SLF4J lazy evaluation.

**Fix:** Use SLF4J's fluent API with structured arguments, or switch to a JSON logging library.

### 2.13 No Executor Metrics for Virtual Thread Engines
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/EngineMetricsRegistrar.java:116`

`registerExecutorMetrics()` checks `instanceof ThreadPoolExecutor`, which only matches platform thread pools. Virtual-thread-backed engines have **zero executor visibility** (no pool size, active count, queue depth).

**Fix:** For virtual threads, register metrics using `ThreadMXBean` (thread count, peak thread count) or JFR events.

---

## 3. Medium-Priority Issues (🟡)

### 3.1 Massive Code Duplication Across Exporters
**Files:** All three report exporters + console exporter

These helpers are duplicated identically in 3-4 files each:
- `nanosToMillis(double)` — 4 copies
- `formatPercentileLabel(double)` — 4 copies (with uppercase/lowercase inconsistency)
- `getPhaseName(int)` — 3 copies
- `toDouble(Object)` — 3 copies
- Adaptive Micrometer gauge name strings — 3 copies

**Fix:** Create a shared `ExportUtils` class in `vajrapulse-core` (accessible to all exporters), or add default methods to `MetricsExporter`.

### 3.2 LoadPatternFactory.create() — 20 Parameters
**File:** `vajrapulse-worker/src/main/java/com/vajrapulse/worker/LoadPatternFactory.java:30-60`

The `create()` method signature takes 20 parameters. This is unreadable and fragile at call sites. Every new load pattern adds more parameters.

**Fix:** Convert to a Builder or a parameter object (e.g., `LoadPatternSpec` record).

### 3.3 LoadTestRunner — Fragile Reflection-Based Config Extraction
**File:** `vajrapulse-worker/src/main/java/com/vajrapulse/worker/pipeline/LoadTestRunner.java:183-210`

`extractStaticLoadConfig()`, `extractRampLoadConfig()`, and `extractAdaptiveLoadConfig()` all use reflection to call getter methods by name. This silently breaks when fields are renamed and produces empty config when it fails.

**Fix:** Add a `Map<String, Object> getConfiguration()` method to the `LoadPattern` interface. This also eliminates the reflection.

### 3.4 HTML Report — Chart.js Loaded from CDN (Offline Breakage)
**File:** `vajrapulse-exporter-report/src/main/java/com/vajrapulse/exporter/report/HtmlReportExporter.java:95`

Chart.js is loaded from `cdn.jsdelivr.net` at report-open time. Reports don't work offline. This also introduces a supply-chain risk and a dependency on CDN availability.

**Fix:** Embed Chart.js as a base64-encoded string, or clearly document the online-only requirement in the report itself.

### 3.5 RunManifest.create() — Dead Code
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/run/RunManifest.java`

The `create()` factory method is defined but **never called anywhere** in the codebase. The `ExecutionEngine` creates manifests directly via the constructor.

**Fix:** Either delete `create()` or route `ExecutionEngine` through it.

### 3.6 ExceptionContext.elapsedMillis — Dead Parameter
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/util/ExceptionContext.java`

The `formatMessage()` method accepts an `elapsedMillis` parameter that **no public method exposes**. It's always called with `-1` internally.

**Fix:** Remove the dead parameter or add a public factory method that accepts elapsed time.

### 3.7 hikaricp-backpressure-example — Orphan Directory
**Directory:** `examples/hikaricp-backpressure-example/`

This has no `build.gradle.kts`, is not in `settings.gradle.kts`, and the entire implementation is commented out. It's not compilable.

**Fix:** Either add a proper `build.gradle.kts` with optional HikariCP dependency, or move it to `documents/integrations/` as a code snippet.

### 3.8 Missing package-info.java for Exception Package
**File:** Missing at `vajrapulse-api/src/main/java/com/vajrapulse/api/exception/package-info.java`

Every other package in the API module has a `package-info.java`. The exception package is the only one without it.

**Fix:** Add one for consistency.

### 3.9 @VirtualThreads/@PlatformThreads — No Compile-Time Mutual Exclusion
**Files:** `vajrapulse-api/src/main/java/com/vajrapulse/api/task/VirtualThreads.java` + `PlatformThreads.java`

A user can annotate a class with both `@VirtualThreads` and `@PlatformThreads`. The framework must detect and reject this at runtime.

**Fix:** Document the precedence rule explicitly in the Javadoc, and add a startup validation check in `ExecutionEngine`.

### 3.10 TaskResultSuccess.data — Raw Object Loses Type Safety
**File:** `vajrapulse-api/src/main/java/com/vajrapulse/api/task/TaskResultSuccess.java`

The `data` field is `Object`. Users must cast, losing type safety and risking `ClassCastException`.

**Fix:** Consider making `TaskResult` generic: `TaskResult<T>`, with `TaskResultSuccess<T>`. Evaluate the API-breaking impact — this may need to wait for 2.0.0.

### 3.11 Unbounded History Growth in MetricsProviderAdapter
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/MetricsProviderAdapter.java:108-111`

The `history` `ConcurrentLinkedDeque` prunes entries older than 60s, but pruning only happens when `getRecentFailureRate()` is called. If only the all-time rate is queried, the history grows unboundedly.

**Fix:** Add a size cap (e.g., 1000 entries) or prune on every addition.

### 3.12 Inconsistent Package Naming in Examples
**Files:** Across all examples

Examples use both `com.example.*` and `com.vajrapulse.examples.*` without a consistent convention. This confuses users about which package to use.

**Fix:** Standardize on one convention. Prefer `com.vajrapulse.examples.*`.

### 3.13 Ramp-sustain Negative Duration on Invalid Input
**File:** `vajrapulse-worker/src/main/java/com/vajrapulse/worker/LoadPatternFactory.java:98`

`testDuration.minus(ramp)` doesn't check whether `ramp > testDuration`. A user specifying `--ramp 60s --duration 30s` gets a negative sustain duration.

**Fix:** Validate and throw `IllegalArgumentException` if ramp >= testDuration.

### 3.14 parseDuration — NullPointerException on Null Input
**File:** `vajrapulse-worker/src/main/java/com/vajrapulse/worker/LoadPatternFactory.java:171`

Calling `.toLowerCase().trim()` on a null argument throws NPE.

**Fix:** Add null check at method entry.

### 3.15 Manual JSON in RunManifest.toJson()
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/run/RunManifest.java`

Same manual JSON fragility as StructuredLogger. Incomplete control character escaping.

**Fix:** Use Jackson (already a dependency of `vajrapulse-exporter-report`) or Gson.

### 3.16 AdaptiveDecisionEngine — Hardcoded Recovery TPS Ratio
**File:** `vajrapulse-api/src/main/java/com/vajrapulse/api/pattern/adaptive/AdaptiveDecisionEngine.java`

`RECOVERY_TPS_RATIO = 0.5` is hardcoded. This is a policy decision that belongs in `RampDecisionPolicy`.

**Fix:** Move to `DefaultRampDecisionPolicy` as a configurable field, or add a method to `RampDecisionPolicy` interface.

### 3.17 CpuBoundTest — Potential AES-GCM Decryption Issue
**File:** `examples/cpu-bound-test/src/main/java/com/example/cpu/CpuBoundTest.java:92`

The decrypt cipher is initialized without a `GCMParameterSpec` (IV). AES-GCM requires the same IV for encryption and decryption. This may fail intermittently.

**Fix:** Store the IV from encryption and pass it to the decrypt cipher.

### 3.18 CpuBoundTest — Deprecated Deflater/Inflater Constructors
**File:** `examples/cpu-bound-test/src/main/java/com/example/cpu/CpuBoundTest.java`

`new Deflater()` and `new Inflater()` are deprecated since Java 13.

**Fix:** Use `new Deflater(Deflater.DEFAULT_COMPRESSION)` and `new Inflater()`.

---

## 4. Low-Priority Issues (🟢)

### 4.1 PeriodicMetricsReporter — scheduleAtFixedRate Race
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/PeriodicMetricsReporter.java:66`

`scheduleAtFixedRate` queues the next execution even if the previous hasn't finished. If reporting takes longer than the interval, multiple `reportSafe()` calls could run concurrently.

**Fix:** Use `scheduleWithFixedDelay` instead, or guard with a semaphore.

### 4.2 ExecutionEngine — Redundant shutdownNow After shutdown
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java:580`

After init failure, `executor.shutdown()` is called, then `executor.awaitTermination()`, then `executor.shutdownNow()`. The `shutdownNow()` is redundant — the executor is already shutting down.

**Fix:** Just call `shutdownNow()` immediately (skip the graceful shutdown if init failed).

### 4.3 ConsoleMetricsExporter — I/O Errors Silently Ignored
**File:** `vajrapulse-exporter-console/src/main/java/com/vajrapulse/exporter/console/ConsoleMetricsExporter.java`

No call to `PrintStream.checkError()` anywhere. If stdout is a broken pipe, errors are swallowed.

**Fix:** Call `checkError()` at the end of `export()` and log a warning.

### 4.4 ShutdownManager — Hook Waits Full Force Timeout
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ShutdownManager.java:175`

The JVM shutdown hook waits the full `forceTimeout` (default 10s) even when all work completes in 100ms, delaying JVM exit unnecessarily.

**Fix:** Use `awaitTermination` with a tighter timeout in the hook, or signal the hook from the main thread.

### 4.5 Static Mutable State in AdaptivePatternMetrics.trackers
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/AdaptivePatternMetrics.java`

`ConcurrentHashMap<AdaptiveLoadPattern, PatternStateTracker>` is a static global map. Tests could leak state between runs.

**Fix:** Make it an instance field, or clear it in `@AfterEach` test hooks.

### 4.6 generateRunId() — 8-Char UUID Loses Entropy
**File:** `vajrapulse-worker/src/main/java/com/vajrapulse/worker/pipeline/LoadTestRunner.java`

Truncating UUID to 8 chars reduces collision resistance from 2^122 to ~2^32. For automated CI/CD running thousands of tests, collisions become plausible.

**Fix:** Use the full UUID, or at least 12 characters.

### 4.7 Stale Javadoc on LoadPattern
**File:** `vajrapulse-api/src/main/java/com/vajrapulse/api/pattern/LoadPattern.java`

Javadoc lists only 3 built-in implementations but there are 7+.

**Fix:** Update Javadoc to list all implementations, or reference `package-info.java`.

### 4.8 OTel Exporter Constructor Too Long
**File:** `vajrapulse-exporter-opentelemetry/src/main/java/com/vajrapulse/exporter/otel/OpenTelemetryExporter.java:82-239`

The constructor is ~160 lines. It builds resource, exporter, reader, meter, counter, and 5 gauges inline.

**Fix:** Extract `initResource()`, `initMeterProvider()`, `initCounters()`, `initGauges()` methods.

### 4.9 Missing README.md for Some Examples
**Examples:** `adaptive-with-warmup/`, `assertion-framework/`

These lack README files, unlike the other examples.

**Fix:** Add README.md files following the pattern from `http-load-test/`.

---

## 5. Architectural & Design Concerns

### 5.1 No getConfiguration() on LoadPattern Interface
The `LoadPattern` interface lacks a method to expose its configuration. This forces consumers (`LoadTestRunner`, `JsonReportExporter`, `HtmlReportExporter`) to use reflection or string-based gauge name lookups. Adding `Map<String, Object> getConfiguration()` (with a default returning empty map) would eliminate the reflection and centralize configuration export.

### 5.2 Exporter Error Handling — No Consistent Policy
- Console exporter: silently ignores errors
- OTel exporter: logs and swallows
- Report exporters: wraps in RuntimeException
- No exporter returns an `ExportResult` with success/failure status

Define a consistent policy. Recommended: all exporters log failures and return a result type. The caller decides whether to abort or continue.

### 5.3 Worker Hardcodes ConsoleMetricsExporter
**File:** `vajrapulse-worker/src/main/java/com/vajrapulse/worker/VajraPulseWorker.java:245-246`

The CLI worker always uses `ConsoleMetricsExporter`. Users can't use JSON/CSV/HTML/OTel exporters from the CLI without modifying source code. The `LoadTestRunner` pipeline supports multiple exporters; the CLI worker should too.

### 5.4 No Plugin/SPI Mechanism for Exporters
Exporters are wired manually. A `ServiceLoader`-based plugin mechanism would allow third-party exporters without code changes. The `MetricsExporter` interface is already well-suited for this.

### 5.5 AdaptiveLoadPattern — Too Much Logic in API Module
At ~820 lines, `AdaptiveLoadPattern` carries significant implementation logic (state transitions, listener management, snapshot capture). The "API" module is meant to be contracts only. Consider moving the implementation to `vajrapulse-core` and keeping only the interface in `vajrapulse-api`. This may require API-breaking changes in 2.0.0.

### 5.6 No Shutdown Coordination Between close() and run()
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java`

`close()` and `run()` can be called from different threads. There is no happens-before edge guaranteeing `run()` is done before `close()` accesses `loadPattern` and `shutdownManager`. Currently mitigated by `executor.isShutdown()` check, but fragile.

---

## 6. Test Coverage Gaps

### 6.1 Examples Have Zero Tests
The `examples/` directory has no test files at all. The build skips test/analysis plugins for examples. A CI regression could silently break examples.

**Fix:** Add at minimum a smoke test that runs each example for 5 seconds and verifies non-zero exit code.

### 6.2 Missing internal-test Subprojects
`high-throughput/` and `long-running/` are documented in the README but don't exist.

**Fix:** Either implement them or remove from README.

### 6.3 OTel Exporter — No Protocol-Level Validation Tests
`OpenTelemetryExporterSpec` tests builder configuration and resilience but never validates actual OTLP wire format.

### 6.4 No E2E Report Validation
Reports (JSON/CSV/HTML) are generated but there's no automated test that parses the output and verifies data correctness.

### 6.5 CpuBoundTest — No Unit Tests for Crypto Logic
The AES-GCM encrypt/decrypt logic has no tests to verify correctness, and the decryption issue (#3.17) would have been caught by one.

---

## 7. Build & CI/CD Gaps

### 7.1 Benchmarks Workflow — Missing Regression Gate
**File:** `.github/workflows/benchmarks.yml`

The benchmarks CI step runs JMH and uploads results, but has a `TODO: Add regression gate to fail if performance degrades`. This is a documented gap.

### 7.2 Gradle 9.x With Configuration Cache — Potential Issues
`org.gradle.configuration-cache=true` is enabled. With Gradle 9.2.0 and the `--enable-preview` flag, certain plugin combinations may not be fully compatible with configuration cache. Verify all CI pipelines use configuration cache without issues.

### 7.3 Hardcoded Service Version in Tracing.java
**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/tracing/Tracing.java:57`

`"0.9.0-SNAPSHOT"` is hardcoded as the OTel service version. It should be derived from the build (e.g., via resource filtering or `System.getProperty("vajrapulse.version")`).

---

## 8. Documentation Gaps

### 8.1 No Architecture Decision Records (ADRs)
Despite 165+ documents, there are no lightweight ADRs capturing key decisions: why unchecked exceptions, why zero-dependency API, why virtual threads, why no generics on TaskResult.

### 8.2 Stale Cross-Module References
- `BackpressureHandler` Javadoc references `BackpressureHandlers` factory (in core, not visible from API alone).
- `LoadPattern` Javadoc references only 3 of 7+ implementations.
- `package-info.java` for root mentions backpressure as part of metrics (it has its own package now).

### 8.3 Missing Troubleshooting Guide for Common Issues
No guide covering: "My test hangs during shutdown", "Virtual threads aren't being created", "OTLP export is not working", "Adaptive pattern never stabilizes".

---

## 9. Prioritized Action Items

### Iteration 1 — Stabilize (1-2 weeks)
| # | Action | Severity |
|---|--------|----------|
| 1 | Fix AdaptivePatternMetrics memory leak (1.1) | 🔴 |
| 2 | Add Tracing.shutdown() and wire into ExecutionEngine.close() (1.2) | 🔴 |
| 3 | Remove gauge-with-side-effects anti-pattern in AdaptivePatternMetrics (1.3) | 🔴 |
| 4 | Fix negative rate handling in MetricsProviderAdapter (1.4) | 🔴 |
| 5 | Add null/negative guards to ConfigLoader.parseDouble (2.4) | 🟠 |
| 6 | Fix ramp-sustain negative duration bug in LoadPatternFactory (3.13) | 🟠 |
| 7 | Add null check to parseDuration (3.14) | 🟠 |
| 8 | Fix CpuBoundTest AES-GCM IV issue and deprecated constructors (3.17, 3.18) | 🟠 |

### Iteration 2 — Harden (2-3 weeks)
| # | Action | Severity |
|---|--------|----------|
| 9 | Standardize metric units on ratio (0.0-1.0) across codebase (2.1) | 🟠 |
| 10 | Fix Assertions errorRate/successRate unit assumptions (2.2) | 🟠 |
| 11 | Replace System.err in AdaptiveLoadPattern with proper logging (2.3) | 🟠 |
| 12 | Fix or remove dead ScopedValue optimization in MetricsCollector (2.5) | 🟠 |
| 13 | Replace CachedMetricsProvider spin-wait with synchronized (2.6) | 🟠 |
| 14 | Convert WarmupCooldownLoadPattern to record (2.7) | 🟠 |
| 15 | Fix LoadTestRunner reflection with correct class name + add getConfiguration() to LoadPattern (2.9, 3.3, 5.1) | 🟠 |
| 16 | Replace manual JSON in StructuredLogger and RunManifest (2.12, 3.15) | 🟠 |
| 17 | Add virtual-thread executor metrics via ThreadMXBean (2.13) | 🟠 |
| 18 | Fix ConcurrentLinkedDeque race in MetricsProviderAdapter (2.8) | 🟠 |

### Iteration 3 — Polish (3-4 weeks)
| # | Action | Severity |
|---|--------|----------|
| 19 | Centralize duplicated exporter utilities (3.1) | 🟡 |
| 20 | Refactor LoadPatternFactory.create() to use Builder/parameter object (3.2) | 🟡 |
| 21 | Embed Chart.js in HTML reports or document online-only (3.4) | 🟡 |
| 22 | Clean up dead code (RunManifest.create, ExceptionContext.elapsedMillis, hikaricp-backpressure) (3.5, 3.6, 3.7) | 🟡 |
| 23 | Add missing package-info.java (3.8) | 🟡 |
| 24 | Add compile-time validation for @VirtualThreads/@PlatformThreads (3.9) | 🟡 |
| 25 | Support multiple exporters in VajraPulseWorker CLI (5.3) | 🟡 |
| 26 | Refactor OTelExporter constructor into init methods (4.8) | 🟢 |
| 27 | Fix PeriodicMetricsReporter scheduleAtFixedRate to scheduleWithFixedDelay (4.1) | 🟢 |
| 28 | Standardize example package naming (3.12) | 🟡 |
| 29 | Add README.md to examples missing them (4.9) | 🟢 |

### Iteration 4 — Future (Post-1.0.x)
| # | Action | Severity |
|---|--------|----------|
| 30 | Deploy ServiceLoader-based exporter plugin mechanism (5.4) | 🟡 |
| 31 | Move AdaptiveLoadPattern implementation to core, keep interface in API (5.5) | 🟡 |
| 32 | Add ADRs for key architectural decisions (8.1) | 🟢 |
| 33 | Add smoke tests for all examples in CI (6.1) | 🟡 |
| 34 | Implement missing high-throughput/long-running internal tests (6.2) | 🟡 |
| 35 | Add protocol-level OTLP validation tests (6.3) | 🟡 |
| 36 | Add E2E report validation tests (6.4) | 🟡 |
| 37 | Add benchmarks regression gate to CI (7.1) | 🟡 |
| 38 | Derive OTel service version from build instead of hardcoding (7.3) | 🟢 |
| 39 | Add troubleshooting guide for common issues (8.3) | 🟢 |
| 40 | Consider TaskResult<T> for type-safe data (3.10) | 🟡 |

---

## Summary Statistics

| Severity | Count |
|----------|-------|
| 🔴 Critical | 4 |
| 🟠 High | 13 |
| 🟡 Medium | 18 |
| 🟢 Low | 10 |
| **Total** | **45** |

**Bottom line:** VajraPulse has a solid architectural foundation — clean module separation, good use of modern Java (records, sealed interfaces, virtual threads, ScopedValue), and strong test coverage for the core. The main problems are: (1) memory/resource leaks that will surface in long-running or repeated-test scenarios, (2) unit inconsistency (percentage vs. ratio) that silently corrupts metrics, (3) fragile reflection and manual serialization that will break on refactoring, and (4) accumulated dead code and duplication from rapid development. The 4 critical issues and 13 high-priority bugs should be addressed before the next production deployment.
