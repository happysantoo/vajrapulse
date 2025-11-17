package com.vajrapulse.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a task should be executed using virtual threads.
 * 
 * <p>Use this for I/O-bound tasks like HTTP requests, database queries,
 * or any operation that involves waiting.
 * 
 * <p>Virtual threads are lightweight and can handle millions of concurrent
 * operations with minimal memory overhead.
 * 
 * <p>Example:
 * <pre>{@code
 * @VirtualThreads
 * public class HttpLoadTest implements Task {
 *     // HTTP client operations
 * }
 * }</pre>
 * 
 * @see PlatformThreads
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface VirtualThreads {
}
