# Release 0.9.9: AdaptiveLoadPattern Refactoring Implementation Plan

**Branch**: `0.9.9`  
**Base**: `main`  
**Status**: Planning  
**Created**: 2025-12-05  
**Target Completion**: TBD

---

## Overview

This document provides a detailed, actionable implementation plan for refactoring `AdaptiveLoadPattern` based on the architectural review. The plan is organized by priority and includes specific tasks, acceptance criteria, dependencies, and testing requirements.

**Goal**: Simplify `AdaptiveLoadPattern` while maintaining functionality and improving maintainability.

---

## Implementation Phases

### Phase 6: Enhance MetricsProvider (High Priority) ⭐ Quick Win

**Priority**: High  
**Estimated Effort**: 1 day  
**Complexity Impact**: 🟢 Low  
**Dependencies**: None  
**Can Start**: Immediately

#### Task 6.1: Add `getFailureCount()` to MetricsProvider Interface

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/MetricsProvider.java`

**Acceptance Criteria**:
- [ ] Add `getFailureCount()` method with complete JavaDoc
- [ ] Default implementation returns `0L`
- [ ] Method signature: `long getFailureCount()`
- [ ] JavaDoc includes `@return` tag and use cases
- [ ] No breaking changes to existing methods

**Implementation**:
```java
/**
 * Gets the total number of failed executions.
 * 
 * <p>This method returns the absolute count of failures,
 * which is useful for:
 * <ul>
 *   <li>Alerting on absolute failure thresholds (e.g., "alert if > 100 failures")</li>
 *   <li>Tracking failure trends over time</li>
 *   <li>Debugging and analysis</li>
 * </ul>
 * 
 * @return total failure count
 * @since 0.9.9
 */
default long getFailureCount() {
    return 0L;
}
```

**Testing**:
- [ ] Unit test: Default implementation returns 0
- [ ] Verify JavaDoc compiles without warnings

---

#### Task 6.2: Update CachedSnapshot Record

**File**: `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/MetricsProviderAdapter.java`

**Acceptance Criteria**:
- [ ] Add `failureCount` field to `CachedSnapshot` record
- [ ] Update `from()` method to include failure count
- [ ] Update all `CachedSnapshot` instantiations

**Implementation**:
```java
private record CachedSnapshot(
    double failureRate,
    long totalExecutions,
    long failureCount  // NEW
) {
    static CachedSnapshot from(AggregatedMetrics metrics) {
        return new CachedSnapshot(
            metrics.failureRate(),
            metrics.totalExecutions(),
            metrics.failureCount()  // NEW
        );
    }
}
```

**Testing**:
- [ ] Unit test: `CachedSnapshot.from()` includes failure count
- [ ] Verify all existing tests still pass

---

#### Task 6.3: Implement `getFailureCount()` in MetricsProviderAdapter

**File**: `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/MetricsProviderAdapter.java`

**Acceptance Criteria**:
- [ ] Override `getFailureCount()` method
- [ ] Return failure count from cached snapshot
- [ ] Handle null snapshot gracefully

**Implementation**:
```java
@Override
public long getFailureCount() {
    return getCachedSnapshot().failureCount();
}
```

**Testing**:
- [ ] Unit test: Returns correct failure count from snapshot
- [ ] Unit test: Handles null snapshot (should not occur, but verify)
- [ ] Integration test: Verify failure count matches actual failures

---

#### Task 6.4: Update Tests for Failure Count

**File**: `vajrapulse-core/src/test/groovy/com/vajrapulse/core/engine/MetricsProviderAdapterSpec.groovy`

**Acceptance Criteria**:
- [ ] Add test: `should return failure count from metrics`
- [ ] Add test: `should return zero when no failures`
- [ ] Verify existing tests still pass

**Test Cases**:
```groovy
def "should return failure count from metrics"() {
    given:
    def collector = new MetricsCollector()
    collector.recordSuccess(100_000_000)  // 100ms
    collector.recordFailure(200_000_000)  // 200ms
    collector.recordFailure(300_000_000)  // 300ms
    
    def adapter = new MetricsProviderAdapter(collector)
    
    when:
    def count = adapter.getFailureCount()
    
    then:
    count == 2
}

def "should return zero when no failures"() {
    given:
    def collector = new MetricsCollector()
    collector.recordSuccess(100_000_000)
    
    def adapter = new MetricsProviderAdapter(collector)
    
    when:
    def count = adapter.getFailureCount()
    
    then:
    count == 0
}
```

---

#### Task 6.5: Update Documentation

**Files**:
- `vajrapulse-api/README.md` (if exists)
- `documents/architecture/DESIGN.md` (if MetricsProvider is documented)

**Acceptance Criteria**:
- [ ] Document `getFailureCount()` in API documentation
- [ ] Add example usage
- [ ] Update any relevant guides

---

### Phase 1: State Simplification (High Priority)

**Priority**: High  
**Estimated Effort**: 2-3 days  
**Complexity Reduction**: 🔴 High → 🟡 Medium  
**Dependencies**: None  
**Can Start**: After Phase 6 (or in parallel)

#### Task 1.1: Create CoreState Record

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Create `CoreState` record with 4 fields:
  - `Phase phase`
  - `double currentTps`
  - `long lastAdjustmentTime`
  - `long phaseStartTime`
- [ ] Add compact constructor with validation
- [ ] Add JavaDoc

**Implementation**:
```java
/**
 * Core state of the adaptive load pattern.
 * 
 * <p>This record tracks the essential state information
 * that is always present regardless of phase.
 * 
 * @param phase the current phase
 * @param currentTps the current TPS value
 * @param lastAdjustmentTime timestamp when TPS was last adjusted (millis)
 * @param phaseStartTime timestamp when current phase started (millis)
 */
private record CoreState(
    Phase phase,
    double currentTps,
    long lastAdjustmentTime,
    long phaseStartTime
) {
    CoreState {
        if (phase == null) {
            throw new IllegalArgumentException("Phase must not be null");
        }
        if (currentTps < 0) {
            throw new IllegalArgumentException("Current TPS must be non-negative");
        }
    }
}
```

**Testing**:
- [ ] Unit test: Valid CoreState creation
- [ ] Unit test: Null phase throws exception
- [ ] Unit test: Negative TPS throws exception

---

#### Task 1.2: Create StabilityTracking Record

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Create `StabilityTracking` record with 4 fields:
  - `double stableTps` (-1 if not found)
  - `double candidateTps` (-1 if not tracking)
  - `long candidateStartTime` (-1 if not tracking)
  - `int stableIntervalsCount`
- [ ] Add compact constructor with validation
- [ ] Add JavaDoc
- [ ] Add helper methods: `isTracking()`, `hasStableTps()`

**Implementation**:
```java
/**
 * Stability tracking state for the adaptive load pattern.
 * 
 * <p>This record tracks stability detection, including:
 * <ul>
 *   <li>Found stable TPS (if any)</li>
 *   <li>Current candidate TPS being evaluated</li>
 *   <li>Stability interval count</li>
 * </ul>
 * 
 * @param stableTps the found stable TPS (-1 if not found)
 * @param candidateTps the current candidate TPS (-1 if not tracking)
 * @param candidateStartTime when candidate tracking started (-1 if not tracking)
 * @param stableIntervalsCount consecutive stable intervals
 */
private record StabilityTracking(
    double stableTps,
    double candidateTps,
    long candidateStartTime,
    int stableIntervalsCount
) {
    StabilityTracking {
        if (stableTps < -1 || candidateTps < -1) {
            throw new IllegalArgumentException("TPS values must be >= -1");
        }
        if (stableIntervalsCount < 0) {
            throw new IllegalArgumentException("Stable intervals count must be non-negative");
        }
    }
    
    boolean isTracking() {
        return candidateTps >= 0;
    }
    
    boolean hasStableTps() {
        return stableTps >= 0;
    }
    
    static StabilityTracking empty() {
        return new StabilityTracking(-1, -1, -1, 0);
    }
}
```

**Testing**:
- [ ] Unit test: Valid StabilityTracking creation
- [ ] Unit test: `isTracking()` returns correct value
- [ ] Unit test: `hasStableTps()` returns correct value
- [ ] Unit test: `empty()` creates empty tracking

---

#### Task 1.3: Create RecoveryTracking Record

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Create `RecoveryTracking` record with 2 fields:
  - `double lastKnownGoodTps`
  - `long recoveryStartTime` (-1 if not in recovery)
- [ ] Add compact constructor with validation
- [ ] Add JavaDoc
- [ ] Add helper method: `isInRecovery()`

**Implementation**:
```java
/**
 * Recovery tracking state for the adaptive load pattern.
 * 
 * <p>This record tracks recovery state, including:
 * <ul>
 *   <li>Last known good TPS (highest TPS before recovery)</li>
 *   <li>Recovery start time</li>
 * </ul>
 * 
 * @param lastKnownGoodTps the highest TPS achieved before recovery
 * @param recoveryStartTime when recovery started (-1 if not in recovery)
 */
private record RecoveryTracking(
    double lastKnownGoodTps,
    long recoveryStartTime
) {
    RecoveryTracking {
        if (lastKnownGoodTps < 0) {
            throw new IllegalArgumentException("Last known good TPS must be non-negative");
        }
    }
    
    boolean isInRecovery() {
        return recoveryStartTime >= 0;
    }
    
    static RecoveryTracking empty() {
        return new RecoveryTracking(0, -1);
    }
}
```

**Testing**:
- [ ] Unit test: Valid RecoveryTracking creation
- [ ] Unit test: `isInRecovery()` returns correct value
- [ ] Unit test: `empty()` creates empty tracking

---

#### Task 1.4: Refactor AdaptiveState to Use Composed Records

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Refactor `AdaptiveState` to use composed records:
  - `CoreState core`
  - `StabilityTracking stability` (nullable)
  - `RecoveryTracking recovery` (nullable)
- [ ] Add helper methods for common access patterns
- [ ] Update all state creation code
- [ ] Maintain backward compatibility where possible

**Implementation**:
```java
/**
 * Complete state of the adaptive load pattern.
 * 
 * <p>This record composes core state with optional tracking
 * for stability and recovery.
 * 
 * @param core the core state (always present)
 * @param stability stability tracking (null if not tracking)
 * @param recovery recovery tracking (null if not in recovery)
 */
private record AdaptiveState(
    CoreState core,
    StabilityTracking stability,
    RecoveryTracking recovery
) {
    AdaptiveState {
        if (core == null) {
            throw new IllegalArgumentException("Core state must not be null");
        }
    }
    
    // Helper methods for backward compatibility
    Phase phase() {
        return core.phase();
    }
    
    double currentTps() {
        return core.currentTps();
    }
    
    long lastAdjustmentTime() {
        return core.lastAdjustmentTime();
    }
    
    long phaseStartTime() {
        return core.phaseStartTime();
    }
    
    double stableTps() {
        return stability != null && stability.hasStableTps() ? stability.stableTps() : -1;
    }
    
    double lastKnownGoodTps() {
        return recovery != null ? recovery.lastKnownGoodTps() : 0;
    }
    
    // Builder-style methods for state updates
    AdaptiveState withCore(CoreState newCore) {
        return new AdaptiveState(newCore, stability, recovery);
    }
    
    AdaptiveState withStability(StabilityTracking newStability) {
        return new AdaptiveState(core, newStability, recovery);
    }
    
    AdaptiveState withRecovery(RecoveryTracking newRecovery) {
        return new AdaptiveState(core, stability, newRecovery);
    }
}
```

**Testing**:
- [ ] Unit test: Valid AdaptiveState creation
- [ ] Unit test: Helper methods return correct values
- [ ] Unit test: Builder-style methods create new state correctly
- [ ] Integration test: All existing tests pass with new state structure

---

#### Task 1.5: Update All State Transitions

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Update `checkAndAdjust()` to use new state structure
- [ ] Update `handleRampUp()` to use new state structure
- [ ] Update `handleRampDown()` to use new state structure
- [ ] Update `handleSustain()` to use new state structure
- [ ] Update `handleRecovery()` to use new state structure
- [ ] Update all state initialization code

**Testing**:
- [ ] All existing unit tests pass
- [ ] All existing integration tests pass
- [ ] Verify state transitions work correctly
- [ ] Verify stability tracking works correctly
- [ ] Verify recovery tracking works correctly

---

#### Task 1.6: Update Tests for New State Structure

**Files**:
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/AdaptiveLoadPatternSpec.groovy`
- `vajrapulse-core/src/test/groovy/com/vajrapulse/core/integration/AdaptiveLoadPatternIntegrationSpec.groovy`

**Acceptance Criteria**:
- [ ] Update all tests to use new state structure
- [ ] Add tests for new helper methods
- [ ] Verify all test assertions still work
- [ ] Add tests for edge cases (null stability, null recovery)

**Testing**:
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Code coverage ≥ 90%

---

### Phase 2: Phase Machine Simplification (High Priority)

**Priority**: High  
**Estimated Effort**: 1-2 days  
**Complexity Reduction**: 🔴 High → 🟡 Medium  
**Dependencies**: Phase 1 (State Simplification)  
**Can Start**: After Phase 1

#### Task 2.1: Remove RECOVERY Phase from Phase Enum

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Remove `RECOVERY` from `Phase` enum
- [ ] Update JavaDoc for `Phase` enum
- [ ] Update any switch statements that reference `RECOVERY`

**Implementation**:
```java
public enum Phase {
    /**
     * Ramping up TPS to find optimal load.
     */
    RAMP_UP,
    
    /**
     * Ramping down TPS due to errors or backpressure.
     * Includes recovery behavior when at minimum TPS.
     */
    RAMP_DOWN,
    
    /**
     * Sustaining at stable TPS.
     */
    SUSTAIN
}
```

**Testing**:
- [ ] Compilation succeeds
- [ ] All tests updated to use new phases

---

#### Task 2.2: Move Recovery Logic to RAMP_DOWN Phase

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Remove `handleRecovery()` method
- [ ] Integrate recovery logic into `handleRampDown()`
- [ ] Recovery behavior triggered when `currentTps == minTps`
- [ ] Recovery TPS calculation (50% of lastKnownGoodTps) preserved
- [ ] Recovery exit conditions preserved

**Implementation**:
```java
private AdaptiveState handleRampDown(AdaptiveState state, MetricsSnapshot metrics, long elapsedMillis) {
    // Check if we're at minimum TPS (recovery mode)
    if (state.currentTps() <= minTps) {
        return handleRecoveryAtMinimum(state, metrics, elapsedMillis);
    }
    
    // Normal ramp down logic
    // ...
}

private AdaptiveState handleRecoveryAtMinimum(AdaptiveState state, MetricsSnapshot metrics, long elapsedMillis) {
    // Recovery logic (previously in handleRecovery)
    // ...
}
```

**Testing**:
- [ ] Unit test: Recovery behavior works in RAMP_DOWN phase
- [ ] Unit test: Recovery TPS calculation correct
- [ ] Unit test: Recovery exit conditions work
- [ ] Integration test: Full recovery cycle works

---

#### Task 2.3: Update Phase Transition Logic

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Remove all references to `Phase.RECOVERY`
- [ ] Update `checkAndAdjust()` switch statement
- [ ] Update phase transition conditions
- [ ] Update JavaDoc

**Testing**:
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Verify no references to RECOVERY phase remain

---

#### Task 2.4: Update Tests and Documentation

**Files**:
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/AdaptiveLoadPatternSpec.groovy`
- `vajrapulse-core/src/test/groovy/com/vajrapulse/core/integration/AdaptiveLoadPatternIntegrationSpec.groovy`
- `README.md`
- `CHANGELOG.md`

**Acceptance Criteria**:
- [ ] Update all tests to use 3 phases
- [ ] Remove tests specific to RECOVERY phase
- [ ] Update documentation to reflect 3 phases
- [ ] Update CHANGELOG with breaking change note

**Testing**:
- [ ] All tests pass
- [ ] Documentation is accurate

---

### Phase 3: Decision Logic Extraction (Medium Priority)

**Priority**: Medium  
**Estimated Effort**: 2-3 days  
**Complexity Reduction**: 🔴 High → 🟡 Medium  
**Dependencies**: Phase 2 (Phase Simplification)  
**Can Start**: After Phase 2

#### Task 3.1: Create RampDecisionPolicy Interface

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/RampDecisionPolicy.java`

**Acceptance Criteria**:
- [ ] Create `RampDecisionPolicy` interface
- [ ] Define methods:
  - `boolean shouldRampUp(MetricsSnapshot metrics)`
  - `boolean shouldRampDown(MetricsSnapshot metrics)`
  - `boolean shouldSustain(MetricsSnapshot metrics, StabilityTracking stability)`
- [ ] Add complete JavaDoc

**Implementation**:
```java
/**
 * Policy for making ramp decisions in adaptive load patterns.
 * 
 * <p>This interface encapsulates the decision logic for when to
 * ramp up, ramp down, or sustain TPS based on metrics.
 * 
 * @since 0.9.9
 */
public interface RampDecisionPolicy {
    /**
     * Determines if TPS should be ramped up.
     * 
     * @param metrics current metrics snapshot
     * @return true if should ramp up
     */
    boolean shouldRampUp(MetricsSnapshot metrics);
    
    /**
     * Determines if TPS should be ramped down.
     * 
     * @param metrics current metrics snapshot
     * @return true if should ramp down
     */
    boolean shouldRampDown(MetricsSnapshot metrics);
    
    /**
     * Determines if TPS should be sustained.
     * 
     * @param metrics current metrics snapshot
     * @param stability current stability tracking
     * @return true if should sustain
     */
    boolean shouldSustain(MetricsSnapshot metrics, StabilityTracking stability);
}
```

**Testing**:
- [ ] Interface compiles
- [ ] JavaDoc compiles without warnings

---

#### Task 3.2: Implement DefaultRampDecisionPolicy

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/DefaultRampDecisionPolicy.java`

**Acceptance Criteria**:
- [ ] Implement `RampDecisionPolicy` interface
- [ ] Extract all decision logic from `AdaptiveLoadPattern`
- [ ] Use configurable thresholds
- [ ] Add complete JavaDoc

**Implementation**:
```java
/**
 * Default implementation of ramp decision policy.
 * 
 * <p>This policy uses configurable thresholds for:
 * <ul>
 *   <li>Error rate threshold</li>
 *   <li>Backpressure thresholds (ramp up/down)</li>
 *   <li>Stable intervals required</li>
 * </ul>
 * 
 * @since 0.9.9
 */
public class DefaultRampDecisionPolicy implements RampDecisionPolicy {
    private final double errorThreshold;
    private final double backpressureRampUpThreshold;
    private final double backpressureRampDownThreshold;
    private final int stableIntervalsRequired;
    
    // Constructor and implementation
}
```

**Testing**:
- [ ] Unit test: `shouldRampUp()` returns correct values
- [ ] Unit test: `shouldRampDown()` returns correct values
- [ ] Unit test: `shouldSustain()` returns correct values
- [ ] Unit test: Thresholds are configurable

---

#### Task 3.3: Extract Decision Logic from AdaptiveLoadPattern

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Add `RampDecisionPolicy` field to `AdaptiveLoadPattern`
- [ ] Replace inline conditions with policy calls
- [ ] Remove hard-coded thresholds from decision logic
- [ ] Update constructors to accept policy (or use default)

**Testing**:
- [ ] All existing tests pass
- [ ] Decision logic behavior unchanged
- [ ] Code is simpler and more maintainable

---

#### Task 3.4: Update Tests

**Files**:
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/AdaptiveLoadPatternSpec.groovy`
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/DefaultRampDecisionPolicySpec.groovy` (new)

**Acceptance Criteria**:
- [ ] Create tests for `DefaultRampDecisionPolicy`
- [ ] Update `AdaptiveLoadPattern` tests to use policy
- [ ] Add test for custom policy injection

**Testing**:
- [ ] All unit tests pass
- [ ] Code coverage ≥ 90%

---

### Phase 4: Configuration Consolidation (Medium Priority)

**Priority**: Medium  
**Estimated Effort**: 2-3 days  
**Complexity Reduction**: 🟡 Medium → 🟢 Low  
**Dependencies**: Phase 3 (Decision Logic)  
**Can Start**: After Phase 3

#### Task 4.1: Create AdaptiveConfig Record

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveConfig.java`

**Acceptance Criteria**:
- [ ] Create `AdaptiveConfig` record with all configuration
- [ ] Include all thresholds and parameters
- [ ] Add `defaults()` static method
- [ ] Add validation in compact constructor
- [ ] Add complete JavaDoc

**Implementation**:
```java
/**
 * Configuration for adaptive load pattern.
 * 
 * <p>This record consolidates all configuration parameters
 * for the adaptive load pattern, including thresholds,
 * intervals, and limits.
 * 
 * @since 0.9.9
 */
public record AdaptiveConfig(
    double initialTps,
    double rampIncrement,
    double rampDecrement,
    Duration rampInterval,
    double maxTps,
    double minTps,
    Duration sustainDuration,
    double errorThreshold,
    double backpressureRampUpThreshold,
    double backpressureRampDownThreshold,
    int stableIntervalsRequired,
    double tpsTolerance,
    double recoveryTpsRatio
) {
    AdaptiveConfig {
        // Validation
        if (initialTps < 0) throw new IllegalArgumentException("Initial TPS must be non-negative");
        // ... more validation
    }
    
    public static AdaptiveConfig defaults() {
        return new AdaptiveConfig(
            100.0,                    // initialTps
            50.0,                     // rampIncrement
            100.0,                    // rampDecrement
            Duration.ofMinutes(1),    // rampInterval
            5000.0,                   // maxTps
            10.0,                     // minTps
            Duration.ofMinutes(10),   // sustainDuration
            0.01,                     // errorThreshold (1%)
            0.3,                      // backpressureRampUpThreshold
            0.7,                      // backpressureRampDownThreshold
            3,                        // stableIntervalsRequired
            50.0,                     // tpsTolerance
            0.5                       // recoveryTpsRatio (50%)
        );
    }
}
```

**Testing**:
- [ ] Unit test: Valid config creation
- [ ] Unit test: Invalid config throws exceptions
- [ ] Unit test: `defaults()` returns valid config

---

#### Task 4.2: Update AdaptiveLoadPattern to Use Config

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Replace all hard-coded values with config values
- [ ] Update constructors to accept `AdaptiveConfig`
- [ ] Remove all constants (STABLE_INTERVALS_REQUIRED, etc.)
- [ ] Update all methods to use config

**Testing**:
- [ ] All existing tests pass
- [ ] Behavior unchanged with default config
- [ ] Configurable values work correctly

---

#### Task 4.3: Add Builder Pattern

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Create `Builder` inner class
- [ ] Support fluent API for configuration
- [ ] Validate in `build()` method
- [ ] Support optional parameters (backpressure provider, listener)
- [ ] Add complete JavaDoc

**Implementation**:
```java
public static Builder builder() {
    return new Builder();
}

public static class Builder {
    private AdaptiveConfig config = AdaptiveConfig.defaults();
    private MetricsProvider metricsProvider;
    private BackpressureProvider backpressureProvider;
    private List<AdaptivePatternListener> listeners = new ArrayList<>();
    
    public Builder config(AdaptiveConfig config) {
        this.config = Objects.requireNonNull(config);
        return this;
    }
    
    public Builder initialTps(double tps) {
        // Update config
        return this;
    }
    
    // ... more builder methods
    
    public AdaptiveLoadPattern build() {
        // Validation
        return new AdaptiveLoadPattern(config, metricsProvider, backpressureProvider, listeners);
    }
}
```

**Testing**:
- [ ] Unit test: Builder creates valid pattern
- [ ] Unit test: Builder validates parameters
- [ ] Unit test: Fluent API works correctly

---

#### Task 4.4: Update Tests and Documentation

**Files**:
- All test files
- `README.md`
- `CHANGELOG.md`
- Example files

**Acceptance Criteria**:
- [ ] Update all tests to use builder pattern
- [ ] Update examples to use builder pattern
- [ ] Update README with builder examples
- [ ] Update CHANGELOG with breaking changes

**Testing**:
- [ ] All tests pass
- [ ] Examples compile and run
- [ ] Documentation is accurate

---

### Phase 5: Strategy Pattern for Phases (Low Priority)

**Priority**: Low  
**Estimated Effort**: 2-3 days  
**Complexity Reduction**: 🟡 Medium → 🟢 Low  
**Dependencies**: Phase 4 (Configuration)  
**Can Start**: After Phase 4

#### Task 5.1: Create PhaseStrategy Interface

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/PhaseStrategy.java`

**Acceptance Criteria**:
- [ ] Create `PhaseStrategy` interface
- [ ] Define `handle()` method
- [ ] Add complete JavaDoc

**Implementation**:
```java
/**
 * Strategy for handling a specific phase of adaptive load pattern.
 * 
 * @since 0.9.9
 */
public interface PhaseStrategy {
    /**
     * Handles the phase logic and returns updated state.
     * 
     * @param current current state
     * @param metrics current metrics snapshot
     * @param elapsedMillis elapsed time since start
     * @return updated state
     */
    AdaptiveState handle(AdaptiveState current, MetricsSnapshot metrics, long elapsedMillis);
}
```

**Testing**:
- [ ] Interface compiles
- [ ] JavaDoc compiles without warnings

---

#### Task 5.2: Implement Phase Strategies

**Files**:
- `vajrapulse-api/src/main/java/com/vajrapulse/api/RampUpStrategy.java`
- `vajrapulse-api/src/main/java/com/vajrapulse/api/RampDownStrategy.java`
- `vajrapulse-api/src/main/java/com/vajrapulse/api/SustainStrategy.java`

**Acceptance Criteria**:
- [ ] Implement `RampUpStrategy`
- [ ] Implement `RampDownStrategy`
- [ ] Implement `SustainStrategy`
- [ ] Extract logic from `AdaptiveLoadPattern` methods
- [ ] Add complete JavaDoc

**Testing**:
- [ ] Unit test: Each strategy works correctly
- [ ] Unit test: Strategies are independent

---

#### Task 5.3: Replace Switch Statement with Strategy Lookup

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Create strategy map/lookup
- [ ] Replace switch statement with strategy lookup
- [ ] Update `checkAndAdjust()` to use strategies

**Testing**:
- [ ] All existing tests pass
- [ ] Behavior unchanged

---

#### Task 5.4: Update Tests

**Files**: All test files

**Acceptance Criteria**:
- [ ] Update tests to work with strategies
- [ ] Add tests for strategy injection
- [ ] Verify all tests pass

**Testing**:
- [ ] All tests pass
- [ ] Code coverage ≥ 90%

---

### Phase 7: Add Event Notification (Medium Priority)

**Priority**: Medium  
**Estimated Effort**: 2-3 days  
**Complexity Impact**: 🟢 Low  
**Dependencies**: Phase 4 (Configuration/Builder)  
**Can Start**: After Phase 4 (can be done in parallel with Phase 5)

#### Task 7.1: Create AdaptivePatternListener Interface

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptivePatternListener.java`

**Acceptance Criteria**:
- [ ] Create `AdaptivePatternListener` interface
- [ ] Define event methods with default implementations
- [ ] Create event records:
  - `PhaseTransitionEvent`
  - `TpsChangeEvent`
  - `StabilityDetectedEvent`
  - `RecoveryEvent`
- [ ] Add complete JavaDoc

**Implementation**: See architectural review document for full interface definition.

**Testing**:
- [ ] Interface compiles
- [ ] JavaDoc compiles without warnings
- [ ] Event records are immutable

---

#### Task 7.2: Add Listener Registration to Builder

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Add `listener()` method to builder
- [ ] Support multiple listeners
- [ ] Store listeners in `CopyOnWriteArrayList`
- [ ] Add complete JavaDoc

**Testing**:
- [ ] Unit test: Builder accepts listeners
- [ ] Unit test: Multiple listeners supported

---

#### Task 7.3: Add Notification Methods

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Add `notifyPhaseTransition()` method
- [ ] Add `notifyTpsChange()` method
- [ ] Add `notifyStabilityDetected()` method
- [ ] Add `notifyRecovery()` method
- [ ] Add error handling (listener errors don't break pattern)

**Implementation**:
```java
private void notifyPhaseTransition(Phase from, Phase to, double tps) {
    if (listeners.isEmpty()) return;
    
    PhaseTransitionEvent event = new PhaseTransitionEvent(
        from, to, tps, System.currentTimeMillis()
    );
    
    for (AdaptivePatternListener listener : listeners) {
        try {
            listener.onPhaseTransition(event);
        } catch (Exception e) {
            // Log but don't fail
            System.err.println("Listener error: " + e.getMessage());
        }
    }
}
```

**Testing**:
- [ ] Unit test: Notifications are sent
- [ ] Unit test: Listener errors don't break pattern
- [ ] Unit test: Empty listener list doesn't cause issues

---

#### Task 7.4: Integrate Notifications into State Transitions

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Acceptance Criteria**:
- [ ] Call `notifyPhaseTransition()` on phase changes
- [ ] Call `notifyTpsChange()` on significant TPS changes
- [ ] Call `notifyStabilityDetected()` when stability found
- [ ] Call `notifyRecovery()` when entering recovery

**Testing**:
- [ ] Integration test: Events are fired correctly
- [ ] Integration test: Event timing is correct

---

#### Task 7.5: Update Tests

**Files**: All test files

**Acceptance Criteria**:
- [ ] Add tests for event notifications
- [ ] Add test listener implementation
- [ ] Verify events are fired at correct times

**Testing**:
- [ ] All tests pass
- [ ] Event tests verify correct behavior

---

#### Task 7.6: Update Documentation

**Files**:
- `README.md`
- `documents/guides/` (if exists)
- Example files

**Acceptance Criteria**:
- [ ] Document event listener interface
- [ ] Add example usage
- [ ] Document event types
- [ ] Add use cases

**Testing**:
- [ ] Documentation is accurate
- [ ] Examples compile and run

---

## Implementation Order

### Recommended Sequence

1. **Phase 6** (1 day) - Quick win, no dependencies
2. **Phase 1** (2-3 days) - High priority, enables other phases
3. **Phase 2** (1-2 days) - High priority, depends on Phase 1
4. **Phase 3** (2-3 days) - Medium priority, depends on Phase 2
5. **Phase 4** (2-3 days) - Medium priority, depends on Phase 3
6. **Phase 7** (2-3 days) - Medium priority, depends on Phase 4 (can parallel with Phase 5)
7. **Phase 5** (2-3 days) - Low priority, depends on Phase 4

**Total Estimated Effort**: 12-20 days

### Parallel Work Opportunities

- Phase 6 can be done in parallel with Phase 1
- Phase 7 can be done in parallel with Phase 5

---

## Testing Strategy

### Unit Tests
- Each new class/interface must have unit tests
- Code coverage ≥ 90%
- Test edge cases and error conditions

### Integration Tests
- Verify end-to-end behavior
- Test with real metrics providers
- Test phase transitions
- Test stability detection
- Test recovery behavior

### Regression Tests
- All existing tests must pass
- Behavior should be unchanged (except for breaking changes)
- Performance should not degrade

---

## Acceptance Criteria for Release

### Code Quality
- [ ] All tests pass (unit + integration)
- [ ] Code coverage ≥ 90%
- [ ] Static analysis passes (SpotBugs)
- [ ] No JavaDoc warnings
- [ ] All public APIs documented

### Functionality
- [ ] All existing features work
- [ ] New features work as designed
- [ ] Breaking changes documented
- [ ] Migration guide provided

### Documentation
- [ ] README updated
- [ ] CHANGELOG updated
- [ ] JavaDoc complete
- [ ] Examples updated
- [ ] Architecture review updated

---

## Risk Mitigation

### High Risk Areas
1. **State Migration** - Complex state transitions
   - **Mitigation**: Comprehensive tests, gradual migration
2. **Phase Removal** - Recovery logic must work
   - **Mitigation**: Extensive integration tests
3. **Decision Logic** - Behavior must be preserved
   - **Mitigation**: Policy tests, behavior verification

### Rollback Plan
- Keep old code in separate branch
- Tag before major changes
- Incremental commits for easy rollback

---

## Progress Tracking

### Task Status
- ⬜ Not Started
- 🟡 In Progress
- ✅ Completed
- ❌ Blocked

### Phase Status
- ⬜ Phase 6: Enhance MetricsProvider
- ⬜ Phase 1: State Simplification
- ⬜ Phase 2: Phase Machine Simplification
- ⬜ Phase 3: Decision Logic Extraction
- ⬜ Phase 4: Configuration Consolidation
- ⬜ Phase 5: Strategy Pattern for Phases
- ⬜ Phase 7: Add Event Notification

---

## Notes

- All breaking changes are acceptable (pre-1.0 status)
- Focus on simplicity and maintainability
- Test thoroughly before moving to next phase
- Document as you go
- Keep commits atomic and well-described

---

**Last Updated**: 2025-12-05  
**Status**: Planning Complete, Ready for Implementation

