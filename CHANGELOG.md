# Changelog

All notable changes to this project will be documented in this file.

The format roughly follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) and uses semantic pre-1.0 versioning (breaking changes allowed).

## [Unreleased]
### Planned
- Distributed execution layer (multi-worker coordination)
- Health & metrics endpoints for Kubernetes deployments
- Additional examples (database, gRPC, Kafka, multi-endpoint REST)
- Configuration system enhancements (schema validation, inheritance)
- GraalVM native image validation
- Scenario scripting DSL

## [0.9.8] - 2025-12-05
### Added
- **AdaptiveLoadPattern Recovery Enhancements**: Automatic recovery from low TPS
  - **RECOVERY → RAMP_UP Transition**: Pattern automatically recovers when conditions improve
  - **Last Known Good TPS Tracking**: Tracks highest TPS achieved before entering RECOVERY
  - **Recovery TPS Calculation**: Recovery starts at 50% of last known good TPS (or minimum TPS)
  - Pattern never gets stuck at minimum TPS - continuously adapts to changing conditions
- **Recent Window Failure Rate**: Time-windowed failure rate for recovery decisions
  - `MetricsProvider.getRecentFailureRate(int windowSeconds)` method added
  - Default implementation returns all-time rate (backward compatible)
  - `MetricsProviderAdapter` implements time-windowed calculation (last 10 seconds)
  - Recovery decisions use recent window instead of all-time average
  - Allows recovery even when historical failures keep all-time rate elevated
- **Intermediate Stability Detection in RAMP_DOWN**: Enhanced stability detection
  - Pattern can detect and sustain at optimal TPS levels during ramp-down
  - `handleRampDown()` now checks for intermediate stability
  - Pattern finds optimal TPS at any level, not just MAX_TPS

### Changed
- **AdaptiveLoadPattern**: Enhanced recovery and stability detection
  - `AdaptiveState` record now includes `lastKnownGoodTps` field
  - `checkAndAdjust()` checks recovery conditions in RECOVERY phase
  - Recovery uses recent window failure rate (10 seconds) for decisions
  - State tracking maintains `lastKnownGoodTps` when entering RAMP_DOWN/RECOVERY
  - `handleRampDown()` checks for intermediate stability before continuing ramp-down
- **MetricsProvider**: Enhanced with recent window support
  - Added `getRecentFailureRate(int windowSeconds)` method
  - Default implementation returns all-time rate (backward compatible)
  - Providers can override for time-windowed calculation
- **MetricsProviderAdapter**: Enhanced with time-windowed calculation
  - Implements `getRecentFailureRate()` with time-windowed tracking
  - Tracks previous snapshot for difference calculation
  - Falls back to all-time rate when window exceeds available history

### Fixed
- Fixed RECOVERY phase to properly transition back to RAMP_UP when conditions improve
- Fixed recovery TPS calculation to use last known good TPS instead of initial TPS
- Fixed stability detection to work during RAMP_DOWN phase (not just RAMP_UP)

### Migration Guide

**No migration required!** All changes are backward compatible enhancements.

**Optional Enhancements**:
- Implement `getRecentFailureRate()` in custom `MetricsProvider` implementations for better recovery behavior
- New recovery and stability features work automatically with existing code

## [0.9.7] - 2025-01-XX
### Added
- **AdaptiveLoadPattern Enhancements**: Continuous operation with recovery and stability detection
  - **RECOVERY Phase**: Replaced terminal COMPLETE phase with RECOVERY phase that can transition back to RAMP_UP
  - **Intermediate Stability Detection**: Pattern can now detect and sustain at optimal TPS levels (not just MAX_TPS)
  - **Minimum TPS Configuration**: Configurable minimum TPS to prevent pattern from going to zero
  - Pattern now operates continuously without terminal states
  - Enhanced phase transitions: RAMP_UP → SUSTAIN (at intermediate TPS) → RAMP_UP → SUSTAIN (at higher TPS)
  - Recovery from low TPS when system conditions improve
- **Client-Side Metrics**: Comprehensive client-side metrics tracking
  - `ClientMetrics` record for connection pool, queue, and error metrics
  - Connection pool metrics: active, idle, waiting connections, utilization
  - Client queue metrics: depth, average wait time
  - Client-side errors: connection timeouts, request timeouts, connection refused
  - Integrated into `MetricsCollector` and `AggregatedMetrics`
  - Console exporter displays client metrics
  - Micrometer gauges and counters for all client metrics
- **Warm-up/Cool-down Phases**: Built-in warm-up and cool-down support for load patterns
  - `WarmupCooldownLoadPattern` wrapper that adds warm-up and cool-down phases to any load pattern
  - Warm-up phase: Gradually ramps from 0 to initial TPS (metrics not recorded)
  - Steady-state phase: Executes base pattern at full TPS (metrics recorded)
  - Cool-down phase: Gradually ramps from final TPS to 0 (metrics not recorded)
  - Phase detection API: `getCurrentPhase()`, `shouldRecordMetrics()`
  - Factory methods: `withWarmup()`, `withCooldown()`
  - `ExecutionEngine` automatically skips metrics during warm-up/cool-down
  - Works with all existing load patterns
- **Assertion Framework**: Built-in assertion framework for test validation
  - `Assertion` interface for evaluating metrics against criteria
  - `AssertionResult` record for success/failure results with messages
  - `Assertions` factory with built-in validators:
    - Latency assertions (percentile-based, e.g., P95 < 100ms)
    - Error rate assertions (e.g., error rate < 1%)
    - Success rate assertions (e.g., success rate > 99%)
    - Throughput assertions (e.g., TPS > 1000)
    - Execution count assertions (e.g., total executions > 10000)
    - Composite assertions: `all()` (all must pass), `any()` (at least one must pass)
  - `Metrics` interface for module boundary compliance (API module has zero dependencies)
  - Zero dependencies, lightweight, tailored for load testing use cases

### Changed
- **AdaptiveLoadPattern**: Enhanced with continuous operation capabilities
  - Phase enum: `COMPLETE` → `RECOVERY` (breaking change, but pre-1.0)
  - `handleRecovery()` method for recovery phase transitions
  - `isStableAtCurrentTps()` method for intermediate stability detection
  - `minimumTps` parameter added to constructors (defaults to 0.0 for backward compatibility)
  - `handleRampUp()` and `handleSustain()` updated to detect intermediate stability
  - `handleRampDown()` transitions to RECOVERY at minimum TPS instead of stopping
- **AggregatedMetrics**: Enhanced with client metrics
  - Added `clientMetrics` parameter (defaults to empty `ClientMetrics` for backward compatibility)
  - Implements `Metrics` interface for assertion framework compatibility
- **MetricsCollector**: Enhanced with client metrics tracking
  - `recordClientMetrics()` method for updating client metrics
  - `recordConnectionTimeout()`, `recordRequestTimeout()`, `recordConnectionRefused()` methods
  - `recordClientQueueWait()` method for queue wait time tracking
  - Micrometer gauges and counters for all client metrics
- **ExecutionEngine**: Enhanced with warm-up/cool-down support
  - Detects `WarmupCooldownLoadPattern` instances
  - Skips metrics recording during warm-up and cool-down phases
  - Only records metrics during steady-state phase
- **ConsoleMetricsExporter**: Enhanced to display client metrics
  - Shows connection pool metrics (active, idle, waiting, utilization)
  - Shows client queue metrics (depth, average wait time)
  - Shows client-side errors (timeouts, connection refused)

### Fixed
- Fixed test failures in exporter modules after `ClientMetrics` addition
- Fixed `AdaptiveLoadPattern` phase transitions to properly handle RECOVERY phase
- Fixed `ClientMetrics.averageQueueWaitTimeMs()` calculation by adding `queueOperationCount` field
- Consolidated `AdaptiveLoadPattern` state management by moving volatile fields into `AdaptiveState` record

### Migration Guide

#### AdaptiveLoadPattern Phase Changes

**Breaking Change**: The `COMPLETE` phase has been replaced with `RECOVERY` phase.

**Before (0.9.6)**:
```java
if (pattern.getPhase() == Phase.COMPLETE) {
    // Handle completion - pattern is done
}
```

**After (0.9.7)**:
```java
if (pattern.getPhase() == Phase.RECOVERY) {
    // Handle recovery - pattern may continue if conditions improve
}

// To check if pattern is truly complete:
if (pattern.calculateTps(elapsedMillis) == 0.0) {
    // Pattern is complete
}
```

**Key Differences**:
- `COMPLETE` was a terminal state - pattern would stop
- `RECOVERY` is a non-terminal state - pattern can transition back to `RAMP_UP` when conditions improve
- Pattern now operates continuously without terminal states
- Use `calculateTps()` returning 0.0 to detect completion

#### ClientMetrics Changes

**New Field**: `queueOperationCount` has been added to properly calculate average queue wait time.

**Before (0.9.6)**:
```java
ClientMetrics metrics = new ClientMetrics(
    activeConnections, idleConnections, waitingConnections,
    queueDepth, queueWaitTimeNanos,
    connectionTimeouts, requestTimeouts, connectionRefused
);
```

**After (0.9.7)**:
```java
ClientMetrics metrics = new ClientMetrics(
    activeConnections, idleConnections, waitingConnections,
    queueDepth, queueWaitTimeNanos, queueOperationCount,  // Added queueOperationCount
    connectionTimeouts, requestTimeouts, connectionRefused
);
```

**Note**: The default constructor `new ClientMetrics()` still works and initializes all fields to zero.

## [0.9.6] - 2025-01-XX
### Added
- **Backpressure Support**: Comprehensive backpressure handling for adaptive load patterns
  - `BackpressureProvider` interface for reporting system backpressure (0.0-1.0 scale)
  - `BackpressureHandler` interface with multiple strategies (DROP, REJECT, RETRY, DEGRADE, THRESHOLD)
  - `BackpressureHandlers` factory with built-in strategies
  - `QueueBackpressureProvider` for queue depth-based backpressure
  - `CompositeBackpressureProvider` for combining multiple backpressure signals
  - Integration with `AdaptiveLoadPattern` to incorporate backpressure in ramp decisions
  - Integration with `ExecutionEngine` to handle requests under backpressure
  - Metrics for dropped and rejected requests (`vajrapulse.execution.backpressure.dropped`, `vajrapulse.execution.backpressure.rejected`)
  - HikariCP backpressure example (in examples, not committed to core)
- **MetricsPipeline.getMetricsProvider()**: Direct access to MetricsProvider from pipeline
  - Added `getMetricsProvider()` method to `MetricsPipeline` for seamless AdaptiveLoadPattern integration
  - Returns `MetricsProviderAdapter` wrapping the pipeline's `MetricsCollector`
  - Eliminates need for manual `MetricsProviderAdapter` creation
  - Enables clean API usage: `pipeline.getMetricsProvider()` instead of manual collector/provider setup
  - Comprehensive test coverage added
- **Adaptive Load Pattern Fixes**: Fixed hanging issue and improved reliability
  - Fixed issue where `AdaptiveLoadPattern` would hang after one iteration
  - Improved loop termination logic in `ExecutionEngine` to handle patterns starting at 0.0 TPS
  - Added comprehensive test coverage (unit, integration, E2E, diagnostic tests)
  - Enhanced example demonstrating full adaptive cycle with backpressure simulation
- **Test Infrastructure Improvements**: Migrated to Awaitility 4.3.0
  - Replaced `Thread.sleep()` with Awaitility for state-waiting scenarios
  - Improved test reliability and performance (10-30% faster execution)
  - Better error messages and early failure detection
  - ~15 state-waiting usages migrated to Awaitility
  - Kept `Thread.sleep()` for intentional work simulation (appropriate use case)

### Changed
- **AdaptiveLoadPattern**: Enhanced with backpressure support
  - Constructor now accepts optional `BackpressureProvider`
  - `checkAndAdjust()` logic incorporates backpressure level in ramp decisions
  - Ramp down when backpressure ≥ 0.7, ramp up when backpressure < 0.3
  - Added `getBackpressureLevel()` method
- **ExecutionEngine**: Added backpressure handling
  - `Builder` now accepts `BackpressureHandler` and `backpressureThreshold`
  - `run()` method checks backpressure before submitting tasks
  - Handles `DROPPED`, `REJECTED`, `RETRY`, `DEGRADE` results from handler
  - Records metrics for dropped and rejected requests
- **MetricsCollector**: Added backpressure metrics
  - `recordDroppedRequest()` method
  - `recordRejectedRequest()` method
  - New counters: `vajrapulse.execution.backpressure.dropped`, `vajrapulse.execution.backpressure.rejected`

### Fixed
- Fixed `AdaptiveLoadPattern` hanging issue where pattern would stop after one iteration
- Fixed loop termination in `ExecutionEngine` to correctly handle patterns starting at 0.0 TPS
- Improved test reliability by migrating from `Thread.sleep()` to Awaitility

## [0.9.5] - 2025-01-XX
### Added
- **Adaptive Load Pattern**: Feedback-driven load pattern that dynamically adjusts TPS based on error rates
  - Automatically ramps up TPS when error rates are low
  - Ramps down TPS when error rates exceed threshold
  - Supports configurable ramp increments/decrements, intervals, max TPS, and sustain duration
  - Integrated with metrics system for real-time error rate monitoring
- **Metrics Caching**: Performance optimization for high-frequency metrics queries
  - `MetricsProviderAdapter` now includes built-in caching with configurable TTL (default 100ms)
  - Reduces overhead from frequent `snapshot()` calls in adaptive load patterns
  - Thread-safe double-check locking pattern for optimal performance
- **Engine Metrics Registrar**: Centralized metrics registration utility
  - `EngineMetricsRegistrar` class for organizing engine-related metrics registration
  - Separates metrics registration logic from execution engine
  - Improves code organization and maintainability
- **Load Pattern Factory**: Centralized load pattern creation utility
  - `LoadPatternFactory` class for creating all load pattern types from configuration
  - Reusable across CLI and programmatic usage
  - Simplifies load pattern instantiation logic

### Changed
- **MetricsProviderAdapter**: Simplified architecture by integrating caching directly
  - Removed unnecessary `SimpleMetricsProvider` layer
  - Caching now built directly into the adapter
  - Improved performance and reduced complexity
- **ExecutionEngine**: Refactored metrics registration
  - Metrics registration logic extracted to `EngineMetricsRegistrar`
  - Cleaner separation of concerns
  - Reduced method count and improved maintainability
- **VajraPulseWorker**: Simplified load pattern creation
  - Load pattern creation logic extracted to `LoadPatternFactory`
  - Reduced complexity in main worker class
  - Improved code reusability

### Fixed
- **MetricsCollector**: Fixed percentile calculation test to handle Micrometer's histogram behavior
  - Test now properly handles cases where percentiles may be 0.0 or NaN
  - Improved test reliability for percentile map population

### Internal / Technical
- Architecture simplification and refactoring
  - Extracted metrics registration to dedicated registrar classes
  - Extracted load pattern creation to factory class
  - Improved code organization and maintainability
  - Reduced complexity in core classes
- Enhanced test coverage for new components
  - Added comprehensive tests for `EngineMetricsRegistrar`
  - Improved test coverage for metrics caching
  - All modules maintain ≥90% code coverage

### Notes
- Adaptive load pattern enables intelligent load testing that responds to system behavior
- Metrics caching significantly improves performance for high-frequency metrics queries
- Architecture simplifications improve maintainability and code organization
- This release focuses on adaptive load patterns and code quality improvements

## [0.9.4] - 2025-01-XX
### Added
- **Report Exporters Module**: New `vajrapulse-exporter-report` module with multiple report formats
  - `HtmlReportExporter` - Beautiful HTML reports with interactive charts using Chart.js
  - `JsonReportExporter` - JSON format for programmatic analysis and CI/CD integration
  - `CsvReportExporter` - CSV format for spreadsheet analysis (Excel, LibreOffice, Google Sheets)
  - All exporters support file-based report generation with automatic directory creation
  - Reports include summary tables, percentile graphs, and run metadata
  - Report module included in BOM for version consistency
- **Document Organization**: Comprehensive document reorganization strategy
  - Documents organized into logical folders: `releases/`, `roadmap/`, `architecture/`, `integrations/`, `guides/`, `analysis/`, `resources/`, `archive/`
  - Clear naming conventions and classification rules
  - Improved discoverability and maintainability
  - `DOCUMENT_ORGANIZATION_STRATEGY.md` guide created
- **Comparison Guide**: Comprehensive comparison document (`COMPARISON.md`) with JMeter, Gatling, and BlazeMeter
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
  - Updated all documentation references to reflect new paths
- **HTTP Load Test Example**: Enhanced example to demonstrate report exporters
  - Added HTML, JSON, and CSV report generation
  - Updated documentation paths to reflect new folder structure
  - Report exporters integrated into example pipeline
- **Cursor IDE Rules**: Enhanced `.cursorrules` with document organization requirements
  - Mandatory document classification rules
  - Clear folder structure guidelines
  - Naming convention standards

### Fixed
- **RateController**: Fixed potential issue where rate controller could sleep past test duration
  - Sleep time now capped to prevent sleeping beyond test duration
  - Maximum sleep capped at 1 second to allow loop condition re-check
  - Prevents potential timing issues in long-running tests

### Internal / Technical
- Report exporters use Jackson for JSON serialization
- HTML reports use Chart.js for interactive visualizations
- CSV reports use simple comma-separated format for maximum compatibility
- All report exporters implement `MetricsExporter` interface for consistent integration
- Example dependencies updated to include report module

### Notes
- Report exporters enable professional test result sharing and analysis
- HTML reports provide visual insights with interactive charts
- JSON reports enable CI/CD integration and automated analysis
- CSV reports enable spreadsheet-based analysis and reporting
- Document organization improves project maintainability and discoverability
- **Note**: Several planned features (health endpoints, client-side metrics, additional examples, configuration enhancements) were deferred to future releases to focus on report exporters and infrastructure improvements

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
