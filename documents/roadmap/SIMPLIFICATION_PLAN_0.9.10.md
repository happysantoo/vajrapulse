# VajraPulse Simplification Plan - 0.9.9

**Date**: 2025-12-12  
**Version**: 0.9.9  
**Status**: Completed  
**Inspired By**: Vortex 0.0.9 simplification (unified API, removed backpressure package)

---

## Executive Summary

This plan outlines a comprehensive simplification strategy for VajraPulse, inspired by the vortex 0.0.9 release approach. The goal is to reduce complexity, remove incomplete features, unify APIs, and improve maintainability while maintaining all core functionality.

**Key Principles** (from vortex 0.0.9):
- ✅ **Unified APIs** - Single method instead of multiple variants
- ✅ **Remove incomplete features** - Don't keep TODOs and half-implemented code
- ✅ **Simplify package structure** - Better organization, fewer packages
- ✅ **Clean code > Backwards compatibility** - Pre-1.0 allows breaking changes

---

## Phase 1: Remove Incomplete Features (High Priority)

### 1.1 Remove Incomplete Backpressure Handling

**Current State**:
- `BackpressureHandlingResult.RETRY` - Not implemented (TODO in ExecutionEngine)
- `BackpressureHandlingResult.DEGRADED` - Not implemented (TODO in ExecutionEngine)
- `BackpressureHandlers.retry()` - Returns RETRY but no actual retry logic
- Complex switch statement in ExecutionEngine with unimplemented cases

**Simplification**:
1. **Remove RETRY and DEGRADED handling results**
   - Remove `RETRY` and `DEGRADED` from `BackpressureHandlingResult` enum
   - Remove `BackpressureHandlers.retry()` method
   - Remove `BackpressureHandlers.DEGRADE` constant
   - Simplify switch statement to only handle: DROPPED, REJECTED, QUEUED, ACCEPTED

2. **Simplify BackpressureHandlers**
   - Keep only: DROP, QUEUE, REJECT, threshold()
   - Remove retry() method
   - Remove DEGRADE constant

3. **Update ExecutionEngine**
   - Remove RETRY and DEGRADED cases from switch
   - Simplify backpressure handling logic

**Impact**:
- ✅ Removes ~50 lines of incomplete code
- ✅ Eliminates confusion about unimplemented features
- ✅ Simpler API surface
- ✅ Clearer behavior (no "TODO" cases)

**Breaking Changes**:
- `BackpressureHandlingResult.RETRY` removed
- `BackpressureHandlingResult.DEGRADED` removed
- `BackpressureHandlers.retry()` removed
- `BackpressureHandlers.DEGRADE` removed

**Migration**:
- Users using RETRY/DEGRADED: Remove or replace with QUEUE/REJECT
- Users using retry(): Implement retry in task logic or use QUEUE

---

### 1.2 Simplify BackpressureHandler Interface

**Current State**:
- `BackpressureHandler.handle()` takes 3 parameters: iteration, backpressureLevel, context
- Most handlers ignore iteration parameter
- Context contains queue depth and max queue depth

**Simplification**:
1. **Reduce parameters** - Most handlers only need backpressure level
   ```java
   // Current
   BackpressureHandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context);
   
   // Simplified
   BackpressureHandlingResult handle(double backpressureLevel, BackpressureContext context);
   ```

2. **Make iteration optional** - If needed, can be added to context
   ```java
   public record BackpressureContext(
       long queueDepth,
       long maxQueueDepth,
       long iteration  // Add if needed
   ) {}
   ```

**Impact**:
- ✅ Simpler interface
- ✅ Less parameter passing
- ✅ Clearer intent

---

## Phase 2: Unify APIs (Medium Priority)

### 2.1 Unify Backpressure Handling API

**Current State**:
- Multiple handler types: DROP, QUEUE, REJECT, threshold(), retry()
- Inconsistent usage patterns

**Simplification**:
1. **Single unified handler creation**
   ```java
   // Current
   BackpressureHandlers.DROP
   BackpressureHandlers.QUEUE
   BackpressureHandlers.REJECT
   BackpressureHandlers.threshold(0.5, 0.7, 0.9)
   
   // Unified (inspired by vortex submit())
   BackpressureHandlers.create(Strategy.DROP)
   BackpressureHandlers.create(Strategy.QUEUE)
   BackpressureHandlers.create(Strategy.REJECT)
   BackpressureHandlers.create(Strategy.threshold(0.5, 0.7, 0.9))
   ```

2. **Or keep simple constants** - If constants are clearer, keep them
   - Keep DROP, QUEUE, REJECT as constants
   - Only unify threshold() if it makes sense

**Decision**: Keep constants (they're already simple), but remove incomplete ones

---

### 2.2 Simplify ExecutionEngine Builder

**Current State**:
- Builder with many optional parameters
- Some parameters rarely used

**Simplification**:
1. **Extract common configurations to factory methods**
   ```java
   // Current
   ExecutionEngine.builder()
       .withTask(task)
       .withLoadPattern(pattern)
       .withMetricsCollector(collector)
       .build();
   
   // Simplified - factory methods for common cases
   ExecutionEngine.create(task, pattern, collector)
   ExecutionEngine.createWithBackpressure(task, pattern, collector, handler, threshold)
   ```

2. **Keep builder for advanced cases**
   - Builder still available for full control
   - Factory methods for 90% of use cases

**Impact**:
- ✅ Simpler API for common cases
- ✅ Less boilerplate
- ✅ Clearer intent

---

## Phase 3: Package Simplification (Medium Priority)

### 3.1 Consolidate Backpressure Package

**Current State**:
- `com.vajrapulse.core.backpressure` package
- 3 classes: BackpressureHandlers, CompositeBackpressureProvider, QueueBackpressureProvider
- BackpressureProvider interface in API

**Simplification** (inspired by vortex removing backpressure package):
1. **Move handlers to metrics package**
   - `BackpressureHandlers` → `com.vajrapulse.core.metrics.BackpressureHandlers`
   - Aligns with `BackpressureHandler` interface location

2. **Move providers to metrics package**
   - `CompositeBackpressureProvider` → `com.vajrapulse.core.metrics.CompositeBackpressureProvider`
   - `QueueBackpressureProvider` → `com.vajrapulse.core.metrics.QueueBackpressureProvider`

3. **Remove backpressure package**
   - All backpressure code in metrics package
   - Simpler package structure

**Impact**:
- ✅ One less package
- ✅ Better organization (backpressure is a metric)
- ✅ Easier to find related code

---

### 3.2 Consolidate Engine Package

**Current State**:
- `com.vajrapulse.core.engine` package
- Multiple classes: ExecutionEngine, TaskExecutor, RateController, ShutdownManager, etc.

**Analysis**:
- Package is well-organized
- No immediate simplification needed
- Keep as-is

---

## Phase 4: Code Simplification (Low Priority)

### 4.1 Simplify ExecutionEngine Switch Statement

**Current State**:
- Large switch statement for backpressure handling
- Multiple cases with similar logic

**Simplification**:
1. **Extract handler logic to separate method**
   ```java
   private void handleBackpressure(long iteration, double backpressure, BackpressureContext context) {
       BackpressureHandlingResult result = backpressureHandler.handle(backpressure, context);
       switch (result) {
           case DROPPED -> handleDropped(iteration);
           case REJECTED -> handleRejected(iteration, backpressure);
           case QUEUED, ACCEPTED -> {} // Normal processing
       }
   }
   ```

2. **Extract individual handlers**
   ```java
   private void handleDropped(long iteration) { ... }
   private void handleRejected(long iteration, double backpressure) { ... }
   ```

**Impact**:
- ✅ More readable
- ✅ Easier to test
- ✅ Clearer separation of concerns

---

### 4.2 Simplify Metrics Collection

**Current State**:
- Multiple methods for recording different metric types
- Some duplication in metric recording

**Simplification**:
1. **Unify metric recording** - Single method with type parameter
2. **Extract common patterns** - Reduce duplication

**Note**: This is lower priority - current implementation is reasonable

---

## Phase 5: Documentation and Examples (Ongoing)

### 5.1 Update Examples

- Update all examples to use simplified APIs
- Remove references to RETRY/DEGRADED
- Add examples of simplified backpressure handling

### 5.2 Update Documentation

- Update JavaDoc for removed methods
- Add migration guide
- Update architecture docs

---

## Implementation Plan

### Step 1: Remove Incomplete Features (Week 1)

#### 1.1 Remove RETRY and DEGRADED from Enum

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/metrics/BackpressureHandlingResult.java`

**Changes**:
- Remove `RETRY` enum value
- Remove `DEGRADED` enum value
- Update JavaDoc to remove references

**Before**:
```java
public enum BackpressureHandlingResult {
    DROPPED,
    QUEUED,
    REJECTED,
    RETRY,      // REMOVE
    DEGRADED,    // REMOVE
    ACCEPTED
}
```

**After**:
```java
public enum BackpressureHandlingResult {
    DROPPED,
    QUEUED,
    REJECTED,
    ACCEPTED
}
```

---

#### 1.2 Remove RETRY and DEGRADED from BackpressureHandlers

**File**: `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/BackpressureHandlers.java`

**Changes**:
- Remove `DEGRADE` constant (lines 94-99)
- Remove `retry()` method (lines 119-127)
- Remove `RetryBackpressureHandler` inner class (lines 173-188)
- Update JavaDoc to remove references

**Code to Remove**:
- `public static final BackpressureHandler DEGRADE = ...` (~6 lines)
- `public static BackpressureHandler retry(...)` method (~9 lines)
- `private static final class RetryBackpressureHandler` (~16 lines)

**Total**: ~31 lines removed

---

#### 1.3 Simplify ExecutionEngine Switch Statement

**File**: `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java`

**Changes**:
- Remove `case RETRY:` block (lines 564-567)
- Remove `case DEGRADED:` block (lines 569-572)
- Simplify switch to only handle: DROPPED, REJECTED, QUEUED, ACCEPTED

**Before**:
```java
switch (result) {
    case DROPPED:
        // ... handle dropped
        continue;
    case REJECTED:
        // ... handle rejected
        continue;
    case RETRY:
        // TODO: Implement retry mechanism
        // For now, queue it
        break;
    case DEGRADED:
        // TODO: Implement degradation mechanism
        // For now, queue it
        break;
    case QUEUED:
    case ACCEPTED:
    default:
        // Normal processing
        break;
}
```

**After**:
```java
switch (result) {
    case DROPPED:
        // ... handle dropped
        continue;
    case REJECTED:
        // ... handle rejected
        continue;
    case QUEUED:
    case ACCEPTED:
    default:
        // Normal processing
        break;
}
```

**Total**: ~8 lines removed

---

#### 1.4 Update BackpressureHandler Interface

**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/metrics/BackpressureHandler.java`

**Changes**:
- Remove `iteration` parameter from `handle()` method
- Update JavaDoc

**Before**:
```java
BackpressureHandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context);
```

**After**:
```java
BackpressureHandlingResult handle(double backpressureLevel, BackpressureContext context);
```

**Rationale**: Most handlers ignore the iteration parameter. If needed, it can be added to `BackpressureContext` later.

---

#### 1.5 Update All Handler Implementations

**Files to Update**:
1. `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/BackpressureHandlers.java`
   - Update DROP, QUEUE, REJECT, threshold() handlers
   - Remove iteration parameter from all implementations

2. Any custom handler implementations (if any)

**Pattern**:
```java
// Before
public BackpressureHandlingResult handle(long iteration, double backpressureLevel, BackpressureContext context) {
    return BackpressureHandlingResult.DROPPED;
}

// After
public BackpressureHandlingResult handle(double backpressureLevel, BackpressureContext context) {
    return BackpressureHandlingResult.DROPPED;
}
```

---

#### 1.6 Update ExecutionEngine Handler Call

**File**: `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java`

**Changes**:
- Remove `currentIteration` parameter from `handle()` call

**Before**:
```java
BackpressureHandlingResult result = backpressureHandler.handle(currentIteration, backpressure, context);
```

**After**:
```java
BackpressureHandlingResult result = backpressureHandler.handle(backpressure, context);
```

---

#### 1.7 Update Tests

**File**: `vajrapulse-core/src/test/groovy/com/vajrapulse/core/backpressure/BackpressureHandlersSpec.groovy`

**Changes**:
- Remove test: `"DEGRADE handler should return DEGRADED result"` (lines 47-57)
- Remove test: `"retry handler should return RETRY result"` (lines 59-69)
- Remove test: `"retry handler should throw exception when retryDelay is null"` (lines 71-77)
- Remove test: `"retry handler should throw exception when retryDelay is negative"` (lines 79-85)
- Remove test: `"retry handler should throw exception when maxRetries is negative"` (lines 87-93)
- Update all remaining tests to remove `iteration` parameter from `handle()` calls

**Pattern**:
```groovy
// Before
def result = handler.handle(1L, 0.8, context)

// After
def result = handler.handle(0.8, context)
```

**Total**: ~5 test methods removed, ~10 test calls updated

---

#### 1.8 Update JavaDoc and Documentation

**Files to Update**:
1. `vajrapulse-api/src/main/java/com/vajrapulse/api/metrics/BackpressureHandler.java`
   - Remove RETRY/DEGRADED from JavaDoc list
   - Update method signature in JavaDoc

2. `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/BackpressureHandlers.java`
   - Remove RETRY/DEGRADE from JavaDoc list
   - Remove retry() method JavaDoc

3. `CHANGELOG.md`
   - Document breaking changes

**Estimated Impact**: ~100 lines removed, 3 classes simplified, 5 test methods removed

---

### Step 2: Package Reorganization (Week 1-2)

#### 2.1 Move BackpressureHandlers to Metrics Package

**Source**: `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/BackpressureHandlers.java`  
**Target**: `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/BackpressureHandlers.java`

**Changes**:
- Change package from `com.vajrapulse.core.backpressure` to `com.vajrapulse.core.metrics`
- Update imports if needed

---

#### 2.2 Move CompositeBackpressureProvider to Metrics Package

**Source**: `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/CompositeBackpressureProvider.java`  
**Target**: `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/CompositeBackpressureProvider.java`

**Changes**:
- Change package from `com.vajrapulse.core.backpressure` to `com.vajrapulse.core.metrics`
- Update imports if needed

---

#### 2.3 Move QueueBackpressureProvider to Metrics Package

**Source**: `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/QueueBackpressureProvider.java`  
**Target**: `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/QueueBackpressureProvider.java`

**Changes**:
- Change package from `com.vajrapulse.core.backpressure` to `com.vajrapulse.core.metrics`
- Update imports if needed

---

#### 2.4 Update All Imports

**Files to Update** (search for `com.vajrapulse.core.backpressure`):
1. `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java`
   - Update import for `BackpressureHandlers` (if used)

2. `vajrapulse-core/src/test/groovy/com/vajrapulse/core/backpressure/BackpressureHandlersSpec.groovy`
   - Move test file to `vajrapulse-core/src/test/groovy/com/vajrapulse/core/metrics/BackpressureHandlersSpec.groovy`
   - Update package declaration
   - Update imports

3. Any other files importing from `com.vajrapulse.core.backpressure`

**Command to find all references**:
```bash
grep -r "com.vajrapulse.core.backpressure" vajrapulse-core/
```

---

#### 2.5 Delete Backpressure Package

**Action**: Delete directory `vajrapulse-core/src/main/java/com/vajrapulse/core/backpressure/`

**Verification**:
- Run `grep -r "com.vajrapulse.core.backpressure" .` to ensure no remaining references
- Run tests to verify everything still works

**Estimated Impact**: 1 package removed, better organization, ~3 import updates

---

### Step 3: Code Simplification (Week 2)

#### 3.1 Extract Backpressure Handling Methods

**File**: `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java`

**Current Code** (lines ~527-583):
```java
// Check backpressure before submitting
if (backpressureHandler != null) {
    double backpressure = getBackpressureLevel();
    if (backpressure >= backpressureThreshold) {
        BackpressureContext context = createBackpressureContext(backpressure);
        BackpressureHandlingResult result = backpressureHandler.handle(backpressure, context);
        
        switch (result) {
            case DROPPED:
                // ... 10 lines of code
                continue;
            case REJECTED:
                // ... 15 lines of code
                continue;
            case QUEUED:
            case ACCEPTED:
            default:
                break;
        }
    }
}
```

**Refactored Code**:
```java
// Check backpressure before submitting
if (backpressureHandler != null) {
    double backpressure = getBackpressureLevel();
    if (backpressure >= backpressureThreshold) {
        BackpressureContext context = createBackpressureContext(backpressure);
        BackpressureHandlingResult result = backpressureHandler.handle(backpressure, context);
        
        if (handleBackpressureResult(result, currentIteration, backpressure)) {
            continue; // Request was handled (dropped/rejected)
        }
        // Otherwise, proceed with normal submission
    }
}

// ... later in class ...

/**
 * Handles backpressure result and returns true if request should be skipped.
 * 
 * @param result the backpressure handling result
 * @param iteration the current iteration number
 * @param backpressure the backpressure level
 * @return true if request should be skipped, false to proceed normally
 */
private boolean handleBackpressureResult(
    BackpressureHandlingResult result,
    long iteration,
    double backpressure
) {
    return switch (result) {
        case DROPPED -> {
            handleDropped(iteration, backpressure);
            yield true;
        }
        case REJECTED -> {
            handleRejected(iteration, backpressure);
            yield true;
        }
        case QUEUED, ACCEPTED -> false;
    };
}

/**
 * Handles a dropped request.
 */
private void handleDropped(long iteration, double backpressure) {
    pendingExecutions.decrementAndGet();
    metricsCollector.updateQueueSize(pendingExecutions.get());
    metricsCollector.recordDroppedRequest();
    logger.debug("Request {} dropped due to backpressure {} runId={}", 
        iteration, String.format("%.2f", backpressure), runId);
}

/**
 * Handles a rejected request.
 */
private void handleRejected(long iteration, double backpressure) {
    pendingExecutions.decrementAndGet();
    metricsCollector.updateQueueSize(pendingExecutions.get());
    if (shouldRecordMetrics) {
        metricsCollector.recordRejectedRequest();
        metricsCollector.record(new ExecutionMetrics(
            System.nanoTime(),
            System.nanoTime(),
            TaskResult.failure(new RuntimeException("Request rejected due to backpressure: " + String.format("%.2f", backpressure))),
            iteration
        ));
    }
    logger.debug("Request {} rejected due to backpressure {} runId={}", 
        iteration, String.format("%.2f", backpressure), runId);
}
```

**Benefits**:
- Main loop is cleaner (~10 lines vs ~50 lines)
- Each handler is a separate method (easier to test)
- Switch statement is simpler (pattern matching)
- Better separation of concerns

---

#### 3.2 Add Factory Methods (Optional)

**File**: `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java`

**Add factory methods for common cases**:

```java
/**
 * Creates an ExecutionEngine with default configuration.
 * 
 * @param task the task to execute
 * @param loadPattern the load pattern to use
 * @param metricsCollector the metrics collector
 * @return configured ExecutionEngine
 */
public static ExecutionEngine create(
    TaskLifecycle task,
    LoadPattern loadPattern,
    MetricsCollector metricsCollector
) {
    return builder()
        .withTask(task)
        .withLoadPattern(loadPattern)
        .withMetricsCollector(metricsCollector)
        .build();
}

/**
 * Creates an ExecutionEngine with backpressure handling.
 * 
 * @param task the task to execute
 * @param loadPattern the load pattern to use
 * @param metricsCollector the metrics collector
 * @param backpressureHandler the backpressure handler
 * @param backpressureThreshold the backpressure threshold (0.0 to 1.0)
 * @return configured ExecutionEngine
 */
public static ExecutionEngine createWithBackpressure(
    TaskLifecycle task,
    LoadPattern loadPattern,
    MetricsCollector metricsCollector,
    BackpressureHandler backpressureHandler,
    double backpressureThreshold
) {
    return builder()
        .withTask(task)
        .withLoadPattern(loadPattern)
        .withMetricsCollector(metricsCollector)
        .withBackpressureHandler(backpressureHandler)
        .withBackpressureThreshold(backpressureThreshold)
        .build();
}
```

**Benefits**:
- Simpler API for 90% of use cases
- Less boilerplate
- Builder still available for advanced cases

**Note**: This is optional - builder pattern is already good. Only add if it significantly improves ergonomics.

**Estimated Impact**: ~50 lines simplified, better readability, optional factory methods

---

### Step 4: Testing and Validation (Week 2-3)

#### 4.1 Update All Tests

**Test Files to Update**:

1. **BackpressureHandlersSpec.groovy** (already covered in Step 1.7)
   - Remove RETRY/DEGRADED tests
   - Update handler calls to remove iteration parameter

2. **ExecutionEngineSpec.groovy**
   - Update any tests that use RETRY/DEGRADED
   - Update any tests that call `handle()` with iteration parameter
   - Add tests for extracted methods (if needed)

3. **Integration Tests**
   - Search for any usage of RETRY/DEGRADED
   - Update to use QUEUE/REJECT instead

**Command to find test references**:
```bash
grep -r "RETRY\|DEGRADED" vajrapulse-*/src/test/
grep -r "\.retry(" vajrapulse-*/src/test/
grep -r "BackpressureHandlers\.DEGRADE" vajrapulse-*/src/test/
```

---

#### 4.2 Update Examples

**Example Directories to Check**:
- `examples/adaptive-load-test/`
- `examples/http-load-test/`
- Any other examples using backpressure

**Changes**:
- Remove any usage of `BackpressureHandlers.retry()`
- Remove any usage of `BackpressureHandlers.DEGRADE`
- Remove any usage of `BackpressureHandlingResult.RETRY` or `DEGRADED`
- Update handler calls to remove iteration parameter

**Command to find example references**:
```bash
grep -r "RETRY\|DEGRADED" examples/
grep -r "\.retry(" examples/
```

---

#### 4.3 Update Documentation

**Files to Update**:

1. **JavaDoc**
   - `vajrapulse-api/src/main/java/com/vajrapulse/api/metrics/BackpressureHandler.java`
   - `vajrapulse-api/src/main/java/com/vajrapulse/api/metrics/BackpressureHandlingResult.java`
   - `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/BackpressureHandlers.java` (after move)

2. **CHANGELOG.md**
   - Add section for 0.9.10 breaking changes
   - Document removed APIs
   - Document package changes

3. **Migration Guide** (create if needed)
   - `documents/guides/MIGRATION_0.9.10.md`
   - Document how to migrate from RETRY/DEGRADED
   - Document package import changes
   - Document interface signature changes

4. **Architecture Docs**
   - `documents/architecture/DESIGN.md` (if it mentions RETRY/DEGRADED)
   - Update any diagrams or descriptions

---

#### 4.4 Run Full Test Suite

**Commands**:
```bash
# Run all tests
./gradlew test --rerun-tasks

# Verify coverage
./gradlew jacocoTestCoverageVerification --rerun-tasks

# Run static analysis
./gradlew spotbugsMain

# Full check
./gradlew check --rerun-tasks
```

**Success Criteria**:
- ✅ All tests pass
- ✅ Coverage ≥90%
- ✅ No SpotBugs issues
- ✅ No compilation errors
- ✅ No deprecation warnings (if applicable)

---

## Success Metrics

### Code Metrics
- **Lines of code removed**: ~150-200 lines
- **Packages reduced**: 1 package removed
- **Classes simplified**: 3-4 classes
- **TODOs removed**: 2-3 TODO items

### Quality Metrics
- **Test coverage**: Maintain ≥90%
- **Build success**: All tests passing
- **API clarity**: Simpler, more intuitive APIs
- **Documentation**: Complete and up-to-date

---

## Breaking Changes Summary

### Removed APIs
- `BackpressureHandlingResult.RETRY`
- `BackpressureHandlingResult.DEGRADED`
- `BackpressureHandlers.retry(Duration, int)`
- `BackpressureHandlers.DEGRADE`
- `BackpressureHandler.handle(long iteration, ...)` → `handle(double backpressureLevel, ...)`

### Package Changes
- `com.vajrapulse.core.backpressure.*` → `com.vajrapulse.core.metrics.*`

### Migration Required
- Users using RETRY/DEGRADED: Remove or replace
- Users using retry(): Implement in task or use QUEUE
- Users importing backpressure package: Update imports

---

## Risk Assessment

### Low Risk
- ✅ Removing incomplete features (RETRY/DEGRADED)
- ✅ Package reorganization (mechanical changes)
- ✅ Code extraction (refactoring, no behavior change)

### Medium Risk
- ⚠️ Changing BackpressureHandler interface (requires updates)
- ⚠️ Package moves (requires import updates)

### Mitigation
- Comprehensive test coverage
- Clear migration guide
- Deprecation period (if needed)
- Pre-1.0 allows breaking changes

---

## Timeline

**Week 1**: Remove incomplete features, simplify interfaces
**Week 2**: Package reorganization, code simplification
**Week 3**: Testing, documentation, examples
**Week 4**: Review, finalize, release 0.9.10

---

## References

- [Vortex 0.0.9 Release Notes](https://github.com/happysantoo/vortex/releases/tag/v0.0.9)
- [VajraPulse Architecture Analysis](./ARCHITECTURE_DESIGN_ANALYSIS.md)
- [AdaptiveLoadPattern Review](./ADAPTIVE_LOAD_PATTERN_ARCHITECTURAL_REVIEW.md)

