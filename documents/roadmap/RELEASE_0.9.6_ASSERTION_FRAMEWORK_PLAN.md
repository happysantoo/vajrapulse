# Assertion Framework - Detailed Plan & Analysis

**Release**: Post-0.9.6 (0.9.7 or 0.10.0)  
**Priority**: MEDIUM-HIGH  
**Source**: CRITICAL_IMPROVEMENTS.md #4  
**Effort**: 5-7 days

## Why Do We Need an Assertion Framework?

### Current Problem

Users must write custom validation logic in every task:

```java
@Override
public TaskResult execute(long iteration) throws Exception {
    HttpResponse<String> response = client.send(request, ...);
    
    // Custom validation - repetitive across tasks
    if (response.statusCode() != 200) {
        return TaskResult.failure(new RuntimeException("Expected 200, got " + response.statusCode()));
    }
    
    // More custom validation
    if (response.body().isEmpty()) {
        return TaskResult.failure(new RuntimeException("Empty response body"));
    }
    
    // Even more validation
    JsonNode json = objectMapper.readTree(response.body());
    if (!json.has("data")) {
        return TaskResult.failure(new RuntimeException("Missing 'data' field"));
    }
    
    return TaskResult.success(response.body());
}
```

### Problems with Current Approach

1. **Repetitive Code**: Same validation logic repeated across tasks
2. **No Standardization**: Each developer writes validation differently
3. **No SLO Support**: Hard to assert on latency percentiles
4. **No Reusability**: Can't share validation logic between tasks
5. **Poor Error Messages**: Custom error messages vary in quality
6. **No Composition**: Can't combine multiple validators easily

### Benefits of Assertion Framework

1. **Standardization**: Consistent validation patterns
2. **Reusability**: Share validators across tasks
3. **SLO Testing**: Built-in support for latency/error rate assertions
4. **Better Errors**: Rich, contextual error messages
5. **Composition**: Combine validators easily
6. **Testability**: Validators are testable in isolation
7. **Documentation**: Validators document expected behavior

## Open Source Alternatives Analysis

### Option 1: AssertJ (Recommended) ⭐⭐⭐

**What it is**: Fluent assertions library for Java

**Pros**:
- ✅ Mature, widely used (millions of downloads)
- ✅ Excellent error messages
- ✅ Fluent API (very readable)
- ✅ Extensible (custom assertions)
- ✅ Zero dependencies (for core)
- ✅ Active maintenance

**Cons**:
- ⚠️ Adds ~500KB to JAR size
- ⚠️ Designed for unit tests, not load tests
- ⚠️ May be overkill for simple validations

**Example**:
```java
import static org.assertj.core.api.Assertions.*;

// In task
if (assertThat(response.statusCode()).isEqualTo(200).isNotEqualTo(500)) {
    // ...
}
```

**Verdict**: Good option, but may be overkill. Consider lighter alternative.

---

### Option 2: Hamcrest ⭐⭐

**What it is**: Matcher library for Java

**Pros**:
- ✅ Mature, stable
- ✅ Extensible
- ✅ Good error messages
- ✅ Small footprint (~200KB)

**Cons**:
- ⚠️ Less fluent API than AssertJ
- ⚠️ Less modern (older API design)
- ⚠️ Less active development

**Example**:
```java
import static org.hamcrest.Matchers.*;

// In task
if (assertThat(response.statusCode(), is(equalTo(200)))) {
    // ...
}
```

**Verdict**: Viable option, but API is less modern.

---

### Option 3: Custom Lightweight Framework (Recommended) ⭐⭐⭐

**What it is**: Build our own assertion framework tailored for load testing

**Pros**:
- ✅ Zero dependencies
- ✅ Tailored for load testing use cases
- ✅ SLO-aware (latency percentiles, error rates)
- ✅ Integrates with TaskResult
- ✅ Small footprint
- ✅ Full control

**Cons**:
- ⚠️ We build and maintain it
- ⚠️ Less feature-rich than AssertJ initially

**Example**:
```java
// Custom framework
ResponseValidator validator = ResponseValidators.composite(
    new HttpStatusValidator(200, 201),
    new LatencyValidator(Duration.ofMillis(100), 0.95),
    new ErrorRateValidator(0.01)
);

TaskResult result = validator.validate(rawResult, metrics);
```

**Verdict**: Best option for VajraPulse. Tailored, lightweight, no dependencies.

---

### Option 4: JUnit Assertions ⭐

**What it is**: Built-in JUnit assertions

**Pros**:
- ✅ No dependencies (if using JUnit)
- ✅ Simple

**Cons**:
- ❌ Not designed for load testing
- ❌ Poor error messages
- ❌ No SLO support
- ❌ Not extensible

**Verdict**: Not suitable for load testing.

---

## Recommended Approach: Custom Lightweight Framework

### Rationale

1. **Zero Dependencies**: Aligns with VajraPulse philosophy
2. **Load Testing Focus**: Built for our specific use cases
3. **SLO Support**: Native support for latency/error rate assertions
4. **TaskResult Integration**: Seamless integration with existing API
5. **Small Footprint**: Minimal overhead
6. **Full Control**: Can evolve based on user needs

### Design

```java
// Core interface
public interface ResponseValidator {
    ValidationResult validate(TaskResult result, AggregatedMetrics metrics);
}

// Validation result
public record ValidationResult(
    boolean passed,
    String message,
    Throwable cause
) {
    public static ValidationResult pass() { ... }
    public static ValidationResult fail(String message) { ... }
    public static ValidationResult fail(String message, Throwable cause) { ... }
}

// Built-in validators
public class HttpStatusValidator implements ResponseValidator { ... }
public class LatencyValidator implements ResponseValidator { ... }
public class ErrorRateValidator implements ResponseValidator { ... }
public class JsonSchemaValidator implements ResponseValidator { ... }
public class ContentValidator implements ResponseValidator { ... }

// Composite validator
public class CompositeValidator implements ResponseValidator {
    private final List<ResponseValidator> validators;
    // ...
}

// Builder for easy composition
public class ResponseValidators {
    public static ResponseValidator composite(ResponseValidator... validators) { ... }
    public static ResponseValidator httpStatus(int... allowedStatuses) { ... }
    public static ResponseValidator latency(Duration maxLatency, double percentile) { ... }
    public static ResponseValidator errorRate(double maxErrorRate) { ... }
    public static ResponseValidator jsonSchema(String schema) { ... }
    public static ResponseValidator content(Predicate<String> predicate) { ... }
}
```

### Usage Examples

**Basic Usage**:
```java
@VirtualThreads
public class ApiTest implements TaskLifecycle {
    private final ResponseValidator validator = ResponseValidators.httpStatus(200, 201);
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        HttpResponse<String> response = client.send(request, ...);
        TaskResult rawResult = response.statusCode() == 200 
            ? TaskResult.success(response.body())
            : TaskResult.failure(new RuntimeException("HTTP " + response.statusCode()));
        
        return validator.validate(rawResult, metrics);
    }
}
```

**SLO-Based Validation**:
```java
@VirtualThreads
public class SloTest implements TaskLifecycle {
    private final ResponseValidator validator = ResponseValidators.composite(
        ResponseValidators.httpStatus(200),
        ResponseValidators.latency(Duration.ofMillis(100), 0.95), // P95 < 100ms
        ResponseValidators.errorRate(0.01) // < 1% error rate
    );
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        // ... make request ...
        return validator.validate(result, metrics);
    }
}
```

**JSON Schema Validation**:
```java
@VirtualThreads
public class JsonApiTest implements TaskLifecycle {
    private final ResponseValidator validator = ResponseValidators.composite(
        ResponseValidators.httpStatus(200),
        ResponseValidators.jsonSchema("""
            {
                "type": "object",
                "properties": {
                    "data": {"type": "array"},
                    "status": {"type": "string"}
                },
                "required": ["data", "status"]
            }
            """)
    );
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        // ... make request ...
        return validator.validate(result, metrics);
    }
}
```

**Custom Validator**:
```java
public class CustomValidator implements ResponseValidator {
    @Override
    public ValidationResult validate(TaskResult result, AggregatedMetrics metrics) {
        if (result instanceof TaskResult.Success success) {
            String body = (String) success.data();
            if (body.contains("error")) {
                return ValidationResult.fail("Response contains 'error'");
            }
            return ValidationResult.pass();
        }
        return ValidationResult.fail("Request failed");
    }
}
```

## Implementation Plan

### Phase 1: Core Framework (Day 1-2)

1. **Create Core Interfaces**
   - `ResponseValidator` interface
   - `ValidationResult` record
   - `ResponseValidators` builder class

2. **Basic Validators**
   - `HttpStatusValidator`
   - `ContentValidator` (simple predicate-based)

3. **Composite Validator**
   - `CompositeValidator` for combining validators
   - Short-circuit on first failure option

### Phase 2: SLO Validators (Day 3)

1. **Latency Validator**
   - Validate against percentile (P95, P99)
   - Support for max latency thresholds

2. **Error Rate Validator**
   - Validate overall error rate
   - Support for time-windowed error rates

3. **Throughput Validator**
   - Validate minimum TPS
   - Support for sustained throughput

### Phase 3: Advanced Validators (Day 4)

1. **JSON Schema Validator**
   - Integrate with JSON schema library (small, zero-dependency)
   - Validate response structure

2. **XML Validator**
   - Basic XML validation
   - XPath support (optional)

3. **Regex Validator**
   - Pattern matching on response content

### Phase 4: Integration & Examples (Day 5)

1. **Update Examples**
   - Add assertion framework to HTTP example
   - Create assertion framework example

2. **Documentation**
   - User guide
   - API documentation
   - Best practices

### Phase 5: Testing & Polish (Day 6-7)

1. **Testing**
   - Unit tests for all validators
   - Integration tests
   - Performance tests

2. **Polish**
   - Error message improvements
   - API refinements
   - Documentation updates

## Module Structure

```
vajrapulse-assertions/
├── src/main/java/com/vajrapulse/assertions/
│   ├── ResponseValidator.java
│   ├── ValidationResult.java
│   ├── ResponseValidators.java
│   ├── validators/
│   │   ├── HttpStatusValidator.java
│   │   ├── LatencyValidator.java
│   │   ├── ErrorRateValidator.java
│   │   ├── JsonSchemaValidator.java
│   │   ├── ContentValidator.java
│   │   └── CompositeValidator.java
│   └── exceptions/
│       └── ValidationException.java
└── src/test/groovy/...
```

## Dependencies

**Minimal Dependencies**:
- None! (Zero dependencies philosophy)

**Optional Dependencies** (for JSON Schema):
- Consider small JSON schema library if needed
- Or implement basic JSON validation ourselves

## Success Criteria

- [ ] Core framework implemented
- [ ] Basic validators work
- [ ] SLO validators work
- [ ] JSON schema validator works (if included)
- [ ] Composite validator works
- [ ] Examples updated
- [ ] Documentation complete
- [ ] Tests pass with ≥90% coverage
- [ ] Zero dependencies (or minimal if JSON schema needed)

## Comparison with Alternatives

| Feature | Custom Framework | AssertJ | Hamcrest |
|---------|------------------|---------|----------|
| Dependencies | 0 | 0 (core) | 0 |
| JAR Size | ~50KB | ~500KB | ~200KB |
| Load Testing Focus | ✅ Yes | ❌ No | ❌ No |
| SLO Support | ✅ Native | ❌ No | ❌ No |
| TaskResult Integration | ✅ Native | ❌ No | ❌ No |
| Extensibility | ✅ Yes | ✅ Yes | ✅ Yes |
| Error Messages | ✅ Good | ✅ Excellent | ✅ Good |
| Maintenance | We maintain | Community | Community |

## Recommendation

**Build a custom lightweight assertion framework** because:
1. Zero dependencies aligns with VajraPulse philosophy
2. Tailored for load testing use cases (SLO support)
3. Native TaskResult integration
4. Small footprint
5. Full control over evolution

We can always add AssertJ as an optional dependency later if users request it, but start with our own framework.

