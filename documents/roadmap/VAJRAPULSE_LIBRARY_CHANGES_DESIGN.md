# VajraPulse Library Changes: Design Document
## For VajraPulse Developers

**Date:** 2025-12-05  
**Status:** Design Document  
**Target Version:** 0.9.8 (or next release)  
**Priority:** High

---

## Executive Summary

This document outlines the changes needed in VajraPulse to support continuous adaptive load testing with automatic recovery. The changes focus on enhancing `AdaptiveLoadPattern` to:

1. **Fix RECOVERY phase transitions** - Allow pattern to recover from low TPS automatically
2. **Add recent window failure rate** - Use recent failures (not all-time) for recovery decisions
3. **Improve stability detection** - Sustain at intermediate TPS levels, not just MAX_TPS

These changes enable continuous load testing that can automatically recover from overload conditions and find optimal sustainable TPS levels.

---

## Problem Statement

### Current Issues

1. **RECOVERY Phase Doesn't Transition**
   - Pattern enters RECOVERY phase when TPS ramps down too far
   - RECOVERY phase doesn't transition back to RAMP_UP even when conditions improve
   - Pattern gets stuck at minimum TPS (e.g., 100 TPS) indefinitely

2. **All-Time Failure Rate Persists**
   - `MetricsProvider.getFailureRate()` returns all-time average
   - Historical failures keep error rate high even when system recovers
   - Recovery can't trigger because error rate never drops below threshold

3. **No Intermediate Stability Detection**
   - Pattern only sustains at MAX_TPS
   - Doesn't find stable points at intermediate TPS levels (e.g., 5000 TPS, 8000 TPS)
   - Continuously ramps up and down without sustaining

---

## Proposed Changes

### Change 1: Fix RECOVERY → RAMP_UP Transition (Priority: High)

**Current Behavior:**
- RECOVERY phase maintains minimum TPS
- No logic to check if conditions improved
- Never transitions back to RAMP_UP

**Proposed Behavior:**
- RECOVERY phase checks conditions periodically
- Transitions to RAMP_UP when conditions improve
- Starts recovery at reasonable TPS (e.g., 50% of last known good)

**Implementation:**
```java
// In AdaptiveLoadPattern.handleRecovery()
private double handleRecovery(long elapsedMillis, AdaptiveState current) {
    double errorRate = metricsProvider.getFailureRate() / 100.0;  // Convert percentage to ratio
    double backpressure = backpressureProvider.getBackpressureLevel();
    
    // Recovery conditions: backpressure low OR (error rate low AND backpressure moderate)
    if (backpressure < 0.3 || (errorRate < errorThreshold && backpressure < 0.5)) {
        // Start recovery at 50% of last known good TPS
        double lastKnownGoodTps = current.stableTps() > 0 
            ? current.stableTps() 
            : initialTps;
        double recoveryTps = Math.max(minimumTps, lastKnownGoodTps * 0.5);
        
        // Transition to RAMP_UP
        transitionPhase(Phase.RAMP_UP, elapsedMillis, recoveryTps);
        return recoveryTps;
    }
    
    // Stay in recovery, maintain minimum TPS
    return minimumTps;
}
```

**Key Points:**
- Check conditions every interval (same as other phases)
- Use lenient recovery conditions (backpressure < 0.3 OR moderate conditions)
- Start recovery at 50% of last known good TPS (not minimum)
- Transition to RAMP_UP phase explicitly

---

### Change 2: Add Recent Window Failure Rate (Priority: High)

**Current Behavior:**
- `MetricsProvider.getFailureRate()` returns all-time average
- Historical failures persist indefinitely
- Recovery can't trigger because error rate never drops

**Proposed Behavior:**
- Add `getRecentFailureRate(int windowSeconds)` method
- Calculate failure rate over recent time window (e.g., last 10 seconds)
- Use recent rate for recovery decisions, all-time rate for other decisions

**Interface Changes:**
```java
public interface MetricsProvider {
    /**
     * Gets the current failure rate as a percentage (0.0 to 100.0).
     * 
     * <p>This method returns the all-time failure rate, which may include
     * historical failures. For recovery decisions, consider using
     * {@link #getRecentFailureRate(int)} instead.
     * 
     * @return failure rate as percentage (0.0-100.0)
     */
    double getFailureRate();
    
    /**
     * Gets the failure rate over a recent time window.
     * 
     * <p>This method calculates the failure rate for the last N seconds,
     * excluding older failures. This is useful for recovery decisions where
     * recent failures matter more than historical failures.
     * 
     * <p>If the provider doesn't support recent window calculation, it may
     * return the all-time failure rate as a fallback.
     * 
     * @param windowSeconds the time window in seconds (e.g., 10)
     * @return failure rate as percentage (0.0-100.0) for the recent window
     * @since 0.9.8
     */
    default double getRecentFailureRate(int windowSeconds) {
        // Default implementation: return all-time rate
        // Providers can override to provide recent window calculation
        return getFailureRate();
    }
    
    /**
     * Gets the total number of executions.
     * 
     * @return total executions count
     */
    long getTotalExecutions();
}
```

**Usage in AdaptiveLoadPattern:**
```java
// In handleRecovery(), use recent window for recovery decisions
double errorRate = metricsProvider.getRecentFailureRate(10) / 100.0;  // Last 10 seconds

// In other phases, use all-time rate for stability decisions
double errorRate = metricsProvider.getFailureRate() / 100.0;  // All-time
```

**Default Implementation:**
- Default implementation returns all-time rate (backward compatible)
- Providers can override to provide recent window calculation
- No breaking changes for existing providers

---

### Change 3: Improve Stability Detection (Priority: Medium)

**Current Behavior:**
- Pattern only sustains at MAX_TPS
- Doesn't detect stability at intermediate TPS levels
- Continuously ramps up and down

**Proposed Behavior:**
- Detect stability at any TPS level (not just MAX_TPS)
- Sustain when conditions are stable for SUSTAIN_DURATION
- Transition to SUSTAIN from RAMP_UP or RAMP_DOWN

**Implementation:**
```java
// In AdaptiveLoadPattern
private boolean isStable(double currentTps, long elapsedMillis, AdaptiveState current) {
    // Check if we've been at this TPS for SUSTAIN_DURATION
    long timeAtCurrentTps = elapsedMillis - current.phaseStartTime();
    
    if (timeAtCurrentTps < sustainDuration.toMillis()) {
        return false;  // Not enough time at current TPS
    }
    
    // Check if conditions are good
    double errorRate = metricsProvider.getFailureRate() / 100.0;
    double backpressure = backpressureProvider.getBackpressureLevel();
    
    // Stable if: error rate low AND backpressure low
    boolean conditionsGood = errorRate < errorThreshold && backpressure < 0.3;
    
    // Stable if: same TPS for SUSTAIN_DURATION AND conditions good
    return conditionsGood;
}

// In handleRampUp() and handleRampDown()
if (isStable(currentTps, elapsedMillis, current)) {
    transitionPhase(Phase.SUSTAIN, elapsedMillis, currentTps);
    return currentTps;
}
```

**Key Points:**
- Check stability at any TPS level (not just MAX_TPS)
- Require SUSTAIN_DURATION at same TPS with good conditions
- Transition to SUSTAIN from RAMP_UP or RAMP_DOWN
- Allow pattern to find optimal TPS at intermediate levels

---

## API Changes Summary

### New Methods

1. **MetricsProvider.getRecentFailureRate(int windowSeconds)**
   - Returns failure rate for recent time window
   - Default implementation returns all-time rate (backward compatible)
   - Optional enhancement for providers

### Modified Behavior

1. **AdaptiveLoadPattern.handleRecovery()**
   - Now checks conditions and transitions to RAMP_UP
   - Uses recent window failure rate for recovery decisions
   - Starts recovery at 50% of last known good TPS

2. **AdaptiveLoadPattern Stability Detection**
   - Now detects stability at intermediate TPS levels
   - Transitions to SUSTAIN from RAMP_UP or RAMP_DOWN
   - Not limited to MAX_TPS

### No Breaking Changes

- All changes are backward compatible
- Default implementations provided where needed
- Existing code continues to work

---

## Implementation Details

### Phase Transition Logic

**RECOVERY → RAMP_UP:**
```java
if (backpressure < 0.3 || (errorRate < errorThreshold && backpressure < 0.5)) {
    double recoveryTps = Math.max(minimumTps, lastKnownGoodTps * 0.5);
    transitionPhase(Phase.RAMP_UP, elapsedMillis, recoveryTps);
    return recoveryTps;
}
```

**RAMP_UP → SUSTAIN:**
```java
if (isStable(currentTps, elapsedMillis, current)) {
    transitionPhase(Phase.SUSTAIN, elapsedMillis, currentTps);
    return currentTps;
}
```

**RAMP_DOWN → SUSTAIN:**
```java
if (isStable(currentTps, elapsedMillis, current)) {
    transitionPhase(Phase.SUSTAIN, elapsedMillis, currentTps);
    return currentTps;
}
```

### State Tracking

**Track Last Known Good TPS:**
```java
// In AdaptiveState
private double lastKnownGoodTps = initialTps;

// Update when transitioning to RAMP_DOWN or RECOVERY
if (phase == Phase.RAMP_DOWN || phase == Phase.RECOVERY) {
    if (currentTps > lastKnownGoodTps) {
        lastKnownGoodTps = currentTps;
    }
}
```

---

## Testing Requirements

### Unit Tests

1. **RECOVERY Phase Transitions**
   - Test RECOVERY → RAMP_UP when backpressure < 0.3
   - Test RECOVERY → RAMP_UP when error rate low and backpressure moderate
   - Test RECOVERY stays in recovery when conditions poor
   - Test recovery TPS is 50% of last known good

2. **Recent Window Failure Rate**
   - Test `getRecentFailureRate(10)` returns rate for last 10 seconds
   - Test old failures don't affect recent rate
   - Test default implementation returns all-time rate

3. **Stability Detection**
   - Test stability detected at intermediate TPS levels
   - Test stability requires SUSTAIN_DURATION
   - Test stability requires good conditions
   - Test RAMP_UP → SUSTAIN transition
   - Test RAMP_DOWN → SUSTAIN transition

### Integration Tests

1. **Full Recovery Cycle**
   - Test: RAMP_UP → RAMP_DOWN → RECOVERY → RAMP_UP
   - Verify pattern recovers and continues ramping

2. **Intermediate Stability**
   - Test: Pattern sustains at intermediate TPS (not MAX_TPS)
   - Verify pattern finds optimal TPS level

3. **Continuous Operation**
   - Test: Pattern runs for extended period
   - Verify no getting stuck, continuous adaptation

---

## Backward Compatibility

### Guaranteed Compatibility

1. **MetricsProvider Interface**
   - `getRecentFailureRate()` has default implementation
   - Existing providers continue to work
   - No breaking changes

2. **AdaptiveLoadPattern Behavior**
   - Existing patterns continue to work
   - New behavior is additive (recovery, stability)
   - No changes to existing phase transitions

3. **API Stability**
   - No public API changes
   - All changes are internal improvements
   - Existing code doesn't need updates

---

## Migration Guide

### For Users

**No migration needed!** All changes are backward compatible.

**Optional Enhancements:**
- Implement `getRecentFailureRate()` in custom MetricsProvider for better recovery
- Use new stability detection automatically (no configuration needed)

### For Providers

**MetricsProvider Implementations:**
- Optionally implement `getRecentFailureRate()` for better recovery decisions
- Default implementation provided (returns all-time rate)
- No breaking changes if not implemented

---

## Success Criteria

### Functional Requirements

- [ ] RECOVERY phase transitions to RAMP_UP when conditions improve
- [ ] Recent window failure rate available (default implementation works)
- [ ] Stability detected at intermediate TPS levels
- [ ] Pattern can sustain at optimal TPS (not just MAX_TPS)
- [ ] Pattern recovers from low TPS automatically

### Quality Requirements

- [ ] All existing tests pass
- [ ] New tests added for all changes
- [ ] Code coverage maintained or improved
- [ ] Documentation updated
- [ ] Backward compatibility verified

### Performance Requirements

- [ ] No performance regression
- [ ] Recent window calculation is efficient
- [ ] Stability detection doesn't add significant overhead

---

## Timeline

**Estimated Effort:** 3-5 days

- **Day 1:** Fix RECOVERY → RAMP_UP transition
- **Day 2:** Add recent window failure rate
- **Day 3:** Improve stability detection
- **Day 4:** Testing and documentation
- **Day 5:** Review and refinement

---

## Questions and Clarifications

### Q1: Should recent window be configurable?
**A:** Yes, but default to 10 seconds. Can be made configurable in future if needed.

### Q2: What if MetricsProvider doesn't support recent window?
**A:** Default implementation returns all-time rate. Providers can override for better behavior.

### Q3: Should recovery TPS be configurable?
**A:** Start with 50% of last known good. Can be made configurable in future if needed.

### Q4: Should stability detection be optional?
**A:** No, it should always be enabled. It improves pattern behavior without drawbacks.

---

## References

- **Current Implementation:** `AdaptiveLoadPattern.java` in VajraPulse 0.9.7
- **Related Documents:** 
  - `COMPLETE_REDESIGN_PRINCIPAL_ENGINEER.md` (overall redesign)
  - `DETAILED_TASK_BREAKDOWN.md` (implementation guide)
  - `VORTEX_QUEUE_ONLY_BACKPRESSURE_ANALYSIS.md` (backpressure approach)

---

**Document Status:** Ready for Implementation  
**Next Steps:** See `VAJRAPULSE_LIBRARY_CHANGES_TASKS.md` for detailed task list

