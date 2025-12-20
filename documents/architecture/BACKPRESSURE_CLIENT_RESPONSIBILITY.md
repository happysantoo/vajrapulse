# Backpressure Provider: Client-Side Responsibility

**Date**: 2025-12-12  
**Version**: 0.9.9  
**Status**: Architectural Principle

---

## Core Principle

**VajraPulse provides the contract (`BackpressureProvider` interface), not framework-specific implementations.**

This architectural decision ensures:

1. **Framework-Agnostic Core**: VajraPulse doesn't depend on vortex, HikariCP, or any specific framework
2. **Lightweight Library**: Core library stays minimal and focused
3. **Client Flexibility**: Clients implement providers for their specific infrastructure
4. **Separation of Concerns**: Framework integration is a client responsibility

---

## What VajraPulse Provides

### 1. Interface Contract

```java
public interface BackpressureProvider {
    double getBackpressureLevel();  // 0.0 to 1.0
    default String getBackpressureDescription() { return null; }
}
```

### 2. Generic Implementations

VajraPulse core provides **generic** providers that work with any infrastructure:

- **`QueueBackpressureProvider`**: Works with any queue (not vortex-specific)
- **`CompositeBackpressureProvider`**: Combines multiple providers

### 3. Integration Points

- `AdaptiveLoadPattern` accepts optional `BackpressureProvider`
- `RampDecisionPolicy` uses backpressure in decisions
- `ExecutionEngine` uses `BackpressureHandler` for request handling

---

## What Clients Provide

### Framework-Specific Implementations

Clients implement `BackpressureProvider` for their specific infrastructure:

- **Vortex**: Track queue rejection exceptions
- **HikariCP**: Monitor connection pool utilization
- **Apache HttpClient**: Track connection pool state
- **Custom Queues**: Track queue depth
- **Latency-Based**: Calculate from metrics
- **Business Logic**: Custom backpressure signals

### Example Pattern

```java
// Client code (NOT in VajraPulse core)
public class MyVortexBackpressureProvider implements BackpressureProvider {
    // Client-specific implementation
    // Tracks vortex exceptions, queue depth, etc.
}

// Use with VajraPulse
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .backpressureProvider(new MyVortexBackpressureProvider())
    .build();
```

---

## Examples in Codebase

### HikariCP Example

**Location**: `examples/hikaricp-backpressure-example/`

**Why in examples, not core?**
- Would require HikariCP as a dependency
- Framework-specific implementation
- Clients can copy and adapt

**Pattern**:
```java
// Example shows HOW to implement, not included in core
public class HikariCpBackpressureProvider implements BackpressureProvider {
    // Implementation here
}
```

### Vortex Example (To Be Created)

**Location**: `examples/vortex-backpressure-example/` (future)

**Why in examples, not core?**
- Would require vortex as a dependency
- Framework-specific implementation
- Clients can copy and adapt

---

## Benefits of This Approach

### 1. Dependency Management

**Without client-side pattern**:
```kotlin
// VajraPulse core would need:
dependencies {
    implementation("com.vajrapulse:vortex:0.0.9")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    // ... many more framework dependencies
}
```

**With client-side pattern**:
```kotlin
// VajraPulse core stays minimal:
dependencies {
    // Only essential dependencies
    implementation("io.micrometer:micrometer-core:1.12.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
}
```

### 2. Version Flexibility

Clients can:
- Use any version of vortex, HikariCP, etc.
- Upgrade frameworks independently
- Choose which frameworks to integrate

### 3. Customization

Clients can:
- Implement custom backpressure logic
- Combine multiple signals
- Adapt examples to their needs

---

## Documentation Strategy

### Core Documentation

Documents the **interface and contract**:
- How `BackpressureProvider` works
- How to implement the interface
- Integration with `AdaptiveLoadPattern`
- Generic provider examples

### Example Documentation

Documents **framework-specific implementations**:
- How to integrate with vortex
- How to integrate with HikariCP
- Copy-paste examples
- Adaptation guidelines

---

## Migration Guide

### If You Were Using Vortex Backpressure Package

**Before (Vortex < 0.0.9)**:
```java
// Old: Used vortex backpressure package
import com.vajrapulse.vortex.backpressure.BackpressureProvider;

BackpressureProvider provider = vortex.getBackpressureProvider();
```

**After (Vortex 0.0.9+)**:
```java
// New: Implement BackpressureProvider in client code
import com.vajrapulse.api.metrics.BackpressureProvider;

// Copy example from examples/vortex-backpressure-example/
public class MyVortexBackpressureProvider implements BackpressureProvider {
    // Track exceptions, implement getBackpressureLevel()
}

BackpressureProvider provider = new MyVortexBackpressureProvider();
```

---

## Best Practices

### 1. Keep Providers Simple

Providers should:
- Calculate backpressure efficiently
- Be thread-safe
- Cache values when appropriate
- Not block or throw exceptions

### 2. Use Examples as Templates

- Copy example implementations
- Adapt to your specific needs
- Don't reinvent the wheel

### 3. Combine Multiple Signals

Use `CompositeBackpressureProvider`:
```java
CompositeBackpressureProvider provider = new CompositeBackpressureProvider(
    new VortexBackpressureProvider(),
    new ConnectionPoolBackpressureProvider(),
    new LatencyBackpressureProvider()
);
```

---

## Summary

| Aspect | VajraPulse Core | Client Code |
|--------|----------------|-------------|
| **Interface** | ✅ Provides `BackpressureProvider` | ✅ Implements interface |
| **Generic Providers** | ✅ `QueueBackpressureProvider`, `CompositeBackpressureProvider` | ❌ Not needed |
| **Framework Providers** | ❌ Not included | ✅ Implement in client code |
| **Examples** | ❌ Not in core | ✅ In `examples/` folder |
| **Dependencies** | ✅ Minimal (no framework deps) | ✅ Add framework deps as needed |

---

**Last Updated**: 2025-12-12  
**Version**: 0.9.9

