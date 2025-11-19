# VajraPulse Test Sample Projects

This directory contains sample test projects for comprehensive end-to-end testing of VajraPulse.

## Structure

```
test-samples/
├── simple-success/          # Basic success-only task
├── mixed-results/            # Task with success and failure
├── high-throughput/         # High TPS stress test
├── long-running/             # Extended duration test
└── all-patterns/            # Tests all load patterns
```

## Purpose

These sample projects are used by the automated test framework to:
1. Verify all load patterns work correctly
2. Test all exporters end-to-end
3. Validate metrics accuracy
4. Test different thread strategies
5. Stress test the framework

## Running Tests

Each sample project can be run independently:

```bash
cd test-samples/simple-success
./gradlew run
```

Or use the test framework to run all:

```bash
./test-framework.sh run-all
```

## Integration with Testing Agent

The testing agent automatically:
- Creates new sample projects when features are ready
- Executes test cases against all combinations
- Validates metrics correctness
- Reports test results

