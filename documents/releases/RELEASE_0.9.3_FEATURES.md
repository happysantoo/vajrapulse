# Release 0.9.3 - Feature Selection

**Date**: 2025-01-XX  
**Status**: Planning  
**Target**: Quick wins with high impact

---

## Recommended Features for 0.9.3

Based on the strategic roadmap analysis, here are the **best low-hanging fruit** features for 0.9.3:

> **Note**: Prometheus Exporter has been moved to a low-priority feature request (see [GitHub Issue #15](https://github.com/happysantoo/vajrapulse/issues/15)). OpenTelemetry exporter already provides Prometheus support via OTEL Collector.

### 🎯 Top 3 Recommendations

#### 1. Basic Queue Depth Tracking ⭐⭐ (2 days)
**Priority**: HIGH  
**Impact**: High | **Effort**: Low | **Reach**: All users

**Why**:
- From user wishlist: "metrics on request getting processed"
- Helps identify client-side bottlenecks
- Simple implementation (atomic counters)
- Immediate debugging value

**Effort**: 2 days  
**Value**: High

---

#### 2. Health & Metrics Endpoints ⭐ (1 day)
**Priority**: MEDIUM  
**Impact**: Medium | **Effort**: Very Low | **Reach**: K8s/Operational users

**Why**:
- Required for Kubernetes deployments
- Very quick to implement
- Enables production deployments
- Standard practice

**Effort**: 1 day  
**Value**: Medium-High

---

## Implementation Options

### Option A: Full Quick Wins (3 days)
- ✅ Queue Depth Tracking (2 days)
- ✅ Health Endpoints (1 day) - Can be done in parallel

**Total**: 3 days  
**Value**: High

### Option B: Single Feature (2 days)
- ✅ Queue Depth Tracking (2 days)

**Total**: 2 days  
**Value**: Medium-High

---

## Recommendation: Option A (Full Quick Wins)

**Rationale**:
1. **Queue Tracking** - From user wishlist, helps debugging, high impact
2. **Health Endpoints** - Enables K8s, very quick, production-ready

**Total Effort**: 3 days (can be parallelized to ~2 days)  
**Total Value**: High

---

## Feature Details

### 1. Queue Depth Tracking

**What it does**:
- Tracks pending executions in queue
- Measures queue wait time
- Exposes as metrics: `vajrapulse.execution.queue.size`, `vajrapulse.execution.queue.wait_time`

**User Benefit**:
```
Client Metrics:
  Queue Size: 1,234 (current)
  Queue Wait Time: P50=2ms, P95=15ms, P99=45ms
```

**Implementation Complexity**: Low (atomic counters)

---

### 2. Health Endpoints

**What it does**:
- `/health` - Returns UP/DOWN status
- `/ready` - Returns readiness status
- `/metrics` - Prometheus metrics (if enabled)

**User Benefit**:
```yaml
# Kubernetes deployment
livenessProbe:
  httpGet:
    path: /health
    port: 8080
readinessProbe:
  httpGet:
    path: /ready
    port: 8080
```

**Implementation Complexity**: Very Low (simple HTTP server)

---

## Comparison with Roadmap

| Feature | Roadmap Priority | Effort | 0.9.3? |
|---------|------------------|--------|--------|
| BOM Module | P0 | 1 day | ✅ Done |
| Prometheus Exporter | P1 | 3 days | ⏸️ Low priority ([#15](https://github.com/happysantoo/vajrapulse/issues/15)) |
| Queue Tracking | P0 (user wishlist) | 2 days | ✅ Recommended |
| Health Endpoints | P1 | 1 day | ✅ Recommended |
| Quick Start Wizard | P0 | 1 week | ❌ Too much effort |
| Client-Side Metrics (full) | P0 | 1 week | ❌ Too much effort |
| BlazeMeter Integration | P0 | 4 weeks | ❌ Post-0.9.3 |

---

## Decision Matrix

### If you have 2 days:
- ✅ Queue Depth Tracking only

### If you have 3 days:
- ✅ Queue Depth Tracking
- ✅ Health Endpoints

---

## Next Steps

1. **Decide on scope** - Which option (A, B, or C)?
2. **Start implementation** - Begin with Prometheus Exporter
3. **Test thoroughly** - Ensure all features work
4. **Update documentation** - README, examples, changelog
5. **Release 0.9.3** - With new features

---

## Success Metrics

After 0.9.3 release:
- ✅ Queue metrics help identify bottlenecks
- ✅ Health endpoints enable K8s deployments
- ✅ Better observability overall
- ✅ Production-ready with health checks

---

*See `RELEASE_0.9.3_QUICK_WINS.md` for detailed implementation plan.*

