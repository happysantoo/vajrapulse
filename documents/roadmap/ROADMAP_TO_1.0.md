# Roadmap: 1.0 and beyond

## Current position (2026)

**Released line**: **1.0.0** — single-process load runner with stable public API under SemVer (see [`CHANGELOG.md`](../../CHANGELOG.md), [`documents/guides/VERSIONING.md`](../guides/VERSIONING.md)).

**What 1.0.0 includes**

- `vajrapulse-api`, `vajrapulse-core`, `vajrapulse-worker`
- Exporters: console, OpenTelemetry (`vajrapulse-exporter-opentelemetry`), report (`vajrapulse-exporter-report`)
- JMH benchmarks, CI quality gates, graceful shutdown, `TaskLifecycle` as the primary task API

**What is not in 1.0.0**

- Distributed multi-worker orchestration (planned **1.1.0**; see [`documents/architecture/DESIGN.md`](../architecture/DESIGN.md) for long-term vision)
- Removal of deprecated `Task` (planned **1.1.0**)

## Historical document

The previous iteration of this file described pre-1.0 tasks (0.9.x era). That content is superseded by the shipped tree and by [`documents/releases/RELEASE_1.0.0_CHECKLIST.md`](../releases/RELEASE_1.0.0_CHECKLIST.md).

## Next milestones

| Target | Focus |
|--------|--------|
| **1.0.x** | Maven Central publication process, patches, Docker image ([`POST_1.0_BACKLOG.md`](POST_1.0_BACKLOG.md)) |
| **1.1.0** | Distributed execution, remove `Task`, richer reporting ([`POST_1.0_BACKLOG.md`](POST_1.0_BACKLOG.md)) |

## Maintainer links

- Publish 1.0.0: [`documents/guides/RELEASE_1.0.0_PUBLICATION.md`](../guides/RELEASE_1.0.0_PUBLICATION.md)
- Performance baselines: [`documents/analysis/PERFORMANCE_BASELINE.md`](../analysis/PERFORMANCE_BASELINE.md)

**Last updated**: 2026-04-04
