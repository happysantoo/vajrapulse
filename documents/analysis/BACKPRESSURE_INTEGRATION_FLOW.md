# Backpressure Integration Flow in AdaptiveLoadPattern

**Date**: 2025-01-XX  
**Purpose**: Document how BackpressureProvider is integrated into AdaptiveLoadPattern

---

## Summary

BackpressureProvider is **fully integrated** into AdaptiveLoadPattern. The backpressure signal flows from the provider → MetricsSnapshot → Decision Policy → TPS adjustments.

---

## Complete Integration Flow

### 1. Injection (Constructor/Builder)

```java
// Via Builder
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .metricsProvider(metricsProvider)
    .backpressureProvider(backpressureProvider)  // ← Injected here
    .build();

// Via Constructor
new AdaptiveLoadPattern(
    config,
    metricsProvider,
    backpressureProvider,  // ← Injected here (can be null)
    decisionPolicy,
    listeners
);
```

**Location**: 
- Constructor: `AdaptiveLoadPattern.java:88-93`
- Builder: `AdaptiveLoadPattern.java:962-964`
- Builder build: `AdaptiveLoadPattern.java:1013`

### 2. Storage

```java
private final BackpressureProvider backpressureProvider;  // Line 60
```

**Location**: `AdaptiveLoadPattern.java:60`

### 3. Capture (Every Adjustment Interval)

When `calculateTps()` is called and it's time to adjust:

```java
// Line 136: Capture metrics snapshot (includes backpressure)
MetricsSnapshot metrics = captureMetricsSnapshot(elapsedMillis);

// Line 137: Make decision using metrics (which includes backpressure)
AdjustmentDecision decision = makeDecision(current, metrics, elapsedMillis);
```

**Location**: `AdaptiveLoadPattern.java:136-137`

### 4. Backpressure Retrieval

```java
private MetricsSnapshot captureMetricsSnapshot(long elapsedMillis) {
    double failureRate = metricsProvider.getFailureRate() / PERCENTAGE_TO_RATIO;
    double recentFailureRate = metricsProvider.getRecentFailureRate(10) / PERCENTAGE_TO_RATIO;
    double backpressure = getBackpressureLevel();  // ← Gets backpressure here
    long totalExecutions = metricsProvider.getTotalExecutions();
    
    return new MetricsSnapshot(
        failureRate,
        recentFailureRate,
        backpressure,  // ← Included in snapshot
        totalExecutions
    );
}

public double getBackpressureLevel() {
    if (backpressureProvider == null) {
        return 0.0;  // No provider = no backpressure signal
    }
    return backpressureProvider.getBackpressureLevel();  // ← Calls provider
}
```

**Location**: 
- `captureMetricsSnapshot()`: `AdaptiveLoadPattern.java:560-572`
- `getBackpressureLevel()`: `AdaptiveLoadPattern.java:579-584`

### 5. Decision Making (Uses Backpressure)

The `MetricsSnapshot` (containing backpressure) is passed to decision methods:

```java
// In decideRampUp()
if (decisionPolicy.shouldRampDown(metrics)) {  // ← metrics contains backpressure
    // Ramp down due to errors OR backpressure
}

if (decisionPolicy.shouldRampUp(metrics)) {  // ← metrics contains backpressure
    // Ramp up only if errors low AND backpressure low
}

// In decideRampDown()
if (!decisionPolicy.shouldRampDown(metrics)) {  // ← metrics contains backpressure
    // Check for stability (errors low AND backpressure low)
}

// In decideSustain()
if (decisionPolicy.shouldRampDown(metrics)) {  // ← metrics contains backpressure
    // Conditions worsened (errors high OR backpressure high)
}
```

**Location**: 
- `decideRampUp()`: `AdaptiveLoadPattern.java:203-265`
- `decideRampDown()`: `AdaptiveLoadPattern.java:271-324`
- `decideSustain()`: `AdaptiveLoadPattern.java:330-359`

### 6. Decision Policy (Uses Backpressure)

The decision policy receives `MetricsSnapshot` and uses backpressure:

```java
// DefaultRampDecisionPolicy.shouldRampUp()
public boolean shouldRampUp(MetricsSnapshot metrics) {
    return metrics.failureRate() < errorThreshold 
        && metrics.backpressure() < backpressureRampUpThreshold;  // ← Uses backpressure
}

// DefaultRampDecisionPolicy.shouldRampDown()
public boolean shouldRampDown(MetricsSnapshot metrics) {
    return metrics.failureRate() >= errorThreshold 
        || metrics.backpressure() >= backpressureRampDownThreshold;  // ← Uses backpressure
}

// DefaultRampDecisionPolicy.canRecoverFromMinimum()
public boolean canRecoverFromMinimum(MetricsSnapshot metrics) {
    return metrics.backpressure() < recoveryBackpressureLowThreshold  // ← Uses backpressure
        || (metrics.recentFailureRate() < errorThreshold 
            && metrics.backpressure() < recoveryBackpressureModerateThreshold);
}
```

**Location**: `DefaultRampDecisionPolicy.java:73-96`

---

## Data Flow Diagram

```
┌─────────────────────┐
│ BackpressureProvider │ (User provides)
│  (optional, can be  │
│        null)        │
└──────────┬──────────┘
           │
           │ getBackpressureLevel()
           ▼
┌─────────────────────┐
│ AdaptiveLoadPattern  │
│  .getBackpressureLevel() │
└──────────┬──────────┘
           │
           │ Included in snapshot
           ▼
┌─────────────────────┐
│  MetricsSnapshot    │
│  - failureRate      │
│  - recentFailureRate│
│  - backpressure  ←──┼── Backpressure value here
│  - totalExecutions  │
└──────────┬──────────┘
           │
           │ Passed to decision methods
           ▼
┌─────────────────────┐
│  Decision Methods    │
│  - decideRampUp()    │
│  - decideRampDown()  │
│  - decideSustain()   │
└──────────┬──────────┘
           │
           │ Calls policy with metrics
           ▼
┌─────────────────────┐
│ RampDecisionPolicy  │
│  .shouldRampUp()    │
│  .shouldRampDown()  │
│  .canRecoverFrom... │
└──────────┬──────────┘
           │
           │ Uses metrics.backpressure()
           ▼
┌─────────────────────┐
│  TPS Adjustment      │
│  - Ramp up if        │
│    backpressure < 0.3│
│  - Ramp down if      │
│    backpressure >= 0.7│
│  - Hold if           │
│    0.3 <= bp < 0.7   │
└──────────────────────┘
```

---

## Example Usage

```java
// 1. Create backpressure provider
BackpressureProvider backpressureProvider = new MyBackpressureProvider();

// 2. Inject via builder
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
    .backpressureProvider(backpressureProvider)  // ← Injected
    .decisionPolicy(new DefaultRampDecisionPolicy(0.01, 0.3, 0.7))  // ← Thresholds
    .build();

// 3. Pattern automatically uses backpressure in decisions:
//    - Every rampInterval, captureMetricsSnapshot() calls getBackpressureLevel()
//    - Backpressure value is included in MetricsSnapshot
//    - Decision policy uses metrics.backpressure() in shouldRampUp/Down()
//    - Pattern adjusts TPS based on backpressure + error rate
```

---

## Verification Points

### ✅ Backpressure is Captured

**Location**: `AdaptiveLoadPattern.java:563`
```java
double backpressure = getBackpressureLevel();  // Gets from provider
```

### ✅ Backpressure is Included in MetricsSnapshot

**Location**: `AdaptiveLoadPattern.java:569`
```java
return new MetricsSnapshot(
    failureRate,
    recentFailureRate,
    backpressure,  // ← Included here
    totalExecutions
);
```

### ✅ Backpressure is Used in Decisions

**Location**: `DefaultRampDecisionPolicy.java:73-96`
```java
// Ramp up: backpressure < 0.3
metrics.backpressure() < backpressureRampUpThreshold

// Ramp down: backpressure >= 0.7
metrics.backpressure() >= backpressureRampDownThreshold

// Recovery: backpressure < 0.3 OR (errors low AND backpressure < 0.5)
metrics.backpressure() < recoveryBackpressureLowThreshold
```

### ✅ Backpressure Affects All Phases

- **RAMP_UP**: Uses `shouldRampUp()` which checks backpressure < 0.3
- **RAMP_DOWN**: Uses `shouldRampDown()` which checks backpressure >= 0.7
- **SUSTAIN**: Uses `shouldRampDown()` to detect if conditions worsened
- **Recovery**: Uses `canRecoverFromMinimum()` which checks backpressure < 0.3

---

## Key Points

1. **BackpressureProvider is Optional**: Can be `null` - if null, backpressure = 0.0
2. **Captured Every Interval**: Backpressure is read every `rampInterval` when making decisions
3. **Included in MetricsSnapshot**: Backpressure flows through MetricsSnapshot to decision policy
4. **Used in All Decisions**: Decision policy uses backpressure in all decision methods
5. **Affects All Phases**: Backpressure influences ramp up, ramp down, sustain, and recovery

---

## Testing

Backpressure integration is tested in:
- `AdaptiveLoadPatternSpec.should use backpressure provider when provided`
- `AdaptiveLoadPatternSpec.should combine error rate and backpressure for ramp down decision`
- `AdaptiveLoadPatternSpec.should ramp up only when both error rate and backpressure are low`

---

## Conclusion

**BackpressureProvider is fully integrated** into AdaptiveLoadPattern. The integration is:
- ✅ **Injected** via constructor/builder
- ✅ **Stored** as a field
- ✅ **Captured** every adjustment interval
- ✅ **Included** in MetricsSnapshot
- ✅ **Used** by decision policy in all decisions
- ✅ **Affects** TPS adjustments in all phases

The flow is complete and working as designed.
