# Release 0.9.7 Feature Suggestions

**Date**: 2025-01-XX  
**Status**: Planning  
**Based on**: Comprehensive documentation review and gap analysis

## Executive Summary

After reviewing all documentation (roadmap, gap analysis, critical improvements, user wishlist, and current state), here are prioritized feature suggestions for the next release. These features address critical gaps, user requests, and align with the path to 1.0.

---

## 🎯 High Priority Features (Must-Have for 0.9.7)

### 1. Client-Side Metrics Enhancement ⭐⭐⭐
**Source**: User Wishlist Item #1  
**Priority**: HIGH  
**Effort**: Medium (3-5 days)

**Problem**: Currently missing metrics on request processing, queuing, and client-side bottlenecks.

**Proposed Solution**:
- Add client-side connection pool metrics (active, idle, waiting connections)
- Track request queuing metrics (queue depth, wait time)
- Add timeout and backlog metrics
- Track client-side errors (connection refused, timeout, etc.)

**Implementation**:
```java
// New metrics in MetricsCollector
public record ClientMetrics(
    long activeConnections,
    long idleConnections,
    long waitingConnections,
    long queueDepth,
    long queueWaitTimeNanos,
    long connectionTimeouts,
    long requestTimeouts
) {}
```

**Benefits**:
- Helps identify service bottlenecks
- Distinguishes between server-side and client-side issues
- Critical for enterprise debugging

**Files to Modify**:
- `MetricsCollector.java` - Add client metrics tracking
- `AggregatedMetrics.java` - Include client metrics
- `ConsoleMetricsExporter.java` - Display client metrics
- Examples - Demonstrate client metrics usage

---

### 2. Enhanced Load Pattern: Trace Replay ⭐⭐⭐
**Source**: Architecture Documents (LOAD_PATTERNS.md, CRITICAL_IMPROVEMENTS.md)  
**Priority**: HIGH  
**Effort**: Medium (4-6 days)

**Problem**: No way to replay real traffic patterns from production logs.

**Proposed Solution**:
- `TraceReplayLoad` pattern that reads traffic timestamps from logs
- Support CSV/JSON input formats
- Replay traffic at original timestamps or scaled timestamps
- Handle missing data gracefully

**Implementation**:
```java
// New load pattern
public class TraceReplayLoad implements LoadPattern {
    public TraceReplayLoad(Path logFile, Duration replayDuration, double timeScale) {
        // Parse log file, extract timestamps, replay at scaled rate
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        // Return TPS based on replay schedule
    }
}
```

**Benefits**:
- Test with realistic traffic patterns
- Replay production incidents
- Validate system behavior under real conditions

**Files to Create**:
- `vajrapulse-api/src/main/java/com/vajrapulse/api/TraceReplayLoad.java`
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/TraceReplayLoadSpec.groovy`
- `examples/trace-replay-load-test/` - Example usage

---

### 3. Assertion Framework ⭐⭐⭐
**Source**: CRITICAL_IMPROVEMENTS.md #4  
**Priority**: HIGH  
**Effort**: Medium (5-7 days)

**Problem**: No built-in assertion library; users must write custom validation.

**Proposed Solution**:
- Create `vajrapulse-assertions` module
- Built-in validators for common scenarios
- SLO-based assertions (P95 < 100ms)
- Extensible validation framework

**Implementation**:
```java
// New assertions module
public interface ResponseValidator {
    ValidationResult validate(TaskResult result, AggregatedMetrics metrics);
}

// Built-in validators
public class HttpStatusValidator implements ResponseValidator { ... }
public class LatencyValidator implements ResponseValidator { ... }
public class ErrorRateValidator implements ResponseValidator { ... }
public class JsonSchemaValidator implements ResponseValidator { ... }

// Usage
@VirtualThreads
public class ApiTest implements TaskLifecycle {
    private final ResponseValidator validator = ResponseValidators.composite(
        new HttpStatusValidator(200, 201),
        new LatencyValidator(Duration.ofMillis(100), 0.95),
        new ErrorRateValidator(0.01) // 1% max error rate
    );
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        // ... make request ...
        return validator.validate(result, metrics);
    }
}
```

**Benefits**:
- Standardized validation patterns
- Reusable test components
- SLO-based testing
- Better test readability

**Files to Create**:
- `vajrapulse-assertions/` - New module
- `ResponseValidator.java` - Interface
- Built-in validators
- Tests and examples

---

### 4. Test Data Management ⭐⭐
**Source**: CRITICAL_IMPROVEMENTS.md #5  
**Priority**: MEDIUM-HIGH  
**Effort**: Medium (4-6 days)

**Problem**: No built-in data generation or CSV/JSON data file support.

**Proposed Solution**:
- Create `vajrapulse-data` module
- Random data generators (names, emails, UUIDs, etc.)
- CSV/JSON data file support
- Parameterization framework

**Implementation**:
```java
// Data generators
DataGenerator generator = DataGenerators.builder()
    .field("email", DataTypes.email())
    .field("name", DataTypes.name())
    .field("age", DataTypes.integer(18, 65))
    .field("uuid", DataTypes.uuid())
    .build();

// CSV data source
DataSource dataSource = DataSources.csv("users.csv");

// Usage in task
@Override
public TaskResult execute(long iteration) throws Exception {
    TestData data = dataSource.next(); // or generator.next()
    String email = data.get("email");
    // ... use in request ...
}
```

**Benefits**:
- Realistic test data
- Data-driven testing
- Reusable data sets
- Parameterized tests

**Files to Create**:
- `vajrapulse-data/` - New module
- `DataGenerator.java`, `DataSource.java`
- Built-in data types
- Tests and examples

---

## 🚀 Medium Priority Features (Nice-to-Have for 0.9.7)

### 5. Retry Policies and Circuit Breakers ⭐⭐
**Source**: CRITICAL_IMPROVEMENTS.md #9  
**Priority**: MEDIUM  
**Effort**: Medium (3-5 days)

**Problem**: No retry mechanisms or circuit breakers for resilience testing.

**Proposed Solution**:
- Annotations for retry policies
- Circuit breaker support
- Configurable backoff strategies

**Implementation**:
```java
@VirtualThreads
@RetryPolicy(maxAttempts = 3, backoff = ExponentialBackoff.of(Duration.ofSeconds(1)))
@CircuitBreaker(failureThreshold = 5, timeout = Duration.ofSeconds(30))
public class ResilientTask implements TaskLifecycle {
    // ... implementation ...
}
```

**Benefits**:
- Resilience testing
- Handle transient errors
- Test error recovery scenarios

---

### 6. Enhanced HTML Reports ⭐⭐
**Source**: CRITICAL_IMPROVEMENTS.md #7  
**Priority**: MEDIUM  
**Effort**: Medium (3-5 days)

**Problem**: Current HTML reports are basic; need more polish and features.

**Proposed Solution**:
- Interactive charts (Chart.js)
- Comparative analysis (side-by-side test runs)
- Trend charts (historical performance)
- Export to PDF
- Customizable templates

**Benefits**:
- Shareable, professional reports
- Better stakeholder communication
- Historical trend analysis

**Files to Modify**:
- `vajrapulse-exporter-report/src/main/java/com/vajrapulse/exporter/report/HtmlReportExporter.java`
- Add Chart.js integration
- Add comparison view
- Add PDF export

---

### 7. Warm-up and Cool-down Phases ⭐⭐
**Source**: ONE_ZERO_GAP_ANALYSIS.md A13  
**Priority**: MEDIUM  
**Effort**: Low-Medium (2-3 days)

**Problem**: No built-in warm-up/cool-down phases for accurate baselines.

**Proposed Solution**:
- Add warm-up phase to load patterns
- Add cool-down phase
- Separate measured steady-state from warm-up/cool-down

**Implementation**:
```java
LoadPattern pattern = LoadPattern.builder()
    .warmUp(Duration.ofSeconds(30))
    .steadyState(new StaticLoad(100.0, Duration.ofMinutes(5)))
    .coolDown(Duration.ofSeconds(10))
    .build();
```

**Benefits**:
- Accurate performance baselines
- JIT warm-up handling
- Cleaner test results

---

### 8. Performance Benchmark Suite ⭐⭐
**Source**: CRITICAL_IMPROVEMENTS.md #10, ONE_ZERO_GAP_ANALYSIS.md P1  
**Priority**: MEDIUM  
**Effort**: Medium (3-5 days)

**Problem**: No official benchmarks or performance regression tests.

**Proposed Solution**:
- JMH benchmarks for core components
- Automated performance regression tests
- CI integration for performance gates

**Implementation**:
- Create `benchmarks/` directory
- JMH benchmarks for TaskExecutor, MetricsCollector, RateController
- Integration benchmarks (real HTTP server)
- Performance regression tests in CI

**Benefits**:
- Verify performance claims
- Detect performance regressions
- Credibility for enterprise adoption

---

## 📚 Documentation & Examples (Always Ongoing)

### 9. Additional Protocol Examples ⭐
**Source**: CRITICAL_IMPROVEMENTS.md #2  
**Priority**: MEDIUM  
**Effort**: Low (1-2 days each)

**Examples Needed**:
- Database load test (PostgreSQL/MySQL with HikariCP)
- Kafka load test (Producer/Consumer)
- gRPC load test (Unary and streaming RPCs)
- WebSocket load test (Real-time communication)

**Benefits**:
- Lower learning curve
- Showcase framework flexibility
- Real-world usage patterns

---

### 10. Enhanced User Documentation ⭐
**Source**: CRITICAL_IMPROVEMENTS.md #12  
**Priority**: MEDIUM  
**Effort**: Medium (3-5 days)

**Documentation Needed**:
- Complete user guide (step-by-step tutorials)
- API reference (complete JavaDoc)
- Best practices guide
- Troubleshooting guide
- Video tutorials (optional)

**Benefits**:
- Easier onboarding
- Better feature discovery
- Reduced support burden

---

## 🔧 Technical Debt & Quality Improvements

### 11. ScopedValues Migration ⭐⭐⭐
**Source**: ONE_ZERO_GAP_ANALYSIS.md A11  
**Priority**: HIGH  
**Effort**: Medium (2-3 days)

**Problem**: ThreadLocal usage in hot path limits virtual thread scalability.

**Proposed Solution**:
- Replace ThreadLocal with ScopedValues where appropriate
- Critical for virtual thread scalability
- Better performance at high concurrency

**Files to Modify**:
- `MetricsCollector.java` - Replace ThreadLocal maps with ScopedValues
- Any other ThreadLocal usage in hot paths

---

### 12. Rate Controller Precision Improvements ⭐⭐
**Source**: ONE_ZERO_GAP_ANALYSIS.md P3  
**Priority**: MEDIUM  
**Effort**: Medium (2-3 days)

**Problem**: Need to validate and improve rate precision.

**Proposed Solution**:
- Empirical drift measurement vs target TPS
- Adjust controller algorithm if needed
- Publish accuracy metrics

**Benefits**:
- More accurate load patterns
- Better test reproducibility

---

### 13. Backpressure Strategy ⭐⭐
**Source**: ONE_ZERO_GAP_ANALYSIS.md A9  
**Priority**: MEDIUM  
**Effort**: Medium (3-4 days)

**Problem**: Need to define behavior when execution falls behind target rate.

**Proposed Solution**:
- Define backpressure strategies (skip, catch-up, degrade)
- Implement chosen strategy
- Add configuration options

**Benefits**:
- Prevent runaway resource usage
- Better control under overload

---

## 🎨 User Experience Improvements

### 14. Configuration System Enhancement ⭐⭐
**Source**: ONE_ZERO_GAP_ANALYSIS.md A4  
**Priority**: MEDIUM  
**Effort**: Medium (4-6 days)

**Problem**: Need central declarative config (YAML/JSON) with validation.

**Proposed Solution**:
- YAML/JSON configuration files
- Schema validation
- Configuration inheritance
- Environment variable overrides

**Benefits**:
- Reproducibility
- Easier automation
- Better CI/CD integration

---

### 15. Health & Liveness Endpoints ⭐
**Source**: ONE_ZERO_GAP_ANALYSIS.md O5  
**Priority**: LOW-MEDIUM  
**Effort**: Low (1-2 days)

**Problem**: No health/liveness endpoints for Kubernetes deployments.

**Proposed Solution**:
- Add `/health` endpoint
- Add `/metrics` endpoint (Prometheus format)
- Add `/ready` endpoint

**Benefits**:
- Kubernetes readiness
- Operational monitoring
- Better deployment experience

---

## 📊 Feature Priority Matrix

| Feature | Priority | Effort | Impact | Recommendation |
|---------|----------|--------|--------|----------------|
| Client-Side Metrics | HIGH | Medium | High | ✅ Include in 0.9.7 |
| Trace Replay Load | HIGH | Medium | High | ✅ Include in 0.9.7 |
| Assertion Framework | HIGH | Medium | High | ✅ Include in 0.9.7 |
| Test Data Management | MEDIUM-HIGH | Medium | Medium | ⚠️ Consider for 0.9.7 |
| ScopedValues Migration | HIGH | Medium | High | ✅ Include in 0.9.7 |
| Retry Policies | MEDIUM | Medium | Medium | ⚠️ Consider for 0.9.7 |
| Enhanced HTML Reports | MEDIUM | Medium | Medium | ⚠️ Consider for 0.9.7 |
| Warm-up/Cool-down | MEDIUM | Low-Medium | Medium | ✅ Include in 0.9.7 |
| Performance Benchmarks | MEDIUM | Medium | Medium | ⚠️ Consider for 0.9.7 |
| Protocol Examples | MEDIUM | Low | Medium | ✅ Include in 0.9.7 |
| Rate Controller Precision | MEDIUM | Medium | Medium | ⚠️ Consider for 0.9.7 |
| Backpressure Strategy | MEDIUM | Medium | Medium | ⚠️ Consider for 0.9.7 |
| Configuration System | MEDIUM | Medium | Medium | ⚠️ Defer to 0.10.0 |
| Health Endpoints | LOW-MEDIUM | Low | Low | ⚠️ Defer to 0.10.0 |

---

## 🎯 Recommended 0.9.7 Release Scope

### Must-Have Features (0.9.7)
1. ✅ **Client-Side Metrics Enhancement** - User wishlist item, critical for debugging
2. ✅ **Trace Replay Load Pattern** - High-value feature, addresses gap
3. ✅ **Assertion Framework** - Critical for test quality
4. ✅ **ScopedValues Migration** - Technical debt, critical for scalability
5. ✅ **Warm-up/Cool-down Phases** - Low effort, high value
6. ✅ **Additional Protocol Examples** - Low effort, high value for adoption

### Nice-to-Have (If Time Permits)
- Test Data Management
- Enhanced HTML Reports
- Retry Policies
- Performance Benchmarks

### Defer to 0.10.0
- Configuration System (larger effort)
- Health Endpoints (lower priority)
- Backpressure Strategy (can refine in 0.9.7 if needed)

---

## 📅 Estimated Timeline

**0.9.7 Release**: 2-3 weeks

- **Week 1**: Client-Side Metrics, ScopedValues Migration, Warm-up/Cool-down
- **Week 2**: Trace Replay Load, Assertion Framework
- **Week 3**: Protocol Examples, Testing, Documentation, Release

---

## 🎓 Success Criteria

- [ ] All must-have features implemented
- [ ] ≥90% test coverage maintained
- [ ] All tests pass
- [ ] Documentation updated
- [ ] CHANGELOG updated
- [ ] Examples working
- [ ] Performance benchmarks (if included) passing

---

## 📝 Notes

- These features align with the path to 1.0
- All features maintain backward compatibility (pre-1.0 allows breaking changes, but we should minimize)
- Focus on high-impact, medium-effort features
- Documentation and examples are always ongoing

---

**Next Steps**:
1. Review and prioritize features
2. Create GitHub issues for selected features
3. Begin implementation
4. Regular progress reviews

