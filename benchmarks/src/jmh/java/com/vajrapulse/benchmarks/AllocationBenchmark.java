package com.vajrapulse.benchmarks;

import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.TaskResult;
import com.vajrapulse.core.engine.TaskExecutor;
import com.vajrapulse.core.metrics.MetricsCollector;
import io.opentelemetry.api.trace.Span;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark to profile allocations in hot paths.
 * Uses JMH's GC profiler to measure allocation rates.
 * Run with: -prof gc
 * Target: ≥20% reduction from baseline.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseG1GC", "-Xmx512m"})
public class AllocationBenchmark {

    private TaskLifecycle noopTask;
    private TaskExecutor taskExecutor;
    private MetricsCollector metricsCollector;
    private long iteration;

    @Setup
    public void setup() {
        noopTask = new TaskLifecycle() {
            @Override
            public void init() throws Exception {
            }
            
            @Override
            public TaskResult execute(long iteration) throws Exception {
                return TaskResult.success();
            }
            
            @Override
            public void teardown() throws Exception {
            }
        };
        metricsCollector = new MetricsCollector();
        taskExecutor = new TaskExecutor(noopTask);
        iteration = 0;
    }

    @TearDown
    public void teardown() {
        metricsCollector.close();
    }

    /**
     * Profile allocations in TaskExecutor.executeWithMetrics().
     * Primary hot path - should minimize allocations.
     */
    @Benchmark
    public com.vajrapulse.core.engine.ExecutionMetrics taskExecutorHotPath() {
        return taskExecutor.executeWithMetrics(iteration++, Span.getInvalid(), "alloc-bench");
    }

    /**
     * Profile allocations in MetricsCollector.record().
     * Called for every task execution.
     */
    @Benchmark
    public void metricsRecordHotPath(Blackhole bh) throws Exception {
        long startNanos = System.nanoTime();
        bh.consume(noopTask.execute(iteration++));
        long endNanos = System.nanoTime();
        com.vajrapulse.core.engine.ExecutionMetrics metrics = 
            new com.vajrapulse.core.engine.ExecutionMetrics(startNanos, endNanos, TaskResult.success(), iteration - 1);
        metricsCollector.record(metrics);
    }

    /**
     * Profile allocations in snapshot() aggregation.
     * Called periodically, not on every execution.
     */
    @Benchmark
    public com.vajrapulse.core.metrics.AggregatedMetrics snapshotAllocation() {
        return metricsCollector.snapshot();
    }

    /**
     * Baseline: TaskResult.success() allocation.
     * Should be minimal (singleton or cached).
     */
    @Benchmark
    public TaskResult taskResultAllocation() {
        return TaskResult.success();
    }
}
