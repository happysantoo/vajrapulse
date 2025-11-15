package com.vajra.core.engine;

import com.vajra.api.LoadPattern;
import com.vajra.api.PlatformThreads;
import com.vajra.api.Task;
import com.vajra.api.VirtualThreads;
import com.vajra.core.metrics.MetricsCollector;
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
 *   <li>Task lifecycle (setup/execute/cleanup)</li>
 *   <li>Thread pool management (virtual or platform)</li>
 *   <li>Rate control according to load pattern</li>
 *   <li>Metrics collection</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * Task task = new MyHttpTask();
 * LoadPattern load = new StaticLoad(100.0, Duration.ofMinutes(5));
 * MetricsCollector metrics = new MetricsCollector();
 * 
 * ExecutionEngine engine = new ExecutionEngine(task, load, metrics);
 * engine.run();
 * 
 * AggregatedMetrics results = metrics.snapshot();
 * }</pre>
 */
public final class ExecutionEngine implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ExecutionEngine.class);
    
    private final Task task;
    private final LoadPattern loadPattern;
    private final MetricsCollector metricsCollector;
    private final ExecutorService executor;
    private final boolean useVirtualThreads;
    
    public ExecutionEngine(Task task, LoadPattern loadPattern, MetricsCollector metricsCollector) {
        this.task = task;
        this.loadPattern = loadPattern;
        this.metricsCollector = metricsCollector;
        
        // Determine thread strategy from annotations
        if (task.getClass().isAnnotationPresent(VirtualThreads.class)) {
            this.executor = Executors.newVirtualThreadPerTaskExecutor();
            this.useVirtualThreads = true;
            logger.info("Using virtual threads for task: {}", task.getClass().getSimpleName());
        } else if (task.getClass().isAnnotationPresent(PlatformThreads.class)) {
            PlatformThreads annotation = task.getClass().getAnnotation(PlatformThreads.class);
            int poolSize = annotation.poolSize();
            if (poolSize == -1) {
                poolSize = Runtime.getRuntime().availableProcessors();
            }
            this.executor = Executors.newFixedThreadPool(poolSize);
            this.useVirtualThreads = false;
            logger.info("Using platform threads (pool size: {}) for task: {}", 
                poolSize, task.getClass().getSimpleName());
        } else {
            // Default to virtual threads
            this.executor = Executors.newVirtualThreadPerTaskExecutor();
            this.useVirtualThreads = true;
            logger.info("No thread annotation found, defaulting to virtual threads for task: {}", 
                task.getClass().getSimpleName());
        }
    }
    
    /**
     * Runs the load test.
     * 
     * <p>This method:
     * <ol>
     *   <li>Calls task.setup()</li>
     *   <li>Submits executions according to load pattern</li>
     *   <li>Waits for test duration</li>
     *   <li>Shuts down executor</li>
     *   <li>Calls task.cleanup()</li>
     * </ol>
     * 
     * @throws Exception if setup, execution, or cleanup fails
     */
    public void run() throws Exception {
        logger.info("Starting load test with pattern: {}", loadPattern.getClass().getSimpleName());
        logger.info("Test duration: {}", loadPattern.getDuration());
        
        // Setup
        task.setup();
        logger.info("Task setup completed");
        
        try {
            TaskExecutor taskExecutor = new TaskExecutor(task);
            RateController rateController = new RateController(loadPattern);
            
            long testStartMillis = System.currentTimeMillis();
            long testDurationMillis = loadPattern.getDuration().toMillis();
            long iteration = 0;
            
            while (rateController.getElapsedMillis() < testDurationMillis) {
                rateController.waitForNext();
                
                long currentIteration = iteration++;
                executor.submit(new ExecutionCallable(taskExecutor, metricsCollector, currentIteration));
            }
            
            logger.info("Test duration completed, shutting down executor");
            
        } finally {
            // Cleanup
            executor.shutdown();
            boolean terminated = executor.awaitTermination(60, TimeUnit.SECONDS);
            if (!terminated) {
                logger.warn("Executor did not terminate in time, forcing shutdown");
                executor.shutdownNow();
            }
            
            task.cleanup();
            logger.info("Task cleanup completed");
        }
    }
    
    @Override
    public void close() {
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
     * Callable for executing a task and recording metrics.
     * Implemented as a concrete class to avoid lambda allocation in hot path.
     */
    private static final class ExecutionCallable implements Callable<Void> {
        private final TaskExecutor taskExecutor;
        private final MetricsCollector metricsCollector;
        private final long iteration;
        
        ExecutionCallable(TaskExecutor taskExecutor, MetricsCollector metricsCollector, long iteration) {
            this.taskExecutor = taskExecutor;
            this.metricsCollector = metricsCollector;
            this.iteration = iteration;
        }
        
        @Override
        public Void call() {
            ExecutionMetrics metrics = taskExecutor.executeWithMetrics(iteration);
            metricsCollector.record(metrics);
            return null;
        }
    }
}
