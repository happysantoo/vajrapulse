package com.vajrapulse.benchmarks;

import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.engine.ExecutionMetrics;
import com.vajrapulse.api.task.TaskResult;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark for MetricsCollector performance.
 * 
 * <p>Measures the overhead of metrics collection operations,
 * including recording, snapshotting, and aggregation.
 * 
 * @since 0.9.10
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MetricsCollectorBenchmark {
    
    private MetricsCollector collector;
    private ExecutionMetrics metrics;
    
    @Setup
    public void setup() {
        collector = new MetricsCollector();
        long startNanos = System.nanoTime();
        long endNanos = startNanos + 1_000_000L;  // 1ms duration
        metrics = new ExecutionMetrics(
            startNanos,
            endNanos,
            TaskResult.success(),
            0L  // iteration
        );
    }
    
    @TearDown
    public void tearDown() {
        collector.close();
    }
    
    @Benchmark
    public void record() {
        collector.record(metrics);
    }
    
    @Benchmark
    public com.vajrapulse.core.metrics.AggregatedMetrics snapshot() {
        return collector.snapshot();
    }
    
    @Benchmark
    public void recordAndSnapshot() {
        collector.record(metrics);
        collector.snapshot();
    }
}
