# AdaptiveLoadPattern Issues Analysis

**Date**: 2025-12-12  
**Version**: 0.9.9  
**Status**: Analysis and Recommendations

---

## Overview

This document analyzes four key issues with `AdaptiveLoadPattern` after the 0.9.9 simplification:

1. **Vortex 0.0.9 Backpressure Integration**: How to feed backpressure from vortex exceptions into AdaptiveLoadPattern
2. **Backpressure Design Documentation**: Comprehensive documentation needed
3. **Ramping Event Notifications**: Interface for tracking ramp up, down, and sustain events
4. **Gradual Ramp Up at Start**: Issue with initial ramp up behavior

---

## Issue 1: Vortex 0.0.9 Backpressure Integration

### Problem

Vortex 0.0.9 removed the `com.vajrapulse.vortex.backpressure` package. Queue rejection now throws exceptions when the queue is full. Clients need to:

1. Catch these exceptions
2. Convert them to backpressure signals
3. Implement `BackpressureProvider` interface
4. Feed backpressure to `AdaptiveLoadPattern`

### Architectural Decision

**VajraPulse provides the contract (`BackpressureProvider` interface), not framework-specific implementations.**

This keeps VajraPulse:
- **Framework-agnostic**: No dependencies on vortex, HikariCP, etc.
- **Lightweight**: Core library stays minimal
- **Extensible**: Clients implement providers for their specific infrastructure

**Pattern**: Framework-specific providers belong in client code or examples, not in core.

### Current State

- `AdaptiveLoadPattern` accepts an optional `BackpressureProvider`
- Backpressure is used in `RampDecisionPolicy` for ramp decisions
- Core library has generic providers (`QueueBackpressureProvider`, `CompositeBackpressureProvider`)
- Examples folder has `HikariCpBackpressureProvider` as a reference implementation

### Solution

**Client-Side Implementation**: Create an example (not in core) showing how clients can implement a vortex-based provider:

```java
package com.example.vortex;  // Client package, not VajraPulse core

import com.vajrapulse.api.metrics.BackpressureProvider;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Example backpressure provider for vortex 0.0.9+.
 * 
 * <p>This is an EXAMPLE implementation showing how to integrate vortex
 * queue rejection exceptions with VajraPulse's backpressure system.
 * 
 * <p><strong>Note:</strong> This example is NOT included in the core VajraPulse
 * distribution to avoid adding vortex as a dependency. Users can copy this
 * example and adapt it to their needs.
 * 
 * <p>Usage:
 * <pre>{@code
 * VortexExceptionBackpressureProvider provider = 
 *     new VortexExceptionBackpressureProvider();
 * 
 * // Wrap vortex submission
 * public ItemResult<T> submitWithBackpressure(T item, Callback<T> callback) {
 *     provider.recordSubmission();
 *     try {
 *         return batcher.submit(item, callback);
 *     } catch (QueueRejectionException e) {
 *         provider.recordRejection();
 *         throw e; // Or handle gracefully
 *     }
 * }
 * 
 * // Use with AdaptiveLoadPattern
 * AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
 *     .metricsProvider(metricsProvider)
 *     .backpressureProvider(provider)
 *     .build();
 * }</pre>
 */
public class VortexExceptionBackpressureProvider implements BackpressureProvider {
    private final AtomicLong totalSubmissions = new AtomicLong(0);
    private final AtomicLong rejectionCount = new AtomicLong(0);
    private final AtomicReference<Long> windowStartTime = new AtomicReference<>(System.currentTimeMillis());
    private final long windowMillis;
    
    /**
     * Creates a new provider with a 10-second sliding window.
     */
    public VortexExceptionBackpressureProvider() {
        this(10_000L); // 10 seconds
    }
    
    /**
     * Creates a new provider with a custom sliding window.
     * 
     * @param windowMillis sliding window duration in milliseconds
     */
    public VortexExceptionBackpressureProvider(long windowMillis) {
        this.windowMillis = windowMillis;
    }
    
    /**
     * Records a submission attempt.
     * 
     * <p>Call this before attempting to submit to vortex.
     */
    public void recordSubmission() {
        totalSubmissions.incrementAndGet();
        resetWindowIfNeeded();
    }
    
    /**
     * Records a queue rejection exception.
     * 
     * <p>Call this when vortex throws a queue rejection exception.
     */
    public void recordRejection() {
        rejectionCount.incrementAndGet();
        resetWindowIfNeeded();
    }
    
    @Override
    public double getBackpressureLevel() {
        resetWindowIfNeeded();
        
        long total = totalSubmissions.get();
        if (total == 0) {
            return 0.0; // No submissions yet
        }
        
        long rejections = rejectionCount.get();
        // Backpressure = rejection rate (0.0 to 1.0)
        return Math.min(1.0, (double) rejections / total);
    }
    
    @Override
    public String getBackpressureDescription() {
        long total = totalSubmissions.get();
        long rejections = rejectionCount.get();
        return String.format("Vortex queue rejections: %d/%d (%.1f%%)",
            rejections, total, total > 0 ? (100.0 * rejections / total) : 0.0);
    }
    
    private void resetWindowIfNeeded() {
        long now = System.currentTimeMillis();
        long start = windowStartTime.get();
        
        if (now - start > windowMillis) {
            // Reset window
            if (windowStartTime.compareAndSet(start, now)) {
                totalSubmissions.set(0);
                rejectionCount.set(0);
            }
        }
    }
}
```

### Integration Example

```java
// Create backpressure provider
VortexExceptionBackpressureProvider backpressureProvider = 
    new VortexExceptionBackpressureProvider();

// Wrap vortex submission
public ItemResult<T> submitWithBackpressure(T item, Callback<T> callback) {
    backpressureProvider.recordSubmission();
    try {
        return batcher.submit(item, callback);
    } catch (QueueRejectionException e) {
        backpressureProvider.recordRejection();
        throw e; // Or handle gracefully
    }
}

// Use with AdaptiveLoadPattern
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(100.0)
    .rampIncrement(50.0)
    .rampDecrement(100.0)
    .rampInterval(Duration.ofSeconds(10))
    .maxTps(1000.0)
    .errorThreshold(0.01)
    .backpressureRampUpThreshold(0.3)
    .backpressureRampDownThreshold(0.7)
    .metricsProvider(metricsProvider)
    .backpressureProvider(backpressureProvider)  // Add provider
    .build();
```

### Implementation Steps

1. ⏳ Create example in `examples/vortex-backpressure-example/` folder (NOT in core)
2. ⏳ Add example usage documentation
3. ⏳ Update main documentation to clarify client-side responsibility
4. ✅ Documentation updated to reflect architectural decision

---

## Issue 2: Backpressure Design Documentation

### Problem

Comprehensive documentation needed on:
- How backpressure works
- How to implement custom providers
- Integration with AdaptiveLoadPattern
- Migration from vortex backpressure package

### Solution

✅ **Created**: `documents/architecture/BACKPRESSURE_DESIGN.md`

This document includes:
- Architecture overview
- Component descriptions
- Built-in providers
- Custom provider examples
- Integration guide
- Troubleshooting

### Status

✅ Documentation complete and ready for review.

---

## Issue 3: Ramping Event Notifications

### Problem

User wants a simple interface to track ramp up, down, and sustain events with current TPS and changed TPS.

### Current State

`AdaptivePatternListener` exists with:
- `onPhaseTransition(PhaseTransitionEvent)` - Phase changes
- `onTpsChange(TpsChangeEvent)` - TPS changes
- `onStabilityDetected(StabilityDetectedEvent)` - Stability detection
- `onRecovery(RecoveryEvent)` - Recovery events

**Issues**:
1. `TpsChangeEvent` doesn't include the phase
2. No simple logging listener provided
3. Events might not be called for all TPS changes (only significant ones)

### Solution

#### Option 1: Enhance TpsChangeEvent

Add phase to `TpsChangeEvent`:

```java
public record TpsChangeEvent(
    double previousTps,
    double newTps,
    AdaptivePhase phase,  // ADD: Current phase
    long timestamp
) {
    // ...
}
```

#### Option 2: Create Simple Logging Listener

Provide a built-in logging listener:

```java
package com.vajrapulse.api.pattern.adaptive;

import java.util.logging.Logger;

/**
 * Simple logging listener for adaptive pattern events.
 * 
 * <p>This listener logs all phase transitions and TPS changes
 * to a logger, making it easy to track pattern behavior.
 * 
 * <p>Example:
 * <pre>{@code
 * AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
 *     .metricsProvider(metrics)
 *     .listener(new LoggingAdaptivePatternListener())
 *     .build();
 * }</pre>
 * 
 * @since 0.9.10
 */
public class LoggingAdaptivePatternListener implements AdaptivePatternListener {
    private final Logger logger;
    
    public LoggingAdaptivePatternListener() {
        this(Logger.getLogger(LoggingAdaptivePatternListener.class.getName()));
    }
    
    public LoggingAdaptivePatternListener(Logger logger) {
        this.logger = logger;
    }
    
    @Override
    public void onPhaseTransition(PhaseTransitionEvent event) {
        logger.info(String.format(
            "Phase transition: %s -> %s at %.2f TPS",
            event.from(), event.to(), event.tps()
        ));
    }
    
    @Override
    public void onTpsChange(TpsChangeEvent event) {
        logger.info(String.format(
            "TPS change: %.2f -> %.2f (delta: %.2f)",
            event.previousTps(), event.newTps(),
            event.newTps() - event.previousTps()
        ));
    }
    
    @Override
    public void onStabilityDetected(StabilityDetectedEvent event) {
        logger.info(String.format(
            "Stable TPS detected: %.2f",
            event.stableTps()
        ));
    }
    
    @Override
    public void onRecovery(RecoveryEvent event) {
        logger.info(String.format(
            "Recovery: %.2f TPS (last known good: %.2f)",
            event.recoveryTps(), event.lastKnownGoodTps()
        ));
    }
}
```

#### Option 3: Ensure All TPS Changes Are Notified

Currently, `notifyTpsChange()` only notifies if change > tolerance. We should also notify when:
- TPS changes during ramp up (even if small)
- TPS changes during ramp down
- TPS changes during recovery

**Fix**: Call `notifyTpsChange()` in `checkAndAdjust()` for all TPS changes, not just in `transitionPhaseInternal()`.

### Implementation Steps

1. ⏳ Add phase to `TpsChangeEvent`
2. ⏳ Create `LoggingAdaptivePatternListener`
3. ⏳ Update `notifyTpsChange()` to be called for all TPS changes
4. ⏳ Add tests
5. ⏳ Update documentation

---

## Issue 4: Gradual Ramp Up at Start

### Problem

User reports that gradual ramp up at the start is broken. The pattern should gradually ramp up from a low TPS to `initialTps`, then continue ramping.

### Current Behavior

Looking at the code:

```java
// In calculateTps():
if (current.lastAdjustmentTime() < 0) {
    // Initialize - sets currentTps to initialTps immediately
    state.updateAndGet(s -> {
        AdaptiveCoreState newCore = new AdaptiveCoreState(
            s.core().phase(),
            s.core().currentTps(),  // This is initialTps from constructor
            0L,  // lastAdjustmentTime = 0
            0L,  // phaseStartTime = 0
            ...
        );
        return new AdaptiveState(newCore, ...);
    });
}

// First call returns initialTps immediately
// Then waits for rampInterval before first adjustment
```

**Issue**: Pattern starts at `initialTps` immediately, not gradually from 0 or a lower value.

### Expected Behavior

User likely expects:
1. Start at 0 TPS (or a fraction of initialTps)
2. Gradually increase to `initialTps` over time
3. Then continue ramping up by `rampIncrement` per interval

### Solution Options

#### Option 1: Add Initial Ramp Duration

Add a configuration for initial ramp duration:

```java
AdaptiveConfig config = AdaptiveConfig.builder()
    .initialTps(100.0)
    .initialRampDuration(Duration.ofSeconds(30))  // NEW: Ramp from 0 to initialTps over 30s
    .rampIncrement(50.0)
    // ...
    .build();
```

**Implementation**:
```java
private double calculateInitialRampTps(long elapsedMillis) {
    if (elapsedMillis <= 0) {
        return 0.0;
    }
    
    long initialRampDuration = config.initialRampDuration().toMillis();
    if (elapsedMillis >= initialRampDuration) {
        return config.initialTps();
    }
    
    // Linear ramp from 0 to initialTps
    double progress = (double) elapsedMillis / initialRampDuration;
    return config.initialTps() * progress;
}
```

#### Option 2: Use Ramp Increment from Start

Start at a fraction of `initialTps` and ramp up:

```java
// Start at 10% of initialTps
double startTps = config.initialTps() * 0.1;

// Then ramp up by rampIncrement until reaching initialTps
// After reaching initialTps, continue normal ramp up
```

#### Option 3: Add Warmup Phase

Add a dedicated warmup phase before RAMP_UP:

```java
enum AdaptivePhase {
    WARMUP,    // NEW: Gradually ramp from 0 to initialTps
    RAMP_UP,
    RAMP_DOWN,
    SUSTAIN
}
```

### Recommended Solution

**Option 1** is cleanest - add `initialRampDuration` configuration:

```java
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
    double recoveryTpsRatio,
    Duration initialRampDuration  // NEW: Optional, defaults to 0 (no initial ramp)
) {
    // ...
}
```

**Behavior**:
- If `initialRampDuration` is 0 or null: Start at `initialTps` immediately (current behavior)
- If `initialRampDuration` > 0: Gradually ramp from 0 to `initialTps` over the duration, then continue normal ramp up

### Implementation Steps

1. ⏳ Add `initialRampDuration` to `AdaptiveConfig`
2. ⏳ Update `calculateTps()` to handle initial ramp
3. ⏳ Update builder to include `initialRampDuration()`
4. ⏳ Add tests
5. ⏳ Update documentation

---

## Summary

### Completed ✅

1. ✅ Backpressure design documentation created
2. ✅ Analysis of all four issues complete

### In Progress ⏳

1. ⏳ Vortex backpressure provider implementation
2. ⏳ Enhanced event notifications
3. ⏳ Gradual ramp up fix

### Next Steps

1. Implement `VortexExceptionBackpressureProvider`
2. Enhance `TpsChangeEvent` with phase
3. Create `LoggingAdaptivePatternListener`
4. Add `initialRampDuration` to `AdaptiveConfig`
5. Update tests and documentation

---

**Last Updated**: 2025-12-12  
**Version**: 0.9.9

