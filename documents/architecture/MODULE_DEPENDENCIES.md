# Module Dependencies - VajraPulse

**Version**: 0.9.5  
**Status**: Architecture Documentation

---

## Overview

This document describes the module structure and dependencies in VajraPulse. Understanding module boundaries is essential for maintaining clean architecture and avoiding circular dependencies.

---

## Module Structure

VajraPulse is organized into the following modules:

1. **vajrapulse-api** - Public API interfaces and contracts
2. **vajrapulse-core** - Core implementation
3. **vajrapulse-exporter-*** - Export implementations (console, OpenTelemetry, report)
4. **vajrapulse-bom** - Bill of Materials for dependency management
5. **vajrapulse-worker** - CLI application

---

## Dependency Graph

```
vajrapulse-api
    ↑
    │ (depends on)
    │
vajrapulse-core
    ↑
    │ (depends on)
    │
vajrapulse-exporter-console
vajrapulse-exporter-opentelemetry
vajrapulse-exporter-report
    ↑
    │ (depends on)
    │
vajrapulse-worker
```

---

## Module Details

### vajrapulse-api

**Purpose**: Public API interfaces and contracts

**Dependencies**: None (zero dependencies)

**Exports**:
- `TaskLifecycle` - Task execution interface
- `LoadPattern` - Load pattern interface
- `MetricsProvider` - Metrics query interface
- `AdaptiveLoadPattern` - Adaptive load pattern implementation
- `TaskResult` - Task execution result

**Key Principle**: This module must have **zero dependencies** to allow other modules to depend on it without pulling in implementation details.

---

### vajrapulse-core

**Purpose**: Core implementation of execution engine, metrics collection, and load patterns

**Dependencies**:
- `vajrapulse-api` (required)
- Micrometer (metrics)
- SLF4J (logging)

**Exports**:
- `ExecutionEngine` - Main execution engine
- `MetricsCollector` - Metrics collection
- `RateController` - Rate control
- `ShutdownManager` - Shutdown handling
- Load pattern implementations (StaticLoad, RampUpLoad, etc.)

**Key Principle**: Depends only on `vajrapulse-api` and minimal external dependencies.

---

### vajrapulse-exporter-console

**Purpose**: Console output for metrics

**Dependencies**:
- `vajrapulse-api` (required)
- `vajrapulse-core` (required)
- Micrometer (metrics)

**Key Principle**: Depends on core for metrics access.

---

### vajrapulse-exporter-opentelemetry

**Purpose**: OpenTelemetry export for metrics and traces

**Dependencies**:
- `vajrapulse-api` (required)
- `vajrapulse-core` (required)
- OpenTelemetry SDK
- Micrometer (metrics)

**Key Principle**: Depends on core for metrics access.

---

### vajrapulse-exporter-report

**Purpose**: Report generation (CSV, HTML, JSON)

**Dependencies**:
- `vajrapulse-api` (required)
- `vajrapulse-core` (required)
- Micrometer (metrics)

**Key Principle**: Depends on core for metrics access.

---

### vajrapulse-worker

**Purpose**: CLI application that bundles all modules

**Dependencies**:
- All other modules
- CLI libraries (Picocli, etc.)

**Key Principle**: Can depend on all modules as it's the entry point.

---

## Dependency Rules

### Rule 1: API Module Has Zero Dependencies

**Enforced**: `vajrapulse-api` must not depend on any other VajraPulse modules or external libraries (except JDK).

**Rationale**: Allows other modules to depend on the API without pulling in implementation details.

**Violation Example**:
```java
// ❌ BAD - API module depending on core
import com.vajrapulse.core.MetricsCollector; // Not allowed!
```

---

### Rule 2: Core Depends Only on API

**Enforced**: `vajrapulse-core` depends only on `vajrapulse-api`, not on exporter modules.

**Rationale**: Keeps core implementation independent of export formats.

**Violation Example**:
```java
// ❌ BAD - Core depending on exporter
import com.vajrapulse.exporter.console.ConsoleExporter; // Not allowed!
```

---

### Rule 3: Exporters Depend on Core and API

**Enforced**: Exporter modules depend on both `vajrapulse-api` and `vajrapulse-core`.

**Rationale**: Exporters need access to metrics and implementation details.

**Valid Example**:
```java
// ✅ GOOD - Exporter depending on core
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.api.MetricsProvider;
```

---

### Rule 4: No Circular Dependencies

**Enforced**: No module can depend on another module that depends on it (directly or transitively).

**Rationale**: Circular dependencies create tight coupling and make testing difficult.

**Violation Example**:
```
vajrapulse-core → vajrapulse-exporter-console → vajrapulse-core
```

---

## Dependency Verification

### Build-Time Checks

The Gradle build verifies module boundaries:

1. **API Module Check**: Ensures `vajrapulse-api` has zero dependencies
2. **Circular Dependency Check**: Gradle detects circular dependencies
3. **Package Visibility**: Java modules enforce package boundaries

### Manual Verification

To verify dependencies:

```bash
# Check API module dependencies
./gradlew :vajrapulse-api:dependencies

# Check core module dependencies
./gradlew :vajrapulse-core:dependencies

# Check for circular dependencies
./gradlew :vajrapulse-core:dependencies --configuration compileClasspath
```

---

## Common Patterns

### Pattern 1: Adapter Pattern

**Use Case**: Adapting core implementation to API interface

**Example**: `MetricsProviderAdapter` adapts `MetricsCollector` to `MetricsProvider`

```java
// In vajrapulse-core
public class MetricsProviderAdapter implements MetricsProvider {
    private final MetricsCollector collector;
    // Adapts collector to MetricsProvider interface
}
```

**Location**: `vajrapulse-core/src/main/java/com/vajrapulse/core/engine/MetricsProviderAdapter.java`

---

### Pattern 2: Factory Pattern

**Use Case**: Creating instances across module boundaries

**Example**: `ExecutionEngine.builder()` creates engine instances

```java
// In vajrapulse-core
public static ExecutionEngine.Builder builder() {
    return new Builder();
}
```

---

## Migration Guide

### Adding a New Module

1. **Create module directory** in root
2. **Add to `settings.gradle.kts`**
3. **Define dependencies** in module's `build.gradle.kts`
4. **Verify no circular dependencies**
5. **Update this document**

### Moving Code Between Modules

1. **Check dependencies** - Ensure target module can depend on source module
2. **Update imports** - Fix all import statements
3. **Update tests** - Move tests to appropriate module
4. **Verify build** - Run `./gradlew build` to check for issues

---

## See Also

- [Gradle Multi-Project Builds](https://docs.gradle.org/current/userguide/multi_project_builds.html)
- [Java Module System](https://docs.oracle.com/javase/9/docs/api/java/lang/module/package-summary.html)
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

