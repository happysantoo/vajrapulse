# Testing Agent Quick Reference

## Quick Commands

### Run Integration Tests
```bash
# All integration tests
./gradlew test --tests "*IntegrationSpec"

# Execution engine tests
./gradlew test --tests "ExecutionEngineLoadPatternIntegrationSpec"

# Exporter tests
./gradlew test --tests "ExporterIntegrationSpec"
```

### Run Sample Projects
```bash
cd test-samples

# All projects
./test-framework.sh run-all

# Specific project
./test-framework.sh run simple-success
./test-framework.sh run mixed-results
./test-framework.sh run all-patterns
```

## Test Coverage Matrix

### Load Patterns (6)
- StaticLoad
- RampUpLoad
- RampUpToMaxLoad
- StepLoad
- SineWaveLoad
- SpikeLoad

### Thread Strategies (3)
- VirtualThreads
- PlatformThreads
- Default

### Exporters (2)
- ConsoleMetricsExporter
- OpenTelemetryExporter

**Total Combinations**: 18 (patterns × strategies) + 12 (exporters × patterns)

## Test Locations

| Test Suite | Location |
|-----------|----------|
| Execution Engine + Load Patterns | `vajrapulse-core/.../ExecutionEngineLoadPatternIntegrationSpec.groovy` |
| Exporters | `vajrapulse-worker/.../ExporterIntegrationSpec.groovy` |
| Sample Projects | `test-samples/` |

## Validation Checklist

- [ ] Total executions > 0
- [ ] Success rate matches expected
- [ ] Response TPS is reasonable
- [ ] Queue size = 0 after completion
- [ ] All percentiles present
- [ ] Queue wait time recorded (when applicable)

## Integration Protocol

1. Feature Agent: "Feature X ready"
2. Testing Agent: Analyze → Test → Report
3. Feature Agent: Review results
4. If pass: Feature validated
5. If fail: Fix and retest

## Test Files

- Integration tests: `*IntegrationSpec.groovy`
- Sample projects: `test-samples/*/`
- Test framework: `test-samples/test-framework.sh`
- Documentation: `documents/TESTING_AGENT_*.md`

