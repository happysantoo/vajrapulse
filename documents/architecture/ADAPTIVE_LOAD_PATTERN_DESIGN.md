# Adaptive Load Pattern Design - 0.9.5

**Feature Branch**: `feature/adaptive-load-pattern`  
**Status**: Design Phase  
**Target**: Simple, metrics-driven adaptive load pattern

---

## Requirements Summary

Create a load pattern that:
1. **Starts** at a configured initial TPS (e.g., 100 TPS)
2. **Ramps up** with a configured increment (e.g., +50 TPS per minute)
3. **Has a maximum limit** (e.g., 5000 TPS, or unlimited)
4. **Detects bottlenecks** by monitoring error rates
5. **Ramps down** when errors occur (e.g., -100 TPS per minute)
6. **Finds stable point** (max TPS without errors)
7. **Sustains** at stable point for configured duration
8. **Provides metrics** at every step for visualization

---

## Design Challenge

The current `LoadPattern` interface is **stateless** and **pure**:
```java
public interface LoadPattern {
    double calculateTps(long elapsedMillis);
    Duration getDuration();
}
```

**Problem**: Adaptive pattern needs feedback from execution metrics (success/failure rates) to adjust TPS, but:
- `LoadPattern` has no access to `MetricsCollector`
- `RateController` calls `calculateTps()` but doesn't have metrics
- Breaking the interface would affect all existing patterns

---

## Design Options

### Option 1: Stateful Adaptive Pattern with Metrics Access ⭐ **RECOMMENDED**

**Approach**: Create a new `AdaptiveLoadPattern` that:
- Implements `LoadPattern` interface (backward compatible)
- Takes `MetricsCollector` in constructor
- Queries metrics internally to determine current state
- Maintains internal state (current TPS, phase, etc.)

**Pros**:
- ✅ No breaking changes to existing interface
- ✅ Simple implementation
- ✅ Clear separation of concerns
- ✅ Easy to test (can mock MetricsCollector)

**Cons**:
- ⚠️ Pattern needs access to MetricsCollector (passed in constructor)
- ⚠️ Stateful pattern (but contained within pattern)

**Implementation**:
```java
public record AdaptiveLoadPattern(
    double initialTps,
    double rampIncrement,
    double rampDecrement,
    Duration rampInterval,
    double maxTps,  // Double.POSITIVE_INFINITY for unlimited
    Duration sustainDuration,
    double errorThreshold,  // e.g., 0.01 = 1% error rate
    MetricsCollector metricsCollector
) implements LoadPattern {
    // Internal state
    private volatile Phase currentPhase = Phase.RAMP_UP;
    private volatile double currentTps;
    private volatile long lastAdjustmentTime;
    private volatile double stableTps;
    
    enum Phase { RAMP_UP, RAMP_DOWN, SUSTAIN, COMPLETE }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        // Query metrics to get current error rate
        AggregatedMetrics snapshot = metricsCollector.snapshot();
        double errorRate = snapshot.failureRate();
        
        // State machine logic
        // ...
    }
}
```

---

### Option 2: Extended Interface with Feedback

**Approach**: Extend `LoadPattern` with optional feedback method:
```java
public interface LoadPattern {
    double calculateTps(long elapsedMillis);
    Duration getDuration();
    
    // Optional feedback (default no-op for backward compatibility)
    default void updateMetrics(AggregatedMetrics metrics) {}
}
```

**Pros**:
- ✅ Clean interface extension
- ✅ Backward compatible (default method)

**Cons**:
- ⚠️ Requires changes to `RateController` to call `updateMetrics()`
- ⚠️ More complex integration
- ⚠️ All patterns need to implement (even if empty)

---

### Option 3: Separate Adaptive Pattern Interface

**Approach**: Create new `AdaptiveLoadPattern` interface separate from `LoadPattern`:
```java
public interface AdaptiveLoadPattern {
    double calculateTps(long elapsedMillis, AggregatedMetrics metrics);
    Duration getDuration();
}
```

**Pros**:
- ✅ Clear separation
- ✅ Explicit metrics dependency

**Cons**:
- ❌ Requires changes to `ExecutionEngine` and `RateController`
- ❌ More complex architecture
- ❌ Two different pattern types to maintain

---

### Option 4: Wrapper Pattern

**Approach**: Create `AdaptiveLoadPatternWrapper` that wraps a base pattern:
```java
public class AdaptiveLoadPatternWrapper implements LoadPattern {
    private final LoadPattern basePattern;
    private final MetricsCollector metricsCollector;
    // Adaptive logic adjusts base pattern's TPS
}
```

**Pros**:
- ✅ Can adapt any existing pattern
- ✅ Flexible

**Cons**:
- ❌ More complex
- ❌ Harder to understand
- ❌ Doesn't fit the use case (we want a specific adaptive behavior)

---

## Recommended Design: Option 1

### Architecture

```
AdaptiveLoadPattern (implements LoadPattern)
├── Constructor Parameters
│   ├── initialTps: 100.0
│   ├── rampIncrement: 50.0 (TPS per interval)
│   ├── rampDecrement: 100.0 (TPS per interval)
│   ├── rampInterval: Duration.ofMinutes(1)
│   ├── maxTps: 5000.0 (or Double.POSITIVE_INFINITY)
│   ├── sustainDuration: Duration.ofMinutes(10)
│   ├── errorThreshold: 0.01 (1% error rate triggers ramp down)
│   └── metricsCollector: MetricsCollector (for feedback)
│
├── Internal State
│   ├── currentPhase: RAMP_UP | RAMP_DOWN | SUSTAIN | COMPLETE
│   ├── currentTps: double (current target TPS)
│   ├── lastAdjustmentTime: long (when we last adjusted)
│   ├── stableTps: double (TPS at stable point)
│   └── phaseStartTime: long (when current phase started)
│
└── State Machine Logic
    ├── RAMP_UP: Increase TPS until error threshold exceeded
    ├── RAMP_DOWN: Decrease TPS until errors stop
    ├── SUSTAIN: Hold at stable TPS for sustainDuration
    └── COMPLETE: Return 0 TPS (test ends)
```

### State Machine Flow

```
START (initialTps)
  ↓
[RAMP_UP]
  ├─ Check error rate every rampInterval
  ├─ If errorRate < errorThreshold: currentTps += rampIncrement
  ├─ If currentTps >= maxTps: currentTps = maxTps, continue monitoring
  └─ If errorRate >= errorThreshold: → RAMP_DOWN
  ↓
[RAMP_DOWN]
  ├─ Check error rate every rampInterval
  ├─ currentTps -= rampDecrement
  ├─ If errorRate < errorThreshold: stableTps = currentTps, → SUSTAIN
  └─ If currentTps <= 0: → COMPLETE (no stable point found)
  ↓
[SUSTAIN]
  ├─ Hold at stableTps
  ├─ Monitor for sustainDuration
  └─ After sustainDuration: → COMPLETE
  ↓
[COMPLETE]
  └─ Return 0 TPS (test ends)
```

### Metrics Requirements

**New Metrics Needed**:
1. **Phase tracking**: Current phase (RAMP_UP, RAMP_DOWN, SUSTAIN)
2. **Current target TPS**: What TPS the pattern is trying to achieve
3. **Stable TPS**: The TPS at which system is stable
4. **Phase transitions**: When and why phase changed
5. **Adjustment history**: Timeline of TPS adjustments

**Implementation**:
- Add tags to existing metrics: `phase=ramp_up|ramp_down|sustain`
- Add gauge: `vajrapulse.adaptive.current_tps`
- Add gauge: `vajrapulse.adaptive.stable_tps`
- Add counter: `vajrapulse.adaptive.phase_transitions` with `from_phase` and `to_phase` tags

---

## Implementation Plan

### Phase 1: Core Pattern (2-3 days)
1. Create `AdaptiveLoadPattern` class
2. Implement state machine logic
3. Add basic metrics integration
4. Unit tests for state transitions

### Phase 2: Metrics & Visualization (1-2 days)
1. Add phase tracking metrics
2. Add TPS adjustment metrics
3. Update exporters to show adaptive metrics
4. Update HTML report to visualize phase transitions

### Phase 3: Integration & Testing (1-2 days)
1. Integration with ExecutionEngine
2. End-to-end tests
3. Example usage
4. Documentation

### Phase 4: CLI Support (1 day)
1. Add CLI flags for adaptive pattern
2. Update worker to support adaptive mode
3. Configuration file support

---

## Configuration Example

```java
// Programmatic
AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    100.0,                          // Start at 100 TPS
    50.0,                           // Increase 50 TPS per minute
    100.0,                          // Decrease 100 TPS per minute when errors occur
    Duration.ofMinutes(1),          // Check/adjust every minute
    5000.0,                         // Max 5000 TPS (or Double.POSITIVE_INFINITY)
    Duration.ofMinutes(10),         // Sustain at stable point for 10 minutes
    0.01,                           // 1% error rate threshold
    metricsCollector                // For feedback
);
```

```yaml
# Configuration file
loadPattern:
  type: adaptive
  initialTps: 100
  rampIncrement: 50
  rampDecrement: 100
  rampInterval: 1m
  maxTps: 5000  # or "unlimited"
  sustainDuration: 10m
  errorThreshold: 0.01  # 1%
```

```bash
# CLI
java -jar vajrapulse-worker.jar MyTask \
  --mode adaptive \
  --initial-tps 100 \
  --ramp-increment 50 \
  --ramp-decrement 100 \
  --ramp-interval 1m \
  --max-tps 5000 \
  --sustain-duration 10m \
  --error-threshold 0.01
```

---

## Design Decisions Needed

### Decision 1: Error Threshold Definition
**Question**: What defines a "bottleneck" or "error threshold"?

**Options**:
- **A**: Failure rate > threshold (e.g., >1% failures)
- **B**: Failure rate > threshold OR latency > threshold (e.g., P95 > 1s)
- **C**: Configurable condition (failure rate, latency, or both)

**Recommendation**: **Option A** (simplest) - Start with failure rate only, can extend later

---

### Decision 2: Ramp Interval vs Continuous Monitoring
**Question**: How often should we check metrics and adjust TPS?

**Options**:
- **A**: Fixed interval (e.g., every 1 minute) - Simple, predictable
- **B**: Sliding window (e.g., last 30 seconds) - More responsive
- **C**: Configurable window size

**Recommendation**: **Option A** (simplest) - Fixed interval, can make configurable later

---

### Decision 3: Ramp Down Behavior
**Question**: When errors occur, how should we ramp down?

**Options**:
- **A**: Immediate step down by decrement amount
- **B**: Gradual ramp down over interval
- **C**: Step down, then wait for stabilization before continuing

**Recommendation**: **Option A** (simplest) - Immediate step down, wait for next interval to check again

---

### Decision 4: Stable Point Detection
**Question**: How do we know we've found the stable point?

**Options**:
- **A**: Error rate < threshold for one interval
- **B**: Error rate < threshold for N consecutive intervals (e.g., 2-3)
- **C**: Error rate < threshold AND latency stable

**Recommendation**: **Option B** (more reliable) - Require 2-3 consecutive intervals with low error rate

---

### Decision 5: What Happens After Sustain?
**Question**: After sustaining at stable point, what happens?

**Options**:
- **A**: Test ends (return 0 TPS)
- **B**: Continue at stable TPS indefinitely
- **C**: Optionally ramp down to 0

**Recommendation**: **Option A** (simplest) - Test ends after sustain duration

---

### Decision 6: Unlimited Max TPS
**Question**: How to represent "unlimited" max TPS?

**Options**:
- **A**: Use `Double.POSITIVE_INFINITY`
- **B**: Use `-1` or `0` as sentinel value
- **C**: Use `Optional<Double>` (null = unlimited)

**Recommendation**: **Option A** - `Double.POSITIVE_INFINITY` is clear and standard

---

### Decision 7: Metrics Access Pattern
**Question**: How should AdaptiveLoadPattern access metrics?

**Options**:
- **A**: Pass MetricsCollector in constructor, call `snapshot()` when needed
- **B**: Pass a MetricsProvider interface (more testable)
- **C**: Query metrics through ExecutionEngine

**Recommendation**: **Option A** (simplest) - Direct MetricsCollector access, easy to test with mock

---

## Open Questions

1. **What if stable point is never found?** (errors persist even at low TPS)
   - **Proposal**: After N ramp-down attempts, complete test with warning

2. **What if we hit max TPS without errors?**
   - **Proposal**: Treat max TPS as stable point, go to SUSTAIN phase

3. **Should we track latency in addition to error rate?**
   - **Proposal**: Start with error rate only, add latency later if needed

4. **How to visualize phase transitions?**
   - **Proposal**: Add phase timeline to HTML report, show TPS over time with phase annotations

5. **What metrics should be exposed?**
   - **Proposal**: 
     - Current phase (gauge)
     - Current target TPS (gauge)
     - Stable TPS (gauge)
     - Phase transition count (counter)
     - TPS adjustment history (events or time series)

---

## Simplification Principles

1. **Start Simple**: Basic state machine with error rate only
2. **Iterate**: Add latency tracking, configurable windows later
3. **Metrics First**: Every state change should emit metrics
4. **Visualization**: Make it easy to see what's happening
5. **No Breaking Changes**: Work within existing LoadPattern interface

---

## Next Steps

1. **Review this design** and make decisions on open questions
2. **Approve approach** (Option 1 recommended)
3. **Create implementation plan** with specific tasks
4. **Start implementation** with core pattern
5. **Add metrics** and visualization
6. **Test and iterate**

---

*This design prioritizes simplicity while providing the adaptive behavior requested. All decisions can be revisited and extended in future iterations.*

