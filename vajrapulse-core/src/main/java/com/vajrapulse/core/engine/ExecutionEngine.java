package com.vajrapulse.core.engine;

import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.PlatformThreads;
import com.vajrapulse.api.Task;
import com.vajrapulse.api.TaskLifecycle;
import com.vajrapulse.api.TaskResult;
import com.vajrapulse.api.VirtualThreads;
import com.vajrapulse.core.config.ConfigLoader;
import com.vajrapulse.core.config.VajraPulseConfig;
import com.vajrapulse.core.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main execution engine for load testing.
 * 
 * <p>This class orchestrates:
 * <ul>
 *   <li>Task lifecycle (init/execute/teardown)</li>
 *   <li>Thread pool management (virtual or platform)</li>
 *   <li>Rate control according to load pattern</li>
 *   <li>Metrics collection</li>
 *   <li>Graceful shutdown handling</li>
 * </ul>
 * 
 * <p>Supports both legacy {@link Task} and new {@link TaskLifecycle} interfaces.
 * Tasks are automatically adapted to the lifecycle model.
 * 
 * <p>Example usage:
 * <pre>{@code
 * TaskLifecycle task = new MyHttpTask();
 * LoadPattern load = new StaticLoad(100.0, Duration.ofMinutes(5));
 * MetricsCollector metrics = new MetricsCollector();
 * 
 * ExecutionEngine engine = new ExecutionEngine(task, load, metrics);
 * engine.run();
 * 
 * AggregatedMetrics results = metrics.snapshot();
 * }</pre>
 * 
 * @since 0.9.0
 */
public final class ExecutionEngine implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionEngine.class);
    
    private final TaskLifecycle taskLifecycle;
    private final LoadPattern loadPattern;
    private final MetricsCollector metricsCollector;
    private final ExecutorService executor;
    private final String runId; // Correlates metrics/traces/logs
    private final VajraPulseConfig config;
    private final ShutdownManager shutdownManager;
    private final java.util.concurrent.atomic.AtomicBoolean stopRequested = new java.util.concurrent.atomic.AtomicBoolean(false);
    
    // Queue depth tracking
    private final java.util.concurrent.atomic.AtomicLong pendingExecutions = new java.util.concurrent.atomic.AtomicLong(0);
    
    /**
     * Creates a new execution engine with automatic run ID generation.
     * 
     * @param task the task to execute (will be adapted to TaskLifecycle)
     * @param loadPattern the load pattern
     * @param metricsCollector the metrics collector
     */
    public ExecutionEngine(Task task, LoadPattern loadPattern, MetricsCollector metricsCollector) {
        this(adaptToLifecycle(task), loadPattern, metricsCollector, deriveRunId(metricsCollector), null);
    }

    public ExecutionEngine(Task task, LoadPattern loadPattern, MetricsCollector metricsCollector, String runId) {
        this(adaptToLifecycle(task), loadPattern, metricsCollector, runId, null);
    }
    
    /**
     * Creates a new execution engine with automatic run ID generation.
     * 
     * @param taskLifecycle the task lifecycle to execute
     * @param loadPattern the load pattern
     * @param metricsCollector the metrics collector
     */
    public ExecutionEngine(TaskLifecycle taskLifecycle, LoadPattern loadPattern, MetricsCollector metricsCollector) {
        this(taskLifecycle, loadPattern, metricsCollector, deriveRunId(metricsCollector), null);
    }

    /**
     * Creates a new execution engine for a legacy Task with explicit run ID and configuration.
     * 
     * @param task the legacy task to adapt
     * @param loadPattern the load pattern
     * @param metricsCollector the metrics collector
     * @param runId the run identifier for correlation
     * @param config configuration, or null to load from default locations
     */
    public ExecutionEngine(Task task, LoadPattern loadPattern, MetricsCollector metricsCollector, String runId, VajraPulseConfig config) {
        this(adaptToLifecycle(task), loadPattern, metricsCollector, runId, config);
    }
    
    /**
     * Creates a new execution engine with explicit run ID and configuration.
     * 
     * @param taskLifecycle the task lifecycle to execute
     * @param loadPattern the load pattern
     * @param metricsCollector the metrics collector
     * @param runId the run identifier for correlation
     * @param config configuration, or null to load from default locations
     */
    public ExecutionEngine(TaskLifecycle taskLifecycle, LoadPattern loadPattern, MetricsCollector metricsCollector, String runId, VajraPulseConfig config) {
        this.taskLifecycle = taskLifecycle;
        this.loadPattern = loadPattern;
        this.metricsCollector = metricsCollector;
        this.runId = runId;
        this.config = config != null ? config : ConfigLoader.load();

        // Determine thread strategy from annotations
        Class<?> taskClass = taskLifecycle.getClass();
        this.executor = createExecutor(taskClass);
        this.shutdownManager = createShutdownManager(runId, metricsCollector);
        shutdownManager.registerShutdownHook();
    }

    private static String deriveRunId(MetricsCollector metricsCollector) {
        return (metricsCollector.getRunId() != null && !metricsCollector.getRunId().isBlank())
            ? metricsCollector.getRunId()
            : java.util.UUID.randomUUID().toString();
    }
    
    private ExecutorService createExecutor(Class<?> taskClass) {
        if (taskClass.isAnnotationPresent(VirtualThreads.class)) {
            logger.info("Using virtual threads for task: {}", taskClass.getSimpleName());
            return Executors.newVirtualThreadPerTaskExecutor();
        } else if (taskClass.isAnnotationPresent(PlatformThreads.class)) {
            PlatformThreads annotation = taskClass.getAnnotation(PlatformThreads.class);
            int poolSize = annotation.poolSize();
            if (poolSize == -1) {
                poolSize = Runtime.getRuntime().availableProcessors();
            }
            logger.info("Using platform threads (pool size: {}) for task: {}", 
                poolSize, taskClass.getSimpleName());
            return Executors.newFixedThreadPool(poolSize);
        } else {
            // Use default from config when no annotation present
            return switch (config.execution().defaultThreadPool()) {
                case VIRTUAL -> {
                    logger.info("No thread annotation found, using configured default VIRTUAL threads for task: {}", 
                        taskClass.getSimpleName());
                    yield Executors.newVirtualThreadPerTaskExecutor();
                }
                case PLATFORM -> {
                    int poolSize = config.execution().platformThreadPoolSize();
                    if (poolSize == -1) {
                        poolSize = Runtime.getRuntime().availableProcessors();
                    }
                    logger.info("No thread annotation found, using configured default PLATFORM threads (pool size: {}) for task: {}", 
                        poolSize, taskClass.getSimpleName());
                    yield Executors.newFixedThreadPool(poolSize);
                }
                case AUTO -> {
                    logger.info("No thread annotation found, using configured default AUTO (virtual) threads for task: {}", 
                        taskClass.getSimpleName());
                    yield Executors.newVirtualThreadPerTaskExecutor();
                }
            };
        }
    }
    
    private ShutdownManager createShutdownManager(String runId, MetricsCollector metricsCollector) {
        return ShutdownManager.builder()
            .withRunId(runId)
            .withDrainTimeout(config.execution().drainTimeout())
            .withForceTimeout(config.execution().forceTimeout())
            .onShutdown(() -> {
                // Flush metrics before shutdown
                try {
                    logger.debug("Flushing metrics for runId={}", runId);
                    // MetricsCollector doesn't have explicit flush, but snapshot captures state
                    metricsCollector.snapshot();
                } catch (Exception e) {
                    logger.error("Failed to flush metrics for runId={}: {}", runId, e.getMessage(), e);
                }
            })
            .build();
    }

    
    /**
     * Adapts a Task to TaskLifecycle interface.
     * If task is already TaskLifecycle, returns it directly.
     * Otherwise, creates an adapter that maps setup/execute/cleanup to init/execute/teardown.
     */
    private static TaskLifecycle adaptToLifecycle(Task task) {
        if (task instanceof TaskLifecycle) {
            return (TaskLifecycle) task;
        }
        
        // Adapter for legacy Task interface
        return new TaskLifecycle() {
            @Override
            public void init() throws Exception {
                task.setup();
            }
            
            @Override
            public TaskResult execute(long iteration) throws Exception {
                return task.execute();
            }
            
            @Override
            public void teardown() throws Exception {
                task.cleanup();
            }
        };
    }
    
    /**
     * Runs the load test with full lifecycle management.
     * 
     * <p>This method:
     * <ol>
     *   <li>Calls {@code taskLifecycle.init()}</li>
     *   <li>Submits executions according to load pattern</li>
     *   <li>Monitors for shutdown signals</li>
     *   <li>Gracefully drains running tasks</li>
     *   <li>Calls {@code taskLifecycle.teardown()}</li>
     * </ol>
     * 
     * <p><strong>Shutdown Behavior:</strong> If a shutdown signal (SIGINT/SIGTERM)
     * is received or {@link #stop()} is called:
     * <ul>
     *   <li>No new iterations are scheduled</li>
     *   <li>Running iterations complete (up to 5s timeout)</li>
     *   <li>Teardown is called to clean up resources</li>
     *   <li>Metrics are flushed</li>
     * </ul>
     * 
     * @throws Exception if init or teardown fails
     */
    @SuppressWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE") // Fire-and-forget executor.submit() is intentional
    public void run() throws Exception {
        logger.info("Starting load test runId={} pattern={} duration={}", runId, loadPattern.getClass().getSimpleName(), loadPattern.getDuration());
        
        // Initialize task
        try {
            taskLifecycle.init();
            logger.info("Task initialization completed for runId={}", runId);
        } catch (Exception e) {
            logger.error("Task initialization failed for runId={}: {}", runId, e.getMessage(), e);
            // Don't call teardown if init failed
            shutdownManager.removeShutdownHook();
            throw e;
        }
        
        try {
            TaskExecutor taskExecutor = new TaskExecutor(taskLifecycle);
            RateController rateController = new RateController(loadPattern);
            
            long testDurationMillis = loadPattern.getDuration().toMillis();
            long iteration = 0;
            
            while (!stopRequested.get() && rateController.getElapsedMillis() < testDurationMillis) {
                rateController.waitForNext();
                
                long currentIteration = iteration++;
                long queueStartNanos = System.nanoTime();
                pendingExecutions.incrementAndGet();
                
                // Update queue size gauge
                metricsCollector.updateQueueSize(pendingExecutions.get());
                
                executor.submit(new ExecutionCallable(taskExecutor, metricsCollector, currentIteration, queueStartNanos, pendingExecutions));
            }
            
            if (stopRequested.get()) {
                logger.info("Stop requested - draining executor runId={}", runId);
            } else {
                logger.info("Test duration completed, shutting down executor runId={}", runId);
            }
            
        } finally {
            // Graceful shutdown sequence
            boolean graceful = shutdownManager.awaitShutdown(executor);
            
            // Always call teardown (cleanup resources)
            try {
                taskLifecycle.teardown();
                logger.info("Task teardown completed for runId={}", runId);
            } catch (Exception e) {
                logger.error("Task teardown failed for runId={}: {}", runId, e.getMessage(), e);
            } finally {
                logger.info("Run finished runId={}", runId);
            }
        }
    }
    
    @Override
    public void close() {
        shutdownManager.removeShutdownHook();
        if (!executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Requests early stop of the load test.
     * 
     * <p>This is a graceful stop - no new iterations will be scheduled,
     * but running iterations will complete. This method is idempotent.
     */
    public void stop() {
        if (stopRequested.compareAndSet(false, true)) {
            logger.info("Manual stop invoked runId={}", runId);
            shutdownManager.initiateShutdown();
        }
    }
    
    /**
     * Returns the run ID for this execution.
     * 
     * @return the run identifier
     */
    public String getRunId() {
        return runId;
    }
    
    /**
     * Returns the current number of pending executions in the queue.
     * 
     * @return the current queue depth
     */
    public long getQueueDepth() {
        return pendingExecutions.get();
    }
    
    /**
     * Callable for executing a task and recording metrics.
     * Implemented as a concrete class to avoid lambda allocation in hot path.
     */
    private static final class ExecutionCallable implements Callable<Void> {
        private final TaskExecutor taskExecutor;
        private final MetricsCollector metricsCollector;
        private final long iteration;
        private final long queueStartNanos;
        private final java.util.concurrent.atomic.AtomicLong pendingExecutions;
        
        ExecutionCallable(TaskExecutor taskExecutor, MetricsCollector metricsCollector, long iteration, 
                         long queueStartNanos, java.util.concurrent.atomic.AtomicLong pendingExecutions) {
            this.taskExecutor = taskExecutor;
            this.metricsCollector = metricsCollector;
            this.iteration = iteration;
            this.queueStartNanos = queueStartNanos;
            this.pendingExecutions = pendingExecutions;
        }
        
        @Override
        public Void call() {
            try {
                // Record queue wait time (time from submission to actual execution start)
                long queueWaitNanos = System.nanoTime() - queueStartNanos;
                metricsCollector.recordQueueWait(queueWaitNanos);
                
                ExecutionMetrics metrics = taskExecutor.executeWithMetrics(iteration);
                metricsCollector.record(metrics);
            } finally {
                // Decrement pending count when execution starts
                pendingExecutions.decrementAndGet();
                // Update queue size gauge
                metricsCollector.updateQueueSize(pendingExecutions.get());
            }
            return null;
        }
    }

    /**
     * Convenience static helper to execute a task with a load pattern and metrics
     * collection without manually managing the engine lifecycle.
     * <p>Usage:
     * <pre>{@code
     * AggregatedMetrics metrics = ExecutionEngine.execute(task, loadPattern, collector);
     * }</pre>
     * @param task the task to execute
     * @param loadPattern the load pattern definition
     * @param metricsCollector metrics collector instance
     * @return aggregated metrics snapshot after execution
     * @throws Exception if setup or cleanup fails
     */
    public static com.vajrapulse.core.metrics.AggregatedMetrics execute(
            Task task,
            LoadPattern loadPattern,
            MetricsCollector metricsCollector) throws Exception {
        try (ExecutionEngine engine = new ExecutionEngine(task, loadPattern, metricsCollector)) {
            engine.run();
        }
        return metricsCollector.snapshot();
    }

    
}
