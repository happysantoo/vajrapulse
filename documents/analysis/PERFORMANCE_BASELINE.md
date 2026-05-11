# VajraPulse Performance Baseline

**Date**: 2026-04-04  
**Version**: 1.0.0  
**Status**: Baseline captured (default JMH suite; macro benchmark optional)

---

## Executive Summary

This document records JMH results for VajraPulse core components. Use them for regression triage with [`scripts/compare-benchmarks.sh`](../../scripts/compare-benchmarks.sh) and the benchmark workflow.

**Baseline environment (this run)**:

| Field | Value |
|-------|--------|
| JDK | OpenJDK 21.0.8 (Homebrew `openjdk@21`), macOS aarch64 |
| JMH | 1.37 |
| JVM options (forks) | `--enable-preview`, `-XX:+UseG1GC`, `-Xmx512m` (see JMH console for per-benchmark variance) |
| Command | `./gradlew :benchmarks:jmh --no-configuration-cache` |

**Note**: `MacroScenarioBenchmark` is **excluded by default** (~2 min per iteration). To include it:

```bash
./gradlew :benchmarks:jmh -Pjmh.includeMacro --no-configuration-cache
```

---

## Benchmark Results (2026-04-04)

Summary copied from `benchmarks/build/results/jmh/results.txt` after a successful local run.

### TaskExecutorBenchmark

| Benchmark | Mode | Score | Error | Units |
|-----------|------|-------|-------|-------|
| `executeWithMetrics` | avgt | 26.968 | ± 0.314 | ns/op |
| `executeDirect` | avgt | 3.274 | ± 0.222 | ns/op |

Instrumentation overhead (approx.): ~23.7 ns/op vs direct execute on this machine.

### MetricsCollectorBenchmark

| Benchmark | Mode | Score | Error | Units |
|-----------|------|-------|-------|-------|
| `record` | avgt | 65.058 | ± 0.917 | ns/op |
| `snapshot` | avgt | 1872.087 | ± 188.325 | ns/op |
| `recordAndSnapshot` | avgt | 39497.876 | ± 743.506 | ns/op |

### RateControllerBenchmark

| Benchmark | Mode | Score | Error | Units |
|-----------|------|-------|-------|-------|
| `getCurrentTps` | avgt | 10.513 | ± 0.171 | ns/op |
| `getElapsedMillis` | avgt | 9.758 | ± 0.215 | ns/op |
| `waitForNext` | avgt | 995508.633 | ± 30109.097 | ns/op |

`waitForNext` average reflects ~1 Hz pacing in the benchmark fixture (expected).

Throughput mode (same class, alternate modes):

| Benchmark | Mode | Score | Error | Units |
|-----------|------|-------|-------|-------|
| `getCurrentTps` | thrpt | 0.095 | ± 0.002 | ops/ns |
| `getElapsedMillis` | thrpt | 0.103 | ± 0.002 | ops/ns |

### MetricsOverheadBenchmark

| Benchmark | Mode | Score | Error | Units |
|-----------|------|-------|-------|-------|
| `executeWithoutMetrics` | avgt | 1.290 | ± 0.026 | ns/op |
| `executeWithMetrics` | avgt | 26.805 | ± 0.448 | ns/op |
| `recordExecution` | avgt | 89.558 | ± 1.605 | ns/op |
| `snapshotMetrics` | avgt | 1836.632 | ± 40.105 | ns/op |

### AllocationBenchmark (throughput)

| Benchmark | Mode | Score | Error | Units |
|-----------|------|-------|-------|-------|
| `metricsRecordHotPath` | thrpt | 11134599.191 | ± 182639.894 | ops/s |
| `snapshotAllocation` | thrpt | 576868.574 | ± 10631.050 | ops/s |
| `taskExecutorHotPath` | thrpt | 37236112.883 | ± 476524.668 | ops/s |
| `taskResultAllocation` | thrpt | 328596930.809 | ± 1084871.269 | ops/s |

---

## Macro Scenario Benchmark

**Status**: Implemented; **not** in the default JMH run (use `-Pjmh.includeMacro`).

Target scenario: 10,000 TPS, 2-minute single-shot invocations. Expect ~120 s/op per iteration.

---

## Performance Targets Summary

| Component | Metric | Target | Baseline (2026-04-04) | Status |
|-----------|--------|--------|------------------------|--------|
| TaskExecutor | `executeWithMetrics` | < 1,000 ns/op | ~27 ns/op | OK |
| MetricsCollector | `record` | < 100 ns/op | ~65 ns/op | Watch |
| MetricsCollector | `snapshot` | context-dependent | ~1.9 μs/op | OK |
| RateController | `getCurrentTps` / `getElapsedMillis` | < 10 μs | ~10 ns/op | OK |

Targets are indicative; compare **relative** changes on the **same** hardware/CI image.

---

## Running benchmarks

```bash
# Default suite (macro excluded)
./gradlew :benchmarks:jmh --no-configuration-cache

# Include macro benchmark
./gradlew :benchmarks:jmh -Pjmh.includeMacro --no-configuration-cache
```

Results: `benchmarks/build/results/jmh/results.txt`

### JMH + preview (ScopedValue)

The bytecode generator and benchmark forks run with `--enable-preview` (see [`benchmarks/build.gradle.kts`](../../benchmarks/build.gradle.kts)).

---

## Regression detection

**Threshold**: investigate if degradation exceeds ~10% on comparable hardware (see `scripts/compare-benchmarks.sh`).

---

## CI

Benchmarks workflow: [`.github/workflows/benchmarks.yml`](../../.github/workflows/benchmarks.yml).

---

**Last updated**: 2026-04-04  
**Next review**: After major engine or metrics changes, or quarterly
