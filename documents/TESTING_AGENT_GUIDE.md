# VajraPulse Testing Agent Guide

## Overview

The Testing Agent is responsible for comprehensive end-to-end testing of VajraPulse, ensuring all combinations of execution engines, load patterns, and exporters work correctly.

## Test Suite Structure

### 1. Integration Tests

#### Execution Engine + Load Pattern Tests
**Location**: `vajrapulse-core/src/test/groovy/com/vajrapulse/core/integration/ExecutionEngineLoadPatternIntegrationSpec.groovy`

**Coverage**:
- All load patterns (StaticLoad, RampUpLoad, RampUpToMaxLoad, StepLoad, SineWaveLoad, SpikeLoad)
- All thread strategies (VirtualThreads, PlatformThreads, Default)
- Queue depth tracking
- Mixed success/failure results
- Graceful shutdown
- TPS calculations

**Test Combinations**:
- 3 thread strategies × 6 load patterns = 18 base combinations
- Plus edge cases (mixed results, shutdown, queue tracking)

#### Exporter Integration Tests
**Location**: `vajrapulse-worker/src/test/groovy/com/vajrapulse/worker/integration/ExporterIntegrationSpec.groovy`

**Coverage**:
- Console exporter formatting
- Multiple exporters simultaneously
- Exporter error handling
- Periodic reporting
- Queue metrics export
- RunId propagation

### 2. Sample Test Projects

**Location**: `test-samples/`

#### simple-success
- Basic success-only task
- Tests all load patterns via command-line argument
- Verifies basic execution engine functionality

#### mixed-results
- Task with success and failure
- Verifies metrics collection for both outcomes
- Tests failure rate calculations

#### all-patterns
- Comprehensive test running all load patterns sequentially
- Verifies each pattern works correctly
- Useful for regression testing

### 3. Test Framework

**Location**: `test-samples/test-framework.sh`

**Commands**:
```bash
# Run all test projects
./test-framework.sh run-all

# Run specific project
./test-framework.sh run simple-success

# List available projects
./test-framework.sh list
```

## Test Matrix

### Load Patterns × Thread Strategies

| Load Pattern | VirtualThreads | PlatformThreads | Default |
|-------------|----------------|-----------------|---------|
| StaticLoad | ✅ | ✅ | ✅ |
| RampUpLoad | ✅ | ✅ | ✅ |
| RampUpToMaxLoad | ✅ | ✅ | ✅ |
| StepLoad | ✅ | ✅ | ✅ |
| SineWaveLoad | ✅ | ✅ | ✅ |
| SpikeLoad | ✅ | ✅ | ✅ |

### Exporters × Load Patterns

| Exporter | StaticLoad | RampUpLoad | RampUpToMaxLoad | StepLoad | SineWaveLoad | SpikeLoad |
|----------|------------|------------|-----------------|----------|--------------|-----------|
| Console | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| OpenTelemetry | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

## Integration with Other Agents

### When Features Are Ready

1. **Feature Agent** notifies Testing Agent:
   - New load pattern implementation
   - New exporter implementation
   - New execution engine feature

2. **Testing Agent** responds:
   - Creates new test cases for the feature
   - Adds to integration test suite
   - Creates sample project if needed
   - Runs comprehensive test matrix
   - Reports results

### Test Execution Workflow

```
1. Feature Agent: "New feature X is ready"
2. Testing Agent: 
   - Analyze feature scope
   - Identify test combinations needed
   - Create/update test cases
   - Run test suite
   - Validate results
   - Report to Feature Agent
3. Feature Agent: Review test results
4. If tests pass: Feature is validated
5. If tests fail: Return to Feature Agent for fixes
```

## Running Tests

### Unit/Integration Tests (Spock)

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests ExecutionEngineLoadPatternIntegrationSpec

# Run with coverage
./gradlew test jacocoTestReport
```

### Sample Projects

```bash
# Run all sample projects
cd test-samples
./test-framework.sh run-all

# Run specific project
./test-framework.sh run simple-success
```

### Continuous Integration

Tests should run:
- On every commit
- Before merging PRs
- On release branches
- After dependency updates

## Test Validation Criteria

### Execution Engine Tests

✅ **Must Verify**:
- All load patterns execute correctly
- Metrics are collected accurately
- Queue depth tracking works
- Shutdown is graceful
- Thread strategies work as expected
- TPS calculations are reasonable

### Exporter Tests

✅ **Must Verify**:
- Metrics are exported correctly
- Formatting is correct (Console)
- Multiple exporters work together
- Error handling doesn't break execution
- Periodic reporting works
- RunId propagation works

### Sample Project Tests

✅ **Must Verify**:
- Projects compile successfully
- Projects run without errors
- Metrics output is reasonable
- All load patterns work
- Exporters function correctly

## Metrics Validation

### Success Criteria

For each test run:
- **Total Executions**: > 0
- **Success Rate**: Matches expected (100% for success-only, ~80% for mixed)
- **Response TPS**: Within reasonable range of target TPS
- **Queue Size**: 0 after completion
- **Percentiles**: All configured percentiles present
- **Queue Wait Time**: Recorded when queue depth > 0

### Failure Indicators

❌ **Test Fails If**:
- No executions recorded
- Success rate doesn't match expected
- TPS is 0 or unreasonably high
- Queue size > 0 after completion
- Missing percentiles
- Exceptions thrown during execution

## Adding New Tests

### For New Load Pattern

1. Add to `ExecutionEngineLoadPatternIntegrationSpec`:
   ```groovy
   def "should execute NewLoadPattern pattern"() {
       // Test implementation
   }
   ```

2. Add to `AllPatternsTest` sample project

3. Update test matrix documentation

### For New Exporter

1. Add to `ExporterIntegrationSpec`:
   ```groovy
   def "should export to NewExporter correctly"() {
       // Test implementation
   }
   ```

2. Create sample project using new exporter

3. Update test matrix documentation

## Troubleshooting

### Common Issues

**Tests timeout**:
- Increase timeout in `@Timeout` annotation
- Check for deadlocks or infinite loops

**Metrics don't match expected**:
- Verify load pattern calculation
- Check thread pool configuration
- Validate timing assumptions

**Exporter tests fail**:
- Check exporter configuration
- Verify network connectivity (for OTLP)
- Check error handling logic

## Best Practices

1. **Test Isolation**: Each test should be independent
2. **Fast Execution**: Keep test duration reasonable (< 30s per test)
3. **Clear Assertions**: Use descriptive failure messages
4. **Coverage**: Test both success and failure paths
5. **Documentation**: Document test purpose and expected behavior

## Future Enhancements

- [ ] Performance benchmarks
- [ ] Stress tests (very high TPS)
- [ ] Long-running tests (hours)
- [ ] Distributed execution tests
- [ ] Memory leak detection
- [ ] Thread safety verification

