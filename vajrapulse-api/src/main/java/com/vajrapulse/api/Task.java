package com.vajrapulse.api;

/**
 * Defines a load test task to be executed repeatedly.
 * 
 * <p>Implementations must be thread-safe if reused across executions.
 * The framework calls {@link #setup()} once before the first execution,
 * {@link #execute()} for each iteration, and {@link #cleanup()} once
 * after all executions complete.
 * 
 * <p>Tasks should return {@link TaskResult.Success} for successful executions
 * and {@link TaskResult.Failure} for failures. The framework's executor will
 * catch any uncaught exceptions and automatically wrap them in
 * {@link TaskResult.Failure}.
 * 
 * <p>Example:
 * <pre>{@code
 * @VirtualThreads
 * public class HttpLoadTest implements Task {
 *     private HttpClient client;
 *     
 *     @Override
 *     public void setup() {
 *         client = HttpClient.newBuilder()
 *             .executor(Executors.newVirtualThreadPerTaskExecutor())
 *             .build();
 *     }
 *     
 *     @Override
 *     public TaskResult execute() throws Exception {
 *         HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
 *         if (response.statusCode() == 200) {
 *             return TaskResult.success(response.body());
 *         } else {
 *             return TaskResult.failure(new RuntimeException("HTTP " + response.statusCode()));
 *         }
 *     }
 *     
 *     @Override
 *     public void cleanup() {
 *         // Cleanup if needed
 *     }
 * }
 * }</pre>
 * 
 * @see TaskResult
 * @see VirtualThreads
 * @see PlatformThreads
 */
public interface Task {
    
    /**
     * Called once before any executions begin.
     * Use this to initialize resources like HTTP clients, database connections, etc.
     * 
     * @throws Exception if setup fails
     */
    default void setup() throws Exception {
        // Optional setup
    }
    
    /**
     * Executes one iteration of the load test.
     * This method will be called repeatedly according to the configured load pattern.
     * 
     * <p>The framework automatically captures timing and metrics.
     * Focus on implementing your test logic only.
     * 
     * @return the result of the execution
     * @throws Exception if execution fails
     */
    TaskResult execute() throws Exception;
    
    /**
     * Called once after all executions complete.
     * Use this to clean up resources.
     * 
     * @throws Exception if cleanup fails
     */
    default void cleanup() throws Exception {
        // Optional cleanup
    }
}
