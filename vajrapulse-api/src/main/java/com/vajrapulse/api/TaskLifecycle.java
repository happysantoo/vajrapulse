package com.vajrapulse.api;

/**
 * Defines the complete lifecycle hooks for a load test task.
 * 
 * <p>This interface provides explicit lifecycle semantics for resource 
 * initialization and cleanup. It is compatible with the legacy {@link Task}
 * interface - implementations can choose to implement either or both.
 * 
 * <p>The framework guarantees the following call sequence:
 * 
 * <ol>
 *   <li>{@link #init()} - called exactly once before any executions begin</li>
 *   <li>{@link #execute(long)} - called repeatedly for each iteration</li>
 *   <li>{@link #teardown()} - called exactly once after all executions complete</li>
 * </ol>
 * 
 * <p><strong>Thread Safety:</strong> {@code init()} and {@code teardown()} are
 * called from the orchestration thread and guaranteed not to run concurrently.
 * {@code execute(iteration)} may be called concurrently from multiple threads
 * depending on the thread pool configuration.
 * 
 * <p><strong>Exception Handling:</strong>
 * <ul>
 *   <li>If {@code init()} throws an exception, the test will not start and
 *       {@code teardown()} will NOT be called</li>
 *   <li>If {@code execute()} throws an exception, it is caught and recorded
 *       as a failure; execution continues with the next iteration</li>
 *   <li>If {@code teardown()} throws an exception, it is logged but does not
 *       prevent graceful shutdown completion</li>
 * </ul>
 * 
 * <p><strong>Graceful Shutdown:</strong> When a shutdown signal (SIGINT/SIGTERM)
 * is received:
 * <ol>
 *   <li>No new iterations are scheduled</li>
 *   <li>Currently running iterations are allowed to complete (with timeout)</li>
 *   <li>{@code teardown()} is called to clean up resources</li>
 *   <li>Metrics and traces are flushed</li>
 * </ol>
 * 
 * <p>Example implementation:
 * <pre>{@code
 * @VirtualThreads
 * public class HttpApiTest implements TaskLifecycle {
 *     private HttpClient client;
 *     private MeterRegistry registry;
 *     
 *     @Override
 *     public void init() throws Exception {
 *         // Initialize once - thread-safe setup
 *         this.client = HttpClient.newBuilder()
 *             .executor(Executors.newVirtualThreadPerTaskExecutor())
 *             .connectTimeout(Duration.ofSeconds(5))
 *             .build();
 *         
 *         this.registry = new SimpleMeterRegistry();
 *         logger.info("HTTP client initialized");
 *     }
 *     
 *     @Override
 *     public TaskResult execute(long iteration) throws Exception {
 *         // Called concurrently - must be thread-safe
 *         HttpRequest request = HttpRequest.newBuilder()
 *             .uri(URI.create("https://api.example.com/status"))
 *             .header("X-Iteration", String.valueOf(iteration))
 *             .GET()
 *             .build();
 *         
 *         HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
 *         
 *         if (response.statusCode() == 200) {
 *             return TaskResult.success(response.body());
 *         } else {
 *             return TaskResult.failure(
 *                 new RuntimeException("HTTP " + response.statusCode())
 *             );
 *         }
 *     }
 *     
 *     @Override
 *     public void teardown() throws Exception {
 *         // Cleanup once - release resources
 *         if (client != null) {
 *             // HttpClient doesn't need explicit close, but show pattern
 *             logger.info("HTTP client cleanup completed");
 *         }
 *         
 *         if (registry != null) {
 *             registry.close();
 *         }
 *     }
 * }
 * }</pre>
 * 
 * <p><strong>Migration from {@link Task}:</strong> Existing {@link Task}
 * implementations with {@code setup()}/{@code cleanup()} methods are fully
 * compatible. The framework adapts them automatically:
 * <ul>
 *   <li>{@code setup()} → {@code init()}</li>
 *   <li>{@code execute()} → {@code execute(0)}, {@code execute(1)}, ...</li>
 *   <li>{@code cleanup()} → {@code teardown()}</li>
 * </ul>
 * 
 * @see Task
 * @see TaskResult
 * @see VirtualThreads
 * @see PlatformThreads
 * @since 0.9.0
 */
public interface TaskLifecycle {
    
    /**
     * Initializes the task before any executions begin.
     * 
     * <p>Called exactly once from the orchestration thread before the first
     * iteration. Use this to:
     * <ul>
     *   <li>Initialize HTTP clients, database connections, or other shared resources</li>
     *   <li>Load configuration or test data</li>
     *   <li>Set up monitoring or custom metrics</li>
     *   <li>Perform any expensive one-time setup</li>
     * </ul>
     * 
     * <p><strong>Guarantees:</strong>
     * <ul>
     *   <li>Called exactly once per test run</li>
     *   <li>Called before any {@code execute()} invocations</li>
     *   <li>Not called concurrently with {@code execute()} or {@code teardown()}</li>
     *   <li>If this method throws an exception, {@code teardown()} will NOT be called</li>
     * </ul>
     * 
     * @throws Exception if initialization fails; the test will not start
     */
    void init() throws Exception;
    
    /**
     * Executes one iteration of the load test.
     * 
     * <p>Called repeatedly according to the configured load pattern. Each
     * invocation represents one "request" or "transaction" in your load test.
     * The framework automatically captures timing and metrics.
     * 
     * <p><strong>Thread Safety:</strong> This method may be called concurrently
     * from multiple threads. Implementations must be thread-safe or use
     * thread-local state.
     * 
     * <p><strong>Iteration Number:</strong> The iteration parameter starts at 0
     * and increments for each execution. It can be used for:
     * <ul>
     *   <li>Correlation IDs or trace headers</li>
     *   <li>Selecting different test data per iteration</li>
     *   <li>Debugging or detailed logging</li>
     * </ul>
     * 
     * <p><strong>Exception Handling:</strong> If this method throws an exception,
     * the framework catches it, records it as a failure with the exception details,
     * and continues with the next iteration. The test does not stop.
     * 
     * @param iteration the iteration number, starting from 0
     * @return the result of the execution (success or failure)
     * @throws Exception if execution fails; automatically wrapped in {@link TaskResult.Failure}
     */
    TaskResult execute(long iteration) throws Exception;
    
    /**
     * Cleans up resources after all executions complete.
     * 
     * <p>Called exactly once from the orchestration thread after:
     * <ul>
     *   <li>The test duration has elapsed, OR</li>
     *   <li>A shutdown signal (SIGINT/SIGTERM) was received</li>
     * </ul>
     * 
     * <p>All running iterations are given a grace period to complete before
     * this method is called. Use this to:
     * <ul>
     *   <li>Close HTTP clients, database connections, or file handles</li>
     *   <li>Flush buffers or write final output</li>
     *   <li>Release any resources allocated in {@code init()}</li>
     *   <li>Log final statistics or debug information</li>
     * </ul>
     * 
     * <p><strong>Guarantees:</strong>
     * <ul>
     *   <li>Called exactly once per test run (if {@code init()} succeeded)</li>
     *   <li>Called after all {@code execute()} invocations complete</li>
     *   <li>Not called concurrently with {@code execute()}</li>
     *   <li>Always called, even if {@code execute()} threw exceptions</li>
     *   <li>Exceptions thrown here are logged but do not prevent shutdown</li>
     * </ul>
     * 
     * @throws Exception if cleanup fails; logged but does not prevent shutdown completion
     */
    void teardown() throws Exception;
}
