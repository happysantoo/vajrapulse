# Release 0.9.6 Plan

## Status: In Progress

## Primary Goal
Fix AdaptiveLoadPattern hanging issue and establish comprehensive test coverage to prove correctness.

## Critical Issues

### 1. AdaptiveLoadPattern Hanging Issue
**Problem**: AdaptiveLoadPattern does one iteration and then hangs.

**Investigation Areas**:
- State machine initialization and timing
- Interaction between `AdaptiveLoadPattern.calculateTps()` and `RateController.waitForNext()`
- Metrics provider behavior when metrics are empty/not ready
- Phase transitions and timing edge cases
- Potential infinite loop or blocking condition

**Root Cause Analysis Needed**:
- [ ] Trace execution flow from first `calculateTps()` call
- [ ] Verify metrics provider returns valid values when metrics are empty
- [ ] Check if `checkAndAdjust()` is called too early or incorrectly
- [ ] Verify `RateController` handles adaptive pattern correctly
- [ ] Check for race conditions in state updates

## Test Strategy

### 1. Unit Tests for AdaptiveLoadPattern
**File**: `vajrapulse-api/src/test/groovy/com/vajrapulse/api/AdaptiveLoadPatternSpec.groovy`

**Coverage**:
- [ ] State initialization
- [ ] First call to `calculateTps()` with various elapsed times
- [ ] Phase transitions (RAMP_UP → RAMP_DOWN → SUSTAIN)
- [ ] TPS adjustments at ramp intervals
- [ ] Error threshold detection
- [ ] Stable point detection (3 consecutive stable intervals)
- [ ] Max TPS handling
- [ ] COMPLETE phase when stable point not found
- [ ] Thread safety (concurrent calls to `calculateTps()`)
- [ ] Edge cases (zero elapsed time, very large elapsed time, etc.)

### 2. Integration Tests with ExecutionEngine
**File**: `vajrapulse-core/src/test/groovy/com/vajrapulse/core/integration/AdaptiveLoadPatternExecutionSpec.groovy`

**Coverage**:
- [ ] Full execution cycle with success-only tasks
- [ ] Execution with failure rate that triggers RAMP_DOWN
- [ ] Execution that finds stable point and transitions to SUSTAIN
- [ ] Execution that reaches max TPS
- [ ] Execution that fails to find stable point (COMPLETE phase)
- [ ] Metrics collection during adaptive execution
- [ ] Phase transition metrics
- [ ] TPS adjustment metrics

### 3. End-to-End Test
**File**: `vajrapulse-core/src/test/groovy/com/vajrapulse/core/integration/AdaptiveLoadPatternE2ESpec.groovy`

**Coverage**:
- [ ] Complete test run that demonstrates full adaptive cycle
- [ ] Verifies pattern works correctly with real task execution
- [ ] Validates metrics are collected correctly
- [ ] Proves pattern doesn't hang

## Additional Tasks for 0.9.6

### 2. Metrics Provider Robustness
- [ ] Handle empty metrics gracefully (return 0.0 failure rate when no executions)
- [ ] Add validation for metrics provider state
- [ ] Improve error messages when metrics are not available

### 3. Adaptive Pattern Configuration Validation
- [ ] Validate that `rampInterval` is reasonable (not too small)
- [ ] Validate that `initialTps` is positive and reasonable
- [ ] Add configuration sanity checks

### 4. Documentation Improvements
- [ ] Update AdaptiveLoadPattern JavaDoc with usage examples
- [ ] Add troubleshooting guide for common issues
- [ ] Document expected behavior in different scenarios

### 5. Performance Optimizations
- [ ] Review metrics query frequency (currently 100ms batch interval)
- [ ] Optimize state update operations if needed
- [ ] Profile adaptive pattern overhead

### 6. Code Quality
- [ ] Ensure all tests pass with ≥90% coverage
- [ ] Fix any SpotBugs findings
- [ ] Update CHANGELOG.md

### 7. RateController Integration
- [ ] Verify RateController handles AdaptiveLoadPattern correctly
- [ ] Check for timing issues when TPS changes rapidly
- [ ] Ensure proper sleep/wait behavior with adaptive patterns

## Implementation Steps

1. **Create comprehensive unit tests** - Prove state machine works correctly
2. **Create integration tests** - Prove pattern works with ExecutionEngine
3. **Create end-to-end test** - Prove pattern works in real scenario
4. **Fix hanging issue** - Based on test findings
5. **Add robustness improvements** - Handle edge cases
6. **Update documentation** - Improve user guidance
7. **Run full test suite** - Ensure everything works
8. **Update CHANGELOG** - Document changes

## Success Criteria

- [ ] All tests pass (unit, integration, e2e)
- [ ] Code coverage ≥90% for AdaptiveLoadPattern
- [ ] AdaptiveLoadPattern works correctly without hanging
- [ ] Full adaptive cycle can be demonstrated (RAMP_UP → RAMP_DOWN → SUSTAIN)
- [ ] Metrics are collected correctly
- [ ] Documentation is updated
- [ ] CHANGELOG is updated

## Timeline

- **Phase 1**: Create tests and diagnose issue (Day 1)
- **Phase 2**: Fix hanging issue (Day 1-2)
- **Phase 3**: Add robustness improvements (Day 2)
- **Phase 4**: Documentation and final validation (Day 2-3)

