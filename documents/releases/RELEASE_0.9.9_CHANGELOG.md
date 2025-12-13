# Release 0.9.9 Changelog

**Date**: 2025-12-13  
**Version**: 0.9.9  
**Status**: Major Refactoring Release

---

## Executive Summary

Release 0.9.9 delivers a **major architectural refactoring** focused on simplification, maintainability, and clarity. This release includes:

- **Complete redesign of AdaptiveLoadPattern** with unified state model and simplified decision logic
- **Backpressure architecture simplification** - moved exclusively to AdaptiveLoadPattern
- **Removed redundant components** - ClientMetrics, phase strategies, nested state records
- **Renamed MetricsPipeline to LoadTestRunner** for better clarity
- **Unified and centralized** metrics provider caching, TPS calculations, and validation logic
- **Fixed all SpotBugs issues** and test failures

**⚠️ Breaking Changes**: This release contains breaking changes. See [Migration Guide](#migration-guide) for details.

---

## Table of Contents

1. [Major Changes](#major-changes)
2. [Simplification Changes](#simplification-changes)
3. [Backpressure Integration](#backpressure-integration)
4. [AdaptiveLoadPattern Changes](#adaptiveloadpattern-changes)
5. [Removed Components](#removed-components)
6. [Migration Guide](#migration-guide)
7. [API Changes](#api-changes)
8. [Examples and Usage](#examples-and-usage)

---

## Major Changes

### 1. AdaptiveLoadPattern Complete Redesign

**What Changed**:
- **Unified State Model**: Replaced 4 nested state records (`AdaptiveState`, `AdaptiveCoreState`, `AdaptiveStabilityTracking`, `AdaptiveRecoveryTracking`) with a single `AdaptiveState` record
- **Simplified Phase Logic**: Removed phase strategy pattern, integrated all logic directly into `AdaptiveLoadPattern`
- **Reduced Code**: From 1,275 lines to ~991 lines (22% reduction)
- **Builder Pattern**: Simplified builder API with clearer configuration
- **Decision Policy**: Extracted decision logic to `RampDecisionPolicy` interface with `DefaultRampDecisionPolicy` implementation

**Why Changed**:
- Previous design had excessive complexity with nested state records
- Phase strategies only handled partial logic, creating confusion
- High cognitive load for developers
- Difficult to test and maintain

**Impact**:
- ✅ Simpler mental model
- ✅ Better testability
- ✅ Clearer state transitions
- ⚠️ **Breaking change** - API changes required

### 2. Backpressure Architecture Simplification

**What Changed**:
- **Moved backpressure handling exclusively to AdaptiveLoadPattern**
- **Removed backpressure handling from ExecutionEngine**
- **Moved backpressure classes to dedicated package**: `com.vajrapulse.api.backpressure`
- **Removed BackpressureHandler from ExecutionEngine** - no longer needed

**Why Changed**:
- Previous design had two-layer backpressure system (pattern-level + request-level)
- ExecutionEngine's backpressure handling only worked with AdaptiveLoadPattern (instanceof check)
- Redundant responsibility - pattern already adjusts TPS based on backpressure
- Unnecessary complexity for non-adaptive patterns

**Impact**:
- ✅ Simpler architecture - single responsibility
- ✅ Better separation of concerns
- ✅ Easier to test and configure
- ⚠️ **Breaking change** - ExecutionEngine no longer accepts BackpressureHandler

### 3. Removed Redundant Components

**What Changed**:
- **Removed ClientMetrics** - redundant with BackpressureProvider
- **Removed phase strategy classes** - `PhaseStrategy`, `RampUpStrategy`, `RampDownStrategy`, `SustainStrategy`
- **Removed nested state records** - `AdaptiveCoreState`, `AdaptiveStabilityTracking`, `AdaptiveRecoveryTracking`
- **Removed PhaseContext** - no longer needed

**Why Changed**:
- ClientMetrics tracked same data as BackpressureProvider but wasn't integrated
- Phase strategies only handled partial logic
- Nested state records added unnecessary complexity

**Impact**:
- ✅ Less code to maintain
- ✅ Clearer responsibilities
- ⚠️ **Breaking change** - ClientMetrics removed from AggregatedMetrics

### 4. MetricsPipeline Renamed to LoadTestRunner

**What Changed**:
- **Renamed `MetricsPipeline` to `LoadTestRunner`**
- Updated all references in code, tests, and examples
- Updated Javadoc examples

**Why Changed**:
- Name "MetricsPipeline" didn't accurately describe the class purpose
- "LoadTestRunner" better reflects its role as a high-level convenience runner

**Impact**:
- ✅ Better naming clarity
- ⚠️ **Breaking change** - class renamed

### 5. Unified and Centralized Utilities

**What Changed**:
- **Created `TimeConstants`** - centralized time-related constants
- **Created `LoadPatternValidator`** - centralized validation logic
- **Unified metrics provider caching** - `MetricsProviderAdapter` now uses `CachedMetricsProvider`
- **Centralized TPS calculations** - `AggregatedMetrics` delegates to `TpsCalculator`

**Why Changed**:
- Scattered constants and validation logic
- Duplicate caching logic in multiple places
- Inconsistent TPS calculations

**Impact**:
- ✅ Consistent behavior across codebase
- ✅ Easier to maintain
- ✅ No breaking changes

---

## Simplification Changes

### Code Reduction Summary

| Component | Before | After | Reduction |
|-----------|--------|-------|-----------|
| AdaptiveLoadPattern | 1,275 lines | 991 lines | 22% |
| State Records | 4 nested records | 1 unified record | 75% |
| Phase Strategies | 3 classes (~300 lines) | 0 (integrated) | 100% |
| Configuration | 13 parameters | 8 parameters | 38% |
| Builder Code | 400+ lines | ~200 lines | 50% |

### Architectural Simplifications

1. **Unified State Model**
   - **Before**: 4 nested records with overlapping concerns
   - **After**: Single `AdaptiveState` record with clear fields
   - **Benefit**: Easier to reason about, no nested state navigation

2. **Integrated Phase Logic**
   - **Before**: Phase strategies + main adjustment logic (dual responsibility)
   - **After**: All logic in `AdaptiveLoadPattern` (single responsibility)
   - **Benefit**: Clearer flow, easier to understand

3. **Simplified Configuration**
   - **Before**: 13 parameters including thresholds
   - **After**: 8 parameters, thresholds moved to `RampDecisionPolicy`
   - **Benefit**: Clearer separation of concerns, easier to configure

4. **Removed Redundancy**
   - **Before**: ClientMetrics + BackpressureProvider (overlapping)
   - **After**: Only BackpressureProvider (unified)
   - **Benefit**: Single source of truth, less confusion

---

## Backpressure Integration

### Overview

Backpressure is now **exclusively handled by AdaptiveLoadPattern**. The pattern reads backpressure signals and adjusts TPS accordingly. There is no request-level backpressure handling in ExecutionEngine.

### How to Include Backpressure

#### Step 1: Create a BackpressureProvider

Implement the `BackpressureProvider` interface to provide backpressure signals from your infrastructure:

```java
import com.vajrapulse.api.backpressure.BackpressureProvider;

public class MyBackpressureProvider implements BackpressureProvider {
    private final Queue<?> myQueue;
    private final int maxQueueSize;
    
    public MyBackpressureProvider(Queue<?> queue, int maxSize) {
        this.myQueue = queue;
        this.maxQueueSize = maxSize;
    }
    
    @Override
    public double getBackpressureLevel() {
        // Returns 0.0 (no backpressure) to 1.0 (maximum backpressure)
        int currentSize = myQueue.size();
        if (currentSize >= maxQueueSize) {
            return 1.0;  // Maximum backpressure
        }
        return (double) currentSize / maxQueueSize;  // Proportional
    }
    
    @Override
    public String getBackpressureDescription() {
        return String.format("Queue depth: %d/%d", myQueue.size(), maxQueueSize);
    }
}
```

#### Step 2: Use Built-in Providers

VajraPulse provides built-in backpressure providers:

**QueueBackpressureProvider** (for any queue):
```java
import com.vajrapulse.core.metrics.QueueBackpressureProvider;
import java.util.concurrent.BlockingQueue;

BlockingQueue<Task> taskQueue = ...;
int maxQueueSize = 1000;

BackpressureProvider provider = new QueueBackpressureProvider(
    () -> (long) taskQueue.size(),
    maxQueueSize
);
```

**CompositeBackpressureProvider** (combine multiple sources):
```java
import com.vajrapulse.core.metrics.CompositeBackpressureProvider;

BackpressureProvider queueProvider = new QueueBackpressureProvider(...);
BackpressureProvider hikariProvider = new HikariCpBackpressureProvider(...);

BackpressureProvider combined = CompositeBackpressureProvider.builder()
    .addProvider(queueProvider)
    .addProvider(hikariProvider)
    .build();
```

#### Step 3: Configure AdaptiveLoadPattern with Backpressure

```java
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern;
import com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy;

// Create backpressure provider
BackpressureProvider backpressureProvider = new MyBackpressureProvider(...);

// Create adaptive pattern with backpressure
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(100.0)
    .rampIncrement(50.0)
    .rampDecrement(100.0)
    .rampInterval(Duration.ofMinutes(1))
    .maxTps(5000.0)
    .minTps(10.0)
    .sustainDuration(Duration.ofMinutes(10))
    .stableIntervalsRequired(3)
    .metricsProvider(metricsProvider)
    .backpressureProvider(backpressureProvider)  // ← Add backpressure
    .decisionPolicy(new DefaultRampDecisionPolicy(
        0.01,   // Error threshold (1%)
        0.3,    // Backpressure ramp up threshold (30%)
        0.7     // Backpressure ramp down threshold (70%)
    ))
    .build();
```

#### Step 4: Configure Decision Policy Thresholds

The `DefaultRampDecisionPolicy` accepts backpressure thresholds:

```java
DefaultRampDecisionPolicy policy = new DefaultRampDecisionPolicy(
    0.01,   // Error threshold: ramp down if error rate >= 1%
    0.3,    // Backpressure ramp up threshold: ramp up if backpressure < 30%
    0.7     // Backpressure ramp down threshold: ramp down if backpressure >= 70%
);
```

**Threshold Behavior**:
- **Error threshold**: If error rate exceeds this, pattern ramps down
- **Backpressure ramp up threshold**: If backpressure is below this, pattern can ramp up
- **Backpressure ramp down threshold**: If backpressure exceeds this, pattern ramps down

### How It Works

1. **AdaptiveLoadPattern** calls `backpressureProvider.getBackpressureLevel()` during each adjustment interval
2. **RampDecisionPolicy** uses backpressure level along with error rate to make decisions:
   - If backpressure < ramp up threshold → can ramp up (if no errors)
   - If backpressure >= ramp down threshold → ramp down
   - If error rate >= error threshold → ramp down
3. **TPS is adjusted** based on the decision
4. **No request-level handling** - ExecutionEngine no longer checks backpressure

### Example: HikariCP Connection Pool

```java
import com.vajrapulse.examples.hikaricp.HikariCpBackpressureProvider;
import com.zaxxer.hikari.HikariDataSource;

HikariDataSource dataSource = ...;

// Create HikariCP backpressure provider
BackpressureProvider hikariProvider = new HikariCpBackpressureProvider(
    dataSource,
    0.8  // Threshold: 80% pool utilization triggers backpressure
);

// Use with adaptive pattern
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(50.0)
    .rampIncrement(25.0)
    .rampDecrement(50.0)
    .rampInterval(Duration.ofSeconds(30))
    .maxTps(Double.POSITIVE_INFINITY)
    .minTps(5.0)
    .sustainDuration(Duration.ofMinutes(5))
    .stableIntervalsRequired(3)
    .metricsProvider(metricsProvider)
    .backpressureProvider(hikariProvider)  // ← HikariCP backpressure
    .decisionPolicy(new DefaultRampDecisionPolicy(
        0.01,   // 1% error threshold
        0.3,    // Ramp up if backpressure < 30%
        0.7     // Ramp down if backpressure >= 70%
    ))
    .build();
```

### Migration from 0.9.8

**Before (0.9.8)**:
```java
// Pattern configuration
AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(...);

// Engine configuration (separate!)
ExecutionEngine engine = ExecutionEngine.builder()
    .withLoadPattern(pattern)
    .withBackpressureHandler(BackpressureHandlers.DROP)  // ← Removed
    .withBackpressureThreshold(0.7)                      // ← Removed
    .build();
```

**After (0.9.9)**:
```java
// All backpressure configuration in pattern
BackpressureProvider provider = new MyBackpressureProvider(...);
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .backpressureProvider(provider)  // ← Configure here
    .decisionPolicy(new DefaultRampDecisionPolicy(
        0.01,   // Error threshold
        0.3,    // Backpressure ramp up threshold
        0.7     // Backpressure ramp down threshold
    ))
    .build();

// Engine has no backpressure configuration
ExecutionEngine engine = ExecutionEngine.builder()
    .withLoadPattern(pattern)
    .build();
```

---

## AdaptiveLoadPattern Changes

### API Changes

#### Constructor → Builder Pattern

**Before (0.9.8)**:
```java
AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    100.0,                          // initialTps
    50.0,                           // rampIncrement
    100.0,                          // rampDecrement
    Duration.ofMinutes(1),          // rampInterval
    5000.0,                         // maxTps
    Duration.ofMinutes(10),         // sustainDuration
    0.01,                           // errorThreshold
    metricsProvider                 // MetricsProvider
);
```

**After (0.9.9)**:
```java
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(100.0)
    .rampIncrement(50.0)
    .rampDecrement(100.0)
    .rampInterval(Duration.ofMinutes(1))
    .maxTps(5000.0)
    .minTps(10.0)                    // ← New: minimum TPS
    .sustainDuration(Duration.ofMinutes(10))
    .stableIntervalsRequired(3)      // ← New: explicit stable intervals
    .metricsProvider(metricsProvider)
    .backpressureProvider(provider)  // ← New: optional backpressure
    .decisionPolicy(new DefaultRampDecisionPolicy(0.01))  // ← Changed: thresholds in policy
    .listener(listener)              // ← New: optional listener
    .build();
```

#### Configuration Parameters

**Removed Parameters** (moved to `RampDecisionPolicy`):
- `errorThreshold` → Now in `DefaultRampDecisionPolicy(errorThreshold)`
- `backpressureThreshold` → Now in `DefaultRampDecisionPolicy(..., rampUpThreshold, rampDownThreshold)`

**New Parameters**:
- `minTps` - Minimum TPS limit (prevents TPS from going to 0)
- `stableIntervalsRequired` - Explicit count of stable intervals required (was hardcoded to 3)
- `backpressureProvider` - Optional backpressure provider
- `decisionPolicy` - Decision policy for ramp decisions (replaces thresholds)
- `listener` - Optional event listener

**Simplified Parameters**:
- Reduced from 13 parameters to 8 core parameters
- Thresholds moved to `RampDecisionPolicy` for better separation

#### State Access Methods

**Before (0.9.8)**:
```java
AdaptiveState state = pattern.getState();
AdaptivePhase phase = state.getPhase();
double currentTps = state.getCurrentTps();
double stableTps = state.getStableTps();
```

**After (0.9.9)**:
```java
AdaptivePhase phase = pattern.getCurrentPhase();
double currentTps = pattern.getCurrentTps();
double stableTps = pattern.getStableTps();
long transitions = pattern.getPhaseTransitionCount();
```

**Changes**:
- Removed `getState()` - state is now internal
- Added convenience methods: `getCurrentPhase()`, `getCurrentTps()`, `getStableTps()`, `getPhaseTransitionCount()`
- State record is no longer exposed (encapsulation)

### Decision Policy

**New Interface**: `RampDecisionPolicy`

```java
public interface RampDecisionPolicy {
    AdjustmentDecision decideRampUp(AdaptiveState current, MetricsSnapshot metrics);
    AdjustmentDecision decideRampDown(AdaptiveState current, MetricsSnapshot metrics);
    boolean shouldSustain(int stableIntervalsCount, int requiredIntervals);
}
```

**Default Implementation**: `DefaultRampDecisionPolicy`

```java
// Simple: error threshold only
DefaultRampDecisionPolicy policy = new DefaultRampDecisionPolicy(0.01);

// Full: error + backpressure thresholds
DefaultRampDecisionPolicy policy = new DefaultRampDecisionPolicy(
    0.01,   // Error threshold
    0.3,    // Backpressure ramp up threshold
    0.7     // Backpressure ramp down threshold
);
```

**Custom Implementation**:
```java
public class MyDecisionPolicy implements RampDecisionPolicy {
    @Override
    public AdjustmentDecision decideRampUp(AdaptiveState current, MetricsSnapshot metrics) {
        // Custom logic
        if (metrics.failureRate() < 0.005 && metrics.backpressureLevel() < 0.2) {
            return AdjustmentDecision.rampUp(current.currentTps() + 10.0);
        }
        return AdjustmentDecision.noChange();
    }
    
    // ... implement other methods
}
```

### Event Listeners

**New Feature**: `AdaptivePatternListener` interface

```java
public interface AdaptivePatternListener {
    void onPhaseTransition(PhaseTransitionEvent event);
    void onTpsChange(TpsChangeEvent event);
    void onStabilityDetected(StabilityDetectedEvent event);
    void onRecovery(RecoveryEvent event);
}
```

**Built-in Listener**: `LoggingAdaptivePatternListener`

```java
import com.vajrapulse.api.pattern.adaptive.LoggingAdaptivePatternListener;

AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .metricsProvider(metricsProvider)
    .listener(new LoggingAdaptivePatternListener())  // ← Logs all events
    .build();
```

**Custom Listener**:
```java
public class MyListener implements AdaptivePatternListener {
    @Override
    public void onPhaseTransition(PhaseTransitionEvent event) {
        System.out.println("Phase: " + event.from() + " -> " + event.to());
    }
    
    // ... implement other methods
}
```

---

## Removed Components

### 1. ClientMetrics

**Removed**: `com.vajrapulse.core.metrics.ClientMetrics`

**Reason**: Redundant with `BackpressureProvider`. ClientMetrics tracked connection pools, queues, and timeouts but wasn't integrated with adaptive decision-making. BackpressureProvider provides the same functionality in a more generic, extensible way.

**Migration**:
- If you were using `ClientMetrics` for reporting, use `BackpressureProvider` instead
- If you were using it for adaptive decisions, it's now integrated via `BackpressureProvider`

**Before**:
```java
ClientMetrics metrics = ClientMetrics.builder()
    .connectionPoolActive(10)
    .connectionPoolIdle(5)
    .queueDepth(100)
    .build();
metricsCollector.recordClientMetrics(metrics);
```

**After**:
```java
BackpressureProvider provider = new QueueBackpressureProvider(
    () -> (long) queue.size(),
    maxQueueSize
);
// Use with AdaptiveLoadPattern
```

### 2. Phase Strategy Classes

**Removed**:
- `PhaseStrategy` (interface)
- `RampUpStrategy`
- `RampDownStrategy`
- `SustainStrategy`
- `PhaseContext`

**Reason**: Phase strategies only handled partial logic, creating confusion. All logic is now integrated directly into `AdaptiveLoadPattern` for clarity.

**Migration**: No migration needed - logic is now in `AdaptiveLoadPattern`.

### 3. Nested State Records

**Removed**:
- `AdaptiveCoreState`
- `AdaptiveStabilityTracking`
- `AdaptiveRecoveryTracking`

**Replaced by**: Single `AdaptiveState` record

**Migration**: If you were accessing nested state, use the new convenience methods:
- `pattern.getCurrentPhase()`
- `pattern.getCurrentTps()`
- `pattern.getStableTps()`

### 4. ExecutionEngine Backpressure Methods

**Removed from ExecutionEngine**:
- `withBackpressureHandler(BackpressureHandler)`
- `withBackpressureThreshold(double)`
- `getBackpressureLevel()` (internal method)

**Reason**: Backpressure is now handled exclusively by `AdaptiveLoadPattern`.

**Migration**: Configure backpressure in `AdaptiveLoadPattern` instead.

---

## Migration Guide

### Step 1: Update AdaptiveLoadPattern Construction

**Before**:
```java
AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    100.0, Duration.ofMinutes(1), 50.0, 100.0, 5000.0,
    Duration.ofMinutes(10), 0.01, metricsProvider
);
```

**After**:
```java
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(100.0)
    .rampIncrement(50.0)
    .rampDecrement(100.0)
    .rampInterval(Duration.ofMinutes(1))
    .maxTps(5000.0)
    .minTps(10.0)  // ← Add minimum TPS
    .sustainDuration(Duration.ofMinutes(10))
    .stableIntervalsRequired(3)  // ← Explicit (was hardcoded)
    .metricsProvider(metricsProvider)
    .decisionPolicy(new DefaultRampDecisionPolicy(0.01))  // ← Error threshold moved here
    .build();
```

### Step 2: Remove ExecutionEngine Backpressure Configuration

**Before**:
```java
ExecutionEngine engine = ExecutionEngine.builder()
    .withTask(task)
    .withLoadPattern(pattern)
    .withBackpressureHandler(BackpressureHandlers.DROP)  // ← Remove
    .withBackpressureThreshold(0.7)                      // ← Remove
    .build();
```

**After**:
```java
ExecutionEngine engine = ExecutionEngine.builder()
    .withTask(task)
    .withLoadPattern(pattern)
    // Backpressure configuration removed - handled by pattern
    .build();
```

### Step 3: Add Backpressure to AdaptiveLoadPattern (if needed)

**Before**:
```java
// Backpressure was configured in ExecutionEngine
```

**After**:
```java
BackpressureProvider provider = new MyBackpressureProvider(...);
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    // ... other config ...
    .backpressureProvider(provider)  // ← Add here
    .decisionPolicy(new DefaultRampDecisionPolicy(
        0.01,   // Error threshold
        0.3,    // Backpressure ramp up threshold
        0.7     // Backpressure ramp down threshold
    ))
    .build();
```

### Step 4: Update State Access

**Before**:
```java
AdaptiveState state = pattern.getState();
AdaptivePhase phase = state.getPhase();
double tps = state.getCurrentTps();
```

**After**:
```java
AdaptivePhase phase = pattern.getCurrentPhase();
double tps = pattern.getCurrentTps();
```

### Step 5: Remove ClientMetrics Usage

**Before**:
```java
ClientMetrics metrics = ClientMetrics.builder()
    .connectionPoolActive(10)
    .queueDepth(100)
    .build();
metricsCollector.recordClientMetrics(metrics);
```

**After**:
```java
// Use BackpressureProvider instead
BackpressureProvider provider = new QueueBackpressureProvider(
    () -> (long) queue.size(),
    maxQueueSize
);
// Use with AdaptiveLoadPattern
```

### Step 6: Update MetricsPipeline References

**Before**:
```java
import com.vajrapulse.worker.pipeline.MetricsPipeline;

MetricsPipeline pipeline = MetricsPipeline.builder()
    .withTask(task)
    .withLoadPattern(pattern)
    .build();
```

**After**:
```java
import com.vajrapulse.worker.pipeline.LoadTestRunner;

LoadTestRunner runner = LoadTestRunner.builder()
    .withTask(task)
    .withLoadPattern(pattern)
    .build();
```

### Step 7: Update AggregatedMetrics Construction

**Before**:
```java
AggregatedMetrics metrics = new AggregatedMetrics(
    totalExecutions, successCount, failureCount,
    successPercentiles, failurePercentiles,
    elapsedMillis, queueSize, queueWaitPercentiles,
    clientMetrics  // ← Remove this parameter
);
```

**After**:
```java
AggregatedMetrics metrics = new AggregatedMetrics(
    totalExecutions, successCount, failureCount,
    successPercentiles, failurePercentiles,
    elapsedMillis, queueSize, queueWaitPercentiles
    // clientMetrics removed
);
```

---

## API Changes

### Package Changes

**Moved**:
- `com.vajrapulse.api.metrics.BackpressureProvider` → `com.vajrapulse.api.backpressure.BackpressureProvider`
- `com.vajrapulse.api.metrics.BackpressureHandler` → `com.vajrapulse.api.backpressure.BackpressureHandler`
- `com.vajrapulse.api.metrics.BackpressureContext` → `com.vajrapulse.api.backpressure.BackpressureContext`
- `com.vajrapulse.api.metrics.BackpressureHandlingResult` → `com.vajrapulse.api.backpressure.BackpressureHandlingResult`

**Removed**:
- `com.vajrapulse.core.metrics.ClientMetrics`

**Renamed**:
- `com.vajrapulse.worker.pipeline.MetricsPipeline` → `com.vajrapulse.worker.pipeline.LoadTestRunner`

### Class Changes

#### AdaptiveLoadPattern

| Method | Before | After | Change |
|--------|--------|-------|--------|
| Constructor | `new AdaptiveLoadPattern(...)` | `AdaptiveLoadPattern.builder()...build()` | Builder pattern |
| State access | `getState()` | `getCurrentPhase()`, `getCurrentTps()`, etc. | Convenience methods |
| Configuration | 13 parameters | 8 parameters + decisionPolicy | Simplified |

#### ExecutionEngine

| Method | Before | After | Change |
|--------|--------|-------|--------|
| `withBackpressureHandler()` | ✅ Available | ❌ Removed | Moved to AdaptiveLoadPattern |
| `withBackpressureThreshold()` | ✅ Available | ❌ Removed | Moved to AdaptiveLoadPattern |

#### AggregatedMetrics

| Field | Before | After | Change |
|-------|--------|-------|--------|
| `clientMetrics` | ✅ Present | ❌ Removed | Use BackpressureProvider instead |

---

## Examples and Usage

### Complete Example: Adaptive Pattern with Backpressure

```java
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern;
import com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy;
import com.vajrapulse.api.backpressure.BackpressureProvider;
import com.vajrapulse.core.metrics.QueueBackpressureProvider;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.engine.MetricsProviderAdapter;
import com.vajrapulse.core.metrics.MetricsCollector;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;

public class AdaptiveWithBackpressureExample {
    public static void main(String[] args) throws Exception {
        // 1. Create metrics collector
        MetricsCollector metrics = MetricsCollector.createWithRunId("test-run",
            new double[]{0.50, 0.95, 0.99});
        
        // 2. Create metrics provider adapter
        MetricsProviderAdapter metricsProvider = new MetricsProviderAdapter(metrics);
        
        // 3. Create backpressure provider (e.g., from queue)
        BlockingQueue<Task> taskQueue = ...;
        int maxQueueSize = 1000;
        BackpressureProvider backpressureProvider = new QueueBackpressureProvider(
            () -> (long) taskQueue.size(),
            maxQueueSize
        );
        
        // 4. Create adaptive pattern with backpressure
        AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
            .initialTps(100.0)
            .rampIncrement(50.0)
            .rampDecrement(100.0)
            .rampInterval(Duration.ofMinutes(1))
            .maxTps(5000.0)
            .minTps(10.0)
            .sustainDuration(Duration.ofMinutes(10))
            .stableIntervalsRequired(3)
            .metricsProvider(metricsProvider)
            .backpressureProvider(backpressureProvider)
            .decisionPolicy(new DefaultRampDecisionPolicy(
                0.01,   // 1% error threshold
                0.3,    // Ramp up if backpressure < 30%
                0.7     // Ramp down if backpressure >= 70%
            ))
            .listener(new LoggingAdaptivePatternListener())  // Optional: log events
            .build();
        
        // 5. Create execution engine (no backpressure config needed)
        ExecutionEngine engine = ExecutionEngine.builder()
            .withTask(new MyTask())
            .withLoadPattern(pattern)
            .withMetricsCollector(metrics)
            .build();
        
        // 6. Run test
        engine.run();
        
        // 7. Check results
        System.out.println("Stable TPS: " + pattern.getStableTps());
        System.out.println("Current Phase: " + pattern.getCurrentPhase());
    }
}
```

### Example: Custom Backpressure Provider

```java
import com.vajrapulse.api.backpressure.BackpressureProvider;

public class LatencyBasedBackpressureProvider implements BackpressureProvider {
    private final Supplier<Double> latencySupplier;
    private final double maxLatencyMs;
    
    public LatencyBasedBackpressureProvider(Supplier<Double> latencySupplier, double maxLatencyMs) {
        this.latencySupplier = latencySupplier;
        this.maxLatencyMs = maxLatencyMs;
    }
    
    @Override
    public double getBackpressureLevel() {
        double currentLatency = latencySupplier.get();
        if (currentLatency >= maxLatencyMs) {
            return 1.0;  // Maximum backpressure
        }
        return Math.min(1.0, currentLatency / maxLatencyMs);
    }
    
    @Override
    public String getBackpressureDescription() {
        return String.format("Latency: %.2f ms (max: %.2f ms)", 
            latencySupplier.get(), maxLatencyMs);
    }
}
```

### Example: Custom Decision Policy

```java
import com.vajrapulse.api.pattern.adaptive.RampDecisionPolicy;
import com.vajrapulse.api.pattern.adaptive.AdaptiveState;
import com.vajrapulse.api.pattern.adaptive.MetricsSnapshot;
import com.vajrapulse.api.pattern.adaptive.AdjustmentDecision;

public class ConservativeDecisionPolicy implements RampDecisionPolicy {
    private static final double ERROR_THRESHOLD = 0.005;  // 0.5% (very strict)
    private static final double BACKPRESSURE_THRESHOLD = 0.5;  // 50%
    
    @Override
    public AdjustmentDecision decideRampUp(AdaptiveState current, MetricsSnapshot metrics) {
        // Only ramp up if error rate is very low AND backpressure is low
        if (metrics.failureRate() < ERROR_THRESHOLD && 
            metrics.backpressureLevel() < BACKPRESSURE_THRESHOLD) {
            return AdjustmentDecision.rampUp(current.currentTps() + 10.0);  // Small increment
        }
        return AdjustmentDecision.noChange();
    }
    
    @Override
    public AdjustmentDecision decideRampDown(AdaptiveState current, MetricsSnapshot metrics) {
        // Ramp down if errors OR high backpressure
        if (metrics.failureRate() >= ERROR_THRESHOLD || 
            metrics.backpressureLevel() >= BACKPRESSURE_THRESHOLD) {
            return AdjustmentDecision.rampDown(current.currentTps() * 0.8);  // 20% reduction
        }
        return AdjustmentDecision.noChange();
    }
    
    @Override
    public boolean shouldSustain(int stableIntervalsCount, int requiredIntervals) {
        // Require more stable intervals for conservative policy
        return stableIntervalsCount >= requiredIntervals + 2;  // Extra 2 intervals
    }
}
```

---

## Summary

### Key Improvements

1. **Simplified Architecture**: Unified state model, integrated phase logic, reduced code complexity
2. **Better Separation of Concerns**: Backpressure handled by pattern, not engine
3. **Clearer API**: Builder pattern, explicit configuration, better naming
4. **Removed Redundancy**: ClientMetrics removed, unified backpressure approach
5. **Improved Extensibility**: Decision policy interface, event listeners, custom backpressure providers

### Breaking Changes

1. ⚠️ **AdaptiveLoadPattern constructor** → Builder pattern
2. ⚠️ **ExecutionEngine backpressure methods** → Removed (moved to pattern)
3. ⚠️ **ClientMetrics** → Removed (use BackpressureProvider)
4. ⚠️ **MetricsPipeline** → Renamed to LoadTestRunner
5. ⚠️ **State access** → New convenience methods (getState() removed)

### Migration Effort

- **Low**: Simple constructor → builder pattern changes
- **Medium**: If using ExecutionEngine backpressure, need to move to pattern
- **High**: If using ClientMetrics extensively, need to refactor to BackpressureProvider

### Next Steps

1. Review your code for breaking changes
2. Follow the migration guide step-by-step
3. Test thoroughly after migration
4. Consider using event listeners for monitoring
5. Explore custom decision policies for advanced use cases

---

**Last Updated**: 2025-12-13  
**Version**: 0.9.9  
**Next Review**: Before 1.0 release
