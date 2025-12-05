# Release 0.9.7 Implementation Plan

**Date**: 2025-12-01  
**Status**: Planning  
**Target Release**: 0.9.7  
**Estimated Timeline**: 3-4 weeks

## Executive Summary

Release 0.9.7 focuses on enhancing `AdaptiveLoadPattern` with continuous operation, recovery capabilities, and intermediate stability detection. Additionally, it includes high-priority features for client-side metrics, trace replay, and assertion framework.

---

## 🎯 Top Priority: AdaptiveLoadPattern Enhancements

**Source**: `TASK_PLAN_VAJRAPULSE.md`  
**Priority**: CRITICAL  
**Estimated Effort**: 1 week (5 days)

### Goal
Transform `AdaptiveLoadPattern` from a terminal pattern to a continuous, self-recovering pattern that can find stable points at any TPS level.

### Task 1: Replace COMPLETE with RECOVERY Phase ⭐⭐⭐

**Priority**: Critical  
**Estimated Effort**: 1 day

#### Objectives
- Remove terminal `COMPLETE` phase
- Add `RECOVERY` phase that can transition back to `RAMP_UP`
- Enable continuous operation without terminal states

#### Implementation Details

**1.1. Update Phase Enum**
```java
// Before
public enum Phase {
    RAMP_UP, RAMP_DOWN, SUSTAIN, COMPLETE
}

// After
public enum Phase {
    RAMP_UP, RAMP_DOWN, SUSTAIN, RECOVERY
}
```

**1.2. Add RECOVERY Phase Handler**
```java
private double handleRecovery(long elapsedMillis, AdaptiveState current) {
    double errorRate = metricsProvider.getFailureRate() / 100.0;
    double backpressure = getBackpressureLevel();
    
    // Transition to RAMP_UP if conditions improved
    if (errorRate < errorThreshold && backpressure < 0.3) {
        double recoveryTps = Math.max(minimumTps, initialTps * 0.5);
        transitionPhase(Phase.RAMP_UP, elapsedMillis, recoveryTps);
        return recoveryTps;
    }
    
    // Transition to RAMP_DOWN if conditions worsened
    if (errorRate >= errorThreshold || backpressure >= 0.7) {
        double reducedTps = Math.max(minimumTps, current.currentTps() - rampDecrement);
        transitionPhase(Phase.RAMP_DOWN, elapsedMillis, reducedTps);
        return reducedTps;
    }
    
    // Stay in RECOVERY
    return current.currentTps();
}
```

**1.3. Update RAMP_DOWN to Transition to RECOVERY**
```java
// When TPS reaches minimum, transition to RECOVERY instead of COMPLETE
if (current.currentTps() <= minimumTps) {
    transitionPhase(Phase.RECOVERY, elapsedMillis, minimumTps);
    return minimumTps;
}
```

#### Files to Modify
- `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

#### Testing Requirements
- [ ] Unit tests for RECOVERY phase transitions
- [ ] Integration test: RAMP_UP → RAMP_DOWN → RECOVERY → RAMP_UP
- [ ] Test recovery from low TPS when backpressure decreases

#### Acceptance Criteria
- [ ] `COMPLETE` phase removed
- [ ] `RECOVERY` phase added
- [ ] Pattern transitions to RECOVERY when TPS reaches minimum
- [ ] Pattern can recover from RECOVERY to RAMP_UP
- [ ] All existing tests pass

---

### Task 2: Add Intermediate Stability Detection ⭐⭐⭐

**Priority**: High  
**Estimated Effort**: 2-3 days

#### Objectives
- Detect stability at intermediate TPS levels (not just MAX_TPS)
- Allow pattern to sustain at optimal TPS levels (e.g., 3000, 5000, 8000 TPS)
- Simple stability detection without complex sliding windows

#### Implementation Details

**2.1. Add Stability Tracking Fields**
```java
private double stableTpsCandidate = -1;
private long stabilityStartTime = -1;
private static final double TPS_TOLERANCE = 50.0;  // TPS can vary by ±50
```

**2.2. Add Stability Detection Method**
```java
/**
 * Checks if the current TPS level is stable.
 * 
 * <p>A TPS level is considered stable if:
 * <ul>
 *   <li>Error rate < errorThreshold</li>
 *   <li>Backpressure < 0.3</li>
 *   <li>TPS hasn't changed significantly (within tolerance)</li>
 *   <li>Stable conditions maintained for SUSTAIN_DURATION</li>
 * </ul>
 */
private boolean isStableAtCurrentTps(double currentTps, long elapsedMillis) {
    double errorRate = metricsProvider.getFailureRate() / 100.0;
    double backpressure = getBackpressureLevel();
    
    // Check if conditions are good
    boolean conditionsGood = errorRate < errorThreshold && backpressure < 0.3;
    
    if (!conditionsGood) {
        // Conditions not good - reset stability tracking
        stableTpsCandidate = -1;
        stabilityStartTime = -1;
        return false;
    }
    
    // Check if TPS is within tolerance of candidate
    if (stableTpsCandidate < 0 || Math.abs(currentTps - stableTpsCandidate) > TPS_TOLERANCE) {
        // New candidate or TPS changed significantly
        stableTpsCandidate = currentTps;
        stabilityStartTime = elapsedMillis;
        return false;
    }
    
    // Check if stable for required duration
    long stabilityDuration = elapsedMillis - stabilityStartTime;
    return stabilityDuration >= sustainDuration.toMillis();
}
```

**2.3. Update RAMP_UP and RAMP_DOWN to Check Stability**
```java
private double handleRampUp(long elapsedMillis, AdaptiveState current) {
    // Check if we've hit max TPS
    if (current.currentTps() >= maxTps) {
        transitionPhase(Phase.SUSTAIN, elapsedMillis, maxTps);
        return maxTps;
    }
    
    // Check if stable at current TPS (intermediate stability)
    if (isStableAtCurrentTps(current.currentTps(), elapsedMillis)) {
        transitionPhase(Phase.SUSTAIN, elapsedMillis, current.currentTps());
        return current.currentTps();
    }
    
    // Continue ramping up
    // ... existing ramp up logic ...
}

private double handleRampDown(long elapsedMillis, AdaptiveState current) {
    // Check if stable at current TPS (intermediate stability)
    if (isStableAtCurrentTps(current.currentTps(), elapsedMillis)) {
        transitionPhase(Phase.SUSTAIN, elapsedMillis, current.currentTps());
        return current.currentTps();
    }
    
    // Continue ramping down
    // ... existing ramp down logic ...
}
```

**2.4. Update SUSTAIN Phase to Allow Transitions**
```java
private double handleSustain(long elapsedMillis, AdaptiveState current) {
    double errorRate = metricsProvider.getFailureRate() / 100.0;
    double backpressure = getBackpressureLevel();
    
    // Check if conditions changed
    if (errorRate >= errorThreshold || backpressure >= 0.7) {
        // Conditions worsened - ramp down
        transitionPhase(Phase.RAMP_DOWN, elapsedMillis, current.currentTps());
        return current.currentTps();
    }
    
    if (errorRate < errorThreshold && backpressure < 0.3 && current.currentTps() < maxTps) {
        // Conditions good and below max - ramp up
        transitionPhase(Phase.RAMP_UP, elapsedMillis, current.currentTps());
        return current.currentTps();
    }
    
    // Stay in SUSTAIN
    return current.currentTps();
}
```

#### Files to Modify
- `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

#### Testing Requirements
- [ ] Unit tests for intermediate stability detection
- [ ] Test stability at 3000 TPS, 5000 TPS, 8000 TPS
- [ ] Integration test: RAMP_UP → SUSTAIN (at 3000) → RAMP_UP → SUSTAIN (at 5000)
- [ ] Test stability resets when conditions worsen

#### Acceptance Criteria
- [ ] Pattern detects stability at intermediate TPS levels
- [ ] Pattern sustains at optimal TPS (not just MAX_TPS)
- [ ] Stability requires good conditions for SUSTAIN_DURATION
- [ ] Pattern transitions out of SUSTAIN when conditions change

---

### Task 3: Add Minimum TPS Configuration ⭐⭐

**Priority**: Medium  
**Estimated Effort**: 1 day

#### Objectives
- Add configurable minimum TPS to prevent pattern from going to zero
- Ensure pattern maintains minimum load level

#### Implementation Details

**3.1. Add minimumTps Field**
```java
private final double minimumTps;  // Default: 0.0 for backward compatibility

public AdaptiveLoadPattern(
        // ... existing parameters ...
        double minimumTps) {  // NEW parameter
    // ... existing initialization ...
    this.minimumTps = minimumTps >= 0.0 ? minimumTps : 0.0;
}
```

**3.2. Enforce Minimum TPS in RAMP_DOWN**
```java
private double handleRampDown(long elapsedMillis, AdaptiveState current) {
    double newTps = current.currentTps() - rampDecrement;
    
    // Enforce minimum TPS
    if (newTps < minimumTps) {
        newTps = minimumTps;
    }
    
    // If at minimum, transition to RECOVERY
    if (newTps <= minimumTps && current.currentTps() <= minimumTps) {
        transitionPhase(Phase.RECOVERY, elapsedMillis, minimumTps);
        return minimumTps;
    }
    
    // Continue ramping down
    // ... existing logic ...
}
```

#### Files to Modify
- `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

#### Testing Requirements
- [ ] Test TPS doesn't go below minimumTps
- [ ] Test transition to RECOVERY when minimumTps reached
- [ ] Test backward compatibility (minimumTps = 0.0)

#### Acceptance Criteria
- [ ] `minimumTps` field added (defaults to 0.0)
- [ ] TPS never goes below `minimumTps`
- [ ] Pattern transitions to RECOVERY when minimumTps reached

---

## 🚀 High Priority Features

### Feature 1: Client-Side Metrics Enhancement ⭐⭐⭐

**Priority**: HIGH  
**Estimated Effort**: 3-5 days  
**Source**: User Wishlist Item #1

#### Problem
Currently missing metrics on request processing, queuing, and client-side bottlenecks.

#### Proposed Solution
- Add client-side connection pool metrics (active, idle, waiting connections)
- Track request queuing metrics (queue depth, wait time)
- Add timeout and backlog metrics
- Track client-side errors (connection refused, timeout, etc.)

#### Implementation
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

#### Files to Modify
- `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/MetricsCollector.java`
- `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/AggregatedMetrics.java`
- `vajrapulse-exporter-console/src/main/java/com/vajrapulse/exporter/console/ConsoleMetricsExporter.java`

#### Benefits
- Helps identify service bottlenecks
- Distinguishes between server-side and client-side issues
- Critical for enterprise debugging

---

### Feature 2: Trace Replay Load Pattern ⭐⭐⭐

**Priority**: HIGH  
**Estimated Effort**: 4-6 days  
**Source**: Architecture Documents

#### Problem
No way to replay real traffic patterns from production logs.

#### Proposed Solution
- `TraceReplayLoad` pattern that reads traffic timestamps from logs
- Support CSV/JSON input formats
- Replay traffic at original timestamps or scaled timestamps
- Handle missing data gracefully

#### Implementation
```java
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

#### Files to Create
- `vajrapulse-api/src/main/java/com/vajrapulse/api/TraceReplayLoad.java`
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/TraceReplayLoadSpec.groovy`
- `examples/trace-replay-load-test/` - Example usage

#### Benefits
- Test with realistic traffic patterns
- Replay production incidents
- Validate system behavior under real conditions

---

### Feature 3: Assertion Framework ⭐⭐⭐

**Priority**: HIGH  
**Estimated Effort**: 5-7 days  
**Source**: CRITICAL_IMPROVEMENTS.md

#### Problem
No built-in assertion library; users must write custom validation.

#### Proposed Solution
- Built-in assertion framework for test validation
- Integration with existing test frameworks
- Custom assertion builders
- Support for metrics-based assertions (latency, error rate, throughput)

#### Implementation
```java
public interface Assertion {
    AssertionResult evaluate(AggregatedMetrics metrics);
}

public class LatencyAssertion implements Assertion {
    private final double maxLatencyMs;
    private final double percentile;
    
    @Override
    public AssertionResult evaluate(AggregatedMetrics metrics) {
        double latency = metrics.latencyPercentiles().get(percentile);
        if (latency > maxLatencyMs) {
            return AssertionResult.failure(
                String.format("P%.0f latency %.2fms exceeds max %.2fms", 
                    percentile * 100, latency, maxLatencyMs));
        }
        return AssertionResult.success();
    }
}
```

#### Files to Create
- `vajrapulse-api/src/main/java/com/vajrapulse/api/Assertion.java`
- `vajrapulse-api/src/main/java/com/vajrapulse/api/AssertionResult.java`
- `vajrapulse-api/src/main/java/com/vajrapulse/api/Assertions.java` (factory)
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/AssertionSpec.groovy`

#### Benefits
- Standardized test validation
- Better test quality
- Easier integration with CI/CD

---

## 📋 Medium Priority Features

### Feature 4: ScopedValues Migration ⭐⭐

**Priority**: HIGH (Technical Debt)  
**Estimated Effort**: 2-3 days

#### Problem
Using `ThreadLocal` with virtual threads can cause issues. `ScopedValue` is the recommended approach for Java 21.

#### Solution
- Replace `ThreadLocal` with `ScopedValue` where appropriate
- Better virtual thread compatibility
- Reduced memory overhead

#### Files to Modify
- `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/MetricsCollector.java` (reusableIndexMap)

---

### Feature 5: Warm-up/Cool-down Phases ⭐⭐

**Priority**: MEDIUM  
**Estimated Effort**: 2-3 days

#### Problem
No built-in warm-up/cool-down phases for accurate baselines.

#### Solution
- Add warm-up phase to load patterns
- Add cool-down phase
- Separate measured steady-state from warm-up/cool-down

#### Implementation
```java
LoadPattern pattern = LoadPattern.builder()
    .warmUp(Duration.ofSeconds(30))
    .coolDown(Duration.ofSeconds(10))
    .build();
```

---

### Feature 6: Additional Protocol Examples ⭐⭐

**Priority**: MEDIUM  
**Estimated Effort**: 1-2 days

#### Solution
- gRPC example
- WebSocket example
- GraphQL example

#### Benefits
- Low effort, high adoption value
- Demonstrates framework flexibility

---

## 📅 Implementation Timeline

### Week 1: AdaptiveLoadPattern Enhancements (Top Priority)

**Day 1**: Task 1 - Replace COMPLETE with RECOVERY Phase
- Update Phase enum
- Add RECOVERY handler
- Update transitions
- Unit tests

**Day 2-3**: Task 2 - Intermediate Stability Detection
- Add stability tracking
- Implement stability detection
- Update RAMP_UP/RAMP_DOWN/SUSTAIN
- Unit tests

**Day 4**: Task 3 - Minimum TPS Configuration
- Add minimumTps field
- Enforce minimum TPS
- Unit tests

**Day 5**: Integration testing and bug fixes
- Full cycle tests
- Edge case testing
- Performance validation

### Week 2: High Priority Features

**Day 1-2**: Client-Side Metrics Enhancement
- Add ClientMetrics record
- Update MetricsCollector
- Update exporters
- Tests

**Day 3-5**: Trace Replay Load Pattern
- Implement TraceReplayLoad
- CSV/JSON parsing
- Time scaling
- Example
- Tests

### Week 3: Assertion Framework & Technical Debt

**Day 1-3**: Assertion Framework
- Design and implement
- Built-in assertions
- Integration examples
- Tests

**Day 4**: ScopedValues Migration
- Replace ThreadLocal
- Test compatibility
- Performance validation

**Day 5**: Warm-up/Cool-down Phases
- Add to LoadPattern interface
- Implement in patterns
- Tests

### Week 4: Polish, Examples, Documentation

**Day 1-2**: Additional Protocol Examples
- gRPC example
- WebSocket example
- GraphQL example

**Day 3**: Documentation
- Update README
- Update CHANGELOG
- JavaDoc completion

**Day 4-5**: Final Testing & Release Prep
- Full test suite
- Coverage verification
- Static analysis
- Release preparation

---

## 📊 Feature Priority Matrix

| Feature | Priority | Effort | Impact | Week |
|---------|----------|--------|--------|------|
| **AdaptiveLoadPattern: RECOVERY Phase** | CRITICAL | 1 day | High | Week 1 |
| **AdaptiveLoadPattern: Stability Detection** | CRITICAL | 2-3 days | High | Week 1 |
| **AdaptiveLoadPattern: Minimum TPS** | HIGH | 1 day | Medium | Week 1 |
| **Client-Side Metrics** | HIGH | 3-5 days | High | Week 2 |
| **Trace Replay Load** | HIGH | 4-6 days | High | Week 2 |
| **Assertion Framework** | HIGH | 5-7 days | High | Week 3 |
| **ScopedValues Migration** | HIGH | 2-3 days | Medium | Week 3 |
| **Warm-up/Cool-down** | MEDIUM | 2-3 days | Medium | Week 3 |
| **Protocol Examples** | MEDIUM | 1-2 days | Medium | Week 4 |

---

## ✅ Success Criteria

### Functional
- [ ] AdaptiveLoadPattern can recover from RECOVERY phase automatically
- [ ] AdaptiveLoadPattern finds stable points at intermediate TPS levels
- [ ] AdaptiveLoadPattern never stops (no terminal states)
- [ ] Client-side metrics available and working
- [ ] Trace replay pattern functional
- [ ] Assertion framework usable

### Non-Functional
- [ ] No performance regression
- [ ] Code coverage ≥90%
- [ ] All tests pass
- [ ] Static analysis passes (SpotBugs)
- [ ] JavaDoc complete (no warnings)
- [ ] Documentation updated

---

## 🎯 Release Checklist

### Pre-Release
- [ ] All tests pass (unit, integration, performance)
- [ ] Code coverage ≥90%
- [ ] Static analysis passes (SpotBugs)
- [ ] JavaDoc complete (no warnings)
- [ ] Documentation updated
- [ ] Examples updated
- [ ] CHANGELOG.md updated

### Release
- [ ] Version bumped to 0.9.7
- [ ] Tagged in git
- [ ] GitHub release created
- [ ] Published to Maven Central
- [ ] Release notes published

### Post-Release
- [ ] Monitor for issues
- [ ] Collect feedback
- [ ] Plan next iteration

---

## 📝 Notes

- **AdaptiveLoadPattern enhancements are TOP PRIORITY** - These are critical for continuous operation
- All features maintain backward compatibility where possible
- Pre-1.0 allows breaking changes, but we should minimize
- Focus on high-impact, medium-effort features
- Documentation and examples are always ongoing

---

## 🚨 Risk Assessment

### Risk 1: AdaptiveLoadPattern Complexity
**Risk**: Adding RECOVERY and stability detection increases complexity

**Mitigation**:
- Keep implementation simple
- Comprehensive testing
- Clear documentation

### Risk 2: Timeline Overrun
**Risk**: 3-4 weeks may be optimistic

**Mitigation**:
- Prioritize AdaptiveLoadPattern enhancements first
- Defer lower-priority features if needed
- Incremental releases if necessary

### Risk 3: Breaking Changes
**Risk**: Removing COMPLETE phase might break existing code

**Mitigation**:
- Pre-1.0 project (breaking changes acceptable)
- Document migration path
- Provide examples

---

**Next Steps**:
1. Review and approve this plan
2. Create GitHub issues for each task
3. Begin implementation with AdaptiveLoadPattern enhancements
4. Regular progress reviews

