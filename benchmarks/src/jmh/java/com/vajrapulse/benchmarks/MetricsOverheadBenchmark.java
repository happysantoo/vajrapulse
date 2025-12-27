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
 * Benchmark to measure instrumentation overhead.
 * Compares execution with and without metrics collection.
 * Target: less than 0.5ms overhead at 50K concurrent threads.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MetricsOverheadBenchmark {

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
     * Baseline: Execute task without any instrumentation.
     */
    @Benchmark
    public TaskResult executeWithoutMetrics() throws Exception {
        return noopTask.execute(iteration++);
    }

    /**
     * Execute task with full metrics instrumentation.
     */
    @Benchmark
    public com.vajrapulse.core.engine.ExecutionMetrics executeWithMetrics() {
        return taskExecutor.executeWithMetrics(iteration++, Span.getInvalid(), "benchmark-run");
    }

    /**
     * Record execution to MetricsCollector directly.
     */
    @Benchmark
    public void recordExecution(Blackhole bh) throws Exception {
        long startNanos = System.nanoTime();
        bh.consume(noopTask.execute(iteration++));
        long endNanos = System.nanoTime();
        com.vajrapulse.core.engine.ExecutionMetrics metrics = 
            new com.vajrapulse.core.engine.ExecutionMetrics(startNanos, endNanos, TaskResult.success(), iteration - 1);
        metricsCollector.record(metrics);
    }

    /**
     * Snapshot metrics (aggregation overhead).
     */
    @Benchmark
    public com.vajrapulse.core.metrics.AggregatedMetrics snapshotMetrics() {
        return metricsCollector.snapshot();
    }
}
