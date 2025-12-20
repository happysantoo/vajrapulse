package com.vajrapulse.api.task;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a task should be executed using platform threads.
 * 
 * <p>Use this for CPU-bound tasks like encryption, compression,
 * or heavy computation.
 * 
 * <p>The pool size determines the number of threads:
 * <ul>
 *   <li>{@code poolSize = -1}: Use {@code Runtime.availableProcessors()}</li>
 *   <li>{@code poolSize > 0}: Use exact number specified</li>
 * </ul>
 * 
 * <p>Example:
 * <pre>{@code
 * @PlatformThreads(poolSize = 8)
 * public class EncryptionTest implements Task {
 *     // CPU-intensive operations
 * }
 * }</pre>
 * 
 * @see com.vajrapulse.api.task.VirtualThreads
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformThreads {
    /**
     * Number of platform threads in the pool.
     * Use -1 for {@code Runtime.availableProcessors()}.
     * 
     * @return pool size
     */
    int poolSize() default -1;
}
