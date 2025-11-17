# VajraPulse Decision Log

Date Started: 2025-11-16

Purpose: Track non-trivial architectural & product decisions for transparency and future review. Pre-1.0 decisions can still be reversed if better patterns emerge.

## Format
Each entry:
```
### [ID] Title
Date: YYYY-MM-DD
Context: Why is this needed?
Options Considered: Brief bullet list
Decision: Chosen option & rationale
Implications: Positive / negative consequences
Revisit: Conditions that would trigger reconsideration
```

## Entries

### D1 Run ID Tagging Strategy
Date: 2025-11-16
Context: Need correlation across metrics, traces, logs for concurrent or overlapping runs.
Options Considered:
- A: Add `run_id` tag to all metrics
- B: Use separate meter registry per run, no tag
- C: Global counter increments with run number only (less uniqueness)
Decision: Adopt Option A (tag `run_id`) for clarity and PromQL filtering simplicity.
Implications: Slight additional cardinality (one extra label). Simplicity outweighs minimal overhead.
Revisit: If cardinality issues arise with many concurrent runs (&gt;100), evaluate registry isolation.

### D2 Graceful Shutdown Mechanism
Date: 2025-11-16
Context: Ensure no lost metrics/traces when user interrupts load test.
Options Considered:
- A: Polling stop flag only
- B: JVM shutdown hook + stop flag
- C: Signal handler library (external dependency)
Decision: Option B (shutdown hook + atomic flag). No external dependency; reliable on SIGINT/SIGTERM.
Implications: Shutdown logic must be idempotent; hook complexity minimal.
Revisit: If multi-process orchestration adds coordination requirements.

### D3 MetricsCollector Run ID Integration
Date: 2025-11-16
Context: Need to include run ID tag consistently.
Options Considered:
- A: Overload constructor with runId
- B: Builder pattern (more code)
- C: ThreadLocal context injection
Decision: Option A: Simple overload maintains minimal code footprint.
Implications: Caller responsibility to pass runId early; immutability preserved.
Revisit: If future context expands (scenario_id, worker_id) -> consider small builder.

### D4 BlazeMeter Integration Approach
Date: 2025-11-16
Context: Provide compatibility prior to distributed mode.
Options Considered:
- A: Implement JTL file exporter (offline integration)
- B: Direct REST push (requires stable remote API)
- C: In-memory adapter requiring BlazeMeter SDK (extra dependency)
Decision: Option A: JTL exporter—lowest dependency; immediate user value.
Implications: Users manually import; no live streaming yet.
Revisit: After 1.0 when real-time dashboards or ingestion needed.

---
Future decisions will append below.

Prepared by: GitHub Copilot
