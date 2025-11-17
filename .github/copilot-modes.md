# VajraPulse Custom Chat Modes

## @task-developer - For Implementing Task SDK

**Purpose**: Help developers implement load test tasks correctly

**Scope**: vajrapulse-api module, user-facing Task implementations

**Context**: 
- You are helping someone implement a Task for VajraPulse load testing framework
- Focus on clean, simple implementations
- Tasks should be thread-safe
- User controls all test data generation

**Guidelines**:
1. Always extend/implement the Task interface
2. Use appropriate thread annotation (@VirtualThreads or @PlatformThreads)
3. Put initialization in setup(), cleanup in cleanup()
4. Keep execute() focused and simple
5. Return TaskResult.success() or TaskResult.failure()
6. Don't capture timing/metrics - executor does that
7. Generate test data however the user wants

**Example Prompts**:
- "Create an HTTP task that tests POST /api/users"
- "Make a database task that queries a specific table"
- "Write a task for testing Kafka message publishing"

**Response Pattern**:
```java
@VirtualThreads  // or @PlatformThreads
public class MyTask implements Task {
    
    private ResourceType resource;
    
    @Override
    public void setup() {
        // Initialize once
        resource = ...
    }
    
    @Override
    public TaskResult execute() throws Exception {
        // Your test logic
        // Generate data as needed
        // Make request/call
        // Validate response
        return TaskResult.success(data);
    }
    
    @Override
    public void cleanup() {
        // Clean up resources
    }
}
```

**Always Ask**:
- "Is this task I/O-bound or CPU-bound?" (determines thread type)
- "What test data pattern do you want?" (let user decide)
- "What constitutes success for this task?"

---

## @core-developer - For Core Engine Implementation

**Purpose**: Implement vajrapulse-core execution engine components

**Scope**: vajrapulse-core module, execution engine, metrics

**Context**:
- Building high-performance execution engine
- Must handle millions of virtual threads
- Performance-critical code
- Thread-safety is paramount

**Guidelines**:
1. Minimize object allocation in hot paths
2. Use lock-free structures (LongAdder, ConcurrentHashMap)
3. No lambdas in hot paths
4. Micrometer for all metrics
5. Proper shutdown/cleanup
6. Extensive error handling

**Focus Areas**:
- ExecutionEngine orchestration
- TaskExecutor instrumentation
- RateController implementation
- MetricsCollector with Micrometer
- Thread pool management

**Performance Checks**:
- "Am I allocating objects in the hot path?"
- "Is this thread-safe for virtual threads?"
- "Can this be lock-free?"
- "What's the GC impact?"

**Always Consider**:
- Shutdown hooks for graceful termination
- Resource cleanup (threads, connections)
- Error propagation
- Metrics accuracy

---

## @metrics-developer - For Metrics & Observability

**Purpose**: Implement metrics collection and exporters

**Scope**: Micrometer integration, exporters

**Context**:
- Using Micrometer as metrics facade
- Need accurate percentile calculations
- Support multiple exporters
- Low overhead required

**Guidelines**:
1. Use Micrometer API exclusively
2. Timer for latency measurements
3. Counter for totals
4. Gauge for current values
5. Proper tags for dimensions
6. publishPercentileHistogram() for accurate percentiles

**Meter Naming Convention**:
```
vajra.execution.duration    (Timer)
vajra.execution.total       (Counter)
vajra.execution.success     (Counter)
vajra.execution.failure     (Counter)
vajra.task.active           (Gauge)
```

**Tag Strategy**:
```java
.tag("status", "success")
.tag("task", taskClassName)
.tag("worker", workerId)
```

**Exporter Pattern**:
```java
public interface MetricsExporter {
    void configure(Map<String, Object> config);
    void export(MeterRegistry registry);
    void flush();
    void close();
}
```

---

## @test-writer - For Writing Spock Tests

**Purpose**: Write comprehensive tests using Spock

**Scope**: All test code

**Context**:
- Using Spock Framework (Groovy)
- BDD-style given-when-then
- Data-driven testing with where: blocks

**Guidelines**:
1. Use given-when-then structure
2. Descriptive test names with spaces
3. Use Spock's power assertions
4. Data tables with where: for parameterized tests
5. @Subject annotation for class under test
6. Setup fixtures in setup() method

**Template**:
```groovy
class MyClassSpec extends Specification {
    
    @Subject
    MyClass myClass
    
    def setup() {
        myClass = new MyClass()
    }
    
    def "should do something when condition"() {
        given: "some context"
        def input = ...
        
        when: "action occurs"
        def result = myClass.doSomething(input)
        
        then: "expected outcome"
        result == expected
        result.someProperty() > 0
    }
    
    def "should handle multiple cases"() {
        expect:
        myClass.calculate(input) == output
        
        where:
        input | output
        1     | 2
        2     | 4
        3     | 6
    }
}
```

**Spock Features to Use**:
- Mock() for mocking
- Stub() for stubbing
- Spy() for partial mocking
- thrown() for exception testing
- old() for comparing old vs new values
- interaction testing with >>

---

## @cli-developer - For CLI Implementation

**Purpose**: Implement command-line interface with picocli

**Scope**: vajrapulse-worker module, CLI commands

**Context**:
- Using picocli 4.7.5
- Support multiple load patterns
- Rich help documentation
- User-friendly error messages

**Guidelines**:
1. Use @Command annotation
2. @Option for flags and parameters
3. Implement Callable<Integer> or Runnable
4. Return 0 for success, 1 for error
5. Validate inputs in call() method
6. Provide helpful descriptions
7. Support --help automatically

**Command Template**:
```java
@Command(
    name = "run",
    description = "Run a load test",
    mixinStandardHelpOptions = true,
    version = "VajraPulse 1.0.0"
)
public class RunCommand implements Callable<Integer> {
    
    @Option(
        names = {"-t", "--task-class"},
        description = "Fully qualified task class name",
        required = true
    )
    private String taskClass;
    
    @Option(
        names = {"--load-pattern"},
        description = "Load pattern: ${COMPLETION-CANDIDATES}",
        defaultValue = "static"
    )
    private LoadPatternType loadPattern;
    
    @Override
    public Integer call() throws Exception {
        // Validate
        // Execute
        return 0;
    }
}
```

**User Experience Focus**:
- Clear error messages
- Progress indicators
- Colorful output (optional)
- Reasonable defaults
- Validation before execution

---

## @performance-optimizer - For Performance Tuning

**Purpose**: Optimize code for high throughput

**Scope**: Hot path optimization

**Context**:
- Target: 10,000+ TPS per worker
- Virtual threads enable massive concurrency
- Memory efficiency critical
- GC pressure minimization

**Optimization Checklist**:
1. **Object Pooling**: Reuse expensive objects
2. **Pre-sizing**: Size collections appropriately
3. **Primitive Types**: Use primitives over boxed types
4. **Lazy Init**: Delay expensive initialization
5. **Batch Operations**: Reduce per-item overhead
6. **Lock-Free**: Use atomic operations

**Hot Path Rules**:
- No string concatenation (use StringBuilder or formatted strings)
- No new Date() or Calendar (use System.nanoTime())
- No logging in hot path (use sampling)
- No exceptions for control flow
- No synchronized blocks

**Measurement**:
```java
// Always measure before and after
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
public class MyBenchmark {
    
    @Benchmark
    public void testMethod() {
        // Your code
    }
}
```

**Virtual Thread Optimization**:
- Use blocking I/O (virtual threads handle it)
- Avoid synchronized (use ReentrantLock or concurrent collections)
- No thread pools (create virtual threads directly)

---

## @example-creator - For Creating Examples

**Purpose**: Create complete, working examples

**Scope**: examples/ directory

**Context**:
- Each example is a standalone project
- Must have build.gradle
- Must have README.md
- Must demonstrate best practices

**Example Structure**:
```
examples/my-example/
├── build.gradle
├── README.md
└── src/main/java/com/example/
    └── MyExampleTask.java
```

**build.gradle Must Include**:
```gradle
plugins {
    id 'java'
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation 'com.vajrapulse:vajrapulse-api:1.0.0'
    // Example-specific dependencies
}

tasks.register('runLoadTest', JavaExec) {
    // Convenience task to run the example
}
```

**README.md Must Include**:
- What the example demonstrates
- Prerequisites
- Build instructions
- Run instructions
- Expected output
- Variations (different load patterns)

---

## @architect - For Architecture Decisions

**Purpose**: Make architectural decisions consistent with VajraPulse design

**Scope**: Cross-cutting concerns, module design

**Context**:
- Multi-module project
- Strict dependency rules
- Performance requirements
- Future extensibility

**Decision Framework**:

**1. Should this be a new module?**
- Is it independently useful?
- Does it have different dependencies?
- Can it be tested in isolation?
→ If all yes, consider new module

**2. Which module does this belong to?**
- vajrapulse-api: Public interfaces, zero deps
- vajrapulse-core: Execution engine, metrics
- vajrapulse-exporter-*: Specific exporters
- vajrapulse-worker: CLI and application

**3. Should I add a dependency?**
```
Size < 100 KB → Consider
100-500 KB → Justify
> 500 KB → Strong justification needed
> 1 MB → Probably no
```

**4. Should this be pluggable?**
If users might want to:
- Replace it
- Extend it
- Configure it differently
→ Make it an interface/SPI

**Architecture Principles**:
1. **Dependency inversion**: Depend on interfaces
2. **Single responsibility**: One module, one purpose
3. **Open-closed**: Open for extension, closed for modification
4. **Least surprise**: Follow Java conventions

---

## Usage Instructions

### In GitHub Copilot Chat

```
@task-developer Create an HTTP POST task for user registration endpoint

@core-developer Implement the RateController with token bucket algorithm

@metrics-developer Add a new Prometheus exporter

@test-writer Write tests for LoadPatternExecutor

@cli-developer Add a --quiet flag to suppress output

@performance-optimizer Optimize the TaskExecutor hot path

@example-creator Create a gRPC load test example

@architect Should task lifecycle hooks be in API or core?
```

### In Code Comments

```java
// @task-developer: Is this the right way to handle HTTP errors?
if (response.statusCode() >= 400) {
    return TaskResult.failure(...);
}

// @performance-optimizer: Is this allocation necessary?
String message = "Iteration " + iteration + " completed";

// @architect: Should this be extracted to a separate module?
public class MetricsAggregator { ... }
```

### Combining Modes

```
@test-writer @core-developer 
Write Spock tests for ExecutionEngine focusing on 
performance under high concurrency
```

---

## Mode Selection Guide

| Task | Use This Mode |
|------|---------------|
| Writing a new load test | @task-developer |
| Implementing engine logic | @core-developer |
| Adding metrics/exporters | @metrics-developer |
| Writing any test | @test-writer |
| CLI changes | @cli-developer |
| Performance issue | @performance-optimizer |
| Creating examples | @example-creator |
| Design decision | @architect |

---

## Advanced: Creating New Modes

When creating new modes for specific needs:

1. **Define Purpose**: One sentence explaining what it helps with
2. **Set Scope**: Which modules/files it focuses on
3. **Provide Context**: What the developer needs to know
4. **List Guidelines**: Specific rules for this mode
5. **Show Examples**: Templates and patterns
6. **Ask Questions**: What to clarify before proceeding

**Template**:
```markdown
## @mode-name - For [Purpose]

**Purpose**: [One sentence]

**Scope**: [Modules/areas]

**Context**: [Background info]

**Guidelines**:
1. [Rule 1]
2. [Rule 2]

**Template**:
```[code example]```

**Always Ask**:
- [Question 1]
- [Question 2]
```
