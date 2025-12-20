# Adaptive Load Pattern Example

This example demonstrates the **AdaptiveLoadPattern** feature, proving its ability to automatically find the maximum sustainable TPS through multiple scale-up and scale-down cycles.

## Overview

The AdaptiveLoadPattern automatically:
- **Scales up** TPS when the system can handle load (low error rate, low latency)
- **Scales down** TPS when backpressure is detected (high latency, errors exceed threshold)
- **Finds stable points** by identifying 3 consecutive intervals with low error rate
- **Adapts continuously** to changing system conditions and backpressure
- **Presents final TPS** after test completion

### Backpressure Simulation

This example simulates realistic backpressure behavior:
- **Low Load (0-40 TPS)**: Fast response (10ms), 0% failure rate
- **Medium Load (40-80 TPS)**: Moderate latency (20-50ms), 0.5% failure rate
- **High Load (80-120 TPS)**: High latency (50-150ms), 2% failure rate
- **Very High Load (120-160 TPS)**: Very high latency (150-300ms), 5% failure rate
- **Overload (160+ TPS)**: Extreme latency (300-500ms), 10%+ failure rate

The AdaptiveLoadPattern detects these backpressure indicators (increasing latency and failures) and adapts by reducing TPS to find a stable operating point.

## What This Example Proves

This comprehensive example demonstrates:

1. ✅ **Multiple Scale-Up Cycles**: Pattern ramps up TPS multiple times as system capacity allows
2. ✅ **Multiple Scale-Down Cycles**: Pattern ramps down when errors occur
3. ✅ **Stable Point Detection**: Pattern finds and sustains at stable TPS points
4. ✅ **Extended Duration**: Runs for configurable duration (default: 2 minutes) to show multiple cycles
5. ✅ **Final TPS Reporting**: Presents final application TPS after completion
6. ✅ **Real-Time Monitoring**: Shows pattern state, TPS, and metrics every 10 seconds

## Running the Example

### Basic Run (2 minutes default)

```bash
./gradlew :examples:adaptive-load-test:run
```

### Custom Duration

```bash
./gradlew :examples:adaptive-load-test:run --args "5"
```

This runs for 5 minutes instead of the default 2 minutes.

## Example Output

The example provides real-time monitoring every 10 seconds:

```
------------------------------------------------
Time: 10s / 120s
Phase: RAMP_UP
Current TPS: 30.0
Stable TPS: Not found yet
Phase Transitions: 0
Total Executions: 150
Failure Rate: 0.00%
Actual TPS: 15.0
Latency: P50=10.0ms, P95=12.0ms
------------------------------------------------
```

And a final comprehensive report:

```
========================================
FINAL TEST REPORT
========================================
Test Duration: 120.0 seconds

Pattern State:
  Final Phase: SUSTAIN
  Final TPS: 85.0
  Stable TPS Found: 85.0
  Phase Transitions: 3

Execution Metrics:
  Total Executions: 10200
  Successful: 10098 (99.00%)
  Failed: 102 (1.00%)
  Latency P50: 45.2ms
  Latency P95: 78.5ms
  Latency P99: 125.3ms

========================================
FINAL APPLICATION TPS: 85.00
========================================

SUCCESS: Adaptive pattern found stable TPS of 85.0
The pattern successfully adapted to find the maximum sustainable load.
```

## How It Works

### Simulated Application Behavior with Backpressure

The example uses a task that simulates realistic backpressure behavior:

- **Low Load (0-40 TPS)**: Fast response (10ms), 0% failure rate - system handles load easily
- **Medium Load (40-80 TPS)**: Moderate latency (20-50ms), 0.5% failure rate - slight backpressure
- **High Load (80-120 TPS)**: High latency (50-150ms), 2% failure rate - significant backpressure
- **Very High Load (120-160 TPS)**: Very high latency (150-300ms), 5% failure rate - severe backpressure
- **Overload (160+ TPS)**: Extreme latency (300-500ms), 10%+ failure rate - system overloaded

**Key Backpressure Indicators:**
- Latency increases proportionally with load
- Failure rate increases as system approaches capacity
- System shows degradation before complete failure
- AdaptiveLoadPattern detects these indicators and adapts

### Adaptive Pattern Configuration

```java
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(10.0)                          // Start at 10 TPS
    .rampIncrement(10.0)                       // Increase by 10 TPS per interval
    .rampDecrement(20.0)                       // Decrease by 20 TPS per interval when errors occur
    .rampInterval(Duration.ofSeconds(5))        // Check/adjust every 5 seconds
    .maxTps(200.0)                             // Maximum 200 TPS
    .minTps(5.0)                               // Minimum 5 TPS
    .sustainDuration(Duration.ofSeconds(30))   // Sustain at stable point for 30 seconds
    .stableIntervalsRequired(3)                // Require 3 stable intervals
    .metricsProvider(metricsProvider)          // Metrics for feedback
    .decisionPolicy(new DefaultRampDecisionPolicy(0.01))  // 1% error threshold
    .build();
```

### Pattern Phases

The pattern goes through these phases:

1. **RAMP_UP**: Starts at initial TPS, increases until errors occur
2. **RAMP_DOWN**: Decreases TPS when errors exceed threshold
3. **SUSTAIN**: Found stable point (3 consecutive intervals with low error rate)
4. **COMPLETE**: Only if stable point never found (after max attempts)

## Expected Behavior

During a 2-minute test, you should see:

1. **Initial Ramp-Up** (0-30s): Pattern starts at 10 TPS and ramps up
   - System responds quickly (low latency)
   - No failures at low load
2. **Backpressure Detection** (30-60s): Latency increases, failures start
   - Pattern detects backpressure (high latency + failures)
   - Pattern ramps down to reduce load
3. **Stable Point Found** (60-90s): Pattern finds first stable TPS
   - System operates at sustainable load
   - Low latency, low failure rate
4. **Sustain Phase** (90-120s): Pattern sustains at stable TPS
   - System maintains stable performance
5. **Multiple Cycles**: If backpressure increases, pattern adapts again
   - Pattern continuously monitors latency and failures
   - Adapts to changing backpressure conditions

## Verification

This example proves beyond doubt that AdaptiveLoadPattern:

- ✅ **Scales up** when system can handle load (low latency, no failures)
- ✅ **Scales down** when backpressure detected (high latency, failures)
- ✅ **Detects backpressure** through latency and failure rate monitoring
- ✅ **Finds stable points** through multiple cycles
- ✅ **Adapts continuously** to changing backpressure conditions
- ✅ **Runs for extended duration** without hanging
- ✅ **Presents final TPS** after completion
- ✅ **Handles realistic backpressure** scenarios

## Customization

You can customize the example by modifying:

- **Test Duration**: Pass duration in minutes as command-line argument
- **Initial TPS**: Change `10.0` in pattern configuration
- **Ramp Increment**: Change `10.0` to adjust how fast it ramps up
- **Ramp Decrement**: Change `20.0` to adjust how fast it ramps down
- **Ramp Interval**: Change `Duration.ofSeconds(5)` to adjust check frequency
- **Error Threshold**: Change `0.01` (1%) to adjust sensitivity
- **Failure Simulation**: Modify `AdaptiveTestTask` to simulate different failure patterns

## Troubleshooting

### Pattern Doesn't Find Stable Point

If the pattern doesn't find a stable point:
- Increase test duration (e.g., `--args "5"` for 5 minutes)
- Adjust error threshold (lower = more sensitive)
- Check failure simulation in `AdaptiveTestTask`

### Pattern Hangs

If the pattern appears to hang:
- This should be fixed in 0.9.6+
- Check that you're using the latest version
- Verify the fix in `ExecutionEngine.java` (checks for 0.0 TPS after 10 iterations and 100ms)

## Next Steps

After running this example:

1. **Modify the failure simulation** to test different scenarios
2. **Adjust pattern parameters** to see how they affect behavior
3. **Integrate with real applications** by replacing `AdaptiveTestTask` with actual tasks
4. **Use in production** to find optimal TPS for your systems

## See Also

- [AdaptiveLoadPattern JavaDoc](../../vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java)
- [Adaptive Pattern Design](../../documents/architecture/ADAPTIVE_LOAD_PATTERN_DESIGN.md)
- [Adaptive Pattern Usage Guide](../../documents/guides/ADAPTIVE_PATTERN_USAGE.md)

