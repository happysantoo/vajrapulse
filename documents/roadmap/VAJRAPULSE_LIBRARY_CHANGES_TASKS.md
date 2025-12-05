# VajraPulse Library Changes: Task List
## For VajraPulse Developers

**Date:** 2025-12-05  
**Status:** Implementation Tasks  
**Target Version:** 0.9.8 (or next release)  
**Priority:** High

---

## Overview

This document provides a detailed, step-by-step task list for implementing the changes outlined in `VAJRAPULSE_LIBRARY_CHANGES_DESIGN.md`.

**Estimated Total Effort:** 3-5 days

---

## Task 1: Fix RECOVERY → RAMP_UP Transition (Priority: High)

**Estimated Effort:** 1 day  
**Dependencies:** None

### Subtask 1.1: Update AdaptiveState to Track Last Known Good TPS

**Objective:** Track the highest TPS achieved before entering RECOVERY phase.

**File:** `AdaptiveState.java` (or equivalent state class)

**Changes:**
```java
// Add field to track last known good TPS
private final double lastKnownGoodTps;

// Update constructor
public AdaptiveState(
    Phase phase,
    double currentTps,
    long lastAdjustmentTime,
    double stableTps,
    long phaseStartTime,
    int stableIntervalsCount,
    int rampDownAttempts,
    long phaseTransitionCount,
    double lastKnownGoodTps  // NEW
) {
    // ... existing code ...
    this.lastKnownGoodTps = lastKnownGoodTps;
}

// Add getter
public double lastKnownGoodTps() {
    return lastKnownGoodTps;
}

// Add method to update last known good TPS
public AdaptiveState withLastKnownGoodTps(double tps) {
    return new AdaptiveState(
        phase, currentTps, lastAdjustmentTime, stableTps,
        phaseStartTime, stableIntervalsCount, rampDownAttempts,
        phaseTransitionCount, Math.max(lastKnownGoodTps, tps)
    );
}
```

**Initialization:**
```java
// In AdaptiveLoadPattern constructor or initial state
this.state = new AtomicReference<>(new AdaptiveState(
    Phase.RAMP_UP,
    initialTps,
    -1L,
    -1.0,
    -1L,
    0,
    0,
    0L,
    initialTps  // Initialize to initialTps
));
```

**Success Criteria:**
- [ ] `lastKnownGoodTps` field added to AdaptiveState
- [ ] Constructor updated
- [ ] Getter method added
- [ ] Update method added
- [ ] Initialized to `initialTps`
- [ ] Unit tests pass

---

### Subtask 1.2: Update handleRecovery() Method

**Objective:** Implement recovery logic that checks conditions and transitions to RAMP_UP.

**File:** `AdaptiveLoadPattern.java`

**Current Code (if exists):**
```java
private double handleRecovery(long elapsedMillis, AdaptiveState current) {
    // Current: Just returns minimumTps, never transitions
    return minimumTps;
}
```

**New Code:**
```java
private double handleRecovery(long elapsedMillis, AdaptiveState current) {
    // Get current conditions
    double errorRate = metricsProvider.getFailureRate() / 100.0;  // Convert percentage to ratio
    double backpressure = backpressureProvider.getBackpressureLevel();
    
    // Recovery conditions: backpressure low OR (error rate low AND backpressure moderate)
    // This is lenient to allow recovery even if error rate is slightly elevated
    boolean canRecover = backpressure < 0.3 
        || (errorRate < errorThreshold && backpressure < 0.5);
    
    if (canRecover) {
        // Calculate recovery TPS: 50% of last known good, but at least minimum
        double lastKnownGoodTps = current.lastKnownGoodTps() > 0 
            ? current.lastKnownGoodTps() 
            : initialTps;
        double recoveryTps = Math.max(minimumTps, lastKnownGoodTps * 0.5);
        
        // Transition to RAMP_UP phase
        transitionPhase(Phase.RAMP_UP, elapsedMillis, recoveryTps);
        
        // Update state with new TPS
        updateState(current -> current
            .withCurrentTps(recoveryTps)
            .withLastKnownGoodTps(lastKnownGoodTps)
        );
        
        return recoveryTps;
    }
    
    // Stay in recovery, maintain minimum TPS
    return minimumTps;
}
```

**Success Criteria:**
- [ ] `handleRecovery()` checks conditions
- [ ] Transitions to RAMP_UP when conditions improve
- [ ] Recovery TPS is 50% of last known good (or minimum)
- [ ] Stays in recovery when conditions poor
- [ ] Unit tests pass

---

### Subtask 1.3: Update State Tracking to Maintain Last Known Good TPS

**Objective:** Update last known good TPS when transitioning to RAMP_DOWN or RECOVERY.

**File:** `AdaptiveLoadPattern.java`

**Changes:**
```java
// In transitionPhase() or when updating state
if (newPhase == Phase.RAMP_DOWN || newPhase == Phase.RECOVERY) {
    // Update last known good TPS before transitioning
    double currentTps = currentState.currentTps();
    if (currentTps > currentState.lastKnownGoodTps()) {
        updateState(state -> state.withLastKnownGoodTps(currentTps));
    }
}
```

**Success Criteria:**
- [ ] Last known good TPS updated when entering RAMP_DOWN
- [ ] Last known good TPS updated when entering RECOVERY
- [ ] Only updates if current TPS is higher
- [ ] Unit tests pass

---

### Subtask 1.4: Add Unit Tests for RECOVERY Transitions

**Objective:** Test that RECOVERY phase transitions correctly.

**File:** `AdaptiveLoadPatternTest.java` (or equivalent)

**Test Cases:**
```java
@Test
void testRecoveryTransitionsToRampUpWhenBackpressureLow() {
    // Setup: Pattern in RECOVERY phase
    // Mock: backpressure = 0.2 (< 0.3)
    // Expected: Transitions to RAMP_UP, returns recovery TPS
}

@Test
void testRecoveryTransitionsToRampUpWhenConditionsGood() {
    // Setup: Pattern in RECOVERY phase
    // Mock: errorRate = 0.5% (< 1%), backpressure = 0.4 (< 0.5)
    // Expected: Transitions to RAMP_UP, returns recovery TPS
}

@Test
void testRecoveryStaysInRecoveryWhenConditionsPoor() {
    // Setup: Pattern in RECOVERY phase
    // Mock: backpressure = 0.8 (> 0.3), errorRate = 2% (> 1%)
    // Expected: Stays in RECOVERY, returns minimumTps
}

@Test
void testRecoveryTpsIsFiftyPercentOfLastKnownGood() {
    // Setup: Pattern in RECOVERY, lastKnownGoodTps = 5000
    // Expected: Recovery TPS = 2500 (50% of 5000)
}

@Test
void testRecoveryTpsIsAtLeastMinimum() {
    // Setup: Pattern in RECOVERY, lastKnownGoodTps = 100, minimumTps = 100
    // Expected: Recovery TPS = 100 (not 50, which would be below minimum)
}
```

**Success Criteria:**
- [ ] All test cases implemented
- [ ] All tests pass
- [ ] Edge cases covered (minimum TPS, zero last known good, etc.)

---

## Task 2: Add Recent Window Failure Rate (Priority: High)

**Estimated Effort:** 1 day  
**Dependencies:** None

### Subtask 2.1: Add getRecentFailureRate() to MetricsProvider Interface

**Objective:** Add method to MetricsProvider interface with default implementation.

**File:** `MetricsProvider.java`

**Changes:**
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

**Success Criteria:**
- [ ] `getRecentFailureRate()` method added
- [ ] Default implementation returns all-time rate
- [ ] JavaDoc documentation added
- [ ] `@since` tag added
- [ ] Backward compatible (default implementation)

---

### Subtask 2.2: Update AdaptiveLoadPattern to Use Recent Window for Recovery

**Objective:** Use recent window failure rate in recovery decisions.

**File:** `AdaptiveLoadPattern.java`

**Changes:**
```java
// In handleRecovery()
private double handleRecovery(long elapsedMillis, AdaptiveState current) {
    // Use recent window failure rate for recovery decisions (last 10 seconds)
    double errorRate = metricsProvider.getRecentFailureRate(10) / 100.0;
    double backpressure = backpressureProvider.getBackpressureLevel();
    
    // ... rest of recovery logic ...
}
```

**Success Criteria:**
- [ ] `handleRecovery()` uses `getRecentFailureRate(10)`
- [ ] Recent window used for recovery decisions
- [ ] All-time rate still used for other decisions (if needed)
- [ ] Unit tests pass

---

### Subtask 2.3: Add Unit Tests for Recent Window Failure Rate

**Objective:** Test recent window failure rate behavior.

**File:** `MetricsProviderTest.java` (or equivalent)

**Test Cases:**
```java
@Test
void testGetRecentFailureRateReturnsAllTimeRateByDefault() {
    // Setup: MetricsProvider with default implementation
    // Expected: getRecentFailureRate(10) returns same as getFailureRate()
}

@Test
void testGetRecentFailureRateCanBeOverridden() {
    // Setup: Custom MetricsProvider that overrides getRecentFailureRate()
    // Expected: Returns recent window rate, not all-time rate
}
```

**Note:** Full recent window calculation testing should be done in provider implementations (e.g., DefaultMetricsProvider), not in interface tests.

**Success Criteria:**
- [ ] Default implementation test passes
- [ ] Override capability verified
- [ ] Tests pass

---

## Task 3: Improve Stability Detection (Priority: Medium)

**Estimated Effort:** 1 day  
**Dependencies:** None

### Subtask 3.1: Add isStable() Method

**Objective:** Create method to detect if current TPS is stable.

**File:** `AdaptiveLoadPattern.java`

**Changes:**
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
 * 
 * @param currentTps the current TPS
 * @param elapsedMillis elapsed time
 * @param current the current adaptive state
 * @return true if stable, false otherwise
 */
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
    
    // Check if TPS has been stable (within tolerance)
    double tpsTolerance = 50.0;  // Allow ±50 TPS variation
    boolean tpsStable = Math.abs(currentTps - current.currentTps()) < tpsTolerance;
    
    return conditionsGood && tpsStable;
}
```

**Success Criteria:**
- [ ] `isStable()` method implemented
- [ ] Checks time at current TPS
- [ ] Checks conditions (error rate, backpressure)
- [ ] Checks TPS stability (within tolerance)
- [ ] Unit tests pass

---

### Subtask 3.2: Update handleRampUp() to Check Stability

**Objective:** Transition to SUSTAIN when stable during RAMP_UP.

**File:** `AdaptiveLoadPattern.java`

**Changes:**
```java
private double handleRampUp(long elapsedMillis, AdaptiveState current) {
    // Check if we've hit max TPS
    if (current.currentTps() >= maxTps) {
        transitionPhase(Phase.SUSTAIN, elapsedMillis, maxTps);
        return maxTps;
    }
    
    // Check if current TPS is stable (NEW)
    if (isStable(current.currentTps(), elapsedMillis, current)) {
        transitionPhase(Phase.SUSTAIN, elapsedMillis, current.currentTps());
        return current.currentTps();
    }
    
    // ... existing ramp-up logic ...
}
```

**Success Criteria:**
- [ ] `handleRampUp()` checks stability
- [ ] Transitions to SUSTAIN when stable
- [ ] Sustains at intermediate TPS levels
- [ ] Unit tests pass

---

### Subtask 3.3: Update handleRampDown() to Check Stability

**Objective:** Transition to SUSTAIN when stable during RAMP_DOWN.

**File:** `AdaptiveLoadPattern.java`

**Changes:**
```java
private double handleRampDown(long elapsedMillis, AdaptiveState current) {
    // Check if current TPS is stable (NEW)
    if (isStable(current.currentTps(), elapsedMillis, current)) {
        transitionPhase(Phase.SUSTAIN, elapsedMillis, current.currentTps());
        return current.currentTps();
    }
    
    // ... existing ramp-down logic ...
}
```

**Success Criteria:**
- [ ] `handleRampDown()` checks stability
- [ ] Transitions to SUSTAIN when stable
- [ ] Sustains at intermediate TPS levels
- [ ] Unit tests pass

---

### Subtask 3.4: Add Unit Tests for Stability Detection

**Objective:** Test stability detection at intermediate TPS levels.

**File:** `AdaptiveLoadPatternTest.java`

**Test Cases:**
```java
@Test
void testStabilityDetectedAtIntermediateTps() {
    // Setup: Pattern at 5000 TPS (not MAX_TPS), conditions good for SUSTAIN_DURATION
    // Expected: Transitions to SUSTAIN at 5000 TPS
}

@Test
void testStabilityRequiresSustainDuration() {
    // Setup: Pattern at 5000 TPS, conditions good but only for 20s (not 30s)
    // Expected: Doesn't transition to SUSTAIN yet
}

@Test
void testStabilityRequiresGoodConditions() {
    // Setup: Pattern at 5000 TPS for SUSTAIN_DURATION, but error rate high
    // Expected: Doesn't transition to SUSTAIN
}

@Test
void testRampUpTransitionsToSustainWhenStable() {
    // Setup: Pattern in RAMP_UP, becomes stable at intermediate TPS
    // Expected: Transitions to SUSTAIN
}

@Test
void testRampDownTransitionsToSustainWhenStable() {
    // Setup: Pattern in RAMP_DOWN, becomes stable at intermediate TPS
    // Expected: Transitions to SUSTAIN
}
```

**Success Criteria:**
- [ ] All test cases implemented
- [ ] All tests pass
- [ ] Edge cases covered

---

## Task 4: Integration Testing (Priority: High)

**Estimated Effort:** 1 day  
**Dependencies:** Tasks 1, 2, 3

### Subtask 4.1: Test Full Recovery Cycle

**Objective:** Verify pattern can recover from RECOVERY phase.

**Test Scenario:**
```java
@Test
void testFullRecoveryCycle() {
    // 1. Start at 1000 TPS (RAMP_UP)
    // 2. Ramp up to 5000 TPS
    // 3. Simulate backpressure → RAMP_DOWN
    // 4. Ramp down to 100 TPS → RECOVERY
    // 5. Simulate conditions improve → RAMP_UP
    // 6. Verify pattern recovers and continues ramping
}
```

**Success Criteria:**
- [ ] Pattern completes full recovery cycle
- [ ] Recovery triggers when conditions improve
- [ ] Pattern continues ramping after recovery
- [ ] Test passes

---

### Subtask 4.2: Test Intermediate Stability

**Objective:** Verify pattern sustains at intermediate TPS levels.

**Test Scenario:**
```java
@Test
void testIntermediateStability() {
    // 1. Start at 1000 TPS (RAMP_UP)
    // 2. Ramp up to 5000 TPS
    // 3. Conditions remain good for SUSTAIN_DURATION
    // 4. Verify pattern sustains at 5000 TPS (not MAX_TPS)
}
```

**Success Criteria:**
- [ ] Pattern sustains at intermediate TPS
- [ ] Doesn't continue ramping to MAX_TPS
- [ ] Test passes

---

### Subtask 4.3: Test Continuous Operation

**Objective:** Verify pattern runs continuously without getting stuck.

**Test Scenario:**
```java
@Test
void testContinuousOperation() {
    // 1. Run pattern for extended period (e.g., 5 minutes)
    // 2. Simulate varying conditions (backpressure, error rate)
    // 3. Verify pattern adapts continuously
    // 4. Verify no getting stuck at low TPS
}
```

**Success Criteria:**
- [ ] Pattern runs continuously
- [ ] Adapts to changing conditions
- [ ] No getting stuck
- [ ] Test passes

---

## Task 5: Documentation and Review (Priority: Medium)

**Estimated Effort:** 0.5 days  
**Dependencies:** Tasks 1, 2, 3, 4

### Subtask 5.1: Update JavaDoc

**Objective:** Update JavaDoc for all changed methods.

**Files:** `AdaptiveLoadPattern.java`, `MetricsProvider.java`

**Changes:**
- Update `handleRecovery()` JavaDoc
- Update `isStable()` JavaDoc
- Update `getRecentFailureRate()` JavaDoc
- Add examples where appropriate

**Success Criteria:**
- [ ] All JavaDoc updated
- [ ] Examples added
- [ ] Clear and comprehensive

---

### Subtask 5.2: Update README/User Guide

**Objective:** Document new features for users.

**Files:** `README.md`, `USER_GUIDE.md` (or equivalent)

**Content:**
- Document RECOVERY phase behavior
- Document recent window failure rate
- Document intermediate stability detection
- Add examples

**Success Criteria:**
- [ ] Documentation updated
- [ ] Examples provided
- [ ] Clear and user-friendly

---

### Subtask 5.3: Code Review

**Objective:** Review all changes for quality and correctness.

**Process:**
- Self-review all code changes
- Run all tests
- Check code coverage
- Review with team

**Success Criteria:**
- [ ] All code reviewed
- [ ] All tests pass
- [ ] Code coverage maintained
- [ ] Team approval

---

## Summary Checklist

### Task 1: RECOVERY Transitions
- [ ] Subtask 1.1: Update AdaptiveState
- [ ] Subtask 1.2: Update handleRecovery()
- [ ] Subtask 1.3: Update state tracking
- [ ] Subtask 1.4: Add unit tests

### Task 2: Recent Window Failure Rate
- [ ] Subtask 2.1: Add getRecentFailureRate()
- [ ] Subtask 2.2: Update AdaptiveLoadPattern
- [ ] Subtask 2.3: Add unit tests

### Task 3: Stability Detection
- [ ] Subtask 3.1: Add isStable() method
- [ ] Subtask 3.2: Update handleRampUp()
- [ ] Subtask 3.3: Update handleRampDown()
- [ ] Subtask 3.4: Add unit tests

### Task 4: Integration Testing
- [ ] Subtask 4.1: Test recovery cycle
- [ ] Subtask 4.2: Test intermediate stability
- [ ] Subtask 4.3: Test continuous operation

### Task 5: Documentation
- [ ] Subtask 5.1: Update JavaDoc
- [ ] Subtask 5.2: Update user guide
- [ ] Subtask 5.3: Code review

---

## Timeline

**Day 1:** Task 1 (RECOVERY transitions)  
**Day 2:** Task 2 (Recent window failure rate)  
**Day 3:** Task 3 (Stability detection)  
**Day 4:** Task 4 (Integration testing)  
**Day 5:** Task 5 (Documentation and review)

**Total:** 5 days

---

## Questions?

If you have questions or need clarifications, please refer to:
- `VAJRAPULSE_LIBRARY_CHANGES_DESIGN.md` for design details
- `COMPLETE_REDESIGN_PRINCIPAL_ENGINEER.md` for overall context
- VajraPulse issue tracker for discussions

---

**Document Status:** Ready for Implementation  
**Last Updated:** 2025-12-05

