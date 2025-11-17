# Changelog

All notable changes to this project will be documented in this file.

The format roughly follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and uses semantic pre-1.0 versioning (breaking changes allowed).

## [Unreleased]
### Planned
- Distributed execution layer (multi-worker coordination)
- Additional exporters: Prometheus, JSON, CSV
- Adaptive / feedback-driven load patterns
- GraalVM native image validation
- Scenario scripting DSL

## [0.9.0] - 2025-11-16 (Pre-release)
### Added
- Advanced load patterns: `StepLoad`, `SpikeLoad`, `SineWaveLoad` (in addition to `StaticLoad`, `RampUpLoad`, `RampUpToMaxLoad`).
- Auto-generated `run_id` tagging for all metrics & traces (CLI override `--run-id`).
- OpenTelemetry exporter (OTLP HTTP/gRPC) with resource & metric attributes, run_id propagation.
- YAML/JSON configuration loader with environment overrides (timeouts, thread strategy).
- Performance harness (`PerformanceHarness`) for throughput validation.
- Console metrics exporter with success/failure latency percentiles.
- Virtual vs platform thread annotations (`@VirtualThreads`, `@PlatformThreads`).
- ≥90% Jacoco coverage gating for api/core/exporter modules.
- Spock BDD tests expanding pattern + harness validation.
- SVG + PNG logo assets (PNG used in README).
- Documentation: pattern reference (`LOAD_PATTERNS.md`), release scope (`RELEASE_0_9_SCOPE.md`), Grafana dashboard JSON.

### Changed
- Artifact group ID migrated from `io.github.happysantoo.vajrapulse` to `com.vajrapulse` (pre-1.0 breaking coordinate change).
- README fully rewritten for concise consumption: highlights, installation, migration note.
- Consolidated publishing configuration (sources + javadoc jars, signing via env vars when present).
- Enforced explicit task dependencies for Jacoco verification (added compileJava/compileGroovy dependencies).

### Removed
- Legacy verbose README sections replaced with concise bullet style.
- Any implicit reliance on previous group coordinates.

### Internal / Technical
- Micrometer timers/counters for success/failure executions with percentile histograms.
- Structured shutdown via `ShutdownManager` (drain + force timeouts).
- Task lifecycle adapter bridging legacy task interface.
- Optimized thread usage defaults to virtual threads when annotation absent.

### Notes
- Pre-1.0: Expect further breaking changes until architecture stabilizes.
- Coordinate migration requires updating build scripts to `com.vajrapulse` before consuming 0.9.0 artifacts.
- Signing skipped automatically if `SIGNING_KEY` / `SIGNING_PASSWORD` not provided.

[Unreleased]: https://github.com/happysantoo/vajrapulse/compare/phase1-opentelemetry-exporter...HEAD
[0.9.0]: https://github.com/happysantoo/vajrapulse/tree/phase1-opentelemetry-exporter
