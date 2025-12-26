package com.vajrapulse.benchmarks;

import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.pattern.StaticLoad;
import com.vajrapulse.core.engine.RateController;
import org.openjdk.jmh.annotations.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for RateController precision and overhead.
 * 
 * <p>Measures the accuracy and performance of rate control,
 * including TPS calculation and wait timing.
 * 
 * @since 0.9.10
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class RateControllerBenchmark {
    
    private RateController rateController;
    private LoadPattern loadPattern;
    
    @Setup
    public void setup() {
        loadPattern = new StaticLoad(1000.0, Duration.ofMinutes(1));
        rateController = new RateController(loadPattern);
    }
    
    @Benchmark
    public double getCurrentTps() {
        return rateController.getCurrentTps();
    }
    
    @Benchmark
    public long getElapsedMillis() {
        return rateController.getElapsedMillis();
    }
    
    @Benchmark
    public void waitForNext() {
        rateController.waitForNext();
    }
}
