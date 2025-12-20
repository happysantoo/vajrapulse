# HikariCP Backpressure Provider Example

This example demonstrates how to integrate HikariCP connection pool metrics with VajraPulse's backpressure system.

## Overview

The `HikariCpBackpressureProvider` is an example implementation showing how to:
- Monitor HikariCP connection pool utilization
- Report backpressure signals to `AdaptiveLoadPattern`
- Automatically adjust load based on connection pool state

## Why This is an Example (Not in Core)

This example is **NOT** included in the core VajraPulse distribution to:
- Avoid adding HikariCP as a dependency
- Keep VajraPulse lightweight and dependency-free
- Allow users to adapt it to their specific needs

## Usage

### 1. Add HikariCP Dependency

Add HikariCP to your project's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.zaxxer:HikariCP:5.0.1")
    // ... other dependencies
}
```

### 2. Copy the Example

Copy `HikariCpBackpressureProvider.java` to your project and uncomment the implementation code.

### 3. Use with AdaptiveLoadPattern

```java
// Configure HikariCP connection pool
HikariDataSource dataSource = new HikariDataSource();
dataSource.setJdbcUrl("jdbc:postgresql://localhost/test");
dataSource.setMaximumPoolSize(100);
dataSource.setMinimumIdle(10);
// ... other configuration ...

// Create backpressure provider
HikariCpBackpressureProvider backpressureProvider = 
    new HikariCpBackpressureProvider(dataSource, 0.8); // 80% utilization threshold

// Use with AdaptiveLoadPattern
AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
    .initialTps(10.0)
    .rampIncrement(15.0)
    .rampDecrement(15.0)
    .rampInterval(Duration.ofSeconds(5))
    .maxTps(200.0)
    .minTps(5.0)
    .sustainDuration(Duration.ofSeconds(30))
    .stableIntervalsRequired(3)
    .metricsProvider(metricsProvider)
    .backpressureProvider(backpressureProvider)
    .decisionPolicy(new DefaultRampDecisionPolicy(0.10))  // 10% error threshold
    .build();
```

## How It Works

1. **Connection Pool Monitoring**: The provider queries HikariCP's `HikariPoolMXBean` for connection pool state
2. **Utilization Calculation**: Calculates utilization as `active / total` connections
3. **Backpressure Signal**: Returns backpressure level (0.0 to 1.0) based on utilization:
   - 0.0 when utilization < threshold (e.g., 80%)
   - Scales from 0.0 to 1.0 as utilization approaches 100%
4. **Adaptive Response**: `AdaptiveLoadPattern` uses this signal to:
   - Ramp down when backpressure >= 0.7 (70%)
   - Hold TPS when backpressure is moderate (0.3 to 0.7)
   - Ramp up when backpressure < 0.3 (30%)

## Customization

You can customize the behavior by:
- Adjusting the `utilizationThreshold` (default: 0.8)
- Modifying the backpressure calculation formula
- Adding additional metrics (waiting connections, connection timeouts, etc.)

## Example Output

```
Adaptive Pattern State:
  Phase: RAMP_DOWN
  Current TPS: 85.0
  Error Rate: 2.5% (below 10% threshold)
  Backpressure: 0.75 (HIGH)
  Backpressure Details: HikariCP: 85/100 connections active (85.0% utilization, 25.0% backpressure)
  Reason: High backpressure detected - ramping down
```

## See Also

- [Backpressure Provider Guide](../../../documents/roadmap/RELEASE_0.9.6_CLIENT_METRICS_PLAN.md)
- [Adaptive Load Pattern Documentation](../../../vajrapulse-api/src/main/java/com/vajrapulse/api/AdaptiveLoadPattern.java)

