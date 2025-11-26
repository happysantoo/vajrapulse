# Adaptive Load Pattern - Distributed Testing Support

**Feature**: Adaptive Load Pattern for 0.9.5  
**Status**: Design Complete - Ready for Review  
**Distributed Support**: Designed for future extension

---

## Executive Summary

The adaptive load pattern design **fully supports distributed testing** through a backward-compatible extension approach:

1. **Phase 1 (0.9.5)**: Single-instance implementation
2. **Phase 2 (Future)**: Distributed mode via optional parameters
3. **No Breaking Changes**: Single-instance mode continues to work

---

## How Distributed Testing Will Work

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│         Distributed Test: 5 Workers, 10,000 TPS Total      │
│                                                             │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐           │
│  │  Worker 1  │  │  Worker 2  │  │  Worker 5  │           │
│  │            │  │            │  │            │           │
│  │ Adaptive   │  │ Adaptive   │  │ Adaptive   │           │
│  │ Pattern    │  │ Pattern    │  │ Pattern    │           │
│  │            │  │            │  │            │           │
│  │ Target:    │  │ Target:    │  │ Target:   │           │
│  │ 2,000 TPS  │  │ 2,000 TPS  │  │ 2,000 TPS │           │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘           │
│        │                │                │                 │
│        └────────────────┼────────────────┘                 │
│                         │                                   │
│              ┌──────────▼──────────┐                        │
│              │  Metrics Store      │                        │
│              │  (Prometheus/OTEL)  │                        │
│              │                     │                        │
│              │  Aggregated View:   │                        │
│              │  - Total TPS: 10k   │                        │
│              │  - Error Rate: 0.5% │                        │
│              └──────────┬──────────┘                        │
│                         │                                   │
│              ┌──────────▼──────────┐                        │
│              │  Coordination       │                        │
│              │  Service            │                        │
│              │                     │                        │
│              │  Current Phase:     │                        │
│              │  RAMP_UP            │                        │
│              │                     │                        │
│              │  Total TPS: 3,000  │                        │
│              │  (each worker: 600)│                        │
│              └─────────────────────┘                        │
└─────────────────────────────────────────────────────────────┘
```

### How It Works

1. **Each Worker**:
   - Runs `AdaptiveLoadPattern` with distributed mode enabled
   - Queries aggregated metrics (all workers combined)
   - Gets current phase from coordination service
   - Calculates its share: `workerTps = totalTps / workerCount`

2. **Metrics Aggregation**:
   - All workers export metrics to Prometheus/OTEL
   - Aggregated metrics provider queries: `sum(vajrapulse.execution.total{status="failure"}) / sum(vajrapulse.execution.total)`
   - Gets error rate across ALL workers

3. **Phase Coordination**:
   - Coordination service maintains current phase
   - When error threshold exceeded, coordinator updates phase to RAMP_DOWN
   - All workers read phase and adjust accordingly

4. **TPS Synchronization**:
   - Coordinator calculates total TPS based on phase
   - Broadcasts total TPS to all workers
   - Each worker calculates its share

---

## Design Compatibility

### Single-Instance Mode (0.9.5)

```java
// Simple: Just pass MetricsCollector
AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    100.0, 50.0, 100.0, Duration.ofMinutes(1),
    5000.0, Duration.ofMinutes(10), 0.01,
    metricsCollector  // Local metrics only
);
```

### Distributed Mode (Future)

```java
// Distributed: Pass aggregated provider and coordinator
AggregatedMetricsProvider provider = new PrometheusMetricsProvider(
    "http://prometheus:9090",
    runId
);

CoordinationService coordinator = new KubernetesConfigMapCoordinator(
    "vajrapulse-adaptive-state",
    namespace
);

AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    100.0, 50.0, 100.0, Duration.ofMinutes(1),
    5000.0, Duration.ofMinutes(10), 0.01,
    null,  // No local metrics in distributed mode
    provider,  // Aggregated metrics
    coordinator,  // Phase coordination
    runId,
    workerId,
    workerCount
);
```

### Backward Compatibility

The design ensures:
- ✅ Single-instance mode works as-is
- ✅ Distributed mode is optional extension
- ✅ No breaking changes to `LoadPattern` interface
- ✅ Can mix modes (some workers single, some distributed)

---

## Coordination Service Options

### Option 1: Kubernetes ConfigMap (Recommended for K8s)

**How it works**:
- Coordinator writes phase/TPS to ConfigMap
- Workers watch ConfigMap for changes
- K8s handles synchronization

**Pros**:
- ✅ No additional service needed
- ✅ Native K8s integration
- ✅ Simple implementation

**Cons**:
- ❌ K8s-specific (doesn't work outside K8s)

---

### Option 2: Redis/Shared Database

**How it works**:
- Coordinator writes state to Redis
- Workers poll Redis for updates
- Works with any orchestration

**Pros**:
- ✅ Works everywhere
- ✅ Fast, reliable
- ✅ Standard approach

**Cons**:
- ❌ Requires Redis/database infrastructure

---

### Option 3: HTTP Coordination Service

**How it works**:
- Lightweight REST service
- Workers poll HTTP endpoint for phase/TPS
- Can be deployed alongside workers

**Pros**:
- ✅ Simple to implement
- ✅ Works everywhere
- ✅ No external dependencies

**Cons**:
- ❌ Additional service to maintain

---

### Option 4: BlazeMeter API (For BlazeMeter Integration)

**How it works**:
- Use BlazeMeter's coordination features
- Workers report to BlazeMeter
- BlazeMeter coordinates adaptive pattern

**Pros**:
- ✅ Enterprise-ready
- ✅ Managed service
- ✅ Rich dashboard

**Cons**:
- ❌ Vendor lock-in
- ❌ Requires BlazeMeter subscription

---

## Implementation Phases

### Phase 1: Single-Instance (0.9.5) - 5-7 days

**Deliverables**:
- `AdaptiveLoadPattern` class
- State machine implementation
- Metrics integration (local)
- Tests and examples
- Documentation

**No distributed support yet** - keeps it simple

---

### Phase 2: Distributed Support (Post-0.9.5) - 1-2 weeks

**Deliverables**:
- `AggregatedMetricsProvider` interface
- Prometheus-based provider
- OTEL-based provider
- Coordination service interface
- K8s ConfigMap coordinator
- Redis coordinator (optional)
- HTTP coordinator (optional)
- Updated `AdaptiveLoadPattern` with distributed mode
- Distributed testing examples

---

## Key Design Decisions for Distributed Mode

### Decision 1: Who Calculates Total TPS?

**Options**:
- **A** ✅: Coordinator calculates, broadcasts to workers
- **B**: Each worker calculates independently (risks divergence)

**Recommendation**: **Option A** - Coordinator ensures consistency

---

### Decision 2: How to Distribute TPS?

**Options**:
- **A** ✅: Equal split (`totalTps / workerCount`)
- **B**: Weighted distribution (based on worker capacity)
- **C**: Configurable strategy

**Recommendation**: **Option A** - Start simple, add weighted later

---

### Decision 3: What if Coordinator Fails?

**Options**:
- **A** ✅: Workers continue at last known TPS (graceful degradation)
- **B**: Workers stop (fail-safe)
- **C**: Leader election (complex)

**Recommendation**: **Option A** - Graceful degradation, test continues

---

### Decision 4: Metrics Aggregation Frequency?

**Options**:
- **A** ✅: Same as ramp interval (e.g., every 1 minute)
- **B**: More frequent (e.g., every 10 seconds)
- **C**: Configurable

**Recommendation**: **Option A** - Match ramp interval for simplicity

---

## Example: Distributed Adaptive Test

### Scenario
- **Total Target**: Find max TPS (up to 10,000)
- **Workers**: 5 instances
- **Configuration**: Start 100 TPS, ramp +50/min, max 10k, sustain 10min

### Execution Flow

```
Time    Phase      Total TPS    Worker TPS    Error Rate    Action
─────────────────────────────────────────────────────────────────────
0:00    RAMP_UP    100          20            0.0%          Start
1:00    RAMP_UP    150          30            0.0%          +50 TPS
2:00    RAMP_UP    200          40            0.0%          +50 TPS
...
10:00   RAMP_UP    600          120           0.0%          +50 TPS
11:00   RAMP_UP    650          130           0.0%          +50 TPS
12:00   RAMP_UP    700          140           1.2%          Errors! → RAMP_DOWN
13:00   RAMP_DOWN  600          120           0.8%           Still errors
14:00   RAMP_DOWN  500          100           0.3%           Still errors
15:00   RAMP_DOWN  400          80            0.0%          Stable! → SUSTAIN
16:00   SUSTAIN    400          80            0.0%          Hold
...
25:00   SUSTAIN    400          80            0.0%          Complete
```

### Key Points

1. **All workers** see same phase (RAMP_UP → RAMP_DOWN → SUSTAIN)
2. **Total TPS** is coordinated (all workers sum to total)
3. **Error rate** is aggregated (across all workers)
4. **Each worker** gets its share (total / workerCount)

---

## Benefits of This Design

### ✅ Backward Compatible
- Single-instance mode works as-is
- No breaking changes
- Can add distributed support later

### ✅ Works with Existing Orchestration
- K8s Jobs: Use ConfigMap coordinator
- BlazeMeter: Use BlazeMeter API
- CI/CD: Use HTTP coordinator
- Message Queue: Use Redis coordinator

### ✅ Fault Tolerant
- Coordinator failure: Workers continue at last TPS
- Worker failure: Others continue, metrics adjust
- Network issues: Graceful degradation

### ✅ Simple to Understand
- Each worker runs same pattern
- Coordination is external (doesn't complicate pattern)
- Clear separation of concerns

---

## Comparison with Alternatives

| Approach | Complexity | Fault Tolerance | Works with K8s | Works with BlazeMeter |
|----------|-----------|-----------------|----------------|----------------------|
| **Metrics Aggregator** ⭐ | Low | High | ✅ | ✅ |
| Coordinator-Based | Medium | Low | ⚠️ | ⚠️ |
| Peer-to-Peer | High | Medium | ⚠️ | ❌ |

**Winner**: Metrics Aggregator Pattern - Simple, fault-tolerant, works everywhere

---

## Next Steps

1. **Review distributed design** - Does this approach work for you?
2. **Make decisions** on coordination service preference
3. **Approve single-instance first** - Then add distributed later?
4. **Start implementation** - Phase 1 (single-instance) for 0.9.5

---

## Questions?

**Q: Will this work with BlazeMeter?**  
A: Yes! BlazeMeter can coordinate via its API. Workers report metrics to BlazeMeter, BlazeMeter coordinates adaptive pattern.

**Q: What if workers have different capabilities?**  
A: Can extend to weighted distribution later. Start with equal split.

**Q: How do we handle network partitions?**  
A: Workers continue at last known TPS. Test doesn't fail, just may not adapt optimally.

**Q: Can we test this without distributed infrastructure?**  
A: Yes! Single-instance mode works standalone. Distributed mode is optional.

---

*This design ensures the adaptive pattern works in both single-instance and distributed scenarios, with a clear path for extension.*

