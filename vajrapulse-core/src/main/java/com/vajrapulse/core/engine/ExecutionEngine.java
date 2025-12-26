package com.vajrapulse.core.engine;

import com.vajrapulse.api.pattern.LoadPattern;
import com.vajrapulse.api.task.PlatformThreads;
import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.VirtualThreads;
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern;
import com.vajrapulse.core.config.ConfigLoader;
import com.vajrapulse.core.config.VajraPulseConfig;
import com.vajrapulse.core.metrics.EngineMetricsRegistrar;
import com.vajrapulse.core.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
 * <p>Example usage:
 * <pre>{@code
 * TaskLifecycle task = new MyHttpTask();
 * LoadPattern load = new StaticLoad(100.0, Duration.ofMinutes(5));
 * 
 * try (MetricsCollector metrics = new MetricsCollector()) {
 *     ExecutionEngine engine = ExecutionEngine.builder()
 *         .withTask(task)
 *         .withLoadPattern(load)
 *         .withMetricsCollector(metrics)
 *         .build();
 *     engine.run();
 *     
 *     AggregatedMetrics results = metrics.snapshot();
 * } // MetricsCollector automatically closed
 * }</pre>
 * 
 * <p><strong>Resource Management:</strong> The {@code MetricsCollector} passed to this
 * engine should be managed by the caller. If using try-with-resources for the engine,
 * also use try-with-resources for the metrics collector to ensure proper cleanup of
 * ThreadLocal instances and prevent memory leaks.
 * 
 * @since 0.9.0
 */
public final class ExecutionEngine implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionEngine.class);
    
    /**
     * Timeout for executor termination in close() method (seconds).
     */
    private static final long EXECUTOR_TERMINATION_TIMEOUT_SECONDS = 10L;
    
    private final TaskLifecycle taskLifecycle;
    private final LoadPattern loadPattern;
    private final MetricsCollector metricsCollector;
    private final ExecutorService executor;
    private final String runId; // Correlates metrics/traces/logs
    private final VajraPulseConfig config;
    private final ShutdownManager shutdownManager;
    private final boolean shutdownHookEnabled;
    private final java.util.concurrent.atomic.AtomicBoolean stopRequested = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean executorShutdown = new java.util.concurrent.atomic.AtomicBoolean(false);
    
    // Queue depth tracking
    private final java.util.concurrent.atomic.AtomicLong pendingExecutions = new java.util.concurrent.atomic.AtomicLong(0);
    
    // Engine health tracking
    private final AtomicLong startTimeMillis = new AtomicLong(0);
    private volatile EngineState engineState = EngineState.STOPPED;
    private Counter lifecycleStartCounter;
    private Counter lifecycleStopCounter;
    private Counter lifecycleCompleteCounter;
    private Timer uptimeTimer;
    
    /**
     * Engine state for health metrics.
     */
    private enum EngineState {
        /** Engine is stopped (not running) */
        STOPPED(0),
        /** Engine is running (executing tasks) */
        RUNNING(1),
        /** Engine is stopping (shutdown in progress) */
        STOPPING(2);
        
        private final int value;
        
        EngineState(int value) {
            this.value = value;
        }
        
        int getValue() {
            return value;
        }
    }
    
    /**
     * Creates a new execution engine using the builder pattern.
     * 
     * <p>This is the primary constructor. Use {@link Builder} to create instances.
     * 
     * @param builder the builder with all required parameters
     */
    private ExecutionEngine(Builder builder) {
        // Validate required parameters
        validateBuilder(builder);
        
        this.taskLifecycle = builder.taskLifecycle;
        this.loadPattern = builder.loadPattern;
        this.metricsCollector = builder.metricsCollector;
        this.runId = builder.runId != null ? builder.runId : deriveRunId(builder.metricsCollector);
        this.config = builder.config != null ? builder.config : ConfigLoader.load();
        this.shutdownHookEnabled = builder.shutdownHookEnabled;

        // Determine thread strategy from annotations
        Class<?> taskClass = taskLifecycle.getClass();
        this.executor = createExecutor(taskClass);
        
        this.shutdownManager = createShutdownManager(runId, metricsCollector);
        
        // Only register shutdown hook if enabled (default: true for production, false for tests)
        if (shutdownHookEnabled) {
            shutdownManager.registerShutdownHook();
        }
        
        // Register all metrics in one place
        registerMetrics(taskClass, metricsCollector.getRegistry(), runId);
    }
    
    /**
     * Returns a new builder for constructing an {@link ExecutionEngine}.
     * 
     * <p>Example usage:
     * <pre>{@code
     * ExecutionEngine engine = ExecutionEngine.builder()
     *     .withTask(task)
     *     .withLoadPattern(loadPattern)
     *     .withMetricsCollector(metricsCollector)
     *     .withRunId("my-run-id")
     *     .withConfig(config)
     *     .build();
     * }</pre>
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for constructing {@link ExecutionEngine} instances.
     * 
     * <p>Required parameters:
     * <ul>
     *   <li>{@code taskLifecycle} - the task to execute</li>
     *   <li>{@code loadPattern} - the load pattern</li>
     *   <li>{@code metricsCollector} - the metrics collector</li>
     * </ul>
     * 
     * <p>Optional parameters:
     * <ul>
     *   <li>{@code runId} - run identifier (auto-generated if not provided)</li>
     *   <li>{@code config} - configuration (loaded from default locations if not provided)</li>
     * </ul>
     * 
     * @since 0.9.5
     */
    public static final class Builder {
        private TaskLifecycle taskLifecycle;
        private LoadPattern loadPattern;
        private MetricsCollector metricsCollector;
        private String runId;
        private VajraPulseConfig config;
        private boolean shutdownHookEnabled = true; // Default: enabled for production use
        
        private Builder() {
            // Private constructor - use ExecutionEngine.builder()
        }
        
        /**
         * Sets the task lifecycle to execute.
         * 
         * @param taskLifecycle the task lifecycle (must not be null)
         * @return this builder
         * @throws NullPointerException if taskLifecycle is null
         */
        public Builder withTask(TaskLifecycle taskLifecycle) {
            this.taskLifecycle = java.util.Objects.requireNonNull(taskLifecycle, "Task lifecycle must not be null");
            return this;
        }
        
        /**
         * Sets the load pattern.
         * 
         * @param loadPattern the load pattern (must not be null)
         * @return this builder
         * @throws NullPointerException if loadPattern is null
         */
        public Builder withLoadPattern(LoadPattern loadPattern) {
            this.loadPattern = java.util.Objects.requireNonNull(loadPattern, "Load pattern must not be null");
            return this;
        }
        
        /**
         * Sets the metrics collector.
         * 
         * @param metricsCollector the metrics collector (must not be null)
         * @return this builder
         * @throws NullPointerException if metricsCollector is null
         */
        public Builder withMetricsCollector(MetricsCollector metricsCollector) {
            this.metricsCollector = java.util.Objects.requireNonNull(metricsCollector, "Metrics collector must not be null");
            return this;
        }
        
        /**
         * Sets the run identifier for correlation.
         * 
         * <p>If not provided, a UUID will be generated automatically.
         * 
         * @param runId the run identifier
         * @return this builder
         */
        public Builder withRunId(String runId) {
            this.runId = runId;
            return this;
        }
        
        /**
         * Sets the configuration.
         * 
         * <p>If not provided, configuration will be loaded from default locations.
         * 
         * @param config the configuration
         * @return this builder
         */
        public Builder withConfig(VajraPulseConfig config) {
            this.config = config;
            return this;
        }
        
        /**
         * Enables or disables JVM shutdown hook registration.
         * 
         * <p>Shutdown hooks are used to handle SIGINT/SIGTERM signals (Ctrl+C, kill, etc.)
         * for graceful shutdown in production environments. In test environments, shutdown
         * hooks are typically not needed and can cause issues with test cleanup.
         * 
         * <p>Default: {@code true} (enabled for production use)
         * 
         * <p>Example:
         * <pre>{@code
         * // Production: hooks enabled (default)
         * ExecutionEngine engine = ExecutionEngine.builder()
         *     .withTask(task)
         *     .withLoadPattern(pattern)
         *     .withMetricsCollector(metrics)
         *     .build(); // Hooks enabled by default
         * 
         * // Tests: hooks disabled
         * ExecutionEngine engine = ExecutionEngine.builder()
         *     .withTask(task)
         *     .withLoadPattern(pattern)
         *     .withMetricsCollector(metrics)
         *     .withShutdownHook(false) // Disable hooks for tests
         *     .build();
         * }</pre>
         * 
         * @param enabled true to enable shutdown hooks, false to disable
         * @return this builder
         * @since 0.9.9
         */
        public Builder withShutdownHook(boolean enabled) {
            this.shutdownHookEnabled = enabled;
            return this;
        }
        
        /**
         * Builds the {@link ExecutionEngine} instance.
         * 
         * @return a new execution engine instance
         * @throws IllegalArgumentException if required parameters are missing
         */
        public ExecutionEngine build() {
            return new ExecutionEngine(this);
        }
    }
    

    /**
     * Validates builder parameters.
     * 
     * @param builder the builder to validate
     * @throws IllegalArgumentException if any required parameter is null
     */
    private static void validateBuilder(Builder builder) {
        if (builder.taskLifecycle == null) {
            throw new IllegalArgumentException("Task lifecycle must not be null");
        }
        if (builder.loadPattern == null) {
            throw new IllegalArgumentException("Load pattern must not be null");
        }
        if (builder.metricsCollector == null) {
            throw new IllegalArgumentException("Metrics collector must not be null");
        }
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
            .withRegistry(metricsCollector.getRegistry()) // Enable metrics for callback failures
            .onShutdown(() -> {
                // Flush metrics before shutdown
                // Note: Exceptions are handled by ShutdownManager with metrics and structured logging
                logger.debug("Flushing metrics for runId={}", runId);
                // MetricsCollector doesn't have explicit flush, but snapshot captures state
                metricsCollector.snapshot();
            })
            .build();
    }
    
    /**
     * Executes the main load test loop.
     * 
     * <p>This method handles the core execution loop:
     * <ul>
     *   <li>Creates task executor and rate controller</li>
     *   <li>Registers rate controller metrics</li>
     *   <li>Submits task executions according to load pattern</li>
     *   <li>Respects stop requests and pattern completion</li>
     * </ul>
     * 
     * @throws Exception if execution fails
     */
    private void executeLoadTest() throws Exception {
        // TaskExecutor is a simple wrapper that provides instrumentation and metrics capture.
        // It's created directly here rather than injected because:
        // 1. It's tightly coupled to the task lifecycle (one TaskExecutor per task)
        // 2. It has no dependencies beyond the TaskLifecycle itself
        // 3. It's a lightweight utility class, not a service requiring DI
        // 4. This pattern is consistent throughout the codebase
        TaskExecutor taskExecutor = new TaskExecutor(taskLifecycle);
        RateController rateController = new RateController(loadPattern);
        
        // Register rate controller accuracy metrics
        EngineMetricsRegistrar.registerRateControllerMetrics(
            rateController, loadPattern, metricsCollector.getRegistry(), runId);
        
        long testDurationMillis = loadPattern.getDuration().toMillis();
        long iteration = 0;
        
        while (!stopRequested.get() && rateController.getElapsedMillis() < testDurationMillis) {
            rateController.waitForNext();
            
            // Check if pattern wants to stop (returns 0.0 TPS)
            // Only break if:
            // 1. We've had at least 10 iterations (to allow patterns that start at 0.0 TPS to ramp up)
            // 2. Elapsed time is significant (> 100ms) to ensure we're not breaking too early
            // 3. TPS is 0.0 (pattern is complete)
            // This prevents breaking on RampUpLoad which starts at 0.0, but still catches
            // AdaptiveLoadPattern at minimum TPS (recovery behavior in RAMP_DOWN phase)
            long elapsedMillis = rateController.getElapsedMillis();
            double currentTps = rateController.getCurrentTps();
            if (iteration >= 10 && elapsedMillis > 100 && currentTps <= 0.0) {
                logger.info("Load pattern returned 0.0 TPS after {} iterations and {}ms, stopping execution runId={}", 
                    iteration, elapsedMillis, runId);
                break;
            }
            
            // Check if we should record metrics (use interface method to avoid instanceof)
            boolean shouldRecordMetrics = loadPattern.shouldRecordMetrics(elapsedMillis);
            
            long currentIteration = iteration++;
            long queueStartNanos = System.nanoTime();
            pendingExecutions.incrementAndGet();
            
            // Update queue size gauge
            metricsCollector.updateQueueSize(pendingExecutions.get());
            
            executor.submit(new ExecutionCallable(
                taskExecutor, metricsCollector, currentIteration, queueStartNanos, pendingExecutions, shouldRecordMetrics));
        }
    }
    
    /**
     * Registers all metrics for this execution engine.
     * 
     * <p>This method consolidates all metrics registration in one place:
     * <ul>
     *   <li>Executor metrics (thread pool statistics)</li>
     *   <li>Engine health metrics (state, uptime, lifecycle events)</li>
     *   <li>Adaptive pattern metrics (if applicable)</li>
     * </ul>
     * 
     * <p>Rate controller metrics are registered separately in {@link #run()} when
     * the RateController is created.
     * 
     * @param taskClass the task class for executor metrics
     * @param registry the meter registry
     * @param runId the run identifier for tagging
     */
    private void registerMetrics(Class<?> taskClass, io.micrometer.core.instrument.MeterRegistry registry, String runId) {
        // Register executor metrics
        EngineMetricsRegistrar.registerExecutorMetrics(
            executor, taskClass, registry, runId);
        
        // Register engine health metrics
        var healthMetrics = EngineMetricsRegistrar.registerHealthMetrics(
            registry,
            runId,
            () -> engineState.getValue(),
            startTimeMillis::get
        );
        this.lifecycleStartCounter = healthMetrics.lifecycleStartCounter();
        this.lifecycleStopCounter = healthMetrics.lifecycleStopCounter();
        this.lifecycleCompleteCounter = healthMetrics.lifecycleCompleteCounter();
        this.uptimeTimer = healthMetrics.uptimeTimer();
        
        // Register pattern-specific metrics
        loadPattern.registerMetrics(registry, runId);
        
        // Adaptive pattern metrics require special handling due to module boundaries:
        // - AdaptiveLoadPattern is in api module (zero dependencies)
        // - AdaptivePatternMetrics is in core module (depends on Micrometer)
        // - Cannot use polymorphism without breaking module boundaries
        // This instanceof check is isolated and acceptable.
        if (loadPattern instanceof AdaptiveLoadPattern adaptivePattern) {
            AdaptivePatternMetrics.register(adaptivePattern, registry, runId);
        }
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
        // Update engine state and metrics
        engineState = EngineState.RUNNING;
        startTimeMillis.set(System.currentTimeMillis());
        lifecycleStartCounter.increment();
        
        logger.info("Starting load test runId={} pattern={} duration={}", runId, loadPattern.getClass().getSimpleName(), loadPattern.getDuration());
        
        // Initialize task
        try {
            taskLifecycle.init();
            logger.info("Task initialization completed for runId={}", runId);
        } catch (Exception e) {
            logger.error("Task initialization failed for runId={}: {}", runId, e.getMessage(), e);
            // Don't call teardown if init failed, but ensure executor is shut down
            executorShutdown.set(true);
            if (shutdownHookEnabled) {
                shutdownManager.signalShutdownComplete();
                shutdownManager.removeShutdownHook();
            }
            // Shutdown executor since run() won't complete normally
            if (!executor.isShutdown()) {
                executor.shutdown();
                try {
                    executor.shutdownNow(); // Force shutdown since no tasks were submitted
                } catch (Exception ex) {
                    logger.warn("Error during executor shutdown after init failure: {}", ex.getMessage());
                }
            }
            throw e;
        }
        
        try {
            executeLoadTest();
            
            if (stopRequested.get()) {
                logger.info("Stop requested - draining executor runId={}", runId);
            } else {
                logger.info("Test duration completed, shutting down executor runId={}", runId);
                lifecycleCompleteCounter.increment();
            }
            
        } finally {
            // Update engine state to stopping
            engineState = EngineState.STOPPING;
            lifecycleStopCounter.increment();
            
            // Record uptime
            long start = startTimeMillis.get();
            if (start > 0) {
                long uptime = System.currentTimeMillis() - start;
                uptimeTimer.record(uptime, TimeUnit.MILLISECONDS);
            }
            
            // Graceful shutdown sequence
            executorShutdown.set(true);
            boolean graceful = shutdownManager.awaitShutdown(executor);
            if (!graceful) {
                logger.warn("Shutdown was not graceful for runId={}", runId);
            }
            
            // Always call teardown (cleanup resources)
            try {
                taskLifecycle.teardown();
                logger.info("Task teardown completed for runId={}", runId);
            } catch (Exception e) {
                logger.error("Task teardown failed for runId={}: {}", runId, e.getMessage(), e);
            } finally {
                // Update engine state to stopped
                engineState = EngineState.STOPPED;
                logger.info("Run finished runId={}", runId);
            }
        }
    }
    
    @Override
    public void close() {
        // Unregister adaptive pattern metrics to prevent memory leaks
        // Use instanceof here as unregister() is a static method in core module
        // and we need to identify AdaptiveLoadPattern instances for cleanup
        if (loadPattern instanceof AdaptiveLoadPattern adaptivePattern) {
            AdaptivePatternMetrics.unregister(adaptivePattern);
        }
        
        // Signal shutdown completion and remove hook only if hooks were enabled
        if (shutdownHookEnabled) {
            shutdownManager.signalShutdownComplete();
            shutdownManager.removeShutdownHook();
        }
        
        // Shutdown executor only if run() didn't already shut it down
        // This prevents redundant shutdown and infinite waits
        if (!executorShutdown.get() && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(EXECUTOR_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
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
    
}
