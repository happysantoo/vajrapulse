# VajraPulse 0.9.x Accelerated Release Plan

Date: 2025-11-16
Purpose: Deliver an initial public 0.9.x release focusing on stability, core observability, scalable execution & expanded load patterns, plus BlazeMeter integration. Distributed multi-node orchestration is explicitly deferred to post-0.9.

## 1. Strategic Objectives
1. Publish a consumable artifact (`0.9.0`) to Maven Central (preferred) or JitPack (fallback) ASAP.
2. Establish trust via stability (lifecycle consistency, graceful shutdown) & correctness of metrics.
3. Close core observability gaps (metrics completeness, tracing, log correlation, run metadata).
4. Support a meaningful set of load patterns (static, ramp, ramp+hold, step, spike, sinusoidal).
5. Provide BlazeMeter integration path before tackling distributed testing.
6. Performance: verify scaling under high concurrency (virtual threads) & low instrumentation overhead.

## 2. Scope In / Scope Out
Included (0.9):
- Lifecycle API & graceful shutdown
- Configuration system (single-file + environment overrides)
- Metrics (current unified schema) + JVM/system metrics
- Tracing (task + scenario spans, sampling) & structured JSON logs with correlation
- Run ID tagging across metrics/traces/logs
- Expanded load patterns implementations
- BlazeMeter integration (export adapter or format translator)
- Benchmark & profiling harness (macro + allocation + overhead)
- Publishing pipeline (Gradle/Maven Central or JitPack fallback)
- Quick Start & Versioning docs

Deferred (post-0.9):
- Distributed coordination (controller + workers)
- Advanced backpressure strategies beyond basic skip/catch-up
- Plugin SPI formalization
- Persistence layer & run archives
- Exemplars, adaptive warm-up, signed builds

## 3. High-Level Phases
| Phase | Focus | Duration (est.) | Exit Criteria |
|-------|-------|-----------------|---------------|
| P0 | Stabilize Core | 3-4 days | Lifecycle, shutdown, config loader working & tested |
| P1 | Observability Core | 3 days | Tracing & log correlation integrated; run ID tagging; metrics validated |
| P2 | Load Pattern Expansion | 2 days | Pattern implementations + unit tests + docs |
| P3 | Performance & Scaling | 3 days | Benchmarks baseline; overhead & allocation targets met |
| P4 | BlazeMeter Integration | 2-3 days | Export adapter producing BlazeMeter-compatible output |
| P5 | Packaging & Docs | 2 days | Quick Start, Versioning, Publishing config complete |
| P6 | Release Prep & QA | 3 days | All gates green; candidate tag `0.9.0-rc1` |

## 4. Detailed Deliverables
### 4.1 Stability
- TaskLifecycle interface: `init()`, `execute(iteration)`, `teardown()` with clear semantics.
- Graceful shutdown hook: traps SIGINT/SIGTERM, stops scheduling, drains running tasks, final metrics/traces flush.
- Configuration: `vajrapulse.conf.yml` (or `.json`), environment variable overrides `VAJRAPULSE_*`, validation errors reported with structured log.

### 4.2 Observability Core
- Metrics enhancement: add JVM (memory, threads) & system CPU metrics via Micrometer binders (minimal set, justify each).
- Tracing: scenario root span, per-execution child spans (sampled); error tagging (`status=error`, exception event).
- Logging: JSON layout; fields: timestamp, level, message, run_id, task_id, iteration, trace_id, span_id, status.
- Run ID: generated UUID at start; exposed via config snapshot log & metrics tag `run_id`.
	- Current status: integrated into `MetricsCollector` and `ExecutionEngine` (worker now creates collector with `run_id`). Pipeline builder supports explicit runId.
	- Tracing: Minimal OpenTelemetry tracing added (env enable `VAJRAPULSE_TRACE_ENABLED=true`); spans for each execution with attributes (run_id, iteration, status). Scenario span placeholder to follow.
	- Structured Logging: Added lightweight `StructuredLogger` producing JSON lines for run start/finish; execution-level JSON planned post refinement.

### 4.3 Load Patterns
Implemented / To Implement records (Java 21) for `LoadPattern`:
1. StaticLoad(rate) – constant TPS.
2. RampUpLoad(maxRate, duration) – linear from 0 to max.
3. RampUpToMaxLoad(maxRate, rampDuration, holdDuration) – linear then steady.
4. StepLoad(List<Step(rate, duration)>) – discrete segments (added).
5. SpikeLoad(baseRate, spikeRate, totalDuration, spikeInterval, spikeDuration) – periodic bursts (added).
6. SineWaveLoad(meanRate, amplitude, totalDuration, period) – smooth oscillation (added).

Testing: deterministic segment assertions, spike window checks, sinusoidal bounds; all non-negative.
Future patterns (post-0.9): ClosedLoopLoad, AdaptiveLoad, PoissonLoad.

### 4.4 Performance & Scaling
- JMH micro benchmarks: executor submit path, rate calculation functions.
- Macro benchmark: 10K TPS for 2 minutes using StaticLoad & RampLoad.
- Overhead measurement: instrumentation vs disabled run; log results in `PERFORMANCE_BASELINE.md`.
- Allocation profiling: JFR or async-profiler output; removal of transient objects in hot path.
- Targets: overhead <0.5 ms, rate drift <5%, allocation reduction >=20% vs baseline.

### 4.5 BlazeMeter Integration
Goal: Enable users to feed VajraPulse results into BlazeMeter ecosystem.
Approach Options:
1. JTL Exporter: generate JMeter-style JTL (CSV/XML) summarizing executions.
2. REST Adapter: POST execution summaries to BlazeMeter-compatible ingestion endpoint (if available).
MVP Choice: JTL exporter (lowest dependency footprint).
Deliverables:
- `BlazeMeterJtlExporter` writing periodic flushes + final file.
- Config flags: output path, flush interval.
- Documentation: mapping from VajraPulse metrics to JTL columns (sampleTime, success, latency, label).

### 4.6 Packaging & Docs
- Publishing: Gradle `maven-publish` + signing keys (if available) fallback to JitPack if keys absent.
- Group coordinates: `com.vajrapulse:vajrapulse-core:0.9.0` etc.
- Quick Start: single page `QUICK_START.md` (install, run sample HTTP test, view metrics).
- Versioning policy: `VERSIONING.md` (0.x break allowed; 1.0 freeze rules; semantic versioning after).
- API JavaDoc coverage 100% for `api` module.

### 4.7 Release Prep & QA
- CI workflow: build, test, lint, minimal benchmark smoke (not full duration), security scan (OWASP dependency-check), JavaDoc check.
- Quality gates: zero warnings, coverage threshold (define e.g. 80%), performance threshold (drift & overhead), JavaDoc coverage.
- Release candidate tag: `v0.9.0-rc1` -> soak test (optional) -> final tag `v0.9.0`.

## 5. Phase Entry / Exit Criteria
| Phase | Entry | Exit |
|-------|-------|------|
| P0 | Current codebase | Lifecycle & shutdown integrated + config validated tests |
| P1 | P0 exit | Tracing + logging + run ID working; sample trace visible |
| P2 | P1 exit | All patterns implemented + tests passing |
| P3 | P2 exit | Baseline performance metrics recorded; targets met or issues logged |
| P4 | P3 exit | JTL exporter functioning with sample run; file validated |
| P5 | P4 exit | Publishing setup tested locally (dry-run) + docs drafted |
| P6 | P5 exit | CI gates all green; RC tag pushed |

## 6. Acceptance Gates (0.9)
| Gate | Condition |
|------|-----------|
| Stability | No uncaught exceptions under stress; graceful shutdown flush complete <5s |
| Observability | Metrics + traces + logs correlate via run_id; latency percentiles accurate |
| Load Patterns | Rate functions produce expected sequences; no negative/NaN rates |
| Performance | Overhead <0.5 ms; drift <5%; allocation reduction achieved |
| BlazeMeter | JTL file import recognized by BlazeMeter or JMeter viewer without errors |
| Publishing | Artifacts build reproducibly; coordinates documented; checksum validation |
| Docs | Quick Start runnable <2 minutes from clone |
| CI | All workflows pass; coverage >= threshold; zero warnings |

## 7. Risks & Mitigations
| Risk | Impact | Mitigation |
|------|--------|-----------|
| Tracing overhead | Perf regression | Sampling configurable & default low (5%) |
| JTL mapping mismatch | Import failure | Validate against sample JMeter output; unit test comparators |
| Performance drift unstable | Hard to gate reliably | Use median of N runs and warm-up; anchor baseline commit |
| Publishing delays (Central) | Release lag | Fallback: JitPack release then follow-up Central submission |

## 8. Implementation Order (Granular Task Queue)
1. Lifecycle interfaces & executor integration
2. Graceful shutdown manager + signal handling tests
3. Config loader & validation + env override utility
4. Log formatter (JSON) + correlation fields
5. Trace instrumentation (scenario + task spans) + sampling config
6. Run ID propagation wiring
7. JVM/system metrics binders minimal set
8. Load pattern suite + tests + docs
9. Benchmark harness (micro + macro); record baseline
10. Allocation & overhead profiling; optimization passes
11. BlazeMeter JTL exporter + test fixture
12. Maven publishing config & dry-run to staging (or JitPack test commit)
13. Quick Start + Versioning docs
14. CI workflow & quality gates integration
15. Release candidate validation & tag

## 9. Metrics & Tracking
- Use GitHub issues labeled: `0.9`, `stability`, `observability`, `performance`, `load-patterns`, `blazemeter`, `publishing`.
- Weekly summary appended to `IMPLEMENTATION_UPDATES.md` referencing issue IDs.

## 10. Decision Log (New for 0.9)
Record non-obvious choices in `documents/DECISIONS.md` (e.g., BlazeMeter JTL vs REST first, sampling default 5%).

## 11. Roll Forward to 1.0 (Preview)
After 0.9: focus shifts to distributed coordination, plugin SPI, advanced backpressure, exemplars, persistence.

---
Prepared by: GitHub Copilot
