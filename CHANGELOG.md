# Changelog

All notable changes to this project will be documented in this file.

The format roughly follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and uses semantic pre-1.0 versioning (breaking changes allowed).

## [Unreleased]
### Planned
- Distributed execution layer (multi-worker coordination)
- Health & metrics endpoints for Kubernetes deployments
- Enhanced client-side metrics (connection pool, timeouts, backlog)
- Additional examples (database, gRPC, Kafka, multi-endpoint REST)
- Configuration system enhancements (schema validation, inheritance)
- Adaptive / feedback-driven load patterns
- GraalVM native image validation
- Scenario scripting DSL

## [0.9.4] - 2025-01-XX
### Added
- **Report Exporters Module**: New `vajrapulse-exporter-report` module with multiple report formats
  - `HtmlReportExporter` - Beautiful HTML reports with interactive charts using Chart.js
  - `JsonReportExporter` - JSON format for programmatic analysis and CI/CD integration
  - `CsvReportExporter` - CSV format for spreadsheet analysis (Excel, LibreOffice, Google Sheets)
  - All exporters support file-based report generation with automatic directory creation
  - Reports include summary tables, percentile graphs, and run metadata
- **Document Organization**: Comprehensive document reorganization strategy
  - Documents organized into logical folders: `releases/`, `roadmap/`, `architecture/`, `integrations/`, `guides/`, `analysis/`, `resources/`, `archive/`
  - Clear naming conventions and classification rules
  - Improved discoverability and maintainability
- **Comparison Guide**: Comprehensive comparison document with JMeter, Gatling, and BlazeMeter
  - Architecture and concurrency model comparison
  - Performance benchmarks and resource usage analysis
  - Enterprise scalability considerations
  - Real-world use case scenarios

### Changed
- **Documentation Structure**: Reorganized all markdown files into proper `documents/` subfolders
  - Release documents moved to `documents/releases/`
  - Architecture documents moved to `documents/architecture/`
  - Integration guides moved to `documents/integrations/`
  - Roadmap documents moved to `documents/roadmap/`
  - Historical documents archived to `documents/archive/`
- **Cursor IDE Rules**: Enhanced `.cursorrules` with document organization requirements
  - Mandatory document classification rules
  - Clear folder structure guidelines
  - Naming convention standards

### Internal / Technical
- Report exporters use Jackson for JSON serialization
- HTML reports use Chart.js for interactive visualizations
- CSV reports use simple comma-separated format for maximum compatibility
- All report exporters implement `MetricsExporter` interface for consistent integration
- Report module included in BOM for version consistency

### Notes
- Report exporters enable professional test result sharing and analysis
- HTML reports provide visual insights with interactive charts
- JSON reports enable CI/CD integration and automated analysis
- CSV reports enable spreadsheet-based analysis and reporting
- Document organization improves project maintainability and discoverability

## [0.9.3] - 2025-01-XX
### Added
- **Queue Depth Tracking**: Client-side bottleneck detection with queue metrics
  - `vajrapulse.execution.queue.size` gauge metric for pending executions
  - `vajrapulse.execution.queue.wait_time` timer with percentiles (P50, P95, P99)
  - Queue metrics exposed in `AggregatedMetrics` and all exporters
  - Helps identify when tasks are waiting in queue vs executing
- **BOM (Bill of Materials) Module**: Centralized dependency version management
  - New `vajrapulse-bom` module for simplified dependency declarations
  - Usage: `implementation(platform("com.vajrapulse:vajrapulse-bom:0.9.3"))`
  - All VajraPulse modules included in BOM for version consistency
- **Request/Response TPS Gauges**: OpenTelemetry exporter enhancements
  - `vajrapulse.request.tps` gauge with `type=total|success|failure`
  - `vajrapulse.response.tps` gauge with `type=total|success|failure`
  - TPS accessors exposed: `lastResponseTps()`, `lastSuccessTps()`, `lastFailureTps()`
  - Updated Grafana dashboard with Request TPS stat and Response TPS graph panels
- **Console Metrics Enhancements**: Improved observability in console output
  - Explicit labeling of "Request TPS" and "Response TPS" in console output
  - Enhanced console metrics with elapsed time and TPS display
- **Static Code Analysis**: SpotBugs integration for code quality
  - SpotBugs static analysis configured for all modules
  - Automated bug detection in CI/CD pipeline
  - Exclusion filter for acceptable patterns (records, builders, etc.)
  - Bug fixer agent documentation for automated issue resolution
- **Release Process Improvements**: Enhanced release automation
  - Automated release script (`scripts/release.sh`) with validation
  - Comprehensive release process documentation
  - Improved bundle creation for Maven Central publishing

### Changed
- **OpenTelemetry Metrics Alignment**: Aligned with OpenTelemetry semantic conventions
  - Metrics now follow OTEL semantic conventions for better integration
  - Improved compatibility with OTEL collectors and backends
- **Build Configuration**: Streamlined static analysis tools
  - Removed PMD and Checkstyle (replaced with SpotBugs)
  - Focus on SpotBugs for comprehensive static analysis
  - Simplified build configuration while maintaining quality gates
- **JavaDoc Standards**: Enhanced documentation requirements
  - JavaDoc linting configured with `-Xdoclint:all,-missing`
  - All public APIs require complete JavaDoc documentation
  - Examples have relaxed rules, main modules enforce strict requirements
- **Documentation**: Comprehensive developer guides
  - Added `.cursorrules` for Cursor IDE with coding standards
  - Enhanced `.github/copilot-instructions.md` with strict JavaDoc requirements
  - Strategic roadmap and BlazeMeter integration planning documents
  - BOM usage examples in README

### Fixed
- **SpotBugs Violations**: Resolved all static analysis findings
  - Fixed legitimate code quality issues identified by SpotBugs
  - Added exclusions for acceptable patterns (record accessors, builder patterns)
  - All modules now pass SpotBugs analysis

### Internal / Technical
- Queue depth tracking uses atomic counters for thread-safe metrics
- Queue wait time measured from submission to execution start
- SpotBugs configured to fail build on findings (enforced code quality)
- Release script includes validation steps before publishing
- BOM module uses `java-platform` plugin (standard for Maven BOMs)

### Notes
- Queue depth tracking helps identify client-side bottlenecks when tasks queue up faster than they execute
- BOM module simplifies dependency management - recommended for all users
- SpotBugs replaces PMD/Checkstyle for a more comprehensive static analysis solution
- OpenTelemetry TPS gauges provide real-time throughput visibility in observability platforms

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

[Unreleased]: https://github.com/happysantoo/vajrapulse/compare/v0.9.4...HEAD
[0.9.4]: https://github.com/happysantoo/vajrapulse/compare/v0.9.3...v0.9.4
[0.9.3]: https://github.com/happysantoo/vajrapulse/compare/v0.9.2...v0.9.3
[0.9.0]: https://github.com/happysantoo/vajrapulse/tree/phase1-opentelemetry-exporter
