# GitHub Copilot Instructions for VajraPulse Load Testing Framework

## Project Overview

VajraPulse is a Java 21-based distributed load testing framework leveraging virtual threads for high-concurrency testing with minimal resource overhead. The project follows strict architectural principles for maintainability, performance, and extensibility.

## Core Principles

### 1. Language & Version
- **Java 21** is MANDATORY - use all modern features
- Use **virtual threads** for I/O-bound tasks
- Use **platform threads** for CPU-bound tasks  
- Leverage **records**, **sealed interfaces**, **pattern matching**
- **Never** suggest Java 8/11/17 syntax when Java 21 features are available

### 2. Build System
- **Gradle 9** with Kotlin DSL preferred, Groovy acceptable
- Use `toolchain` for Java version management
- Enable configuration cache, parallel builds
- Multi-module project structure

### 3. Dependency Management
- **MINIMIZE dependencies** - every dependency must be justified
- **Micrometer-core** for metrics (no direct HdrHistogram)
- **SLF4J API** for logging (simple impl for runtime)
- **Picocli** for CLI parsing
- **Spock** for all tests (no JUnit unless absolutely necessary)
- **NO** Spring, Guava, Commons-Lang, or heavy frameworks

### 4. Code Style

#### Prefer Records over Classes
```java
// ✅ GOOD
public record ExecutionMetrics(
    long startNanos,
    long endNanos,
    TaskResult result,
    long iteration
) {
    public long durationNanos() {
        return endNanos - startNanos;
    }
}

// ❌ BAD
public class ExecutionMetrics {
    private final long startNanos;
    private final long endNanos;
    // ... getters, equals, hashCode, toString
}
```

#### Use Sealed Interfaces for Type Safety
```java
// ✅ GOOD
public sealed interface TaskResult 
    permits TaskResult.Success, TaskResult.Failure {
    record Success(Object data) implements TaskResult {}
    record Failure(Throwable error) implements TaskResult {}
}

// ❌ BAD - Open hierarchy
public interface TaskResult {
    class Success implements TaskResult { ... }
    class Failure implements TaskResult { ... }
}
```

#### Pattern Matching in Switch
```java
// ✅ GOOD
switch (taskResult) {
    case Success(var data) -> recordSuccess(data);
    case Failure(var error) -> recordFailure(error);
}

// ❌ BAD - instanceof chains
if (taskResult instanceof Success) {
    recordSuccess(((Success) taskResult).data());
} else if (taskResult instanceof Failure) {
    recordFailure(((Failure) taskResult).error());
}
```

#### Minimize Lambda Usage
```java
// ✅ GOOD - Concrete class (hot path)
executor.submit(new TaskExecutionCallable(task, iteration));

// ❌ BAD - Lambda in hot path
executor.submit(() -> task.execute());

// ✅ GOOD - Method reference
list.sort(Comparator.naturalOrder());

// ❌ BAD - Lambda comparator
list.sort((a, b) -> a.compareTo(b));
```

### 5. Virtual Threads Guidelines

#### When to Use Virtual Threads
```java
// ✅ GOOD - I/O-bound tasks
@VirtualThreads
public class HttpLoadTest implements Task {
    private HttpClient client = HttpClient.newBuilder()
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .build();
}

// ✅ GOOD - Database operations
@VirtualThreads
public class DatabaseTest implements Task {
    // JDBC calls with virtual threads
}
```

#### When to Use Platform Threads
```java
// ✅ GOOD - CPU-bound tasks
@PlatformThreads(poolSize = 8)
public class EncryptionTest implements Task {
    // Heavy computation
}

// ✅ GOOD - Parallel processing
@PlatformThreads(poolSize = -1)  // Uses availableProcessors()
public class DataProcessingTest implements Task {
    // CPU-intensive work
}
```

#### Never Block Virtual Threads
```java
// ❌ NEVER - Synchronized blocks in virtual threads
synchronized (lock) {
    // This pins the carrier thread!
}

// ✅ GOOD - Use ReentrantLock or concurrent utilities
private final Lock lock = new ReentrantLock();
lock.lock();
try {
    // ...
} finally {
    lock.unlock();
}
```

### 6. Testing with Spock

#### Use Given-When-Then
```groovy
// ✅ GOOD - Clear BDD style
def "should capture success metrics"() {
    given: "a task that succeeds"
    Task task = new Task() {
        @Override
        TaskResult execute() {
            return TaskResult.success("data")
        }
    }
    
    when: "executing the task"
    def metrics = executor.executeWithMetrics(0)
    
    then: "metrics show success"
    metrics.isSuccess()
    metrics.durationNanos() > 0
}

// ❌ BAD - JUnit style
@Test
void testSuccessMetrics() {
    Task task = ...
    ExecutionMetrics metrics = executor.executeWithMetrics(0);
    assertTrue(metrics.isSuccess());
}
```

#### Use Spock's Power Assertions
```groovy
// ✅ GOOD
then:
snapshot.totalExecutions() == 10
snapshot.successCount() == 10

// Shows detailed output on failure:
// Condition not satisfied:
// snapshot.totalExecutions() == 10
// |        |                  |
// |        5                  false
// AggregatedMetrics(...)
```

### 7. Micrometer Metrics Pattern

#### Always Use Micrometer API
```java
// ✅ GOOD - Use Micrometer Timer
private final Timer successTimer = Timer.builder("vajrapulse.execution.duration")
    .tag("status", "success")
    .publishPercentileHistogram()
    .register(registry);

successTimer.record(duration, TimeUnit.NANOSECONDS);

// ❌ BAD - Direct HdrHistogram
private final Histogram histogram = new Histogram(...);
histogram.recordValue(duration);
```

#### Proper Meter Naming
```java
// ✅ GOOD - Lowercase with dots
"vajrapulse.execution.duration"
"vajrapulse.execution.total"
"vajrapulse.task.success"

// ❌ BAD - Camel case or underscores
"VajraPulseExecutionDuration"
"vajra_execution_duration"
```

### 8. Error Handling

#### Task Execution
```java
// ✅ GOOD - Let executor handle exceptions
@Override
public TaskResult execute() throws Exception {
    HttpResponse<String> response = client.send(request, ...);
    if (response.statusCode() == 200) {
        return TaskResult.success(response.body());
    } else {
        return TaskResult.failure(
            new RuntimeException("HTTP " + response.statusCode())
        );
    }
}

// ❌ BAD - Catching everything inside task
@Override
public TaskResult execute() {
    try {
        // ... lots of code
        return TaskResult.success();
    } catch (Exception e) {
        return TaskResult.failure(e);
    }
}
```

#### Executor Level
```java
// ✅ GOOD - Executor wraps and catches
public ExecutionMetrics executeWithMetrics(long iteration) {
    long start = System.nanoTime();
    TaskResult result;
    
    try {
        result = task.execute();
    } catch (Exception e) {
        result = TaskResult.failure(e);
    }
    
    long end = System.nanoTime();
    return new ExecutionMetrics(start, end, result, iteration);
}
```

### 9. Load Patterns

#### Use the LoadPattern Interface
```java
// ✅ GOOD - Implement LoadPattern
public class CustomLoad implements LoadPattern {
    @Override
    public double calculateTps(long elapsedMillis) {
        // Custom logic
    }
    
    @Override
    public Duration getDuration() {
        return totalDuration;
    }
}

// ❌ BAD - Hardcoded TPS
double tps = 100.0; // Static forever
```

#### Built-in Patterns
```java
// Static: constant TPS
new StaticLoad(100.0, Duration.ofMinutes(5))

// Ramp-up: 0 to max
new RampUpLoad(200.0, Duration.ofSeconds(30))

// Ramp then sustain: 0 to max, then hold
new RampUpToMaxLoad(200.0, Duration.ofSeconds(30), Duration.ofMinutes(5))
```

### 10. Module Boundaries

#### vajrapulse-api
- **ZERO runtime dependencies**
- Only interfaces and annotations
- No implementation logic
- No external libraries

```java
// ✅ GOOD - Pure interface
public interface Task {
    TaskResult execute() throws Exception;
}

// ❌ BAD - Implementation in API
public interface Task {
    default TaskResult execute() {
        // Implementation here - NO!
    }
}
```

#### vajrapulse-core
- Depends ONLY on: vajrapulse-api, micrometer-core, slf4j-api
- No CLI, no exporters, no application logic
- Pure execution engine

#### vajrapulse-worker
- Application layer
- Can depend on all modules
- Contains CLI and main entry point

### 11. Naming Conventions

#### Classes
```java
// ✅ GOOD
TaskExecutor          // Noun, clear purpose
MetricsCollector      // Noun, describes what it is
RateController        // Noun, controls rate

// ❌ BAD
TaskExecutorImpl      // Unnecessary 'Impl' suffix
AbstractTaskExecutor  // Don't prefix with 'Abstract'
ITask                 // Don't prefix interfaces with 'I'
```

#### Methods
```java
// ✅ GOOD
public void record(ExecutionMetrics metrics)
public AggregatedMetrics snapshot()
public double calculateTps(long elapsed)

// ❌ BAD
public void doRecord(...)           // Unnecessary 'do' prefix
public AggregatedMetrics getSnapshot()  // Use 'snapshot' not 'get'
public double getTps(...)           // Action, not getter
```

#### Constants
```java
// ✅ GOOD
private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
private static final int MAX_RETRIES = 3;

// ❌ BAD
private static final Duration defaultTimeout = ...  // Not uppercase
private static final int max_retries = 3;          // Underscores only for constants
```

### 12. Documentation

#### JavaDoc Requirements
```java
// ✅ GOOD - Complete JavaDoc
/**
 * Executes a task with automatic instrumentation and metrics collection.
 * 
 * <p>This method wraps the task execution, capturing timing and result
 * information automatically. The executor handles all exceptions and
 * converts them to TaskResult.Failure.
 * 
 * @param iteration the iteration number (0-based)
 * @return execution metrics including timing and result
 * @throws IllegalStateException if task not initialized
 */
public ExecutionMetrics executeWithMetrics(long iteration)

// ❌ BAD - Minimal or missing
// Executes task
public ExecutionMetrics executeWithMetrics(long iteration)
```

#### Package-level Documentation
```java
// ✅ GOOD - package-info.java in each package
/**
 * Core execution engine for VajraPulse load testing framework.
 * 
 * <p>This package contains the main execution components:
 * <ul>
 *   <li>{@link ExecutionEngine} - Main orchestrator
 *   <li>{@link TaskExecutor} - Instrumented task wrapper
 *   <li>{@link RateController} - TPS control
 * </ul>
 * 
 * @see com.vajrapulse.api for public API
 */
package com.vajrapulse.core.engine;
```

### 13. Gradle Configuration

#### Use Toolchain
```gradle
// ✅ GOOD
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// ❌ BAD
sourceCompatibility = '21'
targetCompatibility = '21'
```

#### Enable Optimization
```gradle
// ✅ GOOD
tasks.withType(JavaCompile) {
    options.encoding = 'UTF-8'
    options.release = 21
    options.compilerArgs += ['-parameters']
}

// Enable Gradle optimizations
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
```

### 14. Common Mistakes to Avoid

#### ❌ Don't Use ThreadLocal with Virtual Threads
```java
// ❌ BAD - ThreadLocal with millions of virtual threads
private static final ThreadLocal<Context> context = new ThreadLocal<>();

// ✅ GOOD - Use ScopedValue (Java 21)
private static final ScopedValue<Context> context = ScopedValue.newInstance();
```

#### ❌ Don't Use Heavy Synchronization
```java
// ❌ BAD - Synchronized in hot path
public synchronized void record(ExecutionMetrics metrics) {
    // Blocks all virtual threads on same carrier
}

// ✅ GOOD - Lock-free or concurrent structures
private final LongAdder totalCount = new LongAdder();
public void record(ExecutionMetrics metrics) {
    totalCount.increment();
}
```

#### ❌ Don't Create Excessive Objects in Hot Path
```java
// ❌ BAD - New object per execution
public void record(ExecutionMetrics metrics) {
    String message = "Execution: " + metrics.iteration();
    logger.debug(message);
}

// ✅ GOOD - Parameterized logging
public void record(ExecutionMetrics metrics) {
    logger.debug("Execution: {}", metrics.iteration());
}
```

#### ❌ Don't Use Deprecated APIs
```java
// ❌ BAD - Deprecated Micrometer API
double p95 = timer.percentile(0.95, TimeUnit.MILLISECONDS);
double p99 = timer.percentile(0.99, TimeUnit.MILLISECONDS);

// ✅ GOOD - Current Micrometer API
HistogramSnapshot snapshot = timer.takeSnapshot();
double p95 = getPercentileValue(snapshot, 0.95);
double p99 = getPercentileValue(snapshot, 0.99);

// Helper method
private double getPercentileValue(HistogramSnapshot snapshot, double percentile) {
    for (var value : snapshot.percentileValues()) {
        if (value.percentile() == percentile) {
            return value.value(TimeUnit.MILLISECONDS);
        }
    }
    return 0.0;
}
```

### 15. Performance Considerations

#### Memory Allocation
```java
// ✅ GOOD - Reuse objects
private final ByteBuffer buffer = ByteBuffer.allocate(1024);

// ❌ BAD - Allocate in loop
for (int i = 0; i < iterations; i++) {
    ByteBuffer buffer = ByteBuffer.allocate(1024);
}
```

#### Collection Sizing
```java
// ✅ GOOD - Pre-size collections
List<Result> results = new ArrayList<>(expectedSize);
Map<String, Value> map = new HashMap<>(expectedSize);

// ❌ BAD - Default size when size is known
List<Result> results = new ArrayList<>(); // Resizes multiple times
```

## Decision Tree for Common Scenarios

### "Should I add a dependency?"
1. Is it absolutely necessary? → No? **Don't add it**
2. Is there a lighter alternative? → Yes? **Use lighter one**
3. Is it industry standard? → No? **Reconsider**
4. Size > 500 KB? → **Get approval first**

### "Should I use virtual threads or platform threads?"
1. Task does I/O (network, disk, database)? → **Virtual threads**
2. Task does CPU work (crypto, compression)? → **Platform threads**
3. Task blocks on synchronized? → **Platform threads** (or refactor)
4. Unsure? → **Virtual threads** (default)

### "Should I use a lambda?"
1. Hot path (called per iteration)? → **No, use concrete class**
2. Setup/cleanup (called once)? → **Yes, lambda is fine**
3. Stream operation on small collection? → **Yes, lambda is fine**
4. Needs to be serializable? → **No, use concrete class**

### "Should I use Micrometer or custom metrics?"
1. Need percentiles? → **Micrometer Timer**
2. Simple counter? → **Micrometer Counter**
3. Track current value? → **Micrometer Gauge**
4. Custom aggregation? → **Consider extending Micrometer**

### "How to avoid compiler warnings?"
1. **Build with warnings enabled**: `./gradlew build` (already configured with `-Xlint:deprecation`)
2. **Check API docs** before using new APIs - look for `@Deprecated` annotations
3. **If deprecated API is found**:
   - Check JavaDoc for replacement API
   - Example: `Timer.percentile()` → use `Timer.takeSnapshot().percentileValues()`
   - Update code immediately, don't defer
4. **Common deprecations to watch**:
   - Micrometer: `percentile()`, old builder patterns
   - JDK: `Thread.stop()`, `finalize()`, older date/time APIs
5. **Zero tolerance**: Fix warnings before committing

## Module-Specific Guidelines

### vajrapulse-api Module
- Pure interfaces and records only
- Zero dependencies (check with `./gradlew :vajrapulse-api:dependencies`)
- No implementation logic
- Extensive JavaDoc (this is user-facing API)
- All classes `public` and `final` or `sealed`

### vajrapulse-core Module
- No main methods or CLI code
- All logic must be testable without CLI
- Use dependency injection patterns
- Prefer constructor injection over field injection
- All executors must be closeable/shutdown-able

### vajrapulse-worker Module
- Only module with `main` method
- CLI parsing with picocli
- Thin orchestration layer
- Delegates to vajrapulse-core for all logic
- No business logic here

## Code Review Checklist

Before submitting code, verify:

- [ ] Uses Java 21 features (records, sealed types, pattern matching)
- [ ] No unnecessary dependencies added
- [ ] Virtual/platform threads used appropriately
- [ ] No lambdas in hot paths
- [ ] Micrometer used for all metrics
- [ ] Spock tests with given-when-then
- [ ] JavaDoc on public APIs
- [ ] No synchronized blocks with virtual threads
- [ ] Proper error handling (try-catch at right level)
- [ ] Performance considered (object allocation, sizing)
- [ ] Module boundaries respected
- [ ] Gradle configuration uses toolchain
- [ ] All tests pass with `./gradlew test`
- [ ] **No compiler warnings** - build with `-Xlint:deprecation` enabled
- [ ] No deprecated API usage - check Micrometer/JDK deprecations
- [ ] Remove all unused imports
- [ ] Make sure to check for the latest versions against mvnrepository.com or maven central before including as a dependency on the build.gradle


## Questions to Ask Copilot

When stuck, ask:
- "Is this dependency necessary for vajra?"
- "Should this task use virtual or platform threads?"
- "Is this lambda usage appropriate for vajra?"
- "Does this follow vajra's error handling pattern?"
- "How should I test this with Spock?"

## Getting Started Commands

```bash
# Build all modules
./gradlew clean build

# Run tests
./gradlew test

# Build fat JAR
./gradlew :vajrapulse-worker:shadowJar

# Check dependencies
./gradlew :vajrapulse-api:dependencies
./gradlew :vajrapulse-core:dependencies

# Run example
cd examples/http-load-test
./gradlew runLoadTest
```

---

**Remember**: VajraPulse prioritizes simplicity, performance, and minimal dependencies. When in doubt, choose the simpler, more explicit approach.
**Remember**: Any documentation that you create should be persisted in documents folder.
**Remember**: Any new work you start , make sure to branch out and work towards a successful PR creation and merging. 