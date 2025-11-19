# Examples vs Test Samples - Analysis

## Current Structure

### `examples/` Directory
**Purpose**: User-facing, educational examples  
**Audience**: End users learning how to use VajraPulse  
**Content**:
- Real-world HTTP load test example
- Complete observability stack (Docker, Grafana, OpenTelemetry)
- Production-ready configuration
- Comprehensive documentation (README, QUICKSTART, OTEL guides)
- Multiple usage patterns (CLI, programmatic, OTel export)

**Example**: `examples/http-load-test/`
- Shows how to test an HTTP API
- Includes Docker Compose for full observability stack
- Demonstrates OpenTelemetry integration
- User can copy and adapt for their own use case

### `internal-tests/` Directory (formerly `test-samples/`)
**Purpose**: Internal testing infrastructure  
**Audience**: Testing agent, framework developers  
**Content**:
- Minimal test projects for automated testing
- Simple tasks (success-only, mixed results, all patterns)
- Used by automated test framework (`test-framework.sh`)
- Focused on framework validation, not user education

**Examples**:
- `simple-success/` - Basic success-only task
- `mixed-results/` - Task with success/failure
- `all-patterns/` - Tests all load patterns sequentially

## Key Differences

| Aspect | `examples/` | `internal-tests/` |
|--------|-------------|-----------------|
| **Purpose** | User education | Framework testing |
| **Audience** | End users | Testing agent / developers |
| **Documentation** | Comprehensive | Minimal |
| **Complexity** | Production-ready | Minimal/simple |
| **Observability** | Full stack (Docker, Grafana) | Console only |
| **Real-world** | Yes (HTTP API) | No (simulated) |
| **Copy-paste ready** | Yes | No |

## Recommendation: **Keep Both**

### Why Keep Both?

1. **Different Audiences**
   - `examples/` serves end users who want to learn
   - `internal-tests/` serves automated testing infrastructure

2. **Different Purposes**
   - `examples/` = "How do I use VajraPulse?"
   - `internal-tests/` = "Does the framework work correctly?"

3. **Different Complexity**
   - `examples/` = Real-world scenarios with full setup
   - `internal-tests/` = Minimal, focused test cases

### Suggested Improvements

1. **Clarify in Documentation**
   - Add note in `internal-tests/README.md` that it's for internal testing
   - Add note in `examples/README.md` that it's for users
   - Update main README to distinguish between them

2. **Consider Renaming** (Optional)
   - ✅ **Renamed**: `test-samples/` → `internal-tests/` (completed)
   - Makes it clearer it's not for end users
   - But this requires updating all references

3. **Add Clear Separation**
   - Keep `examples/` in root (user-facing)
   - ✅ **Renamed**: `test-samples/` → `internal-tests/` (completed)
   - Or add a `.gitignore` pattern if test-samples shouldn't be in repo

## Current Usage

### `examples/http-load-test/`
- Referenced in main README
- Used by users to learn VajraPulse
- Production-quality example

### `internal-tests/`
- Used by automated testing agent
- Referenced in `documents/TESTING_AGENT_*.md`
- Used by `test-framework.sh` script
- Part of CI/CD testing infrastructure

## Conclusion

**Both directories serve distinct purposes and should be kept:**

- ✅ **Keep `examples/`** - Essential for user onboarding
- ✅ **Keep `internal-tests/`** - Essential for automated testing

**Action Items:**
1. Add clarifying documentation to distinguish them
2. ✅ **Completed**: Renamed `test-samples/` → `internal-tests/` to make purpose clearer
3. Update README to explain the difference

