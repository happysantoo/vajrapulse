# VajraPulse Performance Baseline

**Date**: 2026-03-10
**Version**: 1.0.0
**Status**: Infrastructure Ready — Run `./gradlew :benchmarks:jmh` to populate actual numbers

---

## Executive Summary

This document establishes performance baselines for VajraPulse core components using JMH benchmarks. These baselines serve as reference points for detecting performance regressions and validating optimizations.

**Baseline Environment**:
- Java: 21
- OS: macOS/Linux
- CPU: Variable (CI runners)
- JVM: OpenJDK 21 (default GC)

---

## Benchmark Results

### TaskExecutorBenchmark

Measures overhead of task execution instrumentation.

| Benchmark | Mode | Score (ns/op) | Error | Target | Status |
|-----------|------|---------------|-------|--------|--------|
| `executeWithMetrics` | avgt | TBD | ±TBD | < 1,000ns | ⏳ Pending |
| `executeDirect` | avgt | TBD | ±TBD | Baseline | ⏳ Pending |

**Notes**:
- `executeWithMetrics` includes full instrumentation (timing, tracing, metrics)
- `executeDirect` measures raw task execution (baseline)
- Overhead = `executeWithMetrics` - `executeDirect`

---

### MetricsCollectorBenchmark

Measures metrics collection performance.

| Benchmark | Mode | Score | Error | Target | Status |
|-----------|------|-------|-------|--------|--------|
| `record` | avgt | TBD ns/op | ±TBD | < 100ns | ⏳ Pending |
| `snapshot` | avgt | TBD ms | ±TBD | < 1ms (1M execs) | ⏳ Pending |
| `recordAndSnapshot` | avgt | TBD ns/op | ±TBD | Combined | ⏳ Pending |

**Notes**:
- `record` measures single metric recording overhead
- `snapshot` measures aggregation overhead (with 1M recorded executions)
- `recordAndSnapshot` measures combined operation

---

### RateControllerBenchmark

Measures rate control precision and overhead.

| Benchmark | Mode | Score | Error | Target | Status |
|-----------|------|-------|-------|--------|--------|
| `getCurrentTps` | avgt | TBD ns/op | ±TBD | < 10μs | ⏳ Pending |
| `getElapsedMillis` | avgt | TBD ns/op | ±TBD | < 100ns | ⏳ Pending |
| `waitForNext` | avgt | TBD ns/op | ±TBD | Variable | ⏳ Pending |

**Notes**:
- `waitForNext` includes adaptive sleep, so timing varies
- Precision measured separately via integration tests

---

## Macro Scenario Benchmark

**Status**: ⏳ To be implemented

A macro benchmark simulating a realistic load test scenario:
- 10,000 TPS sustained load
- 2-minute duration
- Measures end-to-end overhead

**Target Metrics**:
- Total execution count: ~1,200,000
- Average latency overhead: < 1μs
- Memory allocation rate: TBD
- CPU utilization: TBD

---

## Performance Targets Summary

| Component | Metric | Target | Baseline | Status |
|-----------|--------|--------|----------|--------|
| TaskExecutor | Execution overhead | < 1μs | TBD | ⏳ Pending |
| MetricsCollector | Record latency | < 100ns | TBD | ⏳ Pending |
| MetricsCollector | Snapshot (1M execs) | < 1ms | TBD | ⏳ Pending |
| RateController | TPS calculation | < 10μs | TBD | ⏳ Pending |
| RateController | Timing precision | < 1ms deviation | TBD | ⏳ Pending |

---

## Running Benchmarks

### Generate Baseline

```bash
# Run all benchmarks
./gradlew :benchmarks:jmh

# Results will be in benchmarks/build/jmh-results/
```

### Update This Document

After running benchmarks, update this document with actual results:
1. Copy results from JMH output
2. Update tables with actual scores
3. Mark status as ✅ Baseline or ⚠️ Needs Attention
4. Commit baseline to version control

---

## Regression Detection

**Threshold**: 10% degradation triggers investigation

**Process**:
1. Run benchmarks in CI
2. Compare against baseline
3. Fail CI if regression > 10%
4. Document regression in issue tracker

---

## Additional Benchmarks

### MetricsOverheadBenchmark

Measures instrumentation overhead (Task 3.3: P7).

| Benchmark | Mode | Target | Purpose |
|-----------|------|--------|---------|
| `executeWithoutMetrics` | avgt | Baseline | Raw task execution |
| `executeWithMetrics` | avgt | < 500μs overhead | Full instrumentation |
| `recordExecution` | avgt | < 100ns | Direct metric recording |
| `snapshotMetrics` | avgt | < 1ms | Aggregation overhead |

### AllocationBenchmark

Measures allocation rates in hot paths (Task 3.2: P2).

Run with GC profiler: `./gradlew :benchmarks:jmh -Pjmh.profilers=gc`

| Benchmark | Purpose | Target |
|-----------|---------|--------|
| `taskExecutorHotPath` | Full execution path | ≥20% reduction |
| `metricsRecordHotPath` | Metrics recording | Minimal allocations |
| `snapshotAllocation` | Aggregation allocations | Acceptable (periodic) |
| `taskResultAllocation` | TaskResult creation | Zero/minimal |

---

## CI Integration

Benchmarks run on push to main via `.github/workflows/benchmarks.yml`.

Results are uploaded as artifacts for comparison.

---

## Next Steps

1. ✅ Created benchmark suite (TaskExecutor, MetricsCollector, RateController)
2. ✅ Created overhead benchmark (MetricsOverheadBenchmark)
3. ✅ Created allocation benchmark (AllocationBenchmark)
4. ✅ Set up CI benchmark workflow
5. ⏳ Run initial baseline and document actual results
6. ✅ Implement benchmark comparison script (`scripts/compare-benchmarks.sh`)

---

**Last Updated**: 2026-03-10
**Next Review**: After first 1.0.0 baseline run is committed
