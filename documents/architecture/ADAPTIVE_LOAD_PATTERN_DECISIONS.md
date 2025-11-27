# Adaptive Load Pattern - Design Decisions

**Feature**: Adaptive Load Pattern for 0.9.5  
**Status**: Awaiting Decisions  
**Design Doc**: `ADAPTIVE_LOAD_PATTERN_DESIGN.md`

---

## Summary

I've created a design document analyzing different approaches for implementing the adaptive load pattern. The **recommended approach** is **Option 1: Stateful Adaptive Pattern with Metrics Access**, which maintains backward compatibility while providing the adaptive behavior you need.

---

## Key Design Decisions Needed

Please review and choose your preferred options:

### 1. Error Threshold Definition ✅ **DECISION MADE**

**Question**: What defines when we've hit a bottleneck?

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A** ✅ **CHOSEN** | Failure rate > threshold (e.g., >1%) | Simple, clear | Doesn't consider latency |
| **B** | Failure rate OR latency > threshold | More comprehensive | More complex logic |
| **C** | Configurable (failure rate, latency, or both) | Most flexible | Most complex |

**Decision**: **Option A** - Failure rate only. Simple and clear. Can add latency later if needed.

---

### 2. Adjustment Frequency ✅ **DECISION MADE**

**Question**: How often should we check metrics and adjust TPS?

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A** ✅ **CHOSEN** | Fixed interval (e.g., every 1 minute) | Simple, predictable | May be slow to react |
| **B** | Sliding window (e.g., last 30 seconds) | More responsive | More complex |
| **C** | Configurable window size | Flexible | More to configure |

**Decision**: **Option A** - Fixed interval. Predictable and easier to visualize.

---

### 3. Ramp Down Behavior ✅ **DECISION MADE**

**Question**: When errors occur, how should we decrease TPS?

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A** ✅ **CHOSEN** | Immediate step down by decrement amount | Simple, clear | May overshoot |
| **B** | Gradual ramp down over interval | Smoother | More complex |
| **C** | Step down, wait for stabilization | More conservative | Slower to find stable point |

**Decision**: **Option A** - Immediate step down. Simple and effective.

---

### 4. Stable Point Detection ✅ **DECISION MADE**

**Question**: How do we know we've found the stable TPS?

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A** | Error rate < threshold for one interval | Fast detection | May be unstable |
| **B** ✅ **CHOSEN** | Error rate < threshold for N intervals (2-3) | More reliable | Takes longer |
| **C** | Error rate < threshold AND latency stable | Most reliable | Most complex |

**Decision**: **Option B** - Require 2-3 consecutive intervals with low error rate. More reliable.

---

### 5. After Sustain Phase ✅ **DECISION MADE**

**Question**: What happens after sustaining at stable point?

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A** | Test ends (return 0 TPS) | Simple, clear end | Test stops |
| **B** ✅ **CHOSEN** | Continue at stable TPS indefinitely | Can run longer | Need manual stop |
| **C** | Optionally ramp down to 0 | Clean shutdown | More complex |

**Decision**: **Option B** - Continue at stable TPS indefinitely. Test continues until manually stopped or duration limit reached.

**Implementation**: After sustain duration, pattern remains in SUSTAIN phase and continues returning stable TPS. Test can be stopped manually or by external duration limit.

---

### 6. Unlimited Max TPS Representation ✅ **DECISION MADE**

**Question**: How to represent "no maximum limit"?

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| **A** ✅ **CHOSEN** | `Double.POSITIVE_INFINITY` | Standard, clear | Need to check for infinity |
| **B** | `-1` or `0` as sentinel | Easy to check | Not standard |
| **C** | `Optional<Double>` (null = unlimited) | Type-safe | More verbose |

**Decision**: **Option A** - Use `Double.POSITIVE_INFINITY`. Standard Java approach.

---

### 7. Edge Cases - Requires Input ⚠️

**Question 7a**: What if stable point is never found? (errors persist even at low TPS)

**Decision**: ✅ **Accept proposal** - After N ramp-down attempts (e.g., 10), complete test with warning message.

---

**Question 7b**: What if we hit max TPS without any errors?

**Decision**: ✅ **Accept proposal** - Treat max TPS as stable point, immediately go to SUSTAIN phase.

---

**Question 7c**: Should we track latency in addition to error rate?

**Decision**: ✅ **Error rate only** - Start simple, add latency tracking in future iteration if needed.

---

## Final Configuration

Based on your decisions, here's the final configuration:

```java
new AdaptiveLoadPattern(
    100.0,                          // Start at 100 TPS
    50.0,                           // Increase 50 TPS per minute
    100.0,                          // Decrease 100 TPS per minute when errors occur
    Duration.ofMinutes(1),          // Check/adjust every 1 minute
    5000.0,                         // Max 5000 TPS (or Double.POSITIVE_INFINITY)
    Duration.ofMinutes(10),         // Sustain at stable point for 10 minutes
    0.01,                           // 1% error rate threshold
    metricsCollector                // For feedback
);
```

**Behavior**:
- Ramp up: +50 TPS every minute until errors or max TPS
- Ramp down: -100 TPS every minute when errors occur
- Stable detection: 2-3 consecutive intervals with <1% error rate
- After sustain: **Continue at stable TPS indefinitely** (until manual stop or duration limit)

---

## Metrics & Visualization Plan

### Metrics to Add

1. **Phase Gauge**: Current phase (RAMP_UP, RAMP_DOWN, SUSTAIN, COMPLETE)
2. **Current TPS Gauge**: Current target TPS the pattern is trying to achieve
3. **Stable TPS Gauge**: The TPS at which system is stable (once found)
4. **Phase Transitions Counter**: Count of phase changes with `from_phase` and `to_phase` tags
5. **TPS Adjustment Events**: Timeline of TPS adjustments (for visualization)

### Visualization

- **HTML Report**: 
  - TPS over time graph with phase annotations
  - Phase timeline showing transitions
  - Stable TPS highlight
  
- **Console Output**:
  - Show current phase and target TPS in periodic updates
  - Show phase transitions as they happen

---

## Implementation Timeline

**Total Estimated Time**: 5-7 days

1. **Core Pattern** (2-3 days): State machine, basic logic
2. **Metrics Integration** (1-2 days): Add metrics, update exporters
3. **Testing & Integration** (1-2 days): Tests, examples, CLI
4. **Documentation** (1 day): Usage guide, examples

---

## Next Steps

1. **Review design document**: `documents/architecture/ADAPTIVE_LOAD_PATTERN_DESIGN.md`
2. **Make decisions** on the 7 questions above
3. **Approve approach** (Option 1 recommended)
4. **Start implementation** once decisions are made

---

---

## Distributed Testing Support

### Current Design Compatibility

**Good News**: The recommended design (Option 1) can be extended for distributed testing without breaking changes.

**Single-Instance Mode (0.9.5)**:
- `AdaptiveLoadPattern` uses `MetricsCollector` directly
- Works standalone
- No coordination needed

**Distributed Mode (Future)**:
- Add optional `AggregatedMetricsProvider` parameter
- Add optional `CoordinationService` parameter
- If provided, uses aggregated metrics from all instances
- Backward compatible: single-instance mode still works

### Distributed Coordination Approach

**Recommended**: **Metrics Aggregator Pattern**
- Each instance queries aggregated metrics (via Prometheus/OTEL)
- Coordination via lightweight service (K8s ConfigMap, Redis, or HTTP)
- No single point of failure
- Works with existing orchestration (K8s, BlazeMeter, CI/CD)

**See**: `ADAPTIVE_LOAD_PATTERN_DESIGN.md` section "Distributed Testing Considerations" for details.

### Decision Made ✅

**Question**: Should we design for distributed mode from the start, or implement single-instance first?

**Decision**: ✅ **Option A** - Implement single-instance first (0.9.5), add distributed support later.

**Rationale**: Start simple, extend later. The design is already compatible with distributed mode.

---

## Questions?

If you have questions or want to discuss any of these decisions, please let me know. Once you've made your choices, I'll proceed with implementation.

