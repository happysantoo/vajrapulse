# Release 0.9.3 - Quick Wins Plan

**Date**: 2025-01-XX  
**Target Release**: 0.9.3  
**Timeline**: 3-5 days  
**Focus**: High-impact, low-effort features

---

## Executive Summary

This plan identifies **low-hanging fruit** features from the strategic roadmap that can be quickly implemented for 0.9.3 release. These features provide immediate value with minimal development effort.

---

## Selected Features for 0.9.3

### ✅ Already Completed
1. **BOM Module** - ✅ Done
2. **Release Process Improvements** - ✅ Done
3. **JavaDoc Standards** - ✅ Done

### 🎯 Quick Wins (2-3 days total)

> **Note**: Prometheus Exporter has been moved to a low-priority feature request (see [GitHub Issue #15](https://github.com/happysantoo/vajrapulse/issues/15)). OpenTelemetry exporter already provides Prometheus support via OTEL Collector.

#### 1. Basic Queue Depth Tracking ⭐⭐
**Impact**: High | **Reach**: All users | **Effort**: 2 days

**Why**: From user wishlist - helps identify client-side bottlenecks.

**Implementation**:
- Track pending executions in `ExecutionEngine`
- Add gauge metric: `vajrapulse.execution.queue.size`
- Add histogram: `vajrapulse.execution.queue.wait_time`
- Simple implementation (no complex instrumentation needed)

**Timeline**: 2 days

---

#### 2. Health & Metrics Endpoints ⭐
**Impact**: Medium | **Reach**: K8s/Operational users | **Effort**: 1 day

**Why**: Required for Kubernetes deployments (liveness/readiness probes).

**Implementation**:
- Simple HTTP server in worker
- `/health` endpoint (UP/DOWN status)
- `/metrics` endpoint (Prometheus format)
- Optional: `/ready` endpoint

**Timeline**: 1 day

---

## Detailed Implementation Plan

### Feature 1: Basic Queue Depth Tracking (2 days)

#### Day 1: Implementation

**Update ExecutionEngine**:
```java
// Add queue tracking
private final AtomicLong pendingExecutions = new AtomicLong(0);
private final LongAdder queueWaitTimeNanos = new LongAdder();

// In execution loop
public void run() throws Exception {
    // ... existing code ...
    
    while (!stopRequested.get() && rateController.getElapsedMillis() < testDurationMillis) {
        rateController.waitForNext();
        
        long queueStartNanos = System.nanoTime();
        pendingExecutions.incrementAndGet();
        
        long currentIteration = iteration++;
        executor.submit(() -> {
            try {
                long waitTime = System.nanoTime() - queueStartNanos;
                queueWaitTimeNanos.add(waitTime);
                
                // Execute task
                ExecutionMetrics metrics = taskExecutor.executeWithMetrics(currentIteration);
                metricsCollector.record(metrics);
            } finally {
                pendingExecutions.decrementAndGet();
            }
        });
    }
}
```

**Add Metrics to MetricsCollector**:
```java
// Add queue metrics
private final Gauge queueSizeGauge;
private final Timer queueWaitTimer;

// In constructor
this.queueSizeGauge = Gauge.builder("vajrapulse.execution.queue.size")
    .description("Number of pending task executions")
    .register(registry);

this.queueWaitTimer = Timer.builder("vajrapulse.execution.queue.wait_time")
    .description("Time tasks wait in queue before execution")
    .register(registry);
```

#### Day 2: Testing & Integration

- Add tests
- Update console exporter to show queue metrics
- Update documentation

---

### Feature 2: Health & Metrics Endpoints (1 day)

**Implementation**:
```java
package com.vajrapulse.worker;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * HTTP server for health and metrics endpoints.
 */
public final class HealthServer {
    private final HttpServer server;
    private final PrometheusExporter prometheusExporter;
    private volatile boolean healthy = true;
    private volatile boolean ready = true;
    
    public HealthServer(int port, PrometheusExporter prometheusExporter) throws IOException {
        this.prometheusExporter = prometheusExporter;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Health endpoint
        server.createContext("/health", exchange -> {
            String response = healthy ? "{\"status\":\"UP\"}" : "{\"status\":\"DOWN\"}";
            exchange.sendResponseHeaders(healthy ? 200 : 503, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        
        // Readiness endpoint
        server.createContext("/ready", exchange -> {
            String response = ready ? "{\"ready\":true}" : "{\"ready\":false}";
            exchange.sendResponseHeaders(ready ? 200 : 503, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        
        // Metrics endpoint (if Prometheus exporter available)
        if (prometheusExporter != null) {
            server.createContext("/metrics", exchange -> {
                String metrics = prometheusExporter.scrape();
                exchange.sendResponseHeaders(200, metrics.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(metrics.getBytes());
                }
            });
        }
        
        server.setExecutor(null);
    }
    
    public void start() {
        server.start();
    }
    
    public void stop() {
        server.stop(0);
    }
    
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
    
    public void setReady(boolean ready) {
        this.ready = ready;
    }
}
```

---

## Implementation Timeline

| Day | Feature | Tasks |
|-----|---------|-------|
| **Day 1** | Queue Tracking | Implementation, metrics integration |
| **Day 2** | Queue Tracking | Testing, documentation |
| **Day 3** | Health Endpoints | Implementation, testing, final integration |

**Total**: 3 days (can be parallelized to 2 days)

---

## Success Criteria

### Queue Depth Tracking
- ✅ Queue size metric available
- ✅ Queue wait time metric available
- ✅ Metrics appear in console output
- ✅ Metrics appear in Prometheus/OTEL
- ✅ Tests pass

### Health Endpoints
- ✅ `/health` endpoint returns UP/DOWN
- ✅ `/ready` endpoint returns ready status
- ✅ `/metrics` endpoint works (if Prometheus enabled)
- ✅ Suitable for K8s liveness/readiness probes

---

## Files to Create/Modify

### Modified Files
- `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/ExecutionEngine.java`
- `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/MetricsCollector.java`
- `vajrapulse-worker/src/main/java/com/vajrapulse/worker/VajraPulseWorker.java`
- `vajrapulse-exporter-console/src/main/java/com/vajrapulse/exporter/console/ConsoleMetricsExporter.java`

### Documentation
- Update main `README.md` with health endpoints usage
- Update `CHANGELOG.md`

---

## Benefits for 0.9.3

### Immediate Value
- ✅ **Queue metrics** help identify bottlenecks
- ✅ **Health endpoints** enable K8s deployments
- ✅ **Better observability** with more metrics

### User Impact
- ✅ Better debugging with queue depth visibility
- ✅ Production-ready with health checks
- ✅ Kubernetes-ready deployments

### Technical Debt
- ✅ Minimal - all features are well-scoped
- ✅ No breaking changes
- ✅ Backward compatible

---

## Risk Assessment

| Feature | Risk | Mitigation |
|---------|------|------------|
| Queue Tracking | Low | Simple atomic counters |
| Health Endpoints | Low | Simple HTTP server |

**Overall Risk**: Low - All features are straightforward implementations

---

## Alternative: Single Feature

If 3 days is too much, we can do just:

1. **Queue Depth Tracking** (2 days) - High impact, from user wishlist

**Total**: 2 days, still good value

---

## Next Steps

1. **Decide on scope** - Full plan (3 days) or minimal (2 days)
2. **Start with Queue Tracking** - High impact, from user wishlist
3. **Add health endpoints** - Quick win, enables K8s

---

*These quick wins provide immediate value with minimal effort, perfect for 0.9.3 release.*

