# VajraPulse Minimal Viable 1.0 Release Plan

Date: 2025-11-16
Branch Base: `phase1-opentelemetry-exporter` (will evolve)
Document Purpose: Define the narrow, high-impact scope necessary to cut a credible 1.0 release focused on architectural solidity, observability foundation, performance guarantees, and release hygiene—excluding deferable enhancements (P1/P2) documented in `ONE_ZERO_GAP_ANALYSIS.md`.

## 1. Vision (Condensed)
Deliver a lean, reliable distributed load generation core with standardized metrics, initial tracing + log correlation, accurate rate control, lifecycle consistency, and enforceable performance & quality gates. Prioritize correctness, transparency, and maintainability over feature breadth.

## 2. 1.0 Minimum Scope (P0 Items Only)
Included P0 Items:
- Architecture: A1 (Lifecycle), A3 (Distributed Coordination – minimal), A4 (Configuration System), A11 (ScopedValues), A14 (Graceful Shutdown), A15 (API Audit)
- Observability: O1 (Tracing), O2 (Log Correlation), O8 (Run Metadata)
- Performance: P1 (Benchmark Suite), P2 (Allocation Profiling), P7 (Metrics Overhead), P8 (Lock-Free Aggregation), P12 (Regression Gate)
- Release: R1 (CI Pipeline), R3 (JavaDoc Coverage), R4 (Versioning Strategy), R6 (Security Review), R8 (License/Headers), R12 (Quick Start Guide), R14 (Quality Gates)

Explicitly Excluded (until post-1.0): Exemplars (O3), Backpressure variants beyond minimal (A9 advanced modes), Plugin SPI (A5), Advanced load patterns beyond constant/ramp (A8 extended), Persistence (A10), Warm-up auto-detection (P11), Signed builds (R15), Dashboard maturity extensions.

## 3. Release Principles
- Stability over breadth: No partial/experimental subsystems in 1.0 scope.
- Deterministic runs: Lifecycle & graceful shutdown ensure complete metric/tracing export.
- Observability parity: Every task execution emits metrics + optional trace + structured log entries tagged with run ID.
- Performance baselines enforced automatically in CI.
- Zero tolerance for compiler warnings or deprecated API usage.

## 4. Architecture Deliverables
| Deliverable | Description | Acceptance Criteria | Related IDs |
|-------------|-------------|---------------------|-------------|
| Task Lifecycle API | `TaskLifecycle` abstraction with explicit init/execute/teardown; executor honors sequence | Public record/interface documented; teardown always invoked; metrics capture duration excluding init | A1 |
| Distributed Minimal Mode | Controller + worker registration (in-memory or lightweight gRPC); global rate broadcast; aggregated metrics snapshot | Multi-worker run (>=3 workers) shows unified metrics & consistent run ID | A3, O8 |
| Configuration System | Central config loader (YAML/JSON) with schema validation; environment override support | Invalid configs fail fast; config reflected via metrics/log entry; Quick Start uses file | A4 |
| ScopedValues Adoption | Replace ThreadLocal usage; execution context propagated across virtual threads | Benchmark shows <1% perf delta vs prior; no ThreadLocal in hot path | A11 |
| Graceful Shutdown | Coordinated signal handling; drains inflight; flushes metrics/traces; final summary log | SIGINT within 5s yields complete final metrics & trace flush | A14 |
| API Surface Audit | Review + prune public types; finalize package-level docs | API freeze doc approved; 100% JavaDoc for API module | A15, R3 |

## 5. Observability Deliverables
| Deliverable | Description | Acceptance Criteria | IDs |
|-------------|-------------|---------------------|-----|
| Tracing Integration | Spans around task execution (root: scenario, child: task exec); error events recorded | Trace sampled executions visible in local OTEL collector; span attributes: task identity, iteration, status | O1 |
| Log Correlation | JSON structured logs with `trace_id`, `span_id`, `run_id`, `task_id` | Log line fields validated; correlation from log to trace in UI | O2 |
| Run Metadata Tagging | Run ID generated at start; attached to metrics, traces, logs | Parallel runs show distinct run IDs; PromQL filtering works | O8 |

## 6. Performance Deliverables
| Deliverable | Description | Acceptance Criteria | IDs |
|-------------|-------------|---------------------|-----|
| Benchmark Suite | JMH micro (executor hot path, rate control) + macro scenario harness (10K TPS for 2 min) | Benchmarks runnable via `./gradlew benchmarks`; baseline numbers stored | P1 |
| Allocation Profiling | Identify & remove transient objects in hot path; report before/after | Allocation rate reduced by >=20% vs baseline; doc updated | P2 |
| Metrics Overhead Measurement | Quantify added latency per execution with instrumentation on/off | Overhead <0.5 ms at 50K concurrent virtual threads | P7 |
| Lock-Free Aggregation | Replace atomic counters with LongAdder/striped approach | Contention metrics (JFR) show improvement under high concurrency | P8 |
| Regression Gate | CI fails if perf deltas exceed threshold (e.g., >10% regression) | Gate triggered via benchmark comparison job | P12, R14 |

## 7. Release Process Deliverables
| Deliverable | Description | Acceptance Criteria | IDs |
|-------------|-------------|---------------------|-----|
| CI Pipeline | GitHub Actions workflow: build, test (Spock), benchmarks (smoke subset), security scan | All jobs green; PRs blocked on failure | R1, P12 |
| JavaDoc Coverage | 100% public API JavaDoc enforced via reporting plugin | Build fails if coverage <100% | R3 |
| Versioning Strategy | Document semantic versioning policy; 1.0 freeze date; pre-release tags rules | VERSIONING.md added; referenced in README | R4 |
| Security Review | Dependency vulnerability scan + SBOM generation | No critical CVEs; SBOM artifact attached to release | R6 |
| License & Headers | Confirm license (Apache 2.0) + verify headers policy (none or minimal) | LICENSE file present; headers policy documented | R8 |
| Quick Start Guide | Minimal scenario run in <2 minutes: config file + command | `QUICK_START.md` validated by new-user script | R12 |
| Quality Gates | Enforce: zero warnings, test coverage threshold, perf gate, JavaDoc coverage | CI badge reflects gating state | R14 |

## 8. Phase Breakdown & Timeline (Indicative)
| Phase | Duration | Focus | Key Outputs |
|-------|----------|-------|-------------|
| P1: Foundations | Week 1-2 | Lifecycle, config system, API audit start | TaskLifecycle API, config loader draft |
| P2: Observability Core | Week 3 | Tracing + run ID + structured logging | Spans visible, logs correlated |
| P3: Performance Baseline | Week 4 | Benchmarks, allocation profiling, lock-free adjustments | Baseline benchmarks recorded |
| P4: Distributed Minimal | Week 5 | Controller/worker minimal coordination | Multi-worker test passes |
| P5: Shutdown & Reliability | Week 6 | Graceful shutdown + final metrics/traces flush | Signal handling validated |
| P6: Gates & CI Hardening | Week 7 | CI workflows, perf regression gate, JavaDoc enforcement | CI passing with gates active |
| P7: Final Audit & Freeze | Week 8 | API freeze, versioning doc, quick start, security review | VERSIONING.md, QUICK_START.md, release candidate |

## 9. Dependency & Sequencing Notes
- Tracing depends on lifecycle instrumentation (Phase 1). 
- Performance gates depend on benchmarks (Phase 3) before activation in Phase 6.
- Distributed coordination baseline should come after lifecycle & config to avoid churn.
- API freeze precedes release artifacts creation and final security review.

## 10. Acceptance Metrics Summary
| Metric | Target |
|--------|--------|
| Rate Drift @10K TPS | <5% over 2 min run |
| Metrics Overhead | <0.5 ms per execution |
| Allocation Reduction | >=20% vs initial baseline |
| Graceful Shutdown Flush Completion | <5s |
| JavaDoc Coverage | 100% public API |
| Compiler Warnings | 0 |
| Security CVEs (Critical/High) | 0 unresolved |

## 11. Risk & Mitigation
| Risk | Impact | Mitigation |
|------|--------|-----------|
| Distributed coordination complexity | Delays 1.0 | Keep minimal: basic registry & broadcast only |
| Benchmark instability (variance) | False regressions | Use warm-up iterations & median-of-N runs |
| Trace overhead | Performance regression | Sampling (e.g., 5%) configurable; disable option documented |
| Graceful shutdown race | Data loss | Structured testing with chaos signals; finalize ordering guarantees |

## 12. Out-of-Scope (Deferrals)
List retained in gap analysis; explicitly not revisited until post-1.0 roadmap initiation.

## 13. Implementation Tracking Conventions
- Each deliverable -> GitHub issue with labels: `P0`, domain label (architecture/performance/observability/release).
- Milestone per phase (`1.0-P1` ... `1.0-P7`).
- Weekly progress update appended to `IMPLEMENTATION_UPDATES.md` referencing issue IDs.

## 14. Verification Playbook (Pre-Release Checklist)
1. Run macro benchmark scenario (10K TPS constant) and record drift & overhead.
2. Trigger controlled shutdown mid-load; verify final snapshot completeness.
3. Execute multi-worker run (3 workers) with distinct tasks; inspect unified metrics and traces.
4. Check CI gating: warnings=0, coverage >= threshold, perf gate green.
5. Review Quick Start from clean clone by fresh environment user.
6. Validate SBOM + dependency check report (no critical/high CVEs).
7. Confirm API freeze document unchanged for 7 days before tag.

## 15. Release Tagging Procedure
1. Create `v1.0.0-rc1` after passing verification playbook.
2. Run extended 24h soak (optional) measuring stability; record metrics.
3. If stable (no regressions), tag `v1.0.0` and publish artifacts + release notes.

## 16. Post-1.0 Kickoff (First Candidates)
Prepare roadmap issues for: Plugin SPI, advanced rate controllers, exemplars, adaptive warm-up, persistence layer, signed builds.

---
Prepared by: GitHub Copilot
