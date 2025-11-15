# VajraPulse Code Quality Rules & Patterns

## Automated Quality Checks

These rules should be enforced by CI/CD and caught by Copilot during development.

---

## 1. Module Dependency Rules

### vajrapulse-api: ZERO Dependencies

```java
// ✅ ALLOWED in vajrapulse-api
public interface Task {
    TaskResult execute() throws Exception;
}

public record ExecutionConfig(int tps, Duration duration) {}

// ❌ FORBIDDEN in vajrapulse-api
import org.slf4j.Logger;  // NO external dependencies
import io.micrometer.core.instrument.Timer;  // NO
```

**Check Command**:
```bash
./gradlew :vajrapulse-api:dependencies --configuration runtimeClasspath
# Output should show: "No dependencies"
```

### vajrapulse-core: Limited Dependencies

**ALLOWED**:
- vajrapulse-api (api dependency)
- micrometer-core
- slf4j-api

**FORBIDDEN**:
- Any HTTP client
- Any database driver
- Any messaging library
- picocli
- Any -impl or -runtime JARs

```gradle
// ❌ FORBIDDEN in vajrapulse-core
dependencies {
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'  // NO
    implementation 'org.postgresql:postgresql:42.7.1'    // NO
    implementation 'info.picocli:picocli:4.7.5'          // NO - CLI is in worker
}
```

---

## 2. Thread Safety Patterns

### Rule: No synchronized in Virtual Thread Code

```java
// ❌ FORBIDDEN
@VirtualThreads
public class BadTask implements Task {
    private synchronized void updateState() {
        // This pins the carrier thread!
    }
}

// ✅ REQUIRED
@VirtualThreads
public class GoodTask implements Task {
    private final Lock lock = new ReentrantLock();
    
    private void updateState() {
        lock.lock();
        try {
            // ...
        } finally {
            lock.unlock();
        }
    }
}
```

### Rule: Use Concurrent Collections

```java
// ❌ FORBIDDEN
private final Map<String, Integer> map = new HashMap<>();
synchronized (map) {
    map.put(key, value);
}

// ✅ REQUIRED
private final ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
map.put(key, value);
```

### Rule: Use Atomic Counters

```java
// ❌ FORBIDDEN
private long counter = 0;
public synchronized void increment() {
    counter++;
}

// ✅ REQUIRED
private final LongAdder counter = new LongAdder();
public void increment() {
    counter.increment();
}
```

---

## 3. Performance Patterns

### Rule: No Object Allocation in Hot Path

**Hot Path** = Any code called per task execution iteration

```java
// ❌ FORBIDDEN - Creates objects every iteration
public void record(ExecutionMetrics metrics) {
    String msg = "Execution " + metrics.iteration() + " completed";
    logger.debug(msg);
}

// ✅ REQUIRED - Parameterized logging
public void record(ExecutionMetrics metrics) {
    logger.debug("Execution {} completed", metrics.iteration());
}
```

### Rule: Pre-size Collections

```java
// ❌ FORBIDDEN
List<String> results = new ArrayList<>();  // Default size 10
for (int i = 0; i < 1000; i++) {
    results.add(...);  // Multiple resizes!
}

// ✅ REQUIRED
List<String> results = new ArrayList<>(1000);
for (int i = 0; i < 1000; i++) {
    results.add(...);  // No resizing
}
```

### Rule: Reuse Expensive Objects

```java
// ❌ FORBIDDEN - New client per execution
@Override
public TaskResult execute() {
    HttpClient client = HttpClient.newHttpClient();  // Expensive!
    // use client
}

// ✅ REQUIRED - Create in setup()
private HttpClient client;

@Override
public void setup() {
    client = HttpClient.newHttpClient();
}

@Override
public TaskResult execute() {
    // use client
}
```

---

## 4. Lambda Usage Rules

### Rule: No Lambdas in Hot Paths

```java
// ❌ FORBIDDEN - Lambda in hot path
for (int i = 0; i < iterations; i++) {
    executor.submit(() -> task.execute());
}

// ✅ REQUIRED - Concrete class
public class TaskCallable implements Callable<TaskResult> {
    private final Task task;
    public TaskCallable(Task task) { this.task = task; }
    
    @Override
    public TaskResult call() throws Exception {
        return task.execute();
    }
}

for (int i = 0; i < iterations; i++) {
    executor.submit(new TaskCallable(task));
}
```

### Rule: Method References over Lambdas

```java
// ❌ ACCEPTABLE but not preferred
list.sort((a, b) -> a.compareTo(b));

// ✅ PREFERRED
list.sort(Comparator.naturalOrder());

// ❌ ACCEPTABLE but not preferred
names.stream().map(n -> n.toUpperCase()).collect(toList());

// ✅ PREFERRED
names.stream().map(String::toUpperCase).collect(toList());
```

---

## 5. Java 21 Modern Patterns

### Rule: Use Records for Data Classes

```java
// ❌ FORBIDDEN - Traditional class for data
public class ExecutionMetrics {
    private final long startNanos;
    private final long endNanos;
    
    public ExecutionMetrics(long startNanos, long endNanos) {
        this.startNanos = startNanos;
        this.endNanos = endNanos;
    }
    
    public long getStartNanos() { return startNanos; }
    public long getEndNanos() { return endNanos; }
    
    // equals, hashCode, toString...
}

// ✅ REQUIRED - Record
public record ExecutionMetrics(
    long startNanos,
    long endNanos
) {
    public long durationNanos() {
        return endNanos - startNanos;
    }
}
```

### Rule: Use Sealed Interfaces for Type Hierarchies

```java
// ❌ FORBIDDEN - Open hierarchy
public interface TaskResult {}
public class Success implements TaskResult {}
public class Failure implements TaskResult {}

// ✅ REQUIRED - Sealed hierarchy
public sealed interface TaskResult 
    permits Success, Failure {
    record Success(Object data) implements TaskResult {}
    record Failure(Throwable error) implements TaskResult {}
}
```

### Rule: Pattern Matching in Switch

```java
// ❌ FORBIDDEN - instanceof cascade
if (result instanceof Success) {
    Success s = (Success) result;
    process(s.data());
} else if (result instanceof Failure) {
    Failure f = (Failure) result;
    handleError(f.error());
}

// ✅ REQUIRED - Pattern matching
switch (result) {
    case Success(var data) -> process(data);
    case Failure(var error) -> handleError(error);
}
```

---

## 6. Error Handling Patterns

### Rule: Let Executor Catch Task Exceptions

```java
// ❌ FORBIDDEN - Catching in task
@Override
public TaskResult execute() {
    try {
        String response = makeHttpCall();
        return TaskResult.success(response);
    } catch (Exception e) {
        return TaskResult.failure(e);  // Don't do this!
    }
}

// ✅ REQUIRED - Declare throws, let executor catch
@Override
public TaskResult execute() throws Exception {
    String response = makeHttpCall();
    return TaskResult.success(response);
}
// Executor will catch and wrap exceptions
```

### Rule: Specific TaskResult for Known Failures

```java
// ✅ GOOD - Specific result for business failures
@Override
public TaskResult execute() throws Exception {
    HttpResponse<String> response = client.send(request, ...);
    
    if (response.statusCode() == 200) {
        return TaskResult.success(response.body());
    } else if (response.statusCode() == 404) {
        return TaskResult.failure(new NotFoundException());
    } else {
        return TaskResult.failure(
            new HttpException(response.statusCode())
        );
    }
}
```

---

## 7. Micrometer Metrics Patterns

### Rule: Use Micrometer API Only

```java
// ❌ FORBIDDEN - Direct HdrHistogram
import org.HdrHistogram.Histogram;
private final Histogram histogram = new Histogram(...);

// ✅ REQUIRED - Micrometer Timer
import io.micrometer.core.instrument.Timer;
private final Timer timer = Timer.builder("vajrapulse.execution.duration")
    .publishPercentileHistogram()
    .register(registry);
```

### Rule: Consistent Naming Convention

```java
// ✅ REQUIRED - All lowercase, dot-separated
"vajrapulse.execution.duration"
"vajrapulse.execution.total"
"vajrapulse.task.active"

// ❌ FORBIDDEN
"VajraPulseExecutionDuration"  // No camelCase
"vajra_execution_duration"  // No underscores
"execution.duration"  // Missing 'vajra' prefix
```

### Rule: Meaningful Tags

```java
// ✅ REQUIRED - Dimension tags
Timer.builder("vajrapulse.execution.duration")
    .tag("status", "success")  // success/failure
    .tag("task", taskClass)    // task class name
    .tag("worker", workerId)   // worker identifier
    .register(registry);

// ❌ FORBIDDEN - No tags (loses dimensions)
Timer.builder("vajrapulse.execution.duration")
    .register(registry);
```

---

## 8. Testing Patterns (Spock)

### Rule: Given-When-Then Structure

```groovy
// ❌ FORBIDDEN - No structure
def "test"() {
    def task = new Task() { ... }
    def result = executor.execute(task)
    assert result.isSuccess()
}

// ✅ REQUIRED - Clear structure
def "should execute task successfully"() {
    given: "a simple task"
    Task task = new Task() {
        @Override
        TaskResult execute() {
            return TaskResult.success()
        }
    }
    
    when: "executing the task"
    def result = executor.execute(task)
    
    then: "result indicates success"
    result.isSuccess()
}
```

### Rule: Descriptive Test Names

```groovy
// ❌ FORBIDDEN
def "test1"() { }
def "testExecute"() { }

// ✅ REQUIRED - Descriptive with spaces
def "should execute task successfully"() { }
def "should handle task failure gracefully"() { }
def "should capture metrics when task completes"() { }
```

### Rule: Use Power Assertions

```groovy
// ❌ ACCEPTABLE but less informative
then:
assert snapshot.totalExecutions() == 10

// ✅ PREFERRED - Let Spock's assertion do the work
then:
snapshot.totalExecutions() == 10  // Auto-generates detailed failure message
snapshot.successCount() == 10
```

---

## 9. Logging Patterns

### Rule: Parameterized Logging

```java
// ❌ FORBIDDEN - String concatenation
logger.info("Task " + taskName + " executed in " + duration + "ms");

// ✅ REQUIRED - Parameterized
logger.info("Task {} executed in {}ms", taskName, duration);
```

### Rule: Appropriate Log Levels

```java
// ✅ GOOD - Level usage
logger.trace("Entering method with param: {}", param);  // Very detailed
logger.debug("Task state: {}", state);                  // Debug info
logger.info("Test started with {} workers", count);     // Important events
logger.warn("Worker {} is slow", workerId);             // Warnings
logger.error("Failed to execute task", exception);      // Errors
```

### Rule: No Logging in Hot Path

```java
// ❌ FORBIDDEN - Debug log per iteration
@Override
public TaskResult execute() {
    logger.debug("Executing iteration");  // Called millions of times!
    // ...
}

// ✅ REQUIRED - Log at higher level or sample
private final AtomicLong counter = new AtomicLong();

@Override
public TaskResult execute() {
    long iteration = counter.incrementAndGet();
    if (iteration % 1000 == 0) {  // Log every 1000th
        logger.debug("Completed {} iterations", iteration);
    }
    // ...
}
```

---

## 10. Resource Management Patterns

### Rule: Try-with-Resources

```java
// ❌ FORBIDDEN
Connection conn = dataSource.getConnection();
PreparedStatement stmt = conn.prepareStatement(sql);
ResultSet rs = stmt.executeQuery();
// ... use rs
rs.close();
stmt.close();
conn.close();

// ✅ REQUIRED
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql);
     ResultSet rs = stmt.executeQuery()) {
    // ... use rs
}  // Auto-closed in reverse order
```

### Rule: Implement AutoCloseable

```java
// ✅ REQUIRED for resource-holding classes
public class TaskExecutor implements AutoCloseable {
    private final ExecutorService executor;
    
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## 11. Documentation Patterns

### Rule: Public API Must Have JavaDoc

```java
// ❌ FORBIDDEN - No JavaDoc on public API
public interface Task {
    TaskResult execute() throws Exception;
}

// ✅ REQUIRED
/**
 * Defines a load test task to be executed repeatedly.
 * 
 * <p>Implementations must be thread-safe if reused across executions.
 * The framework calls {@link #setup()} once before the first execution,
 * {@link #execute()} for each iteration, and {@link #cleanup()} once
 * after all executions complete.
 * 
 * <p>Example:
 * <pre>{@code
 * public class MyTask implements Task {
 *     public TaskResult execute() {
 *         // Your test logic
 *         return TaskResult.success();
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
     * Executes one iteration of the load test.
     * 
     * @return the result of the execution
     * @throws Exception if execution fails
     */
    TaskResult execute() throws Exception;
}
```

### Rule: Package Documentation

Each package must have `package-info.java`:

```java
/**
 * Core execution engine for VajraPulse load testing framework.
 * 
 * <p>This package contains the main orchestration components:
 * <ul>
 *   <li>{@link com.vajrapulse.core.engine.ExecutionEngine} - Main coordinator
 *   <li>{@link com.vajrapulse.core.engine.TaskExecutor} - Task wrapper with metrics
 *   <li>{@link com.vajrapulse.core.engine.RateController} - TPS control
 * </ul>
 * 
 * @see com.vajrapulse.api
 */
package com.vajrapulse.core.engine;
```

---

## 12. Gradle Build Patterns

### Rule: Use Toolchain

```gradle
// ❌ FORBIDDEN
sourceCompatibility = '21'
targetCompatibility = '21'

// ✅ REQUIRED
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

### Rule: Explicit Dependency Versions

```gradle
// ❌ FORBIDDEN - Version ranges
dependencies {
    implementation 'io.micrometer:micrometer-core:1.+'
}

// ✅ REQUIRED - Exact versions
dependencies {
    implementation 'io.micrometer:micrometer-core:1.12.0'
}
```

---

## Enforcement

### Pre-commit Checks

```bash
#!/bin/bash
# .git/hooks/pre-commit

# Check API has zero dependencies
echo "Checking vajrapulse-api dependencies..."
API_DEPS=$(./gradlew :vajrapulse-api:dependencies --configuration runtimeClasspath | grep -v "No dependencies")
if [ -n "$API_DEPS" ]; then
    echo "ERROR: vajrapulse-api must have zero runtime dependencies"
    exit 1
fi

# Run tests
./gradlew test
if [ $? -ne 0 ]; then
    echo "ERROR: Tests failed"
    exit 1
fi

# Check code formatting
./gradlew spotlessCheck
if [ $? -ne 0 ]; then
    echo "ERROR: Code formatting issues found. Run ./gradlew spotlessApply"
    exit 1
fi

echo "All checks passed!"
```

### CI/CD Pipeline

```yaml
# .github/workflows/quality.yml
name: Code Quality

on: [push, pull_request]

jobs:
  quality-checks:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
      
      - name: Validate Dependencies
        run: |
          ./gradlew :vajrapulse-api:dependencies --configuration runtimeClasspath | grep "No dependencies"
      
      - name: Run Tests
        run: ./gradlew test
      
      - name: Check Coverage
        run: ./gradlew jacocoTestCoverageVerification
      
      - name: Build Fat JAR
        run: ./gradlew :vajrapulse-worker:shadowJar
      
      - name: Check JAR Size
        run: |
          SIZE=$(stat -f%z build/libs/vajrapulse-worker-*-all.jar)
          if [ $SIZE -gt 2000000 ]; then
            echo "ERROR: Fat JAR exceeds 2MB limit"
            exit 1
          fi
```

---

## Quick Reference Checklist

Before committing code, verify:

- [ ] No dependencies added to vajrapulse-api
- [ ] Micrometer used for all metrics (not direct HdrHistogram)
- [ ] No lambdas in hot paths
- [ ] No synchronized blocks with virtual threads
- [ ] Records used for data classes
- [ ] Sealed interfaces for type hierarchies
- [ ] Pattern matching in switch statements
- [ ] Given-when-then in Spock tests
- [ ] JavaDoc on all public APIs
- [ ] Try-with-resources for closeable resources
- [ ] Parameterized logging (no string concatenation)
- [ ] Pre-sized collections when size known
- [ ] Appropriate thread type (@VirtualThreads / @PlatformThreads)
- [ ] All tests pass
- [ ] No new compiler warnings
- [ ] unit test case coverage > 90%
