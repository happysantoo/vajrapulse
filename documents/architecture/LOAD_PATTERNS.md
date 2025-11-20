# VajraPulse Load Patterns

Pre-1.0 design: patterns emphasize clarity, minimal allocation, and deterministic math. All patterns implement `LoadPattern` with:

```java
public interface LoadPattern {
    double calculateTps(long elapsedMillis);
    Duration getDuration();
}
```

## StaticLoad
Constant TPS for entire duration.
```java
new StaticLoad(200.0, Duration.ofMinutes(2));
```
Use for baseline throughput measurement.

## RampUpLoad
Linear 0→target TPS over ramp duration; test ends when ramp completes.
```java
new RampUpLoad(500.0, Duration.ofSeconds(30));
```
Use for cold-start / autoscaler warmup observation.

## RampUpToMaxLoad
Linear 0→target TPS then sustain.
```java
new RampUpToMaxLoad(500.0, Duration.ofSeconds(30), Duration.ofMinutes(5));
```
Highlights resource contention after stabilization.

## StepLoad
Discrete TPS changes – explicit control of staging phases.
```java
new StepLoad(List.of(
  new StepLoad.Step(100.0, Duration.ofSeconds(30)),
  new StepLoad.Step(250.0, Duration.ofSeconds(45)),
  new StepLoad.Step(400.0, Duration.ofSeconds(60))
));
```
Validation: non-empty list; each rate ≥0; each duration >0.
End behavior: returns 0 TPS after final step for engine termination.

## SineWaveLoad
Smooth oscillation.
```java
new SineWaveLoad(300.0 /*mean*/, 150.0 /*amp*/, Duration.ofMinutes(3), Duration.ofSeconds(20));
```
Equation: `mean + amplitude * sin(2π * (elapsed % period) / period)` clipped at 0.
Use to reveal latency drift and GC cadence sensitivity.

## SpikeLoad
Periodic short spikes over a base rate.
```java
new SpikeLoad(200.0, 800.0, Duration.ofMinutes(2), Duration.ofSeconds(15), Duration.ofSeconds(3));
```
Validation: all rates ≥0; totalDuration >0; interval >0; spikeDuration >0 and < interval.
Use for burst absorption / queue depth analysis.

## Choosing Patterns
| Goal | Recommended Pattern |
|------|---------------------|
| Baseline latency | StaticLoad |
| Autoscaler warmup | RampUpLoad |
| Sustained pressure post-ramp | RampUpToMaxLoad |
| Release-like phased increase | StepLoad |
| Cyclical traffic / daily curve | SineWaveLoad |
| Burst resilience / throttling | SpikeLoad |

## CLI Mapping
| Mode | Flags |
|------|-------|
| static | `--tps`, `--duration` |
| ramp | `--tps`, `--ramp-duration` |
| ramp-sustain | `--tps`, `--ramp-duration`, `--duration` |
| step | `--steps "rate:dur,..."` |
| sine | `--mean-rate`, `--amplitude`, `--period`, `--duration` |
| spike | `--base-rate`, `--spike-rate`, `--spike-interval`, `--spike-duration`, `--duration` |

## Future (Post-0.9) Ideas
- AdaptiveFeedbackLoad (PID controller around target latency)
- BurstBucketLoad (token bucket shaped bursts)
- TraceReplayLoad (replay empirical traffic distribution)

Breaking changes allowed until 1.0; keep implementations allocation-light & branch-predictable.
