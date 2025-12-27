package com.vajrapulse.benchmarks;

import com.vajrapulse.api.pattern.StaticLoad;
import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.TaskResult;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.MetricsCollector;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Macro scenario benchmark simulating realistic load test.
 * 
 * <p>This benchmark simulates a 10K TPS sustained load for 2 minutes
 * to measure end-to-end overhead and system behavior under realistic conditions.
 * 
 * <p>Target metrics:
 * - Total execution count: ~1,200,000
 * - Average latency overhead: less than 1 microsecond
 * - Memory allocation rate: TBD
 * - CPU utilization: TBD
 * 
 * @since 0.9.11
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 0)  // No warmup for macro scenario
@Measurement(iterations = 1)
@Fork(value = 1, jvmArgsAppend = {"-Xmx2g", "-XX:+UseG1GC"})
public class MacroScenarioBenchmark {

    private TaskLifecycle noopTask;
    private MetricsCollector metricsCollector;
    
    @Setup
    public void setup() {
        noopTask = new TaskLifecycle() {
            @Override
            public void init() throws Exception {
            }
            
            @Override
            public TaskResult execute(long iteration) throws Exception {
                // Simulate minimal work (HTTP request simulation)
                return TaskResult.success();
            }
            
            @Override
            public void teardown() throws Exception {
            }
        };
        metricsCollector = new MetricsCollector();
    }
    
    @TearDown
    public void teardown() {
        metricsCollector.close();
    }
    
    /**
     * Macro scenario: 10K TPS for 2 minutes.
     * 
     * This is a realistic load test scenario that measures:
     * - End-to-end execution overhead
     * - Memory allocation patterns
     * - System stability under sustained load
     */
    @Benchmark
    public void macroScenario_10K_TPS_2min(Blackhole bh) throws Exception {
        // 10,000 TPS for 2 minutes = 1,200,000 total executions
        StaticLoad pattern = new StaticLoad(10_000.0, Duration.ofMinutes(2));
        
        try (ExecutionEngine engine = ExecutionEngine.builder()
                .withTask(noopTask)
                .withLoadPattern(pattern)
                .withMetricsCollector(metricsCollector)
                .build()) {
            engine.run();
            
            // Consume metrics to prevent optimization
            var metrics = metricsCollector.snapshot();
            bh.consume(metrics.totalExecutions());
            bh.consume(metrics.successCount());
            bh.consume(metrics.successPercentiles());
        }
    }
    
    /**
     * Smaller scenario for faster CI runs: 1K TPS for 10 seconds.
     */
    @Benchmark
    public void microScenario_1K_TPS_10s(Blackhole bh) throws Exception {
        StaticLoad pattern = new StaticLoad(1_000.0, Duration.ofSeconds(10));
        
        try (ExecutionEngine engine = ExecutionEngine.builder()
                .withTask(noopTask)
                .withLoadPattern(pattern)
                .withMetricsCollector(metricsCollector)
                .build()) {
            engine.run();
            
            var metrics = metricsCollector.snapshot();
            bh.consume(metrics.totalExecutions());
            bh.consume(metrics.successCount());
        }
    }
}
