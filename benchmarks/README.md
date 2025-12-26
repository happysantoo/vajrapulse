# VajraPulse Benchmarks

JMH (Java Microbenchmark Harness) benchmarks for measuring core component performance.

## Overview

This module provides micro-benchmarks for critical VajraPulse components to ensure optimal performance and identify regressions.

## Setup

The benchmarks use the JMH Gradle plugin. Benchmark classes are located in `src/jmh/java`.

**Prerequisites**:
- Java 21
- Gradle 9.0+

## Running Benchmarks

### Run All Benchmarks

```bash
./gradlew :benchmarks:jmh
```

This will:
- Compile benchmark classes
- Run all benchmarks with default settings
- Generate results in `benchmarks/build/jmh-results/`

### Run Specific Benchmark

```bash
# Run only TaskExecutorBenchmark
./gradlew :benchmarks:jmh -- -t 1 -f 1 -wi 5 -i 10 .*TaskExecutorBenchmark.*

# Run only MetricsCollectorBenchmark
./gradlew :benchmarks:jmh -- -t 1 -f 1 -wi 5 -i 10 .*MetricsCollectorBenchmark.*

# Run only RateControllerBenchmark
./gradlew :benchmarks:jmh -- -t 1 -f 1 -wi 5 -i 10 .*RateControllerBenchmark.*
```

### Custom JMH Options

```bash
# Custom warmup iterations, measurement iterations, forks
./gradlew :benchmarks:jmh -- -wi 10 -i 20 -f 3

# Run with specific JVM options
./gradlew :benchmarks:jmh -- -jvmArgs "-Xmx2g -XX:+UseG1GC"
```

## Benchmark Classes

### TaskExecutorBenchmark

Measures `TaskExecutor` overhead for task execution, instrumentation, and metrics recording.

**Metrics**:
- Execution overhead (nanoseconds per execution)
- Instrumentation overhead
- Metrics recording overhead

**Performance Target**: < 1μs per execution

### MetricsCollectorBenchmark

Measures `MetricsCollector` performance for recording and snapshot operations.

**Metrics**:
- Record operation latency (nanoseconds)
- Snapshot generation latency
- Concurrent recording performance

**Performance Target**: < 100ns per record

### RateControllerBenchmark

Measures `RateController` precision and overhead for rate control operations.

**Metrics**:
- Rate control calculation overhead
- Timing precision (deviation from target)
- Adaptive sleep performance

**Performance Target**: < 1ms deviation from target TPS

## Performance Targets

| Component | Metric | Target |
|-----------|--------|--------|
| TaskExecutor | Execution overhead | < 1μs per execution |
| MetricsCollector | Record latency | < 100ns per record |
| MetricsCollector | Snapshot generation | < 1ms for 1M executions |
| RateController | Timing precision | < 1ms deviation |
| RateController | Calculation overhead | < 10μs per check |

## Interpreting Results

JMH results are written to `benchmarks/build/jmh-results/` in JSON format.

**Key Metrics**:
- **Score**: Primary metric (lower is better for time, higher is better for throughput)
- **Error**: Statistical error margin
- **Units**: Measurement units (ns/op, ops/ns, etc.)

**Example Output**:
```
Benchmark                                    Mode  Cnt    Score   Error  Units
TaskExecutorBenchmark.executeTask           avgt   10  850.234 ± 12.456  ns/op
MetricsCollectorBenchmark.recordMetrics     avgt   10   45.123 ±  2.345  ns/op
RateControllerBenchmark.waitForNext         avgt   10  125.678 ±  5.432  ns/op
```

## CI Integration

Benchmarks can be integrated into CI pipelines to detect performance regressions:

```bash
# Run benchmarks and compare against baseline
./gradlew :benchmarks:jmh
# Compare results with baseline in CI
```

## Baseline Results

Baseline results will be documented in `documents/analysis/PERFORMANCE_BASELINE.md` after initial runs.

## Adding New Benchmarks

1. Create benchmark class in `src/jmh/java/com/vajrapulse/benchmarks/`
2. Annotate methods with `@Benchmark`
3. Use `@State` for shared state
4. Run with `./gradlew :benchmarks:jmh`

**Example**:
```java
@State(Scope.Benchmark)
public class MyComponentBenchmark {
    private MyComponent component;
    
    @Setup
    public void setup() {
        component = new MyComponent();
    }
    
    @Benchmark
    public void benchmarkOperation() {
        component.operation();
    }
}
```

## Troubleshooting

**Issue**: Benchmarks fail to compile
- **Solution**: Ensure JMH dependencies are correctly configured in `build.gradle.kts`

**Issue**: Results show high variance
- **Solution**: Increase warmup iterations (`-wi`) and measurement iterations (`-i`)

**Issue**: OutOfMemoryError
- **Solution**: Increase JVM heap size: `-jvmArgs "-Xmx4g"`
