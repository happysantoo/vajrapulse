# Testing Agent Integration Guide

## For Other Agents

This document explains how other agents can integrate with the Testing Agent to ensure features are properly tested.

## Integration Protocol

### 1. Feature Ready Notification

When a feature is ready for testing, notify the Testing Agent:

```
Feature: [Feature Name]
Type: [LoadPattern|Exporter|ExecutionEngine|Other]
Description: [Brief description]
Files Changed: [List of files]
```

### 2. Testing Agent Response

The Testing Agent will:
1. Analyze the feature scope
2. Identify test combinations needed
3. Create/update test cases
4. Run comprehensive test suite
5. Report results

### 3. Test Results Format

```
Test Results for [Feature Name]
================================
Status: [PASS|FAIL|PARTIAL]
Tests Run: [number]
Tests Passed: [number]
Tests Failed: [number]

Details:
- [Test name]: [PASS|FAIL] - [details]
- [Test name]: [PASS|FAIL] - [details]

Recommendations:
- [Recommendation 1]
- [Recommendation 2]
```

## Test Categories

### Load Pattern Features

**Required Tests**:
- All thread strategies (VirtualThreads, PlatformThreads, Default)
- With success-only tasks
- With mixed success/failure tasks
- Queue depth tracking
- TPS calculations
- Shutdown behavior

**Test Location**: `ExecutionEngineLoadPatternIntegrationSpec`

### Exporter Features

**Required Tests**:
- Basic export functionality
- Multiple exporters simultaneously
- Error handling
- Periodic reporting
- Format validation (Console)
- Network connectivity (OTLP)

**Test Location**: `ExporterIntegrationSpec`

### Execution Engine Features

**Required Tests**:
- All load patterns
- All thread strategies
- Metrics collection
- Shutdown behavior
- Queue tracking

**Test Location**: `ExecutionEngineLoadPatternIntegrationSpec`

## Automated Test Execution

### Trigger Tests

```bash
# Run all integration tests
./gradlew test --tests "*IntegrationSpec"

# Run specific test suite
./gradlew test --tests "ExecutionEngineLoadPatternIntegrationSpec"

# Run sample projects
cd test-samples
./test-framework.sh run-all
```

### Expected Duration

- Integration tests: ~5-10 minutes
- Sample projects: ~2-5 minutes
- Full test suite: ~10-15 minutes

## Test Validation Checklist

Before marking a feature as "tested", verify:

- [ ] All relevant test combinations run
- [ ] Tests pass consistently
- [ ] Metrics are accurate
- [ ] No regressions introduced
- [ ] Sample projects work
- [ ] Documentation updated

## Continuous Integration

Tests run automatically:
- On every commit (unit tests)
- On PR creation (full suite)
- On merge to main (full suite + sample projects)
- On release (full suite + extended tests)

## Reporting Issues

If tests fail:

1. **Check test logs** for specific failure
2. **Verify feature implementation** matches test expectations
3. **Update tests** if feature behavior changed intentionally
4. **Fix implementation** if feature doesn't match specification

## Best Practices

1. **Test Early**: Run tests during development, not just at the end
2. **Test Often**: Run tests after each significant change
3. **Test Comprehensively**: Cover all combinations, not just happy path
4. **Document Changes**: Update test documentation when adding features
5. **Maintain Tests**: Keep tests up-to-date with code changes

