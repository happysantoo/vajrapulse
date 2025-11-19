# VajraPulse Internal Test Projects

> **⚠️ Internal Use Only**: This directory contains test projects for framework validation and automated testing.  
> **For user examples**, see the [`examples/`](../examples/) directory.

This directory contains minimal test projects used by the automated testing framework to validate VajraPulse functionality.

## Structure

```
internal-tests/
├── simple-success/          # Basic success-only task
├── mixed-results/            # Task with success and failure
├── high-throughput/         # High TPS stress test
├── long-running/             # Extended duration test
└── all-patterns/            # Tests all load patterns
```

## Purpose

These test projects are used by the automated test framework to:
1. Verify all load patterns work correctly
2. Test all exporters end-to-end
3. Validate metrics accuracy
4. Test different thread strategies
5. Stress test the framework

**Note**: These are minimal, focused test cases for framework validation.  
For real-world examples with full observability stacks, see [`examples/`](../examples/).

## Running Tests

Each test project can be run independently:

```bash
cd internal-tests/simple-success
./gradlew run
```

Or use the test framework to run all:

```bash
cd internal-tests
./test-framework.sh run-all
```

## Integration with Testing Agent

The testing agent automatically:
- Creates new test projects when features are ready
- Executes test cases against all combinations
- Validates metrics correctness
- Reports test results

