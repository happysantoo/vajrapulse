# Post-1.0 backlog

Items **explicitly deferred** from the 1.0.0 single-process GA scope. See also [CHANGELOG.md](../../CHANGELOG.md) (Unreleased / future versions).

## 1.1.0 (planned)

- **Distributed execution** — multi-worker coordination, orchestration, and related APIs (see long-term vision in [`documents/architecture/DESIGN.md`](../architecture/DESIGN.md)).
- **Remove deprecated `Task`** — migrate users to `TaskLifecycle` only; breaking change appropriate for minor under current deprecation policy.
- **Reporting enhancements** — time-series views, richer error detail (see CHANGELOG Unreleased).

## 1.0.1 or patch

- **Docker Hub image** — container distribution for the worker (CHANGELOG Unreleased).
- **Hotfixes** — as needed without API changes.

## Maintenance

- Keep **performance baselines** updated when changing hot paths (`./gradlew :benchmarks:jmh`).
- Re-run **`./gradlew check --rerun-tasks`** before each release.

**Last updated**: 2026-04-04
