package com.vajrapulse.core.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages graceful shutdown of load test execution.
 * 
 * <p>This class handles:
 * <ul>
 *   <li>Signal interception (SIGINT/SIGTERM via shutdown hooks)</li>
 *   <li>Stopping new task scheduling</li>
 *   <li>Draining in-flight executions with configurable timeout</li>
 *   <li>Final metrics and trace flushing</li>
 *   <li>Resource cleanup coordination</li>
 * </ul>
 * 
 * <p><strong>Shutdown Sequence:</strong>
 * <ol>
 *   <li>Signal received (Ctrl+C, kill, etc.) or manual {@link #initiateShutdown()}</li>
 *   <li>Set shutdown flag - no new iterations scheduled</li>
 *   <li>Wait for running tasks to complete (up to drain timeout)</li>
 *   <li>Execute cleanup callbacks (task teardown, metrics flush)</li>
 *   <li>Force shutdown if drain timeout exceeded</li>
 * </ol>
 * 
 * <p><strong>Timing Targets:</strong>
 * <ul>
 *   <li>Default drain timeout: 5 seconds</li>
 *   <li>Default force shutdown timeout: 10 seconds</li>
 *   <li>Total graceful shutdown: &lt;5 seconds (P95 target)</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * ShutdownManager shutdownMgr = ShutdownManager.builder()
 *     .withRunId(runId)
 *     .withDrainTimeout(Duration.ofSeconds(5))
 *     .withForceTimeout(Duration.ofSeconds(10))
 *     .onShutdown(() -> {
 *         metricsCollector.flush();
 *         tracingExporter.flush();
 *     })
 *     .build();
 * 
 * shutdownMgr.registerShutdownHook();
 * 
 * // In main execution loop
 * while (!shutdownMgr.isShutdownRequested() && ...) {
 *     // Submit tasks
 * }
 * 
 * // When done
 * shutdownMgr.awaitShutdown(executor);
 * }</pre>
 * 
 * @since 0.9.0
 */
public final class ShutdownManager {
    private static final Logger logger = LoggerFactory.getLogger(ShutdownManager.class);
    
    private static final Duration DEFAULT_DRAIN_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_FORCE_TIMEOUT = Duration.ofSeconds(10);
    
    private final String runId;
    private final Duration drainTimeout;
    private final Duration forceTimeout;
    private final Runnable shutdownCallback;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private final CountDownLatch shutdownComplete = new CountDownLatch(1);
    private volatile Thread shutdownHookThread;
    
    private ShutdownManager(Builder builder) {
        this.runId = builder.runId;
        this.drainTimeout = builder.drainTimeout;
        this.forceTimeout = builder.forceTimeout;
        this.shutdownCallback = builder.shutdownCallback;
    }
    
    /**
     * Returns a new builder for constructing a {@link ShutdownManager}.
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Checks if shutdown has been requested.
     * 
     * <p>Execution loops should check this flag periodically and stop
     * scheduling new tasks when it becomes true.
     * 
     * @return true if shutdown was requested, false otherwise
     */
    public boolean isShutdownRequested() {
        return shutdownRequested.get();
    }
    
    /**
     * Initiates graceful shutdown.
     * 
     * <p>This method is idempotent - calling it multiple times has the same
     * effect as calling it once. It can be called manually or automatically
     * via the registered shutdown hook.
     * 
     * @return true if this call initiated shutdown, false if already requested
     */
    public boolean initiateShutdown() {
        if (shutdownRequested.compareAndSet(false, true)) {
            logger.info("Shutdown initiated for runId={}", runId);
            return true;
        }
        return false;
    }
    
    /**
     * Registers a JVM shutdown hook for automatic graceful shutdown.
     * 
     * <p>The hook will call {@link #initiateShutdown()} when the JVM receives
     * SIGINT (Ctrl+C) or SIGTERM signals. This method should be called once
     * during initialization.
     * 
     * <p><strong>Warning:</strong> The shutdown hook does NOT wait for executor
     * draining. You must call {@link #awaitShutdown(ExecutorService)} explicitly
     * in your main thread.
     */
    public void registerShutdownHook() {
        shutdownHookThread = new Thread(() -> {
            if (initiateShutdown()) {
                logger.info("Shutdown hook triggered for runId={} - waiting for graceful completion", runId);
                try {
                    // Wait for main thread to complete shutdown sequence
                    boolean completed = shutdownComplete.await(
                        forceTimeout.toMillis(), 
                        TimeUnit.MILLISECONDS
                    );
                    if (!completed) {
                        logger.warn("Shutdown did not complete within force timeout {}ms for runId={}", 
                            forceTimeout.toMillis(), runId);
                    }
                } catch (InterruptedException e) {
                    logger.warn("Shutdown hook interrupted for runId={}", runId);
                    Thread.currentThread().interrupt();
                }
            }
        }, "vajrapulse-shutdown-hook-" + runId);
        
        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
        logger.debug("Shutdown hook registered for runId={}", runId);
    }
    
    /**
     * Removes the registered shutdown hook.
     * 
     * <p>Call this if you need to unregister the hook, for example in tests
     * or when managing lifecycle manually.
     * 
     * @return true if the hook was removed, false if it wasn't registered
     */
    public boolean removeShutdownHook() {
        if (shutdownHookThread != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
                logger.debug("Shutdown hook removed for runId={}", runId);
                return true;
            } catch (IllegalStateException e) {
                // Hook already executing or JVM shutting down
                logger.debug("Cannot remove shutdown hook for runId={}: {}", runId, e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    /**
     * Waits for executor to drain and completes shutdown sequence.
     * 
     * <p>This method should be called after the execution loop exits. It:
     * <ol>
     *   <li>Stops the executor from accepting new tasks</li>
     *   <li>Waits for running tasks to complete (up to drain timeout)</li>
     *   <li>Forces shutdown if timeout exceeded</li>
     *   <li>Executes shutdown callback (metrics flush, cleanup)</li>
     *   <li>Signals shutdown completion</li>
     * </ol>
     * 
     * <p><strong>Timing:</strong> This method blocks for up to {@code drainTimeout}
     * plus a small overhead for cleanup. If tasks don't complete within the drain
     * timeout, they are forcibly terminated.
     * 
     * @param executor the executor service to drain and shutdown
     * @return true if executor drained gracefully, false if forced shutdown occurred
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitShutdown(ExecutorService executor) throws InterruptedException {
        long startNanos = System.nanoTime();
        logger.info("Awaiting executor shutdown for runId={} (drain timeout: {}ms)", 
            runId, drainTimeout.toMillis());
        
        try {
            // Stop accepting new tasks
            executor.shutdown();
            
            // Wait for running tasks to complete
            boolean terminatedGracefully = executor.awaitTermination(
                drainTimeout.toMillis(), 
                TimeUnit.MILLISECONDS
            );
            
            if (!terminatedGracefully) {
                long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                logger.warn("Executor did not terminate gracefully within {}ms for runId={}, forcing shutdown (elapsed: {}ms)", 
                    drainTimeout.toMillis(), runId, elapsedMillis);
                
                executor.shutdownNow();
                
                // Give forced shutdown a chance
                boolean forcedTermination = executor.awaitTermination(
                    forceTimeout.toMillis(), 
                    TimeUnit.MILLISECONDS
                );
                
                if (!forcedTermination) {
                    logger.error("Executor did not terminate even after forced shutdown for runId={}", runId);
                }
                
                return false;
            }
            
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            logger.info("Executor drained gracefully in {}ms for runId={}", elapsedMillis, runId);
            return true;
            
        } finally {
            // Execute cleanup callback (metrics flush, task teardown, etc.)
            if (shutdownCallback != null) {
                long callbackStartNanos = System.nanoTime();
                try {
                    logger.debug("Executing shutdown callback for runId={}", runId);
                    shutdownCallback.run();
                    long callbackMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - callbackStartNanos);
                    logger.debug("Shutdown callback completed in {}ms for runId={}", callbackMillis, runId);
                } catch (Exception e) {
                    logger.error("Shutdown callback failed for runId={}: {}", runId, e.getMessage(), e);
                }
            }
            
            // Signal shutdown complete
            shutdownComplete.countDown();
            
            long totalMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            logger.info("Shutdown sequence completed in {}ms for runId={}", totalMillis, runId);
        }
    }
    
    /**
     * Builder for {@link ShutdownManager}.
     */
    public static final class Builder {
        private String runId = "default";
        private Duration drainTimeout = DEFAULT_DRAIN_TIMEOUT;
        private Duration forceTimeout = DEFAULT_FORCE_TIMEOUT;
        private Runnable shutdownCallback = () -> {};
        
        /**
         * Sets the run ID for logging correlation.
         * 
         * @param runId the run identifier
         * @return this builder
         */
        public Builder withRunId(String runId) {
            this.runId = runId;
            return this;
        }
        
        /**
         * Sets the timeout for draining running tasks gracefully.
         * 
         * <p>Default: 5 seconds
         * 
         * @param drainTimeout the drain timeout
         * @return this builder
         */
        public Builder withDrainTimeout(Duration drainTimeout) {
            this.drainTimeout = drainTimeout;
            return this;
        }
        
        /**
         * Sets the timeout for forced shutdown after drain timeout expires.
         * 
         * <p>Default: 10 seconds
         * 
         * @param forceTimeout the force shutdown timeout
         * @return this builder
         */
        public Builder withForceTimeout(Duration forceTimeout) {
            this.forceTimeout = forceTimeout;
            return this;
        }
        
        /**
         * Sets a callback to execute during shutdown.
         * 
         * <p>Use this for:
         * <ul>
         *   <li>Flushing metrics to exporters</li>
         *   <li>Flushing traces to collectors</li>
         *   <li>Calling task teardown</li>
         *   <li>Writing final reports</li>
         * </ul>
         * 
         * <p>The callback is executed after executor draining completes,
         * regardless of whether it was graceful or forced.
         * 
         * @param callback the shutdown callback
         * @return this builder
         */
        public Builder onShutdown(Runnable callback) {
            this.shutdownCallback = callback;
            return this;
        }
        
        /**
         * Builds the {@link ShutdownManager}.
         * 
         * @return a new shutdown manager instance
         */
        public ShutdownManager build() {
            return new ShutdownManager(this);
        }
    }
}
