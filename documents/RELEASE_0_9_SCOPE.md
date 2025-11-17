# VajraPulse 0.9 Scope & Release Plan

Status: Pre-1.0 (breaking changes allowed)
Target Version: 0.9.0
Branch: `phase1-opentelemetry-exporter`

## Included Features (Completed)
- Task lifecycle API (`TaskLifecycle`) with adapter for legacy `Task`.
- Graceful shutdown manager (drain + force timeouts, hooks).
- YAML/JSON configuration loader with env overrides (`ConfigLoader`, `VajraPulseConfig`).
- Execution engine with virtual/platform/AUTO thread strategy and annotation support.
- Metrics collection via Micrometer (success/failure timers + percentiles, counters, success rate gauge).
- Console metrics exporter.
- OpenTelemetry exporter (OTLP HTTP/gRPC, run_id propagation, resource & metric attributes, percentiles).
- Advanced load patterns: `StepLoad`, `SineWaveLoad`, `SpikeLoad` + existing `StaticLoad`, `RampUpLoad`, `RampUpToMaxLoad`.
- CLI enhancements: pattern selection, run-id override, config file loading.
- Pattern documentation (`LOAD_PATTERNS.md`).
- Example HTTP load test runner updated for all patterns + OTLP variant.
- Grafana dashboard JSON (`grafana-dashboard-runid-simple.json`).
- Performance harness (`PerformanceHarness`) + throughput test specs.
- High test coverage (≥90%) for core/api/exporter modules.

## Deferred (Post-0.9)
- Distributed execution (multi-worker coordination, scheduling plane).
- Additional exporters (JSON, CSV, Prometheus scrape endpoint).
- Adaptive / replay-based load patterns.
- Rich performance benchmarking suite & regression baselines.
- Blazemeter integration

## Quality Gates for Release
| Item | Requirement |
|------|-------------|
| Tests | All pass; no flaky specs |
| Coverage | ≥90% line coverage (Jacoco rules) |
| JavaDoc | Public APIs documented (api module) |
| Lint | No deprecation warnings (-Xlint:deprecation clean) |
| Build | Deterministic reproducible build (no dynamic network during compile) |
| Threading | No unbounded blocking/synchronized in virtual threads hot path |
| Dependencies | Minimal set (no heavy frameworks) |

## Maven Central Compliance Checklist
 Group ID: `com.vajrapulse`
## Release Steps
1. Bump version to `0.9.0` (remove `-SNAPSHOT`).
2. Ensure GPG key material available:
implementation("com.vajrapulse:vajrapulse-api:0.9.0")
   - Set `SIGNING_PASSWORD` env or Gradle property.
implementation("com.vajrapulse:vajrapulse-core:0.9.0")
   - `OSSRH_USERNAME`, `OSSRH_PASSWORD` env vars or gradle.properties.
implementation("com.vajrapulse:vajrapulse-exporter-console:0.9.0")
   ```bash
implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry:0.9.0")
   ```
runtimeOnly("com.vajrapulse:vajrapulse-worker:0.9.0")
   ```bash
   ./gradlew publish
   # Use Sonatype UI to close & release if auto not configured.
   ```
6. Tag commit:
   ```bash
   git tag -a v0.9.0 -m "VajraPulse 0.9.0"
   git push origin v0.9.0
   ```
7. JitPack availability: confirm build via `https://jitpack.io/#happysantoo/vajrapulse/v0.9.0`.
8. Publish release notes (this file summarizing scope) in GitHub Release.
9. Verify artifact sync to Maven Central (search by group and version).
10. Update README dependency snippets.

## Gradle Dependency Snippets (Post-Release)
```kotlin
// Minimal API
implementation("com.vajrapulse:vajrapulse-api:0.9.0")
// Core engine
implementation("com.vajrapulse:vajrapulse-core:0.9.0")
// Console exporter
implementation("com.vajrapulse:vajrapulse-exporter-console:0.9.0")
// OpenTelemetry exporter
implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry:0.9.0")
// Worker CLI (optional runtime usage)
runtimeOnly("com.vajrapulse:vajrapulse-worker:0.9.0")
```

## JitPack Coordinates (Alternative)
```kotlin
implementation("com.github.happysantoo.vajrapulse:vajrapulse-core:v0.9.0")
```

## Risk / Mitigation Summary
| Risk | Mitigation |
|------|------------|
| Missing signing keys | Document env var setup early |
| Coverage drop from new code | Enforce Jacoco on `check` task |
| Inadvertent heavy dependency | Manual review before release tag |
| Threading regression | Performance harness quick sanity run |

## Post-Release Actions
- Gather feedback on new patterns usage.
- Begin distributed execution design doc.
- Implement Prometheus scrape exporter.
- Establish latency SLO regression baseline.

---
Prepared for 0.9.0 release. Update any placeholder credentials locally before executing publish.
