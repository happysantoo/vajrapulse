package com.vajrapulse.core.metrics;

import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.VirtualThreads;
import com.vajrapulse.core.engine.RateController;
import com.vajrapulse.core.util.TpsCalculator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Registers engine-related metrics with Micrometer.
 * 
 * <p>This class centralizes metrics registration for:
 * <ul>
 *   <li>Engine health metrics (state, uptime, lifecycle events)</li>
 *   <li>Executor metrics (thread pool statistics)</li>
 *   <li>Rate controller metrics (target vs actual TPS)</li>
 * </ul>
 * 
 * @since 0.9.5
 */
public final class EngineMetricsRegistrar {
    
    private EngineMetricsRegistrar() {
        // Utility class - no instantiation
    }
    
    /**
     * Registers engine health metrics (state, uptime, lifecycle events).
     * 
     * @param registry the meter registry
     * @param runId optional run ID for tagging
     * @param engineStateSupplier supplier for engine state value (0=STOPPED, 1=RUNNING, 2=STOPPING)
     * @param startTimeMillisSupplier supplier for engine start time in milliseconds
     * @return registered metrics holders for lifecycle counters and uptime timer
     */
    public static EngineHealthMetrics registerHealthMetrics(
            MeterRegistry registry,
            String runId,
            java.util.function.Supplier<Integer> engineStateSupplier,
            java.util.function.Supplier<Long> startTimeMillisSupplier) {
        
        // Engine state gauge (0=STOPPED, 1=RUNNING, 2=STOPPING)
        var stateBuilder = Gauge.builder("vajrapulse.engine.state", 
                () -> (double) engineStateSupplier.get())
            .description("Engine state (0=STOPPED, 1=RUNNING, 2=STOPPING)");
        
        // Uptime timer (time since engine started)
        var uptimeBuilder = Timer.builder("vajrapulse.engine.uptime")
            .description("Engine uptime in milliseconds");
        
        // Lifecycle event counters
        var startCounterBuilder = Counter.builder("vajrapulse.engine.lifecycle.events")
            .tag("event", "start")
            .description("Number of times engine started");
        var stopCounterBuilder = Counter.builder("vajrapulse.engine.lifecycle.events")
            .tag("event", "stop")
            .description("Number of times engine stopped");
        var completeCounterBuilder = Counter.builder("vajrapulse.engine.lifecycle.events")
            .tag("event", "complete")
            .description("Number of times engine completed successfully");
        
        if (runId != null && !runId.isBlank()) {
            stateBuilder.tag("run_id", runId);
            uptimeBuilder.tag("run_id", runId);
            startCounterBuilder.tag("run_id", runId);
            stopCounterBuilder.tag("run_id", runId);
            completeCounterBuilder.tag("run_id", runId);
        }
        
        Counter lifecycleStartCounter = startCounterBuilder.register(registry);
        Counter lifecycleStopCounter = stopCounterBuilder.register(registry);
        Counter lifecycleCompleteCounter = completeCounterBuilder.register(registry);
        Timer uptimeTimer = uptimeBuilder.register(registry);
        
        stateBuilder.register(registry);
        
        // Register uptime gauge that updates based on start time
        var uptimeGaugeBuilder = Gauge.builder("vajrapulse.engine.uptime.ms",
                () -> {
                    long start = startTimeMillisSupplier.get();
                    return start > 0 ? (System.currentTimeMillis() - start) : 0.0;
                })
            .description("Engine uptime in milliseconds");
        if (runId != null && !runId.isBlank()) {
            uptimeGaugeBuilder.tag("run_id", runId);
        }
        uptimeGaugeBuilder.register(registry);
        
        return new EngineHealthMetrics(lifecycleStartCounter, lifecycleStopCounter, 
                                      lifecycleCompleteCounter, uptimeTimer);
    }
    
    /**
     * Registers metrics for the executor service.
     * 
     * @param executor the executor service
     * @param taskClass the task class (for determining thread type)
     * @param registry the meter registry
     * @param runId optional run ID for tagging
     */
    public static void registerExecutorMetrics(
            ExecutorService executor,
            Class<?> taskClass,
            MeterRegistry registry,
            String runId) {
        
        String threadType = taskClass.isAnnotationPresent(VirtualThreads.class) ? "virtual" : "platform";
        
        // Active thread count (for platform threads only)
        if (executor instanceof ThreadPoolExecutor tpe) {
            var activeThreadsBuilder = Gauge.builder("vajrapulse.executor.active.threads",
                    tpe, ThreadPoolExecutor::getActiveCount)
                .description("Number of active threads")
                .tag("thread_type", threadType);
            var poolSizeBuilder = Gauge.builder("vajrapulse.executor.pool.size",
                    tpe, ThreadPoolExecutor::getPoolSize)
                .description("Current pool size")
                .tag("thread_type", threadType);
            var corePoolSizeBuilder = Gauge.builder("vajrapulse.executor.pool.core.size",
                    tpe, ThreadPoolExecutor::getCorePoolSize)
                .description("Core pool size")
                .tag("thread_type", threadType);
            var maxPoolSizeBuilder = Gauge.builder("vajrapulse.executor.pool.max.size",
                    tpe, ThreadPoolExecutor::getMaximumPoolSize)
                .description("Maximum pool size")
                .tag("thread_type", threadType);
            var queueSizeBuilder = Gauge.builder("vajrapulse.executor.queue.size",
                    tpe, e -> e.getQueue().size())
                .description("Executor internal queue size")
                .tag("thread_type", threadType);
            
            if (runId != null && !runId.isBlank()) {
                activeThreadsBuilder.tag("run_id", runId);
                poolSizeBuilder.tag("run_id", runId);
                corePoolSizeBuilder.tag("run_id", runId);
                maxPoolSizeBuilder.tag("run_id", runId);
                queueSizeBuilder.tag("run_id", runId);
            }
            
            activeThreadsBuilder.register(registry);
            poolSizeBuilder.register(registry);
            corePoolSizeBuilder.register(registry);
            maxPoolSizeBuilder.register(registry);
            queueSizeBuilder.register(registry);
        }
    }
    
    /**
     * Registers metrics for rate controller accuracy (target vs actual TPS).
     * 
     * @param rateController the rate controller to monitor
     * @param loadPattern the load pattern (for target TPS)
     * @param registry the meter registry
     * @param runId optional run ID for tagging
     */
    public static void registerRateControllerMetrics(
            RateController rateController,
            LoadPattern loadPattern,
            MeterRegistry registry,
            String runId) {
        
        // Target TPS gauge (from load pattern)
        var targetTpsBuilder = Gauge.builder("vajrapulse.rate.target_tps",
                rateController, RateController::getCurrentTps)
            .description("Target transactions per second from load pattern");
        
        // Actual TPS gauge (calculated from execution count and elapsed time)
        var actualTpsBuilder = Gauge.builder("vajrapulse.rate.actual_tps",
                () -> {
                    long elapsedMillis = rateController.getElapsedMillis();
                    long executionCount = rateController.getExecutionCount();
                    return TpsCalculator.calculateActualTps(executionCount, elapsedMillis);
                })
            .description("Actual transactions per second (measured)");
        
        // TPS error gauge (target - actual)
        var tpsErrorBuilder = Gauge.builder("vajrapulse.rate.tps_error",
                () -> {
                    double targetTps = rateController.getCurrentTps();
                    long elapsedMillis = rateController.getElapsedMillis();
                    long executionCount = rateController.getExecutionCount();
                    return TpsCalculator.calculateTpsError(targetTps, executionCount, elapsedMillis);
                })
            .description("TPS error (target - actual)");
        
        if (runId != null && !runId.isBlank()) {
            targetTpsBuilder.tag("run_id", runId);
            actualTpsBuilder.tag("run_id", runId);
            tpsErrorBuilder.tag("run_id", runId);
        }
        
        targetTpsBuilder.register(registry);
        actualTpsBuilder.register(registry);
        tpsErrorBuilder.register(registry);
    }
    
    /**
     * Holder for engine health metrics.
     */
    public record EngineHealthMetrics(
            Counter lifecycleStartCounter,
            Counter lifecycleStopCounter,
            Counter lifecycleCompleteCounter,
            Timer uptimeTimer
    ) {}
}

