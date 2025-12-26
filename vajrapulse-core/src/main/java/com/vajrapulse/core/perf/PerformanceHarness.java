package com.vajrapulse.core.perf;

import com.vajrapulse.api.pattern.StaticLoad;
import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.TaskResult;
import com.vajrapulse.api.task.VirtualThreads;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.util.TpsCalculator;
import com.vajrapulse.core.util.TimeConstants;

import java.time.Duration;

/**
 * Simple performance harness for benchmarking core execution overhead.
 * <p>Not a micro-benchmark; focuses on end-to-end scheduler + execution throughput.
 * <p>Usage:
 * <pre>{@code
 * java -cp ... com.vajrapulse.core.perf.PerformanceHarness 1000 2s
 * }</pre>
 */
public final class PerformanceHarness {

    @VirtualThreads
    private static final class NoOpTask implements TaskLifecycle {
        @Override public void init() { }
        @Override public TaskResult execute(long iteration) { return TaskResult.success(null); }
        @Override public void teardown() { }
    }

    public static void main(String[] args) throws Exception {
        double targetTps = args.length > 0 ? Double.parseDouble(args[0]) : 1000.0;
        String durStr = args.length > 1 ? args[1] : "1s";
        Duration duration = parseDuration(durStr);
        var pattern = new StaticLoad(targetTps, duration);
        var metricsCollector = MetricsCollector.createWithRunId("harness-" + System.currentTimeMillis(), new double[]{0.5,0.95,0.99});

        long start = System.nanoTime();
        try (ExecutionEngine engine = ExecutionEngine.builder()
                .withTask(new NoOpTask())
                .withLoadPattern(pattern)
                .withMetricsCollector(metricsCollector)
                .build()) {
            engine.run();
        }
        long end = System.nanoTime();

        AggregatedMetrics snapshot = metricsCollector.snapshot();
        double achieved = TpsCalculator.calculateActualTps(snapshot.totalExecutions(), duration.toMillis());
        System.out.println("=== Performance Harness ===");
        System.out.println("Target TPS:    " + targetTps);
        System.out.println("Duration:      " + duration);
        System.out.println("Total Exec:    " + snapshot.totalExecutions());
        System.out.println("Achieved TPS:  " + String.format("%.2f", achieved));
        System.out.println("Success Count: " + snapshot.successCount());
        System.out.println("Failures:      " + snapshot.failureCount());
        System.out.println("Wall Time ms:  " + ((end - start) / TimeConstants.NANOS_PER_MILLIS));
    }

    private static Duration parseDuration(String s) {
        s = s.trim().toLowerCase();
        if (s.endsWith("ms")) return Duration.ofMillis(Long.parseLong(s.substring(0, s.length()-2)));
        if (s.endsWith("s")) return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length()-1)));
        if (s.endsWith("m")) return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length()-1)));
        if (s.endsWith("h")) return Duration.ofHours(Long.parseLong(s.substring(0, s.length()-1)));
        return Duration.ofSeconds(Long.parseLong(s));
    }
}
