# Logging Strategy for VajraPulse

**Version**: 0.9.5  
**Status**: Logging Guidelines

---

## Overview

This document defines the logging strategy and best practices for VajraPulse. Consistent logging helps with debugging, monitoring, and operational visibility.

---

## Logging Levels

### TRACE
**Purpose**: Very detailed diagnostic information, typically only of interest when diagnosing problems.

**Usage**:
- Individual task execution details (iteration, duration, status)
- Fine-grained state transitions
- Detailed metrics calculations
- **Performance Impact**: High - only enable for debugging

**Example**:
```java
logger.trace("Iteration={} Status=SUCCESS Duration={}ns ({}ms)", 
    iteration, durationNanos, durationMs);
```

### DEBUG
**Purpose**: Detailed diagnostic information for debugging, typically of interest only when diagnosing problems.

**Usage**:
- Shutdown hook registration/removal
- Metrics flushing operations
- Configuration loading details
- Internal state changes
- **Performance Impact**: Medium - safe to enable in development

**Example**:
```java
logger.debug("Shutdown hook registered for runId={}", runId);
```

### INFO
**Purpose**: Informational messages highlighting the progress of the application at coarse-grained level.

**Usage**:
- Application lifecycle events (start, stop, completion)
- Thread pool creation and configuration
- Task initialization and teardown
- Test execution milestones
- **Performance Impact**: Low - safe to enable in production

**Example**:
```java
logger.info("Starting load test runId={} pattern={} duration={}", 
    runId, patternName, duration);
```

### WARN
**Purpose**: Warning messages indicating potential problems or unexpected situations.

**Usage**:
- Resource cleanup warnings (executor not closed)
- Shutdown timeout warnings
- Graceful shutdown failures
- Configuration issues
- **Performance Impact**: Low - always enabled

**Example**:
```java
logger.warn("Executor not closed via close() for runId={}, forcing shutdown via Cleaner", runId);
```

### ERROR
**Purpose**: Error events that might still allow the application to continue running.

**Usage**:
- Task initialization failures
- Task teardown failures
- Shutdown callback failures
- Metrics export failures
- **Performance Impact**: Low - always enabled

**Example**:
```java
logger.error("Task initialization failed for runId={}: {}", runId, e.getMessage(), e);
```

---

## Best Practices

### 1. Use Parameterized Logging

**✅ Good:**
```java
logger.info("Starting load test runId={} pattern={}", runId, patternName);
```

**❌ Bad:**
```java
logger.info("Starting load test runId=" + runId + " pattern=" + patternName);
logger.info(String.format("Starting load test runId=%s pattern=%s", runId, patternName));
```

**Why**: Parameterized logging defers string formatting until the log level is actually enabled, avoiding unnecessary string allocations in hot paths.

### 2. Guard Expensive Operations

**✅ Good:**
```java
if (logger.isTraceEnabled()) {
    double durationMs = durationNanos / 1_000_000.0;
    logger.trace("Duration={}ms", durationMs);
}
```

**❌ Bad:**
```java
logger.trace("Duration={}ms", String.format("%.3f", durationNanos / 1_000_000.0));
```

**Why**: Even with parameterized logging, expensive calculations should be guarded to avoid overhead when logging is disabled.

### 3. Include Context

**✅ Good:**
```java
logger.error("Task initialization failed for runId={}: {}", runId, e.getMessage(), e);
```

**❌ Bad:**
```java
logger.error("Task initialization failed", e);
```

**Why**: Including context (like `runId`) helps correlate logs across different components and trace execution flows.

### 4. Use Appropriate Levels

- **TRACE**: Only for very detailed diagnostics (disabled by default)
- **DEBUG**: For development debugging (disabled in production)
- **INFO**: For important lifecycle events (enabled in production)
- **WARN**: For potential issues (always enabled)
- **ERROR**: For errors (always enabled)

### 5. Avoid Logging in Hot Paths

**✅ Good:**
```java
// In hot path - minimal logging
if (logger.isTraceEnabled()) {
    logger.trace("Iteration={}", iteration);
}
```

**❌ Bad:**
```java
// In hot path - avoid INFO/DEBUG
logger.debug("Processing iteration {}", iteration); // Called millions of times!
```

**Why**: Logging in hot paths (like per-iteration execution) can significantly impact performance. Use TRACE level with guards.

---

## Logging by Component

### ExecutionEngine
- **INFO**: Lifecycle events (start, stop, completion)
- **DEBUG**: Shutdown hook operations, metrics flushing
- **WARN**: Graceful shutdown failures, resource cleanup warnings
- **ERROR**: Initialization/teardown failures

### TaskExecutor
- **TRACE**: Individual execution details (iteration, duration, status)
- **DEBUG**: Execution failures (with exception details)
- **INFO**: None (too frequent for INFO level)

### RateController
- **TRACE**: Rate control calculations (if needed)
- **INFO**: None (called too frequently)

### ShutdownManager
- **INFO**: Shutdown initiation, completion
- **DEBUG**: Hook registration/removal, callback execution
- **WARN**: Timeout warnings, interruption warnings
- **ERROR**: Callback failures

### MetricsCollector
- **DEBUG**: Metrics registration, snapshot operations
- **INFO**: None (too frequent)

---

## Configuration

### Development
```xml
<logger name="com.vajrapulse" level="DEBUG"/>
<logger name="com.vajrapulse.core.engine.TaskExecutor" level="TRACE"/>
```

### Production
```xml
<logger name="com.vajrapulse" level="INFO"/>
<logger name="com.vajrapulse.core.engine.TaskExecutor" level="WARN"/>
```

### Troubleshooting
```xml
<logger name="com.vajrapulse" level="DEBUG"/>
<logger name="com.vajrapulse.core.engine" level="TRACE"/>
```

---

## Performance Considerations

1. **Parameterized Logging**: Always use `{}` placeholders, never string concatenation
2. **Level Guards**: Use `isTraceEnabled()`, `isDebugEnabled()` for expensive operations
3. **Avoid Formatting**: Don't use `String.format()` in logging statements
4. **Hot Paths**: Minimize logging in frequently called methods (use TRACE with guards)

---

## Migration Notes

As of 0.9.5:
- ✅ Removed `String.format()` from `TaskExecutor` trace logging
- ✅ All logging uses parameterized format (`{}` placeholders)
- ✅ Expensive operations guarded with level checks

---

**Last Updated**: 2025-01-XX  
**Next Review**: Before 1.0 release

