# Adaptive Load Pattern Usage Guide

**Version**: 0.9.9  
**Status**: Usage Guide

---

## Overview

The `AdaptiveLoadPattern` automatically finds the maximum sustainable TPS for your system by:
1. Starting at an initial TPS
2. Ramping up until errors occur
3. Ramping down to find a stable point
4. Sustaining at the stable point

This guide provides comprehensive examples and best practices for using adaptive load patterns.

---

## Quick Start

### Basic Example

```java
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern;
import com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy;
import com.vajrapulse.core.engine.MetricsProviderAdapter;
import com.vajrapulse.core.metrics.MetricsCollector;
import java.time.Duration;

// Create metrics collector
MetricsCollector metrics = new MetricsCollector();

// Create metrics provider adapter
MetricsProviderAdapter metricsProvider = new MetricsProviderAdapter(metrics);

// Create adaptive pattern
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(100.0)                          // Start at 100 TPS
    .rampIncrement(50.0)                        // Increase 50 TPS per interval
    .rampDecrement(100.0)                       // Decrease 100 TPS per interval when errors occur
    .rampInterval(Duration.ofMinutes(1))       // Check/adjust every minute
    .maxTps(5000.0)                             // Max 5000 TPS
    .minTps(10.0)                               // Min TPS
    .sustainDuration(Duration.ofMinutes(10))    // Sustain at stable point for 10 minutes
    .stableIntervalsRequired(3)                 // Require 3 stable intervals
    .metricsProvider(metricsProvider)           // For feedback
    .decisionPolicy(new DefaultRampDecisionPolicy(0.01))  // 1% error rate threshold
    .build();

// Use with ExecutionEngine
ExecutionEngine engine = ExecutionEngine.builder()
    .withTask(task)
    .withLoadPattern(pattern)
    .withMetricsCollector(metrics)
    .build();
engine.run();
```

---

## State Machine Behavior

The adaptive pattern follows a state machine with four phases:

### Phase 1: RAMP_UP

**Behavior**:
- Starts at `initialTps`
- Increases TPS by `rampIncrement` every `rampInterval`
- Continues until:
  - Error rate exceeds `errorThreshold`, OR
  - TPS reaches `maxTps`

**Transition Conditions**:
- **To RAMP_DOWN**: Error rate ≥ `errorThreshold`
- **To SUSTAIN**: TPS reaches `maxTps` (treats max as stable point)

**Example Timeline**:
```
Time    TPS    Phase     Reason
0s      100    RAMP_UP   Start
60s     150    RAMP_UP   +50 increment
120s    200    RAMP_UP   +50 increment
180s    250    RAMP_UP   +50 increment
240s    300    RAMP_DOWN Error rate >1% (errors detected)
```

### Phase 2: RAMP_DOWN

**Behavior**:
- Decreases TPS by `rampDecrement` every `rampInterval`
- Monitors error rate
- Tracks consecutive stable intervals (error rate < threshold)

**Transition Conditions**:
- **To SUSTAIN**: 3 consecutive intervals with error rate < threshold
- **To COMPLETE**: TPS reaches 0 (no stable point found)

**Stable Point Detection**:
- Requires `STABLE_INTERVALS_REQUIRED` (3) consecutive intervals with low error rate
- Once found, that TPS becomes the `stableTps`

**Example Timeline**:
```
Time    TPS    Phase      Stable Intervals
240s    300    RAMP_DOWN  0 (errors still occurring)
300s    200    RAMP_DOWN  0 (errors still occurring)
360s    100    RAMP_DOWN  1 (first stable interval)
420s    100    RAMP_DOWN  2 (second stable interval)
480s    100    RAMP_DOWN  3 → SUSTAIN (stable point found!)
```

### Phase 3: SUSTAIN

**Behavior**:
- Holds at `stableTps` for `sustainDuration`
- Monitors but doesn't adjust TPS
- After `sustainDuration`, continues at stable TPS indefinitely

**Example Timeline**:
```
Time    TPS    Phase    Duration Remaining
480s    100    SUSTAIN  10 minutes
1080s   100    SUSTAIN  0 minutes → Continue at 100 TPS
```

### Phase 4: COMPLETE

**Behavior**:
- Only reached if no stable point is found
- Returns 0 TPS (test ends)
- Rare - usually means system is completely broken

---

## Configuration Parameters

### initialTps

**Type**: `double`  
**Required**: Yes  
**Range**: > 0

Starting TPS for the load test.

**Recommendations**:
- Start conservatively (e.g., 10-20% of expected max)
- Too high: May immediately hit errors
- Too low: Takes longer to find stable point

**Example**:
```java
100.0  // Good: Conservative start
1000.0 // Risky: May immediately hit errors
```

### rampIncrement

**Type**: `double`  
**Required**: Yes  
**Range**: > 0

TPS increase per adjustment interval.

**Recommendations**:
- 10-20% of `initialTps` for gradual ramp-up
- Larger increments find max faster but may overshoot
- Smaller increments are more precise but slower

**Example**:
```java
50.0   // Good: 50% of initial 100 TPS
10.0   // Conservative: Very gradual
200.0  // Aggressive: May overshoot
```

### rampDecrement

**Type**: `double`  
**Required**: Yes  
**Range**: > 0

TPS decrease per adjustment interval when errors occur.

**Recommendations**:
- Usually larger than `rampIncrement` (e.g., 2x)
- Ensures quick recovery from error state
- Too small: Takes long to recover
- Too large: May undershoot stable point

**Example**:
```java
100.0  // Good: 2x rampIncrement (50)
50.0   // Conservative: Same as increment
200.0  // Aggressive: Quick recovery
```

### rampInterval

**Type**: `Duration`  
**Required**: Yes  
**Range**: > 0

Time between TPS adjustments.

**Recommendations**:
- 30-60 seconds for responsive systems
- 1-5 minutes for slower systems
- Too short: May react to transient errors
- Too long: Slow to find stable point

**Example**:
```java
Duration.ofSeconds(30)   // Responsive
Duration.ofMinutes(1)    // Balanced (recommended)
Duration.ofMinutes(5)    // Conservative
```

### maxTps

**Type**: `double`  
**Required**: Yes  
**Range**: > 0 or `Double.POSITIVE_INFINITY`

Maximum TPS limit.

**Recommendations**:
- Set based on system capacity estimates
- Use `Double.POSITIVE_INFINITY` for unlimited
- Too low: May never find true max
- Too high: May waste time ramping beyond capacity

**Example**:
```java
5000.0                    // Limited to 5000 TPS
Double.POSITIVE_INFINITY // Unlimited (recommended for discovery)
```

### sustainDuration

**Type**: `Duration`  
**Required**: Yes  
**Range**: > 0

Duration to sustain at stable point before continuing indefinitely.

**Recommendations**:
- 5-15 minutes for validation
- Longer for stress testing
- Shorter for quick tests

**Example**:
```java
Duration.ofMinutes(5)   // Quick validation
Duration.ofMinutes(10)  // Standard (recommended)
Duration.ofMinutes(30)  // Extended validation
```

### Decision Policy

**Type**: `RampDecisionPolicy`  
**Required**: No (defaults to `DefaultRampDecisionPolicy` with 1% error threshold)

Decision policy for making ramp decisions. Configure thresholds via the policy:

**Example**:
```java
// Default policy (1% error threshold)
.decisionPolicy(new DefaultRampDecisionPolicy(0.01))

// Custom thresholds
.decisionPolicy(new DefaultRampDecisionPolicy(
    0.01,   // Error threshold (1%)
    0.3,    // Backpressure ramp up threshold
    0.7     // Backpressure ramp down threshold
))
```

---

## Complete Examples

### Example 1: Finding Max TPS for API

```java
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern;
import com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.engine.MetricsProviderAdapter;
import com.vajrapulse.core.metrics.MetricsCollector;
import java.time.Duration;

public class FindMaxApiTps {
    public static void main(String[] args) throws Exception {
        // Setup
        MetricsCollector metrics = MetricsCollector.createWithRunId("api-test", 
            new double[]{0.50, 0.95, 0.99});
        MetricsProviderAdapter provider = new MetricsProviderAdapter(metrics);
        
        // Create adaptive pattern
        AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
            .initialTps(50.0)                          // Start conservatively
            .rampIncrement(25.0)                       // Increase 25 TPS per minute
            .rampDecrement(50.0)                       // Decrease 50 TPS when errors occur
            .rampInterval(Duration.ofSeconds(30))     // Check every 30 seconds
            .maxTps(Double.POSITIVE_INFINITY)          // No max limit
            .minTps(5.0)                               // Min TPS
            .sustainDuration(Duration.ofMinutes(5))    // Sustain for 5 minutes
            .stableIntervalsRequired(3)               // Require 3 stable intervals
            .metricsProvider(provider)
            .decisionPolicy(new DefaultRampDecisionPolicy(0.01))  // 1% error threshold
            .build();
        
        // Run test
        ExecutionEngine engine = ExecutionEngine.builder()
            .withTask(new MyApiTask())
            .withLoadPattern(pattern)
            .withMetricsCollector(metrics)
            .build();
        
        engine.run();
        
        // Results
        var snapshot = metrics.snapshot();
        System.out.println("Stable TPS found: " + pattern.getStableTps());
        System.out.println("Total executions: " + snapshot.totalExecutions());
        System.out.println("Success rate: " + snapshot.successRate() + "%");
    }
}
```

### Example 2: Stress Testing with Max Limit

```java
// Stress test with known capacity estimate
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(100.0)                          // Start at 100 TPS
    .rampIncrement(100.0)                        // Aggressive ramp-up (+100/min)
    .rampDecrement(200.0)                        // Quick recovery (-200/min)
    .rampInterval(Duration.ofSeconds(20))        // Fast adjustments (20s)
    .maxTps(2000.0)                              // Max 2000 TPS (known limit)
    .minTps(10.0)                                // Min TPS
    .sustainDuration(Duration.ofMinutes(15))     // Extended sustain
    .stableIntervalsRequired(3)                  // Require 3 stable intervals
    .metricsProvider(metricsProvider)
    .decisionPolicy(new DefaultRampDecisionPolicy(0.05))  // 5% error threshold (stress test)
    .build();
```

### Example 3: High Reliability System

```java
// Very conservative for high-reliability system
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(10.0)                           // Very conservative start
    .rampIncrement(5.0)                          // Small increments
    .rampDecrement(10.0)                          // Quick recovery
    .rampInterval(Duration.ofMinutes(2))         // Longer intervals
    .maxTps(Double.POSITIVE_INFINITY)            // No limit
    .minTps(1.0)                                 // Min TPS
    .sustainDuration(Duration.ofMinutes(30))      // Long validation
    .stableIntervalsRequired(3)                  // Require 3 stable intervals
    .metricsProvider(metricsProvider)
    .decisionPolicy(new DefaultRampDecisionPolicy(0.001))  // 0.1% error threshold (very strict)
    .build();
```

---

## Monitoring and Metrics

### Available Metrics

The adaptive pattern exposes the following metrics (tagged with `run_id`):

| Metric | Type | Description |
|--------|------|-------------|
| `vajrapulse.adaptive.phase` | Gauge | Current phase (0=RAMP_UP, 1=RAMP_DOWN, 2=SUSTAIN, 3=COMPLETE) |
| `vajrapulse.adaptive.current_tps` | Gauge | Current target TPS |
| `vajrapulse.adaptive.stable_tps` | Gauge | Stable TPS found (NaN if not found) |
| `vajrapulse.adaptive.phase_transitions` | Gauge | Number of phase transitions |
| `vajrapulse.adaptive.transitions` | Counter | Phase transitions with reason tags |
| `vajrapulse.adaptive.phase.duration` | Timer | Duration spent in each phase |
| `vajrapulse.adaptive.tps_adjustment` | Histogram | TPS adjustment magnitudes |

### Querying Current State

```java
// Get current phase
AdaptiveLoadPattern.Phase phase = pattern.getCurrentPhase();
System.out.println("Current phase: " + phase);

// Get current TPS
double currentTps = pattern.getCurrentTps();
System.out.println("Current TPS: " + currentTps);

// Get stable TPS (if found)
double stableTps = pattern.getStableTps();
if (stableTps >= 0) {
    System.out.println("Stable TPS: " + stableTps);
} else {
    System.out.println("Stable TPS not found yet");
}

// Get transition count
long transitions = pattern.getPhaseTransitionCount();
System.out.println("Phase transitions: " + transitions);
```

---

## Best Practices

### 1. Start Conservatively

**✅ Good**:
```java
initialTps = 50.0;  // 10-20% of expected max
```

**❌ Bad**:
```java
initialTps = 1000.0;  // May immediately hit errors
```

### 2. Balance Ramp Increments

**✅ Good**:
```java
rampIncrement = 50.0;   // 50% of initial
rampDecrement = 100.0;  // 2x increment for quick recovery
```

**❌ Bad**:
```java
rampIncrement = 500.0;  // Too aggressive
rampDecrement = 10.0;   // Too slow to recover
```

### 3. Choose Appropriate Intervals

**✅ Good**:
```java
rampInterval = Duration.ofMinutes(1);  // Balanced
```

**❌ Bad**:
```java
rampInterval = Duration.ofSeconds(5);   // Too frequent, reacts to noise
rampInterval = Duration.ofMinutes(10);  // Too slow, takes forever
```

### 4. Set Realistic Error Thresholds

**✅ Good**:
```java
errorThreshold = 0.01;  // 1% for production
```

**❌ Bad**:
```java
errorThreshold = 0.0;   // Impossible to achieve
errorThreshold = 0.5;   // Too permissive
```

### 5. Use Unlimited Max for Discovery

**✅ Good**:
```java
maxTps = Double.POSITIVE_INFINITY;  // Discover true max
```

**❌ Bad**:
```java
maxTps = 100.0;  // Artificially limits discovery
```

---

## Common Patterns

### Pattern 1: Quick Discovery

Find max TPS quickly with aggressive ramping:

```java
AdaptiveLoadPattern.builder()
    .initialTps(100.0)                          // Start
    .rampIncrement(100.0)                       // Large increments
    .rampDecrement(200.0)                        // Quick recovery
    .rampInterval(Duration.ofSeconds(30))        // Fast adjustments
    .maxTps(Double.POSITIVE_INFINITY)            // No limit
    .minTps(10.0)                                // Min TPS
    .sustainDuration(Duration.ofMinutes(5))      // Short sustain
    .stableIntervalsRequired(3)                  // Require 3 stable intervals
    .metricsProvider(metricsProvider)
    .decisionPolicy(new DefaultRampDecisionPolicy(0.01))  // Standard threshold
    .build();
```

### Pattern 2: Precise Discovery

Find max TPS precisely with gradual ramping:

```java
AdaptiveLoadPattern.builder()
    .initialTps(50.0)                           // Conservative start
    .rampIncrement(10.0)                        // Small increments
    .rampDecrement(20.0)                         // Moderate recovery
    .rampInterval(Duration.ofMinutes(2))        // Longer intervals
    .maxTps(Double.POSITIVE_INFINITY)            // No limit
    .minTps(5.0)                                 // Min TPS
    .sustainDuration(Duration.ofMinutes(15))      // Extended sustain
    .stableIntervalsRequired(3)                  // Require 3 stable intervals
    .metricsProvider(metricsProvider)
    .decisionPolicy(new DefaultRampDecisionPolicy(0.01))  // Standard threshold
    .build();
```

### Pattern 3: Stress Testing

Test system under stress with higher error tolerance:

```java
AdaptiveLoadPattern.builder()
    .initialTps(200.0)                          // Higher start
    .rampIncrement(50.0)                        // Moderate increments
    .rampDecrement(100.0)                        // Quick recovery
    .rampInterval(Duration.ofSeconds(20))        // Fast adjustments
    .maxTps(5000.0)                              // Known limit
    .minTps(10.0)                                // Min TPS
    .sustainDuration(Duration.ofMinutes(30))     // Extended sustain
    .stableIntervalsRequired(3)                  // Require 3 stable intervals
    .metricsProvider(metricsProvider)
    .decisionPolicy(new DefaultRampDecisionPolicy(0.05))  // 5% threshold (stress)
    .build();
```

---

## Troubleshooting

### Pattern Never Finds Stable Point

**Symptoms**: Pattern stays in RAMP_DOWN phase, TPS keeps decreasing

**Possible Causes**:
1. Error threshold too low (system always has errors)
2. Ramp decrement too small (can't recover fast enough)
3. System fundamentally unstable

**Solutions**:
- Increase error threshold in decision policy (e.g., 0.01 → 0.05)
- Increase `rampDecrement`
- Check system health independently

### Pattern Oscillates Between Phases

**Symptoms**: Frequent transitions between RAMP_UP and RAMP_DOWN

**Possible Causes**:
1. `rampInterval` too short (reacts to transient errors)
2. `rampIncrement` too large (overshoots stable point)
3. System has intermittent issues

**Solutions**:
- Increase `rampInterval` (e.g., 30s → 2min)
- Decrease `rampIncrement`
- Check for system instability

### Pattern Reaches Max TPS Immediately

**Symptoms**: Pattern transitions to SUSTAIN at max TPS without errors

**Possible Causes**:
1. `maxTps` set too low
2. System can handle more than max

**Solutions**:
- Increase `maxTps` or use `Double.POSITIVE_INFINITY`
- Verify system capacity

---

## Advanced Usage

### Custom Metrics Provider

You can implement your own `MetricsProvider` for custom metrics:

```java
public class CustomMetricsProvider implements MetricsProvider {
    private final MetricsCollector collector;
    
    public CustomMetricsProvider(MetricsCollector collector) {
        this.collector = collector;
    }
    
    @Override
    public double getFailureRate() {
        // Custom calculation
        var snapshot = collector.snapshot();
        return snapshot.failureRate();
    }
    
    @Override
    public long getTotalExecutions() {
        return collector.snapshot().totalExecutions();
    }
}
```

### Monitoring Phase Transitions

```java
// Register metrics
AdaptivePatternMetrics.register(pattern, metrics.getRegistry(), runId);

// Query metrics
MeterRegistry registry = metrics.getRegistry();
Gauge phaseGauge = registry.find("vajrapulse.adaptive.phase").gauge();
double phaseValue = phaseGauge.value(); // 0, 1, 2, or 3
```

---

## See Also

- [MetricsProvider Implementation Guide](METRICS_PROVIDER_IMPLEMENTATION.md)
- [Troubleshooting Guide](TROUBLESHOOTING.md)
- [Adaptive Pattern Design](../architecture/ADAPTIVE_LOAD_PATTERN_DESIGN.md)

---

**Last Updated**: 2025-01-XX  
**Next Review**: Before 1.0 release

