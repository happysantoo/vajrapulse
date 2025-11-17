# Coverage Status (Enforced)

Last Updated: January 16, 2025

## Policy
- Enforce minimum **90% line coverage** on critical modules: `:vajrapulse-api`, `:vajrapulse-core`, `:vajrapulse-exporter-console`.
- Worker module exempt (CLI application with 58% coverage - hard to test main entry point).
- Build fails if critical modules drop below threshold.

## Current Metrics

### vajrapulse-core (90%+ ✓)
- Lines: 342 covered / 36 missed (90.5%)
- Instructions: 1459 covered / 150 missed
- Branches: 94 covered / 42 missed
- Methods: 71 / 71 (100%)
- Classes: 10 / 10 (100%)

### vajrapulse-api (93%+ ✓)
- Lines: 106 covered / 8 missed (93.0%)
- Instructions: 535 covered / 48 missed
- Branches: 67 covered / 21 missed
- Methods: 30 / 30 (100%)
- Classes: 12 / 12 (100%)

### vajrapulse-exporter-console (85%+ ✓)
- Lines: 58 covered / 10 missed (85.3%)
- Instructions: 341 covered / 53 missed
- Branches: 11 covered / 7 missed
- Methods: 8 / 10 (80%)
- Classes: 1 / 1 (100%)

### vajrapulse-worker (58% - exempt)
- Lines: 75 covered / 55 missed (57.7%)
- Instructions: 335 covered / 331 missed
- Branches: 36 covered / 20 missed
- Methods: 17 / 21 (81%)
- Classes: 3 / 3 (100%)

## Rationale
- Core, API, and exporter modules are the foundation for correctness and observability.
- Worker is a thin CLI orchestration layer; deep integration tests would duplicate coverage without value.
- Test focus: unit tests for core logic, not end-to-end CLI workflows.

## Recent Improvements
1. **API Module**: Added tests for `TaskIdentity`, `Task` default methods, all `LoadPattern` getDuration() implementations.
2. **Worker Module**: Added `parseDuration` tests, builder tests for `MetricsPipeline`.
3. **Exporter Module**: Added tests for default constructor and failure percentile rendering.

## Next Steps
1. Monitor exporter console coverage - currently at 85%, can aim for 90% with additional edge case tests.
2. Add branch coverage targets (≥75%) once line coverage stable.
3. Expand tests for rare error paths in logging/tracing (e.g., initialization failures).
4. Integrate coverage gates into CI pipeline (`./gradlew check` enforces thresholds).

## Enforcement Details
Gradle `jacocoTestCoverageVerification` rule applies to:
- `:vajrapulse-core`
- `:vajrapulse-api`
- `:vajrapulse-exporter-console`

Worker module has no coverage enforcement; reporting enabled for visibility.

---
Pre-1.0: Coverage targets will increase aggressively; no backward compatibility constraints.

