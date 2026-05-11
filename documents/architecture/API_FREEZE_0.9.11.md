# VajraPulse API Freeze Document - Version 0.9.11

**Date**: 2025-12-26  
**Version**: 0.9.11  
**Status**: Pre-Freeze Review  
**Target Freeze Date**: Before 1.0.0 Release

---

## Executive Summary

This document provides a comprehensive inventory of all public APIs in the `vajrapulse-api` module, categorizes them by stability level, and defines the breaking change policy for the 1.0.0 release and beyond.

**Key Principles**:
- **Zero Dependencies**: The `vajrapulse-api` module maintains zero external dependencies
- **Stability Guarantees**: APIs are categorized as Stable, Experimental, or Internal
- **Breaking Change Policy**: Defined for pre-1.0 and post-1.0 releases
- **JavaDoc Coverage**: 100% coverage required for all public APIs

---

## API Stability Categories

### Stable APIs
**Definition**: APIs that are production-ready and will maintain backward compatibility through 1.0.0 and beyond. Breaking changes require a major version bump.

**Guarantees**:
- Method signatures will not change
- Class/interface structure will not change
- Behavior semantics will not change in incompatible ways
- Deprecation period of at least one minor version before removal

### Experimental APIs
**Definition**: APIs that are functional but may evolve based on user feedback. Breaking changes may occur in minor versions before 1.0.0.

**Guarantees**:
- Will be stabilized before 1.0.0
- Breaking changes will be documented in CHANGELOG
- Migration path will be provided when possible

### Internal APIs
**Definition**: APIs that are public for technical reasons but are not intended for direct use by end users. May change without notice.

**Guarantees**:
- No stability guarantees
- May be removed or changed in any version
- Use at your own risk

---

## Complete API Inventory

### Package: `com.vajrapulse.api.task`

#### Stable APIs

**`TaskLifecycle`** (Interface)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Main interface for load test task lifecycle
- **Methods**:
  - `void init() throws Exception` - Initialize task
  - `TaskResult execute(long iteration) throws Exception` - Execute iteration
  - `void teardown() throws Exception` - Cleanup resources
- **Breaking Change Policy**: Protected through 1.0.0+

**`TaskResult`** (Sealed Interface)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Result type for task execution
- **Permitted Types**: `TaskResultSuccess`, `TaskResultFailure`
- **Static Methods**:
  - `TaskResultSuccess success(Object data)`
  - `TaskResultSuccess success()`
  - `TaskResultFailure failure(Throwable error)`
- **Breaking Change Policy**: Protected through 1.0.0+

**`TaskResultSuccess`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Success result container
- **Components**: `Object data`
- **Breaking Change Policy**: Protected through 1.0.0+

**`TaskResultFailure`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Failure result container
- **Components**: `Throwable error`
- **Breaking Change Policy**: Protected through 1.0.0+

**`TaskIdentity`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Task identifier and metadata
- **Components**: `String name`, `Map<String, String> tags`
- **Breaking Change Policy**: Protected through 1.0.0+

**`@VirtualThreads`** (Annotation)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Mark task for virtual thread execution
- **Breaking Change Policy**: Protected through 1.0.0+

**`@PlatformThreads`** (Annotation)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Mark task for platform thread execution
- **Components**: `int poolSize()` (default: -1)
- **Breaking Change Policy**: Protected through 1.0.0+

#### Experimental APIs

**`Task`** (Interface)
- **Status**: ⚠️ Experimental (Deprecated)
- **Since**: 0.9.0
- **Purpose**: Legacy task interface (extends TaskLifecycle)
- **Note**: Deprecated in favor of `TaskLifecycle`. Will be removed in 1.0.0.
- **Migration**: Implement `TaskLifecycle` directly
- **Breaking Change Policy**: Will be removed in 1.0.0

---

### Package: `com.vajrapulse.api.pattern`

#### Stable APIs

**`LoadPattern`** (Interface)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Base interface for load patterns
- **Methods**:
  - `double calculateTps(long elapsedMillis)` - Calculate target TPS
  - `Duration getDuration()` - Get pattern duration
  - `default boolean supportsWarmupCooldown()` - Warm-up/cool-down support (since 0.9.9)
  - `default boolean shouldRecordMetrics(long elapsedMillis)` - Metrics recording control (since 0.9.9)
  - `default void registerMetrics(Object registry, String runId)` - Custom metrics registration (since 0.9.10)
- **Breaking Change Policy**: Protected through 1.0.0+

**`StaticLoad`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Constant TPS load pattern
- **Components**: `double tps`, `Duration duration`
- **Breaking Change Policy**: Protected through 1.0.0+

**`RampUpLoad`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Linear ramp-up load pattern
- **Components**: `double maxTps`, `Duration rampDuration`
- **Breaking Change Policy**: Protected through 1.0.0+

**`RampUpToMaxLoad`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Ramp-up then sustain pattern
- **Components**: `double maxTps`, `Duration rampDuration`, `Duration sustainDuration`
- **Breaking Change Policy**: Protected through 1.0.0+

**`StepLoad`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Step-wise load pattern
- **Components**: `List<Step> steps`
- **Breaking Change Policy**: Protected through 1.0.0+

**`SineWaveLoad`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Sine wave load pattern
- **Components**: `double baseTps`, `double amplitude`, `Duration period`, `Duration duration`
- **Breaking Change Policy**: Protected through 1.0.0+

**`SpikeLoad`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Spike load pattern
- **Components**: `double baseTps`, `double spikeTps`, `Duration spikeDuration`, `Duration interval`, `Duration duration`
- **Breaking Change Policy**: Protected through 1.0.0+

**`WarmupCooldownLoadPattern`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.9
- **Purpose**: Load pattern with warm-up and cool-down phases
- **Components**: `LoadPattern basePattern`, `Duration warmupDuration`, `Duration cooldownDuration`
- **Breaking Change Policy**: Protected through 1.0.0+

**`LoadPatternValidator`** (Class)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Utility for validating load pattern parameters
- **Methods**: Static validation methods
- **Breaking Change Policy**: Protected through 1.0.0+

---

### Package: `com.vajrapulse.api.pattern.adaptive`

#### Stable APIs

**`AdaptiveLoadPattern`** (Class)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Adaptive load pattern that finds maximum sustainable TPS
- **Methods**: Builder pattern, TPS calculation, phase management
- **Breaking Change Policy**: Protected through 1.0.0+

**`AdaptiveConfig`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Configuration for adaptive pattern
- **Components**: Various configuration parameters
- **Breaking Change Policy**: Protected through 1.0.0+

**`AdaptiveState`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Unified state for adaptive pattern
- **Components**: Phase, TPS, metrics, etc.
- **Breaking Change Policy**: Protected through 1.0.0+

**`AdaptivePhase`** (Enum)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Phases of adaptive pattern
- **Values**: `RAMP_UP`, `SUSTAIN`, `RAMP_DOWN`, `RECOVERY`
- **Breaking Change Policy**: Protected through 1.0.0+

**`AdaptivePatternListener`** (Interface)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Event listener for adaptive pattern events
- **Methods**: Event notification methods
- **Breaking Change Policy**: Protected through 1.0.0+

**`RampDecisionPolicy`** (Interface)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Policy for ramp-up/ramp-down decisions
- **Methods**: Decision methods
- **Breaking Change Policy**: Protected through 1.0.0+

**`DefaultRampDecisionPolicy`** (Class)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Default implementation of RampDecisionPolicy
- **Breaking Change Policy**: Protected through 1.0.0+

**`LoggingAdaptivePatternListener`** (Class)
- **Status**: ✅ Stable
- **Since**: 0.9.10
- **Purpose**: Logging implementation of AdaptivePatternListener
- **Breaking Change Policy**: Protected through 1.0.0+

#### Event Records (Stable)

**`TpsChangeEvent`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: TPS change event
- **Breaking Change Policy**: Protected through 1.0.0+

**`PhaseTransitionEvent`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Phase transition event
- **Breaking Change Policy**: Protected through 1.0.0+

**`StabilityDetectedEvent`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Stability detection event
- **Breaking Change Policy**: Protected through 1.0.0+

**`RecoveryEvent`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Recovery event
- **Breaking Change Policy**: Protected through 1.0.0+

**`MetricsSnapshot`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Metrics snapshot for adaptive decisions
- **Breaking Change Policy**: Protected through 1.0.0+

#### Internal APIs

**`AdaptiveDecisionEngine`** (Class)
- **Status**: 🔒 Internal
- **Since**: 0.9.10
- **Purpose**: Decision logic for adaptive pattern (extracted from AdaptiveLoadPattern)
- **Note**: Package-private methods, not intended for direct use
- **Breaking Change Policy**: May change without notice

---

### Package: `com.vajrapulse.api.metrics`

#### Stable APIs

**`Metrics`** (Interface)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Comprehensive metrics snapshot for assertions and reporting
- **Methods**: Various metric accessors (percentiles, TPS, latency, etc.)
- **Breaking Change Policy**: Protected through 1.0.0+

**`MetricsProvider`** (Interface)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Lightweight metrics provider for high-frequency queries
- **Methods**: Failure rate, execution count, failure count accessors
- **Breaking Change Policy**: Protected through 1.0.0+

---

### Package: `com.vajrapulse.api.backpressure`

#### Stable APIs

**`BackpressureProvider`** (Interface)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Provider of backpressure information
- **Methods**: Backpressure detection methods
- **Breaking Change Policy**: Protected through 1.0.0+

**`BackpressureHandler`** (Interface)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Handler for backpressure situations
- **Methods**: Handling methods
- **Breaking Change Policy**: Protected through 1.0.0+

**`BackpressureHandlingResult`** (Enum)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Result of backpressure handling
- **Values**: `ACCEPTED`, `QUEUED`, `DROPPED`, `REJECTED`
- **Breaking Change Policy**: Protected through 1.0.0+

**`BackpressureContext`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Context for backpressure handling
- **Components**: Context information
- **Breaking Change Policy**: Protected through 1.0.0+

---

### Package: `com.vajrapulse.api.assertion`

#### Stable APIs

**`Assertion`** (Interface)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Assertion for metrics validation
- **Methods**: Evaluation methods
- **Breaking Change Policy**: Protected through 1.0.0+

**`AssertionResult`** (Record)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Result of assertion evaluation
- **Components**: `boolean success`, `String message`
- **Breaking Change Policy**: Protected through 1.0.0+

**`Assertions`** (Class)
- **Status**: ✅ Stable
- **Since**: 0.9.0
- **Purpose**: Factory for common assertions
- **Methods**: Static factory methods
- **Breaking Change Policy**: Protected through 1.0.0+

---

### Package: `com.vajrapulse.api.exception`

#### Stable APIs

**`VajraPulseException`** (Class)
- **Status**: ✅ Stable
- **Since**: 0.9.10
- **Purpose**: Base exception for all VajraPulse framework errors
- **Breaking Change Policy**: Protected through 1.0.0+

**`ValidationException`** (Class)
- **Status**: ✅ Stable
- **Since**: 0.9.10
- **Purpose**: Exception for validation failures
- **Breaking Change Policy**: Protected through 1.0.0+

**`ExecutionException`** (Class)
- **Status**: ✅ Stable
- **Since**: 0.9.10
- **Purpose**: Exception for execution errors
- **Breaking Change Policy**: Protected through 1.0.0+

---

## Breaking Change Policy

### Pre-1.0.0 (Current: 0.9.x)

**Policy**: Breaking changes are **allowed** but should be:
- Documented in CHANGELOG.md
- Justified by code quality or architectural improvements
- Minimized when possible
- Accompanied by migration guides when significant

**Rationale**: Pre-1.0 releases allow breaking changes to achieve clean architecture and establish solid patterns before stabilization.

### Post-1.0.0

**Policy**: Breaking changes require a **major version bump** (e.g., 1.0.0 → 2.0.0).

**Exceptions**:
- Security vulnerabilities (may require immediate breaking change)
- Critical bugs that cannot be fixed without breaking changes
- Deprecated APIs can be removed after deprecation period (minimum 1 minor version)

**Process for Breaking Changes**:
1. Deprecate in minor version with `@Deprecated` annotation
2. Document migration path in JavaDoc and migration guide
3. Remove in next major version
4. Update CHANGELOG.md with breaking changes section

---

## JavaDoc Coverage Requirements

**Requirement**: 100% JavaDoc coverage for all public APIs in `vajrapulse-api` module.

**Required Elements**:
- Class/interface/record-level JavaDoc
- Method JavaDoc with `@param`, `@return`, `@throws` tags
- Constructor JavaDoc
- `@since` tags for version tracking

**Verification**:
- CI enforces JavaDoc coverage
- Build fails if JavaDoc is missing
- JavaDoc linting enabled (`-Xdoclint:all`)

---

## Package Structure Review

### Current Structure
```
com.vajrapulse.api
├── task/          # Task interfaces and annotations
├── pattern/       # Load patterns
│   └── adaptive/  # Adaptive pattern
├── metrics/       # Metrics interfaces
├── backpressure/  # Backpressure handling
├── assertion/     # Assertion framework
└── exception/      # Exception hierarchy
```

### Finalized Structure
✅ **No changes planned** - Current structure is well-organized and logical.

---

## API Freeze Checklist

### Pre-Freeze (0.9.11)
- [x] Complete API inventory
- [x] Categorize APIs by stability
- [x] Document breaking change policy
- [x] Review package structure
- [ ] Verify 100% JavaDoc coverage
- [ ] Review all public method signatures
- [ ] Identify any APIs that need deprecation

### Freeze Date
**Target**: Before 1.0.0 release (TBD)

**Criteria for Freeze**:
- All stable APIs finalized
- All experimental APIs stabilized or removed
- 100% JavaDoc coverage verified
- No pending breaking changes
- Migration guide prepared

### Post-Freeze (1.0.0+)
- Breaking changes require major version bump
- Deprecation period enforced
- Migration guides required

---

## Summary Statistics

**Total Public APIs**: 50+ types
- **Stable APIs**: 45+
- **Experimental APIs**: 1 (`Task` - deprecated)
- **Internal APIs**: 1 (`AdaptiveDecisionEngine`)

**Packages**: 7
- All packages finalized

**JavaDoc Coverage**: TBD (to be verified)

---

## Next Steps

1. **Verify JavaDoc Coverage**: Run JavaDoc coverage check
2. **Review Method Signatures**: Ensure all public methods are final
3. **Deprecation Review**: Finalize deprecation of `Task` interface
4. **Migration Guide**: Prepare migration guide for deprecated APIs
5. **Freeze Date**: Set target freeze date for 1.0.0

---

**Document Status**: ✅ Complete  
**Last Updated**: 2025-12-26  
**Next Review**: Before 1.0.0 release
