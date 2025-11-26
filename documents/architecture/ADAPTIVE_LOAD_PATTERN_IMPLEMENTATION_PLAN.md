# Adaptive Load Pattern - Implementation Plan

**Feature**: Adaptive Load Pattern for 0.9.5  
**Status**: Ready for Implementation  
**Branch**: `feature/adaptive-load-pattern`  
**Timeline**: 5-7 days

---

## Decisions Summary

All design decisions have been made:

1. ✅ **Error Threshold**: Failure rate only (Option A)
2. ✅ **Adjustment Frequency**: Fixed interval (Option A)
3. ✅ **Ramp Down**: Immediate step down (Option A)
4. ✅ **Stable Detection**: 2-3 consecutive intervals (Option B)
5. ✅ **After Sustain**: Continue at stable TPS indefinitely (Option B - User Choice)
6. ✅ **Unlimited Max**: `Double.POSITIVE_INFINITY` (Option A)
7. ✅ **Edge Cases**: Accept all proposals
8. ✅ **Approach**: Single-instance first, distributed later

---

## Implementation Phases

### Phase 1: Core Pattern Implementation (2-3 days)

#### Task 1.1: Create AdaptiveLoadPattern Class
**File**: `vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java`

**Implementation**:
```java
package com.vajrapulse.api;

import com.vajrapulse.core.metrics.MetricsCollector;
import java.time.Duration;

/**
 * Adaptive load pattern that automatically finds the maximum sustainable TPS.
 * 
 * <p>This pattern:
 * <ol>
 *   <li>Starts at initial TPS</li>
 *   <li>Ramps up until errors occur</li>
 *   <li>Ramps down to find stable point</li>
 *   <li>Sustains at stable point indefinitely</li>
 * </ol>
 * 
 * <p>Example:
 * <pre>{@code
 * AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
 *     100.0,                          // Start at 100 TPS
 *     50.0,                           // Increase 50 TPS per minute
 *     100.0,                          // Decrease 100 TPS per minute when errors occur
 *     Duration.ofMinutes(1),          // Check/adjust every minute
 *     5000.0,                         // Max 5000 TPS (or Double.POSITIVE_INFINITY)
 *     Duration.ofMinutes(10),         // Sustain at stable point for 10 minutes
 *     0.01,                           // 1% error rate threshold
 *     metricsCollector                // For feedback
 * );
 * }</pre>
 */
public final class AdaptiveLoadPattern implements LoadPattern {
    // Configuration
    private final double initialTps;
    private final double rampIncrement;
    private final double rampDecrement;
    private final Duration rampInterval;
    private final double maxTps;
    private final Duration sustainDuration;
    private final double errorThreshold;
    private final MetricsCollector metricsCollector;
    
    // State
    private volatile Phase currentPhase = Phase.RAMP_UP;
    private volatile double currentTps;
    private volatile long lastAdjustmentTime;
    private volatile double stableTps = -1.0;  // -1 means not found yet
    private volatile long phaseStartTime;
    private volatile int stableIntervalsCount = 0;  // For 2-3 interval detection
    private volatile int rampDownAttempts = 0;
    private static final int MAX_RAMP_DOWN_ATTEMPTS = 10;
    private static final int STABLE_INTERVALS_REQUIRED = 3;
    
    enum Phase {
        RAMP_UP,
        RAMP_DOWN,
        SUSTAIN,
        COMPLETE  // Only if stable point never found
    }
    
    // Constructor and methods...
}
```

**Key Methods**:
- `calculateTps(long elapsedMillis)` - Main state machine logic
- `getDuration()` - Returns `Duration.ofDays(365)` for indefinite (or configurable max)
- `checkAndAdjust()` - Checks metrics and adjusts TPS
- `transitionPhase(Phase newPhase)` - Handles phase transitions

---

#### Task 1.2: Implement State Machine Logic

**State Machine Implementation**:

```java
@Override
public double calculateTps(long elapsedMillis) {
    if (elapsedMillis < 0) return 0.0;
    
    // Initialize on first call
    if (currentTps == 0.0) {
        currentTps = initialTps;
        lastAdjustmentTime = System.currentTimeMillis();
        phaseStartTime = System.currentTimeMillis();
    }
    
    // Check if it's time to adjust
    long timeSinceLastAdjustment = elapsedMillis - (lastAdjustmentTime - (System.currentTimeMillis() - elapsedMillis));
    if (timeSinceLastAdjustment >= rampInterval.toMillis()) {
        checkAndAdjust(elapsedMillis);
    }
    
    // Handle phase-specific logic
    return switch (currentPhase) {
        case RAMP_UP -> handleRampUp(elapsedMillis);
        case RAMP_DOWN -> handleRampDown(elapsedMillis);
        case SUSTAIN -> handleSustain(elapsedMillis);
        case COMPLETE -> 0.0;  // Test ends (only if stable point never found)
    };
}

private double handleRampUp(long elapsedMillis) {
    // Check if we've hit max TPS
    if (currentTps >= maxTps) {
        // Treat max TPS as stable point
        stableTps = maxTps;
        transitionPhase(Phase.SUSTAIN);
        return stableTps;
    }
    
    return currentTps;
}

private double handleRampDown(long elapsedMillis) {
    // Check if we've exhausted attempts
    if (rampDownAttempts >= MAX_RAMP_DOWN_ATTEMPTS) {
        transitionPhase(Phase.COMPLETE);
        return 0.0;
    }
    
    return currentTps;
}

private double handleSustain(long elapsedMillis) {
    // After sustain duration, continue indefinitely
    long sustainElapsed = elapsedMillis - phaseStartTime;
    if (sustainElapsed >= sustainDuration.toMillis()) {
        // Continue at stable TPS indefinitely
        return stableTps;
    }
    return stableTps;
}

private void checkAndAdjust(long elapsedMillis) {
    AggregatedMetrics snapshot = metricsCollector.snapshot();
    double errorRate = snapshot.failureRate() / 100.0;  // Convert percentage to ratio
    
    switch (currentPhase) {
        case RAMP_UP -> {
            if (errorRate >= errorThreshold) {
                // Errors detected, start ramping down
                transitionPhase(Phase.RAMP_DOWN);
                currentTps = Math.max(0, currentTps - rampDecrement);
            } else {
                // No errors, continue ramping up
                currentTps = Math.min(maxTps, currentTps + rampIncrement);
            }
        }
        case RAMP_DOWN -> {
            rampDownAttempts++;
            if (errorRate < errorThreshold) {
                stableIntervalsCount++;
                if (stableIntervalsCount >= STABLE_INTERVALS_REQUIRED) {
                    // Found stable point
                    stableTps = currentTps;
                    transitionPhase(Phase.SUSTAIN);
                }
            } else {
                // Still errors, continue ramping down
                stableIntervalsCount = 0;  // Reset counter
                currentTps = Math.max(0, currentTps - rampDecrement);
            }
        }
        case SUSTAIN -> {
            // Monitor during sustain, but don't adjust
            // After sustain duration, pattern continues indefinitely
        }
    }
    
    lastAdjustmentTime = System.currentTimeMillis();
}
```

---

#### Task 1.3: Add Metrics Integration

**New Metrics to Add**:
```java
// In AdaptiveLoadPattern constructor or initialization
private void registerMetrics(MeterRegistry registry) {
    // Phase gauge
    Gauge.builder("vajrapulse.adaptive.phase", () -> currentPhase.ordinal())
        .description("Current adaptive pattern phase (0=RAMP_UP, 1=RAMP_DOWN, 2=SUSTAIN, 3=COMPLETE)")
        .tag("run_id", runId)
        .register(registry);
    
    // Current TPS gauge
    Gauge.builder("vajrapulse.adaptive.current_tps", () -> currentTps)
        .description("Current target TPS for adaptive pattern")
        .tag("run_id", runId)
        .register(registry);
    
    // Stable TPS gauge
    Gauge.builder("vajrapulse.adaptive.stable_tps", () -> stableTps >= 0 ? stableTps : Double.NaN)
        .description("Stable TPS found by adaptive pattern")
        .tag("run_id", runId)
        .register(registry);
    
    // Phase transition counter
    Counter.builder("vajrapulse.adaptive.phase_transitions")
        .description("Number of phase transitions")
        .tag("run_id", runId)
        .register(registry);
}
```

---

### Phase 2: Testing (1-2 days)

#### Task 2.1: Unit Tests
**File**: `vajrapulse-api/src/test/groovy/com/vajrapulse/api/AdaptiveLoadPatternSpec.groovy`

**Test Cases**:
1. Ramp up until errors occur
2. Ramp down to find stable point
3. Sustain at stable point
4. Continue indefinitely after sustain
5. Handle max TPS without errors
6. Handle stable point never found
7. Edge cases (initial TPS, zero increment, etc.)

---

#### Task 2.2: Integration Tests
**File**: `vajrapulse-core/src/test/groovy/com/vajrapulse/core/integration/AdaptiveLoadPatternIntegrationSpec.groovy`

**Test Cases**:
1. End-to-end adaptive test with real task
2. Metrics collection during adaptive pattern
3. Phase transitions visible in metrics
4. Sustained execution after finding stable point

---

### Phase 3: Metrics & Visualization (1 day)

#### Task 3.1: Update Exporters
- **Console Exporter**: Show current phase and TPS in periodic updates
- **HTML Report**: Add phase timeline visualization
- **JSON/CSV Report**: Include adaptive pattern metrics

#### Task 3.2: Add Phase Visualization
- Phase transitions on TPS over time graph
- Stable TPS highlight
- Phase annotations

---

### Phase 4: CLI & Configuration (1 day)

#### Task 4.1: CLI Support
**File**: `vajrapulse-worker/src/main/java/com/vajrapulse/worker/VajraPulseWorker.java`

**Add CLI flags**:
```bash
--mode adaptive
--initial-tps 100
--ramp-increment 50
--ramp-decrement 100
--ramp-interval 1m
--max-tps 5000  # or "unlimited"
--sustain-duration 10m
--error-threshold 0.01
```

#### Task 4.2: Configuration File Support
**File**: `vajrapulse-core/src/main/java/com/vajrapulse/core/config/ConfigLoader.java`

**YAML Configuration**:
```yaml
loadPattern:
  type: adaptive
  initialTps: 100
  rampIncrement: 50
  rampDecrement: 100
  rampInterval: 1m
  maxTps: 5000  # or "unlimited"
  sustainDuration: 10m
  errorThreshold: 0.01
```

---

## File Structure

```
vajrapulse-api/
  src/main/java/com/vajrapulse/api/
    AdaptiveLoadPattern.java          # NEW - Main pattern class

vajrapulse-api/
  src/test/groovy/com/vajrapulse/api/
    AdaptiveLoadPatternSpec.groovy    # NEW - Unit tests

vajrapulse-core/
  src/test/groovy/com/vajrapulse/core/integration/
    AdaptiveLoadPatternIntegrationSpec.groovy  # NEW - Integration tests

vajrapulse-worker/
  src/main/java/com/vajrapulse/worker/
    VajraPulseWorker.java             # MODIFY - Add CLI support

vajrapulse-core/
  src/main/java/com/vajrapulse/core/config/
    ConfigLoader.java                 # MODIFY - Add adaptive pattern config

vajrapulse-exporter-report/
  src/main/java/com/vajrapulse/exporter/report/
    HtmlReportExporter.java           # MODIFY - Add phase visualization
```

---

## Implementation Checklist

### Core Pattern
- [ ] Create `AdaptiveLoadPattern` class
- [ ] Implement state machine (RAMP_UP, RAMP_DOWN, SUSTAIN)
- [ ] Add metrics integration
- [ ] Handle edge cases (max TPS, no stable point, etc.)
- [ ] Add JavaDoc documentation

### Testing
- [ ] Unit tests for state transitions
- [ ] Unit tests for edge cases
- [ ] Integration tests with real tasks
- [ ] Test metrics collection
- [ ] Test continue indefinitely behavior

### Metrics & Visualization
- [ ] Add phase gauge metric
- [ ] Add current TPS gauge metric
- [ ] Add stable TPS gauge metric
- [ ] Add phase transition counter
- [ ] Update console exporter
- [ ] Update HTML report with phase visualization
- [ ] Update JSON/CSV reports

### CLI & Configuration
- [ ] Add CLI flags for adaptive mode
- [ ] Parse adaptive pattern from CLI
- [ ] Add YAML configuration support
- [ ] Update configuration loader
- [ ] Add configuration validation

### Documentation
- [ ] Update `LOAD_PATTERNS.md`
- [ ] Add usage examples
- [ ] Update README with adaptive pattern
- [ ] Create example test using adaptive pattern

---

## Testing Strategy

### Unit Tests
- Test each phase independently
- Test state transitions
- Test edge cases
- Mock MetricsCollector for predictable behavior

### Integration Tests
- Real task execution with adaptive pattern
- Verify metrics are collected correctly
- Verify phase transitions occur
- Verify sustained execution

### Manual Testing
- Run example with adaptive pattern
- Verify console output shows phases
- Verify HTML report shows phase timeline
- Verify test continues after sustain duration

---

## Success Criteria

1. ✅ Pattern finds stable TPS automatically
2. ✅ Phase transitions are visible in metrics
3. ✅ Test continues indefinitely after sustain (Decision 5B)
4. ✅ All tests pass (unit + integration)
5. ✅ Code coverage ≥90%
6. ✅ JavaDoc complete
7. ✅ SpotBugs clean
8. ✅ Example works end-to-end

---

## Next Steps

1. **Start Implementation**: Create `AdaptiveLoadPattern` class
2. **Implement State Machine**: Core logic for phase transitions
3. **Add Metrics**: Register metrics for visualization
4. **Write Tests**: Unit and integration tests
5. **Update Exporters**: Add phase visualization
6. **Add CLI Support**: Command-line flags
7. **Documentation**: Usage guide and examples

---

*Ready to start implementation!*

