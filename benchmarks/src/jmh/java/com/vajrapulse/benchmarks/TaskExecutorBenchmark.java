package com.vajrapulse.benchmarks;

import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.TaskResult;
import com.vajrapulse.core.engine.TaskExecutor;
import io.opentelemetry.api.trace.Span;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark for TaskExecutor overhead.
 * 
 * <p>Measures the overhead of task execution instrumentation,
 * including metrics collection and timing.
 * 
 * @since 0.9.10
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class TaskExecutorBenchmark {
    
    private TaskExecutor taskExecutor;
    private TaskLifecycle task;
    
    /**
     * Simple task that returns success immediately.
     */
    private static class NoOpTask implements TaskLifecycle {
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
    }
    
    @Setup
    public void setup() {
        task = new NoOpTask();
        try {
            task.init();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        taskExecutor = new TaskExecutor(task);
    }
    
    @TearDown
    public void tearDown() {
        try {
            task.teardown();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Benchmark
    public com.vajrapulse.core.engine.ExecutionMetrics executeWithMetrics() {
        // Use invalid span for benchmarks (tracing disabled)
        // In real usage, ExecutionEngine provides the actual scenario span
        return taskExecutor.executeWithMetrics(0, Span.getInvalid(), "benchmark-run");
    }
    
    @Benchmark
    public TaskResult executeDirect() throws Exception {
        return task.execute(0);
    }
}
