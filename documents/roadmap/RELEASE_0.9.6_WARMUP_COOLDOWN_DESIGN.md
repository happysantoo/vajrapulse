# Warm-up and Cool-down Phases - Detailed Design

**Release**: 0.9.6  
**Priority**: MEDIUM  
**Source**: ONE_ZERO_GAP_ANALYSIS.md A13  
**Effort**: 2-3 days

## Problem Statement

Currently, load patterns start immediately at target TPS, which can lead to:
1. **JIT Warm-up Issues**: JVM needs time to optimize hot paths
2. **Cold Start Effects**: First requests are slower due to initialization
3. **Inaccurate Baselines**: Measured performance includes warm-up artifacts
4. **Resource Initialization**: Connection pools, caches need time to stabilize
5. **No Clean Separation**: Can't distinguish warm-up from steady-state performance

## Solution Overview

Add built-in warm-up and cool-down phases to load patterns that:
1. **Warm-up Phase**: Gradually ramp to target TPS, metrics not counted
2. **Steady-State Phase**: Actual test execution, metrics counted
3. **Cool-down Phase**: Gradually reduce TPS, metrics not counted

This provides clean separation between initialization and measurement phases.

## Design

### 1. Load Pattern Enhancement

**Option A: Wrapper Pattern (Recommended)**

Wrap existing load patterns with warm-up/cool-down:

```java
public class WarmupCooldownLoadPattern implements LoadPattern {
    private final LoadPattern basePattern;
    private final Duration warmupDuration;
    private final Duration cooldownDuration;
    private final long testStartTime;
    
    public WarmupCooldownLoadPattern(
        LoadPattern basePattern,
        Duration warmupDuration,
        Duration cooldownDuration
    ) {
        this.basePattern = basePattern;
        this.warmupDuration = warmupDuration;
        this.cooldownDuration = cooldownDuration;
        this.testStartTime = System.currentTimeMillis();
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        Duration baseDuration = basePattern.getDuration();
        long steadyStateStart = warmupDuration.toMillis();
        long steadyStateEnd = warmupDuration.toMillis() + baseDuration.toMillis();
        long totalDuration = warmupDuration.toMillis() + baseDuration.toMillis() + cooldownDuration.toMillis();
        
        if (elapsedMillis < steadyStateStart) {
            // Warm-up phase: ramp from 0 to initial TPS
            double initialTps = basePattern.calculateTps(0);
            double progress = (double) elapsedMillis / warmupDuration.toMillis();
            return initialTps * progress;
        } else if (elapsedMillis < steadyStateEnd) {
            // Steady-state phase: use base pattern
            long steadyStateElapsed = elapsedMillis - steadyStateStart;
            return basePattern.calculateTps(steadyStateElapsed);
        } else if (elapsedMillis < totalDuration) {
            // Cool-down phase: ramp from current TPS to 0
            long cooldownElapsed = elapsedMillis - steadyStateEnd;
            double finalTps = basePattern.calculateTps(baseDuration.toMillis());
            double progress = 1.0 - ((double) cooldownElapsed / cooldownDuration.toMillis());
            return finalTps * progress;
        } else {
            // Test complete
            return 0.0;
        }
    }
    
    @Override
    public Duration getDuration() {
        return Duration.ofMillis(
            warmupDuration.toMillis() + 
            basePattern.getDuration().toMillis() + 
            cooldownDuration.toMillis()
        );
    }
    
    public Phase getCurrentPhase(long elapsedMillis) {
        long steadyStateStart = warmupDuration.toMillis();
        long steadyStateEnd = warmupDuration.toMillis() + basePattern.getDuration().toMillis();
        long totalDuration = warmupDuration.toMillis() + basePattern.getDuration().toMillis() + cooldownDuration.toMillis();
        
        if (elapsedMillis < steadyStateStart) {
            return Phase.WARMUP;
        } else if (elapsedMillis < steadyStateEnd) {
            return Phase.STEADY_STATE;
        } else if (elapsedMillis < totalDuration) {
            return Phase.COOLDOWN;
        } else {
            return Phase.COMPLETE;
        }
    }
    
    public enum Phase {
        WARMUP,
        STEADY_STATE,
        COOLDOWN,
        COMPLETE
    }
}
```

**Option B: Builder Pattern**

Add warm-up/cool-down to LoadPattern builder:

```java
LoadPattern pattern = LoadPattern.builder()
    .warmup(Duration.ofSeconds(30))
    .steadyState(new StaticLoad(100.0, Duration.ofMinutes(5)))
    .cooldown(Duration.ofSeconds(10))
    .build();
```

**Recommendation**: Option A (Wrapper) - More flexible, doesn't require changing all load patterns.

### 2. Metrics Collection Enhancement

**Track Phase in Metrics**:

```java
public class MetricsCollector {
    private volatile WarmupCooldownLoadPattern.Phase currentPhase = 
        WarmupCooldownLoadPattern.Phase.STEADY_STATE;
    
    public void setCurrentPhase(WarmupCooldownLoadPattern.Phase phase) {
        this.currentPhase = phase;
    }
    
    public void record(ExecutionMetrics metrics) {
        // Only record metrics during steady-state phase
        if (currentPhase == WarmupCooldownLoadPattern.Phase.STEADY_STATE) {
            // Record metrics
        } else {
            // Skip metrics recording (warm-up/cool-down)
        }
    }
}
```

**Alternative: Phase-Aware Snapshot**:

```java
public record AggregatedMetrics(
    // ... existing fields ...
    Map<Phase, PhaseMetrics> phaseMetrics
) {
    public record PhaseMetrics(
        long executions,
        long successes,
        long failures,
        Map<Double, Double> latencyPercentiles
    ) {}
}
```

**Recommendation**: Simple approach - skip metrics during warm-up/cool-down, only record during steady-state.

### 3. ExecutionEngine Integration

**Update ExecutionEngine to Track Phase**:

```java
public class ExecutionEngine {
    private WarmupCooldownLoadPattern pattern; // If using warm-up/cool-down
    
    private void updatePhase(long elapsedMillis) {
        if (pattern instanceof WarmupCooldownLoadPattern wcPattern) {
            WarmupCooldownLoadPattern.Phase phase = wcPattern.getCurrentPhase(elapsedMillis);
            metricsCollector.setCurrentPhase(phase);
        }
    }
    
    public void run() {
        // ... existing code ...
        while (!stopRequested.get() && ...) {
            long elapsedMillis = rateController.getElapsedMillis();
            updatePhase(elapsedMillis); // Update phase
            // ... rest of loop ...
        }
    }
}
```

## Detailed Phase Behavior

### Warm-up Phase

**Purpose**: Allow system to stabilize before measurement

**Behavior**:
- Gradually ramp from 0 TPS to initial TPS of base pattern
- Linear ramp (configurable)
- Metrics **not recorded** during warm-up
- Duration: Configurable (default: 30 seconds)

**Example**:
```java
// Base pattern: StaticLoad(100 TPS, 5 minutes)
// Warm-up: 30 seconds
// Result: Ramp from 0 to 100 TPS over 30 seconds, then sustain at 100 TPS
```

**Benefits**:
- JIT compiler has time to optimize
- Connection pools initialize
- Caches warm up
- System stabilizes

### Steady-State Phase

**Purpose**: Actual test execution with metrics

**Behavior**:
- Use base pattern TPS calculation
- Metrics **recorded** during this phase
- Duration: Base pattern duration

**Example**:
```java
// Base pattern: StaticLoad(100 TPS, 5 minutes)
// Steady-state: 5 minutes at 100 TPS
// Metrics recorded during this phase only
```

**Benefits**:
- Clean measurement baseline
- No warm-up artifacts
- Accurate performance data

### Cool-down Phase

**Purpose**: Gradually reduce load for graceful shutdown

**Behavior**:
- Gradually ramp from final TPS to 0
- Linear ramp (configurable)
- Metrics **not recorded** during cool-down
- Duration: Configurable (default: 10 seconds)

**Example**:
```java
// Base pattern: StaticLoad(100 TPS, 5 minutes)
// Cool-down: 10 seconds
// Result: Ramp from 100 TPS to 0 TPS over 10 seconds
```

**Benefits**:
- Graceful shutdown
- Allows in-flight requests to complete
- Reduces abrupt termination effects

## Usage Examples

### Basic Usage

```java
// Wrap existing pattern with warm-up/cool-down
LoadPattern basePattern = new StaticLoad(100.0, Duration.ofMinutes(5));
LoadPattern pattern = new WarmupCooldownLoadPattern(
    basePattern,
    Duration.ofSeconds(30),  // Warm-up: 30 seconds
    Duration.ofSeconds(10)   // Cool-down: 10 seconds
);

// Total duration: 30s warm-up + 5m steady-state + 10s cool-down = 5m 40s
```

### With Ramp Pattern

```java
// Ramp pattern with warm-up/cool-down
LoadPattern basePattern = new RampUpToMaxLoad(
    200.0,
    Duration.ofSeconds(60),  // Ramp up over 60 seconds
    Duration.ofMinutes(5)    // Sustain for 5 minutes
);

LoadPattern pattern = new WarmupCooldownLoadPattern(
    basePattern,
    Duration.ofSeconds(30),  // Additional warm-up before ramp
    Duration.ofSeconds(10)   // Cool-down after sustain
);
```

### With Adaptive Pattern

```java
// Adaptive pattern with warm-up
LoadPattern basePattern = new AdaptiveLoadPattern(...);
LoadPattern pattern = new WarmupCooldownLoadPattern(
    basePattern,
    Duration.ofSeconds(30),  // Warm-up before adaptive phase
    Duration.ofSeconds(10)     // Cool-down after adaptive phase
);
```

## Implementation Details

### Phase Detection

```java
public class WarmupCooldownLoadPattern {
    public Phase getCurrentPhase(long elapsedMillis) {
        long warmupEnd = warmupDuration.toMillis();
        long steadyStateEnd = warmupEnd + basePattern.getDuration().toMillis();
        long totalDuration = steadyStateEnd + cooldownDuration.toMillis();
        
        if (elapsedMillis < warmupEnd) {
            return Phase.WARMUP;
        } else if (elapsedMillis < steadyStateEnd) {
            return Phase.STEADY_STATE;
        } else if (elapsedMillis < totalDuration) {
            return Phase.COOLDOWN;
        } else {
            return Phase.COMPLETE;
        }
    }
}
```

### TPS Calculation

```java
@Override
public double calculateTps(long elapsedMillis) {
    long warmupEnd = warmupDuration.toMillis();
    long steadyStateEnd = warmupEnd + basePattern.getDuration().toMillis();
    long totalDuration = steadyStateEnd + cooldownDuration.toMillis();
    
    if (elapsedMillis < warmupEnd) {
        // Warm-up: linear ramp from 0 to initial TPS
        double initialTps = basePattern.calculateTps(0);
        double progress = (double) elapsedMillis / warmupDuration.toMillis();
        return Math.max(0, initialTps * progress);
    } else if (elapsedMillis < steadyStateEnd) {
        // Steady-state: use base pattern
        long steadyStateElapsed = elapsedMillis - warmupEnd;
        return basePattern.calculateTps(steadyStateElapsed);
    } else if (elapsedMillis < totalDuration) {
        // Cool-down: linear ramp from final TPS to 0
        long cooldownElapsed = elapsedMillis - steadyStateEnd;
        double finalTps = basePattern.calculateTps(basePattern.getDuration().toMillis());
        double progress = 1.0 - ((double) cooldownElapsed / cooldownDuration.toMillis());
        return Math.max(0, finalTps * progress);
    } else {
        return 0.0; // Complete
    }
}
```

### Metrics Filtering

```java
public class MetricsCollector {
    private volatile boolean recordMetrics = true;
    
    public void setRecordMetrics(boolean record) {
        this.recordMetrics = record;
    }
    
    public void record(ExecutionMetrics metrics) {
        if (recordMetrics) {
            // Record metrics
        }
        // Skip during warm-up/cool-down
    }
}
```

## Configuration Options

### Warm-up Strategies

1. **Linear Ramp** (default): Linear increase from 0 to initial TPS
2. **Exponential Ramp**: Exponential increase (faster start, slower end)
3. **Step Ramp**: Step increases at intervals

### Cool-down Strategies

1. **Linear Ramp** (default): Linear decrease from final TPS to 0
2. **Exponential Ramp**: Exponential decrease (faster start, slower end)
3. **Immediate**: Immediate drop to 0 (not recommended)

## Testing Strategy

1. **Unit Tests**
   - Test phase detection
   - Test TPS calculation for each phase
   - Test duration calculation

2. **Integration Tests**
   - Test with ExecutionEngine
   - Test metrics filtering
   - Test with various base patterns

3. **Example Tests**
   - Verify examples work
   - Verify metrics are filtered correctly

## Files to Create/Modify

### New Files
- `vajrapulse-api/src/main/java/com/vajrapulse/api/WarmupCooldownLoadPattern.java`
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/WarmupCooldownLoadPatternSpec.groovy`

### Modified Files
- `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/MetricsCollector.java` - Add phase tracking
- `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java` - Update phase
- `vajrapulse-worker/src/main/java/com/vajrapulse/worker/LoadPatternFactory.java` - Add warm-up/cool-down support

## Success Criteria

- [ ] WarmupCooldownLoadPattern implements LoadPattern correctly
- [ ] Phase detection works correctly
- [ ] TPS calculation works for all phases
- [ ] Metrics filtered correctly during warm-up/cool-down
- [ ] Works with all base patterns
- [ ] Examples updated
- [ ] Documentation complete
- [ ] Tests pass with ≥90% coverage

## Future Enhancements

- Configurable warm-up/cool-down strategies
- Per-phase metrics (optional)
- Automatic warm-up duration detection
- Cool-down with graceful request completion

