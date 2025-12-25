# VajraPulse Benchmarks

JMH benchmarks for measuring core component performance.

## Setup

The benchmarks use the JMH (Java Microbenchmark Harness) plugin. Benchmark classes are located in `src/jmh/java`.

## Running Benchmarks

```bash
# Run all benchmarks
./gradlew :benchmarks:jmh

# Run specific benchmark
./gradlew :benchmarks:jmh -- -t 1 -f 1 -wi 5 -i 10 .*TaskExecutorBenchmark.*
```

## Benchmark Classes

- **TaskExecutorBenchmark**: Measures TaskExecutor overhead
- **MetricsCollectorBenchmark**: Measures metrics collection performance
- **RateControllerBenchmark**: Measures rate control precision

## Performance Targets

- **TaskExecutor overhead**: < 1μs per execution
- **Metrics collection**: < 100ns per record
- **Rate control precision**: < 1ms deviation

## Baseline Results

Baseline results will be documented in `documents/analysis/PERFORMANCE_BASELINE.md` after initial runs.
