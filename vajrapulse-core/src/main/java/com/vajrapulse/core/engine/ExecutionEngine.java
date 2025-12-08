package com.vajrapulse.core.engine;

import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.PlatformThreads;
import com.vajrapulse.api.TaskLifecycle;
import com.vajrapulse.api.VirtualThreads;
import com.vajrapulse.api.WarmupCooldownLoadPattern;
import com.vajrapulse.core.config.ConfigLoader;
import com.vajrapulse.core.config.VajraPulseConfig;
import com.vajrapulse.core.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import java.lang.ref.Cleaner;
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
    private static final Cleaner CLEANER = Cleaner.create();
    
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
    private final java.util.concurrent.atomic.AtomicBoolean stopRequested = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final Cleaner.Cleanable cleanable; // Safety net for executor cleanup
    private final com.vajrapulse.api.BackpressureHandler backpressureHandler; // Optional
    private final double backpressureThreshold; // Default: 0.7
    
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
        if (builder.taskLifecycle == null) {
            throw new IllegalArgumentException("Task lifecycle must not be null");
        }
        if (builder.loadPattern == null) {
            throw new IllegalArgumentException("Load pattern must not be null");
        }
        if (builder.metricsCollector == null) {
            throw new IllegalArgumentException("Metrics collector must not be null");
        }
        
        this.taskLifecycle = builder.taskLifecycle;
        this.loadPattern = builder.loadPattern;
        this.metricsCollector = builder.metricsCollector;
        this.runId = builder.runId != null ? builder.runId : deriveRunId(builder.metricsCollector);
        this.config = builder.config != null ? builder.config : ConfigLoader.load();
        this.backpressureHandler = builder.backpressureHandler;
        this.backpressureThreshold = builder.backpressureThreshold;

        // Determine thread strategy from annotations
        Class<?> taskClass = taskLifecycle.getClass();
        this.executor = createExecutor(taskClass);
        
        // Register thread pool metrics
        com.vajrapulse.core.metrics.EngineMetricsRegistrar.registerExecutorMetrics(
            executor, taskClass, metricsCollector.getRegistry(), runId);
        
        this.shutdownManager = createShutdownManager(runId, metricsCollector);
        shutdownManager.registerShutdownHook();
        
        // Register Cleaner as safety net for executor cleanup
        this.cleanable = CLEANER.register(this, new ExecutorCleanup(executor, runId));
        
        // Register adaptive pattern metrics if applicable
        if (loadPattern instanceof com.vajrapulse.api.AdaptiveLoadPattern adaptivePattern) {
            AdaptivePatternMetrics.register(adaptivePattern, metricsCollector.getRegistry(), runId);
        }
        
        // Register engine health metrics
        var healthMetrics = com.vajrapulse.core.metrics.EngineMetricsRegistrar.registerHealthMetrics(
            metricsCollector.getRegistry(),
            runId,
            () -> engineState.getValue(),
            startTimeMillis::get
        );
        this.lifecycleStartCounter = healthMetrics.lifecycleStartCounter();
        this.lifecycleStopCounter = healthMetrics.lifecycleStopCounter();
        this.lifecycleCompleteCounter = healthMetrics.lifecycleCompleteCounter();
        this.uptimeTimer = healthMetrics.uptimeTimer();
        
        // Rate controller metrics will be registered in run() method when RateController is created
    }
    
    /**
     * Creates a new execution engine with automatic run ID generation.
     * 
     * @param taskLifecycle the task lifecycle to execute
     * @param loadPattern the load pattern
     * @param metricsCollector the metrics collector
     * @deprecated Use {@link #builder()} instead. This constructor will be removed in 0.9.6.
     */
    @Deprecated(since = "0.9.5", forRemoval = true)
    public ExecutionEngine(TaskLifecycle taskLifecycle, LoadPattern loadPattern, MetricsCollector metricsCollector) {
        this(builder()
                .withTask(taskLifecycle)
                .withLoadPattern(loadPattern)
                .withMetricsCollector(metricsCollector));
    }
    
    /**
     * Creates a new execution engine with explicit run ID and configuration.
     * 
     * @param taskLifecycle the task lifecycle to execute
     * @param loadPattern the load pattern
     * @param metricsCollector the metrics collector
     * @param runId the run identifier for correlation
     * @param config configuration, or null to load from default locations
     * @deprecated Use {@link #builder()} instead. This constructor will be removed in 0.9.6.
     */
    @Deprecated(since = "0.9.5", forRemoval = true)
    public ExecutionEngine(TaskLifecycle taskLifecycle, LoadPattern loadPattern, MetricsCollector metricsCollector, String runId, VajraPulseConfig config) {
        this(builder()
                .withTask(taskLifecycle)
                .withLoadPattern(loadPattern)
                .withMetricsCollector(metricsCollector)
                .withRunId(runId)
                .withConfig(config));
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
        private com.vajrapulse.api.BackpressureHandler backpressureHandler;
        private double backpressureThreshold = 0.7; // Default threshold
        
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
         * Sets the backpressure handler for request loss handling.
         * 
         * <p>If not provided, requests will be queued normally (default behavior).
         * 
         * <p>Example usage:
         * <pre>{@code
         * ExecutionEngine engine = ExecutionEngine.builder()
         *     .withTask(task)
         *     .withLoadPattern(pattern)
         *     .withMetricsCollector(metrics)
         *     .withBackpressureHandler(BackpressureHandlers.DROP)
         *     .withBackpressureThreshold(0.7)
         *     .build();
         * }</pre>
         * 
         * @param backpressureHandler the backpressure handler (can be null)
         * @return this builder
         * @since 0.9.6
         */
        public Builder withBackpressureHandler(com.vajrapulse.api.BackpressureHandler backpressureHandler) {
            this.backpressureHandler = backpressureHandler;
            return this;
        }
        
        /**
         * Sets the backpressure threshold for triggering handler.
         * 
         * <p>When backpressure level exceeds this threshold, the handler is invoked.
         * Default is 0.7 (70% backpressure).
         * 
         * @param threshold backpressure threshold (0.0 to 1.0)
         * @return this builder
         * @throws IllegalArgumentException if threshold is not between 0.0 and 1.0
         * @since 0.9.6
         */
        public Builder withBackpressureThreshold(double threshold) {
            if (threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException("Backpressure threshold must be between 0.0 and 1.0, got: " + threshold);
            }
            this.backpressureThreshold = threshold;
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
            // Don't call teardown if init failed
            shutdownManager.removeShutdownHook();
            throw e;
        }
        
        try {
            TaskExecutor taskExecutor = new TaskExecutor(taskLifecycle);
            RateController rateController = new RateController(loadPattern);
            
            // Register rate controller accuracy metrics
            com.vajrapulse.core.metrics.EngineMetricsRegistrar.registerRateControllerMetrics(
                rateController, loadPattern, metricsCollector.getRegistry(), runId);
            
            long testDurationMillis = loadPattern.getDuration().toMillis();
            long iteration = 0;
            
            // Check if load pattern supports warm-up/cool-down
            boolean hasWarmupCooldown = loadPattern instanceof WarmupCooldownLoadPattern;
            
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
                
                // Check if we should record metrics (only during steady-state phase)
                boolean shouldRecordMetrics = !hasWarmupCooldown || 
                    ((WarmupCooldownLoadPattern) loadPattern).shouldRecordMetrics(elapsedMillis);
                
                long currentIteration = iteration++;
                long queueStartNanos = System.nanoTime();
                pendingExecutions.incrementAndGet();
                
                // Update queue size gauge
                metricsCollector.updateQueueSize(pendingExecutions.get());
                
                // Check backpressure before submitting
                if (backpressureHandler != null) {
                    double backpressure = getBackpressureLevel();
                    if (backpressure >= backpressureThreshold) {
                        com.vajrapulse.api.BackpressureHandler.BackpressureContext context = 
                            createBackpressureContext(backpressure);
                        com.vajrapulse.api.BackpressureHandler.HandlingResult result = 
                            backpressureHandler.handle(currentIteration, backpressure, context);
                        
                        switch (result) {
                            case DROPPED:
                                // Skip this request - don't submit
                                pendingExecutions.decrementAndGet();
                                metricsCollector.updateQueueSize(pendingExecutions.get());
                                metricsCollector.recordDroppedRequest();
                                logger.debug("Request {} dropped due to backpressure {} runId={}", 
                                    currentIteration, String.format("%.2f", backpressure), runId);
                                continue; // Skip to next iteration
                                
                            case REJECTED:
                                // Fail immediately - record as failure
                                pendingExecutions.decrementAndGet();
                                metricsCollector.updateQueueSize(pendingExecutions.get());
                                if (shouldRecordMetrics) {
                                    metricsCollector.recordRejectedRequest();
                                    metricsCollector.record(new ExecutionMetrics(
                                        System.nanoTime(),
                                        System.nanoTime(),
                                        com.vajrapulse.api.TaskResult.failure(
                                            new RuntimeException("Request rejected due to backpressure: " + String.format("%.2f", backpressure))),
                                        currentIteration
                                    ));
                                }
                                logger.debug("Request {} rejected due to backpressure {} runId={}", 
                                    currentIteration, String.format("%.2f", backpressure), runId);
                                continue; // Skip to next iteration
                                
                            case RETRY:
                                // TODO: Implement retry mechanism
                                // For now, queue it
                                break;
                                
                            case DEGRADED:
                                // TODO: Implement degradation mechanism
                                // For now, queue it
                                break;
                                
                            case QUEUED:
                            case ACCEPTED:
                            default:
                                // Normal processing - submit to executor
                                break;
                        }
                    }
                }
                
                executor.submit(new ExecutionCallable(taskExecutor, metricsCollector, currentIteration, queueStartNanos, pendingExecutions, shouldRecordMetrics));
            }
            
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
        shutdownManager.removeShutdownHook();
        // Clean up the Cleaner registration
        cleanable.clean();
        
        if (!executor.isShutdown()) {
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
    
    /**
     * Gets the current backpressure level.
     * 
     * <p>If the load pattern is an AdaptiveLoadPattern with a BackpressureProvider,
     * returns the backpressure level from that provider. Otherwise returns 0.0.
     * 
     * @return backpressure level (0.0 to 1.0)
     */
    private double getBackpressureLevel() {
        if (loadPattern instanceof com.vajrapulse.api.AdaptiveLoadPattern adaptivePattern) {
            return adaptivePattern.getBackpressureLevel();
        }
        return 0.0;
    }
    
    /**
     * Creates a backpressure context for the handler.
     * 
     * @param backpressureLevel current backpressure level
     * @return backpressure context
     */
    private com.vajrapulse.api.BackpressureHandler.BackpressureContext createBackpressureContext(double backpressureLevel) {
        long queueDepth = pendingExecutions.get();
        var snapshot = metricsCollector.snapshot();
        return new com.vajrapulse.api.BackpressureHandler.BackpressureContext(
            queueDepth,
            0L, // Max queue depth (unbounded for virtual threads)
            0L, // Active connections (not tracked here)
            0L, // Max connections (not tracked here)
            snapshot.failureRate(),
            java.util.Map.of() // Custom metrics
        );
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
        private final boolean shouldRecordMetrics;
        
        ExecutionCallable(TaskExecutor taskExecutor, MetricsCollector metricsCollector, long iteration, 
                         long queueStartNanos, java.util.concurrent.atomic.AtomicLong pendingExecutions,
                         boolean shouldRecordMetrics) {
            this.taskExecutor = taskExecutor;
            this.metricsCollector = metricsCollector;
            this.iteration = iteration;
            this.queueStartNanos = queueStartNanos;
            this.pendingExecutions = pendingExecutions;
            this.shouldRecordMetrics = shouldRecordMetrics;
        }
        
        @Override
        public Void call() {
            // Record queue wait time (time from submission to actual execution start)
            // Only record during steady-state phase
            if (shouldRecordMetrics) {
                long queueWaitNanos = System.nanoTime() - queueStartNanos;
                metricsCollector.recordQueueWait(queueWaitNanos);
            }
            
            // Decrement pending count when execution starts (before actual execution)
            // This ensures queue size metric reflects only tasks waiting in queue,
            // not tasks that have started executing
            pendingExecutions.decrementAndGet();
            metricsCollector.updateQueueSize(pendingExecutions.get());
            
            try {
                ExecutionMetrics metrics = taskExecutor.executeWithMetrics(iteration);
                // Only record metrics during steady-state phase (warm-up/cool-down excluded)
                if (shouldRecordMetrics) {
                    metricsCollector.record(metrics);
                }
            } finally {
                // No cleanup needed - queue size already updated above
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
     * @param taskLifecycle the task lifecycle to execute
     * @param loadPattern the load pattern definition
     * @param metricsCollector metrics collector instance
     * @return aggregated metrics snapshot after execution
     * @throws Exception if setup or cleanup fails
     */
    public static com.vajrapulse.core.metrics.AggregatedMetrics execute(
            TaskLifecycle taskLifecycle,
            LoadPattern loadPattern,
            MetricsCollector metricsCollector) throws Exception {
        try (ExecutionEngine engine = ExecutionEngine.builder()
                .withTask(taskLifecycle)
                .withLoadPattern(loadPattern)
                .withMetricsCollector(metricsCollector)
                .build()) {
            engine.run();
        }
        return metricsCollector.snapshot();
    }
    
    /**
     * Cleanup action for executor service.
     * 
     * <p>This is called by the {@link Cleaner} API if the {@code ExecutionEngine} is not
     * properly closed via {@link #close()}. The Cleaner API provides a safety net to ensure
     * that resources are cleaned up even if the caller forgets to call {@code close()} or if
     * an exception prevents the normal cleanup path.
     * 
     * <p><strong>When Cleaner Runs:</strong>
     * <ul>
     *   <li>The Cleaner runs when the {@code ExecutionEngine} instance becomes unreachable
     *       and is eligible for garbage collection</li>
     *   <li>The cleanup action runs in a separate thread managed by the Cleaner</li>
     *   <li>There is no guarantee about when cleanup will occur - it depends on GC timing</li>
     * </ul>
     * 
     * <p><strong>Best Practice:</strong> Always use try-with-resources or explicitly call
     * {@link #close()} to ensure timely cleanup. Do not rely on the Cleaner as the primary
     * cleanup mechanism.
     * 
     * <p><strong>Example:</strong>
     * <pre>{@code
     * try (ExecutionEngine engine = ExecutionEngine.builder()
     *         .withTask(task)
     *         .withLoadPattern(load)
     *         .withMetricsCollector(collector)
     *         .build()) {
     *     engine.run();
     * } // Engine automatically closed, Cleaner not invoked
     * }</pre>
     * 
     * @since 0.9.0
     */
    private static final class ExecutorCleanup implements Runnable {
        private final ExecutorService executor;
        private final String runId;
        
        ExecutorCleanup(ExecutorService executor, String runId) {
            this.executor = executor;
            this.runId = runId;
        }
        
        @Override
        public void run() {
            if (!executor.isShutdown()) {
                logger.warn("Executor not closed via close() for runId={}, forcing shutdown via Cleaner", runId);
                executor.shutdownNow();
            }
        }
    }
}
