# VajraPulse Enhancement Task Plan

## Overview

This document provides a detailed implementation plan for enhancing VajraPulse's `AdaptiveLoadPattern` to support continuous operation with recovery and intermediate stability detection.

**Target Version:** 0.9.7  
**Estimated Effort:** 2-4 weeks  
**Priority:** High

## Goals

1. **Remove Terminal COMPLETE Phase** - Replace with RECOVERY phase that can transition back to RAMP_UP
2. **Add Intermediate Stability Detection** - Allow pattern to sustain at intermediate TPS levels (not just MAX_TPS)
3. **Simple Stability Detection** - Track stability without complex sliding windows

## Current State Analysis

### Current AdaptiveLoadPattern Behavior

**Phases:**
- `RAMP_UP` - Increases TPS by `rampIncrement` every `rampInterval`
- `RAMP_DOWN` - Decreases TPS by `rampDecrement` every `rampInterval`
- `SUSTAIN` - Holds TPS constant (only at MAX_TPS or stable point during RAMP_UP)
- `COMPLETE` - Terminal state (cannot recover)

**Problems:**
1. `COMPLETE` phase is terminal - pattern cannot recover
2. Only sustains at `MAX_TPS` - doesn't find stable points at intermediate levels
3. Stability detection only works during `RAMP_UP` phase

## Task Breakdown

### Task 1: Replace COMPLETE with RECOVERY Phase

**Priority:** Critical  
**Estimated Effort:** 1 day

#### Subtasks

1.1. **Update Phase Enum**
- Change `COMPLETE` to `RECOVERY` in `AdaptiveLoadPattern.Phase` enum
- Update all references to `COMPLETE` phase

**Files to Modify:**
- `com/vajrapulse/api/AdaptiveLoadPattern.java`

**Code Changes:**
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

1.2. **Update Phase Transition Logic**
- Change `COMPLETE` transitions to `RECOVERY`
- Add `RECOVERY → RAMP_UP` transition logic
- Add `RECOVERY → RAMP_DOWN` transition logic

**Code Changes:**
```java
// In handleRampDown() method
// Before: Transition to COMPLETE when TPS reaches 0
if (current.currentTps() <= 0) {
    transitionPhase(Phase.COMPLETE, elapsedMillis, 0.0);
    return 0.0;
}

// After: Transition to RECOVERY when TPS reaches minimum
if (current.currentTps() <= minimumTps) {
    transitionPhase(Phase.RECOVERY, elapsedMillis, minimumTps);
    return minimumTps;
}

// Add new method: handleRecovery()
private double handleRecovery(long elapsedMillis, AdaptiveState current) {
    // Check if conditions improved
    double errorRate = metricsProvider.getFailureRate() / 100.0;
    double backpressure = backpressureProvider.getBackpressureLevel();
    
    // Transition to RAMP_UP if conditions improved
    if (errorRate < errorThreshold && backpressure < 0.3) {
        double recoveryTps = Math.max(minimumTps, initialTps * 0.5);
        transitionPhase(Phase.RAMP_UP, elapsedMillis, recoveryTps);
        return recoveryTps;
    }
    
    // Transition to RAMP_DOWN if conditions worsened
    if (errorRate >= errorThreshold || backpressure >= 0.7) {
        double reducedTps = Math.max(0.0, current.currentTps() - rampDecrement);
        transitionPhase(Phase.RAMP_DOWN, elapsedMillis, reducedTps);
        return reducedTps;
    }
    
    // Stay in RECOVERY
    return current.currentTps();
}
```

1.3. **Update calculateTps() Method**
- Add case for `RECOVERY` phase in `calculateTps()` method
- Call `handleRecovery()` when in RECOVERY phase

**Code Changes:**
```java
@Override
public double calculateTps(long elapsedMillis) {
    // ... existing interval check logic ...
    
    return switch (currentState.phase()) {
        case RAMP_UP -> handleRampUp(elapsedMillis, currentState);
        case RAMP_DOWN -> handleRampDown(elapsedMillis, currentState);
        case SUSTAIN -> handleSustain(elapsedMillis, currentState);
        case RECOVERY -> handleRecovery(elapsedMillis, currentState);  // NEW
    };
}
```

#### Acceptance Criteria

- [ ] `COMPLETE` phase removed from enum
- [ ] `RECOVERY` phase added to enum
- [ ] Pattern transitions to `RECOVERY` when TPS reaches minimum (instead of 0)
- [ ] Pattern can transition from `RECOVERY` to `RAMP_UP` when conditions improve
- [ ] Pattern can transition from `RECOVERY` to `RAMP_DOWN` when conditions worsen
- [ ] All existing tests pass
- [ ] New tests added for RECOVERY phase transitions

#### Testing Requirements

**Unit Tests:**
- Test transition to RECOVERY when TPS reaches minimum
- Test RECOVERY → RAMP_UP transition when conditions improve
- Test RECOVERY → RAMP_DOWN transition when conditions worsen
- Test RECOVERY phase maintains minimum TPS

**Integration Tests:**
- Test full cycle: RAMP_UP → RAMP_DOWN → RECOVERY → RAMP_UP
- Test recovery from low TPS when backpressure decreases

### Task 2: Add Intermediate Stability Detection

**Priority:** High  
**Estimated Effort:** 2-3 days

#### Subtasks

2.1. **Add Stability Tracking Fields**
- Add fields to track stability state
- Track: current TPS, stability start time, stability conditions

**Code Changes:**
```java
public class AdaptiveLoadPattern {
    // Existing fields...
    
    // New fields for stability detection
    private double stableTpsCandidate = -1;
    private long stabilityStartTime = -1;
    private static final double TPS_TOLERANCE = 50.0;  // TPS can vary by ±50
    
    // ... existing code ...
}
```

2.2. **Add Stability Detection Method**
- Create method to check if current TPS is stable
- Simple check: same TPS + good conditions for SUSTAIN_DURATION

**Code Changes:**
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
 * @param elapsedMillis elapsed time since start
 * @return true if stable, false otherwise
 */
private boolean isStableAtCurrentTps(double currentTps, long elapsedMillis) {
    double errorRate = metricsProvider.getFailureRate() / 100.0;
    double backpressure = backpressureProvider.getBackpressureLevel();
    
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

2.3. **Update RAMP_UP and RAMP_DOWN to Check Stability**
- Check stability in both RAMP_UP and RAMP_DOWN phases
- Transition to SUSTAIN when stability detected

**Code Changes:**
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
        stableTps = current.currentTps();
        return current.currentTps();
    }
    
    // Continue ramping up
    return current.currentTps();
}

private double handleRampDown(long elapsedMillis, AdaptiveState current) {
    // Check if stable at current TPS (intermediate stability)
    if (isStableAtCurrentTps(current.currentTps(), elapsedMillis)) {
        transitionPhase(Phase.SUSTAIN, elapsedMillis, current.currentTps());
        stableTps = current.currentTps();
        return current.currentTps();
    }
    
    // Continue ramping down
    // ... existing ramp down logic ...
}
```

2.4. **Update SUSTAIN Phase to Allow Ramping**
- SUSTAIN phase should check if conditions changed
- Transition back to RAMP_UP or RAMP_DOWN if conditions change

**Code Changes:**
```java
private double handleSustain(long elapsedMillis, AdaptiveState current) {
    double errorRate = metricsProvider.getFailureRate() / 100.0;
    double backpressure = backpressureProvider.getBackpressureLevel();
    
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

#### Acceptance Criteria

- [ ] Pattern detects stability at intermediate TPS levels (not just MAX_TPS)
- [ ] Pattern transitions to SUSTAIN when stable conditions detected
- [ ] Stability requires: good conditions + same TPS for SUSTAIN_DURATION
- [ ] Pattern can sustain at any TPS level (e.g., 3000, 5000, 8000 TPS)
- [ ] Pattern transitions out of SUSTAIN when conditions change
- [ ] All existing tests pass
- [ ] New tests added for intermediate stability detection

#### Testing Requirements

**Unit Tests:**
- Test stability detection at intermediate TPS (e.g., 3000 TPS)
- Test stability requires good conditions for SUSTAIN_DURATION
- Test stability resets when TPS changes significantly
- Test stability resets when conditions worsen
- Test transition from SUSTAIN back to RAMP_UP/RAMP_DOWN

**Integration Tests:**
- Test full cycle: RAMP_UP → SUSTAIN (at 3000 TPS) → RAMP_UP → SUSTAIN (at 5000 TPS)
- Test pattern finds multiple stable points at different TPS levels

### Task 3: Add Minimum TPS Configuration

**Priority:** Medium  
**Estimated Effort:** 1 day

#### Subtasks

3.1. **Add minimumTps Field**
- Add `minimumTps` field to `AdaptiveLoadPattern`
- Default to 0.0 (backward compatible)

**Code Changes:**
```java
public class AdaptiveLoadPattern {
    private final double minimumTps;  // NEW
    
    public AdaptiveLoadPattern(
            double initialTps,
            double rampIncrement,
            double rampDecrement,
            Duration rampInterval,
            double maxTps,
            Duration sustainDuration,
            double errorThreshold,
            MetricsProvider metricsProvider,
            BackpressureProvider backpressureProvider) {
        // ... existing initialization ...
        this.minimumTps = 0.0;  // Default: no minimum
    }
    
    // Optional: Add constructor with minimumTps parameter
    public AdaptiveLoadPattern(
            // ... existing parameters ...
            double minimumTps) {  // NEW parameter
        // ... existing initialization ...
        this.minimumTps = minimumTps;
    }
}
```

3.2. **Enforce Minimum TPS in RAMP_DOWN**
- Ensure TPS never goes below `minimumTps` in RAMP_DOWN phase

**Code Changes:**
```java
private double handleRampDown(long elapsedMillis, AdaptiveState current) {
    double newTps = current.currentTps() - rampDecrement;
    
    // Enforce minimum TPS
    if (newTps < minimumTps) {
        newTps = minimumTps;
    }
    
    // If at minimum, transition to RECOVERY
    if (newTps <= minimumTps) {
        transitionPhase(Phase.RECOVERY, elapsedMillis, minimumTps);
        return minimumTps;
    }
    
    // Continue ramping down
    // ... existing logic ...
}
```

#### Acceptance Criteria

- [ ] `minimumTps` field added (defaults to 0.0 for backward compatibility)
- [ ] TPS never goes below `minimumTps` in RAMP_DOWN
- [ ] Pattern transitions to RECOVERY when TPS reaches `minimumTps`
- [ ] All existing tests pass
- [ ] New tests added for minimum TPS enforcement

#### Testing Requirements

**Unit Tests:**
- Test TPS doesn't go below minimumTps
- Test transition to RECOVERY when minimumTps reached
- Test backward compatibility (minimumTps = 0.0)

## Implementation Details

### Code Structure

**Files to Modify:**
1. `com/vajrapulse/api/AdaptiveLoadPattern.java`
   - Update Phase enum
   - Add RECOVERY phase handling
   - Add stability detection
   - Add minimum TPS enforcement

**New Methods:**
- `handleRecovery(long elapsedMillis, AdaptiveState current)` - Handles RECOVERY phase
- `isStableAtCurrentTps(double currentTps, long elapsedMillis)` - Checks stability

**Modified Methods:**
- `calculateTps(long elapsedMillis)` - Add RECOVERY case
- `handleRampUp(long elapsedMillis, AdaptiveState current)` - Add stability check
- `handleRampDown(long elapsedMillis, AdaptiveState current)` - Add stability check, minimum TPS
- `handleSustain(long elapsedMillis, AdaptiveState current)` - Allow transitions out

### Dependencies

**No New Dependencies Required**
- Uses existing `MetricsProvider` and `BackpressureProvider`
- No external libraries needed

### Backward Compatibility

**Breaking Changes:**
- ❌ None - `COMPLETE` phase removal is acceptable (pre-1.0 project)

**Deprecations:**
- None

**Migration:**
- Existing code continues to work
- `COMPLETE` phase replaced with `RECOVERY` (automatic migration)

## Testing Strategy

### Unit Tests

**Test Coverage Requirements:**
- ≥90% code coverage (project requirement)
- All new methods must have tests
- All phase transitions must be tested

**Key Test Cases:**
1. RECOVERY phase transitions
2. Intermediate stability detection
3. Minimum TPS enforcement
4. Stability reset conditions

### Integration Tests

**Test Scenarios:**
1. Full recovery cycle: RAMP_UP → RAMP_DOWN → RECOVERY → RAMP_UP
2. Intermediate stability: RAMP_UP → SUSTAIN (at 3000 TPS) → RAMP_UP → SUSTAIN (at 5000 TPS)
3. Multiple stable points at different TPS levels

### Performance Tests

**Requirements:**
- No performance regression
- Stability detection overhead < 1% of total execution time
- Memory overhead < 1MB

## Documentation Requirements

### JavaDoc

**Required JavaDoc:**
- All new methods
- All modified methods (update existing JavaDoc)
- New enum values
- New fields

**Example:**
```java
/**
 * Handles the RECOVERY phase, checking if conditions have improved.
 * 
 * <p>In RECOVERY phase, the pattern checks if error rate and backpressure
 * have improved. If conditions are good (error rate < threshold and
 * backpressure < 0.3), it transitions to RAMP_UP. If conditions worsen,
 * it transitions to RAMP_DOWN.
 * 
 * @param elapsedMillis elapsed time since pattern start
 * @param current the current adaptive state
 * @return the TPS for the RECOVERY phase
 */
private double handleRecovery(long elapsedMillis, AdaptiveState current) {
    // ...
}
```

### User Documentation

**Update:**
- `README.md` - Document RECOVERY phase
- `CHANGELOG.md` - Document changes
- Examples - Update examples to show RECOVERY phase

## Release Checklist

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
- [ ] Published to Maven Central
- [ ] Release notes published

### Post-Release

- [ ] Monitor for issues
- [ ] Collect feedback
- [ ] Plan next iteration

## Success Criteria

### Functional

- [ ] Pattern can recover from RECOVERY phase automatically
- [ ] Pattern finds stable points at intermediate TPS levels
- [ ] Pattern never stops (no terminal states)
- [ ] Pattern sustains at optimal TPS levels

### Non-Functional

- [ ] No performance regression
- [ ] Backward compatible (existing code works)
- [ ] Code coverage ≥90%
- [ ] All tests pass
- [ ] Documentation complete

## Risk Assessment

### Risk 1: Breaking Existing Code

**Risk:** Code using `COMPLETE` phase might break

**Mitigation:**
- Pre-1.0 project (breaking changes acceptable)
- Document migration path
- Provide examples

### Risk 2: Performance Impact

**Risk:** Stability detection adds overhead

**Mitigation:**
- Simple stability check (no complex calculations)
- Measure performance before/after
- Optimize if needed

### Risk 3: Stability Detection Too Sensitive

**Risk:** Pattern sustains too frequently

**Mitigation:**
- Tune TPS_TOLERANCE if needed
- Monitor in production
- Adjust based on feedback

## Timeline

**Week 1:**
- Day 1-2: Task 1 (RECOVERY phase)
- Day 3-5: Task 2 (Stability detection)

**Week 2:**
- Day 1: Task 3 (Minimum TPS)
- Day 2-3: Testing
- Day 4-5: Documentation and release prep

**Total:** 2 weeks

## Dependencies

**Blockers:**
- None

**Dependencies:**
- None (self-contained changes)

**Blocks:**
- Vortex 0.0.5 (can proceed in parallel)
- Testing project integration (waits for this)

