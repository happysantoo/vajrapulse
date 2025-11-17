# VajraPulse Metrics Exporter Architecture - Clean Design (Pre-1.0)

## 🚨 Pre-1.0 Status: Breaking Changes Welcome!

**This project has NOT reached 1.0 release.** We prioritize:
- ✅ **Clean, simple code** over backwards compatibility
- ✅ **Correct architecture** over preserving old APIs  
- ✅ **Bold refactoring** over incremental patches
- ✅ **Removing complexity** over maintaining deprecated code

**If a breaking change makes the code cleaner: DO IT!**

---

## Current State Analysis

### Existing Architecture
```
vajrapulse-core/
  └── metrics/
      ├── MetricsExporter (interface)         # ✅ Already abstract
      ├── PeriodicMetricsReporter             # ✅ Already pluggable
      ├── MetricsCollector
      └── AggregatedMetrics

vajrapulse-exporter-console/
  └── console/
      └── ConsoleMetricsExporter              # Console implementation

vajrapulse-worker/
  └── pipeline/
      └── MetricsPipeline                      # Orchestration layer
```

### Current Dependencies
- `vajrapulse-core` has **zero exporter implementations** ✅ (Good!)
- `vajrapulse-exporter-console` depends on `vajrapulse-core` ✅
- `vajrapulse-worker` depends on `vajrapulse-exporter-console` (hardcoded)
- `PeriodicMetricsReporter` accepts any `MetricsExporter` ✅ (Already pluggable!)

### Issues to Address
1. ❌ **Duplicate interface** - `MetricsExporter.java` exists in both core and console
2. ❌ Console exporter bundled with worker (should be separate)  
3. ❌ No OpenTelemetry exporter
4. ❌ No BlazeMeter exporter
5. 🔥 **BREAK IT**: Since pre-1.0, we can fix everything properly now!

---

## Design Principles (Pre-1.0: No Compromises!)

### 1. **Zero Dependencies in Core**
- `vajrapulse-api`: No dependencies ✅
- `vajrapulse-core`: Only Micrometer + SLF4J ✅
- **ABSOLUTELY NO** exporter implementations in core

### 2. **Pure Plugin Architecture**
- Each exporter = completely separate artifact
- NO bundled exporters (not even console!)
- Worker imports ONLY what it needs
- **Breaking change acceptable**: Remove console from worker deps

### 3. **Minimal JAR Sizes**  
- Core: ~600 KB (micrometer + slf4j)
- Console: ~50 KB (zero deps)
- Worker: ~700 KB (NO exporters included)
- Users add exporters explicitly

### 4. **Independent Everything**
- Exporters version independently
- Core doesn't know exporters exist
- No cross-exporter dependencies

---

## Clean Architecture (Option A: Pure Separation)

**Pre-1.0 Decision: Choose the CLEANEST design, not the most compatible!**

```
vajrapulse/
├── vajrapulse-api/                          # v0.x
├── vajrapulse-core/                         # v0.x
│   └── metrics/
│       ├── MetricsExporter (interface ONLY)
│       ├── PeriodicMetricsReporter
│       └── MetricsCollector
│
├── vajrapulse-exporter-console/             # v0.x (SEPARATE, NOT BUNDLED)
├── vajrapulse-exporter-opentelemetry/       # v0.x (NEW)
├── vajrapulse-exporter-blazemeter/          # v0.x (NEW)
│
└── vajrapulse-worker/                       # v0.x
    └── NO exporter dependencies (users add them!)
```

**Key Change:** Worker doesn't include ANY exporters by default!

### Why This is Better (Pre-1.0 Advantage)

**Old (compromised for compatibility):**
```gradle
// vajrapulse-worker/build.gradle.kts
dependencies {
    api(project(":vajrapulse-exporter-console"))  // Bundled
}
```

**New (clean, pre-1.0 freedom):**
```gradle
// vajrapulse-worker/build.gradle.kts
dependencies {
    // NO exporters! Users add what they need
}
```

**User's build.gradle:**
```gradle
dependencies {
    implementation("com.vajrapulse:vajrapulse-worker:0.9.0")
    implementation("com.vajrapulse:vajrapulse-exporter-console:0.9.0")  // Explicit choice
}
```

---

## Simplified Module Dependencies

**Clean Design (Pre-1.0):**
```
vajrapulse-api (0 deps)
     ↑
vajrapulse-core (micrometer + slf4j)
     ↑
     ├── vajrapulse-exporter-console (independent)
     ├── vajrapulse-exporter-opentelemetry (independent)  
     └── vajrapulse-exporter-blazemeter (independent)
     
vajrapulse-worker (NO exporter deps)
```

**Breaking Change:** Existing examples need to add console exporter dependency.
**Why it's OK:** Pre-1.0! Clean > Compatible!

```
vajrapulse/
├── vajrapulse-api/                          # v1.0.0
├── vajrapulse-core/                         # v1.0.0
│   └── metrics/
│       ├── MetricsExporter (interface)
│       ├── PeriodicMetricsReporter
│       ├── MetricsCollector
│       └── AggregatedMetrics
│
├── vajrapulse-exporter-console/             # v1.0.0 (bundled with worker)
│   ├── build.gradle.kts
│   └── src/main/java/
│       └── com.vajrapulse.exporter.console/
│           └── ConsoleMetricsExporter
│
├── vajrapulse-exporter-opentelemetry/       # v1.0.0 (optional)
│   ├── build.gradle.kts
│   └── src/main/java/
│       └── com.vajrapulse.exporter.otel/
│           ├── OpenTelemetryExporter
│           └── OtelConfiguration
│
├── vajrapulse-exporter-blazemeter/          # v1.0.0 (optional)
│   ├── build.gradle.kts
│   └── src/main/java/
│       └── com.vajrapulse.exporter.blazemeter/
│           ├── BlazeMeterExporter
│           └── BlazeMeterConfig
│
└── vajrapulse-worker/                       # v1.0.0
    ├── build.gradle.kts (includes console only)
    └── pipeline/MetricsPipeline
```

**Benefits:**
- ✅ True modularity
- ✅ Users choose exporters at compile time
- ✅ Small JAR sizes per exporter
- ✅ Independent versioning

**Drawbacks:**
- ⚠️ More modules to maintain
- ⚠️ Slightly more complex dependency management

---

**Rejected Options:**
- ❌ Option B (Single module): Too bloated, can't choose exporters
- ❌ Option C (Hybrid with bundled console): Still couples worker to one exporter

**Pre-1.0 Winner: Pure Option A** - Cleanest separation, zero coupling!

---

## Implementation Plan (Pre-1.0: Move Fast, Break Things!)

### Phase 0: Clean Sweep (1 hour) 🔥

**Goal:** Remove all cruft, establish clean foundation

1. **Delete duplicate interface**
   ```bash
   rm vajrapulse-exporter-console/src/main/java/com/vajrapulse/exporter/console/MetricsExporter.java
   ```

2. **BREAKING: Remove console from worker**
   ```gradle
   // vajrapulse-worker/build.gradle.kts
   dependencies {
       api(project(":vajrapulse-api"))
       api(project(":vajrapulse-core"))
       // REMOVED: api(project(":vajrapulse-exporter-console"))
   }
   ```

3. **Update examples to be explicit**
   ```gradle
   // examples/*/build.gradle.kts
   dependencies {
       implementation(project(":vajrapulse-worker"))
       implementation(project(":vajrapulse-exporter-console"))  // NOW EXPLICIT!
   }
   ```

4. **Run tests, fix breaks**
   - This WILL break existing code
   - That's OK - we're pre-1.0!
   - Fix all examples and tests

**Commit:** "BREAKING: remove console exporter from worker, make fully pluggable"

---

### Phase 2: Create OpenTelemetry Exporter

#### 2.1 Module Setup

```bash
mkdir -p vajrapulse-exporter-opentelemetry/src/main/java/com/vajrapulse/exporter/otel
mkdir -p vajrapulse-exporter-opentelemetry/src/test/groovy/com/vajrapulse/exporter/otel
```

#### 2.2 Build Configuration

**`vajrapulse-exporter-opentelemetry/build.gradle.kts`:**
```gradle
dependencies {
    api(project(":vajrapulse-core"))
    
    implementation("io.opentelemetry:opentelemetry-api:1.32.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.32.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.32.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.32.0")
    
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
}
```

**Dependency size:** ~2.5 MB (OpenTelemetry SDK + OTLP exporter)

#### 2.3 Implementation

**`OpenTelemetryExporter.java`:**
```java
public final class OpenTelemetryExporter implements MetricsExporter {
    private final MeterProvider meterProvider;
    private final String serviceName;
    private final String endpoint;
    
    public OpenTelemetryExporter(String endpoint, String serviceName) {
        this.endpoint = endpoint;
        this.serviceName = serviceName;
        this.meterProvider = initializeMeterProvider();
    }
    
    @Override
    public void export(String title, AggregatedMetrics metrics) {
        // Convert AggregatedMetrics → OTLP metrics
        // Push to collector endpoint
    }
    
    private MeterProvider initializeMeterProvider() {
        return SdkMeterProvider.builder()
            .setResource(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, serviceName)))
            .registerMetricReader(PeriodicMetricReader.builder(
                OtlpHttpMetricExporter.builder()
                    .setEndpoint(endpoint)
                    .build())
                .build())
            .build();
    }
}
```

**Configuration Options:**
- OTLP endpoint (HTTP/gRPC)
- Service name
- Headers (auth tokens)
- Batch size
- Export interval

---

### Phase 3: Create BlazeMeter Exporter

#### 3.1 Module Setup

```bash
mkdir -p vajrapulse-exporter-blazemeter/src/main/java/com/vajrapulse/exporter/blazemeter
mkdir -p vajrapulse-exporter-blazemeter/src/test/groovy/com/vajrapulse/exporter/blazemeter
```

#### 3.2 Build Configuration

**`vajrapulse-exporter-blazemeter/build.gradle.kts`:**
```gradle
dependencies {
    api(project(":vajrapulse-core"))
    
    implementation("com.squareup.okhttp3:okhttp:4.12.0")  // ~400 KB
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")  // ~1.5 MB
    
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
```

**Dependency size:** ~2 MB (HTTP client + JSON)

#### 3.3 Implementation

**`BlazeMeterExporter.java`:**
```java
public final class BlazeMeterExporter implements MetricsExporter {
    private final String apiKey;
    private final String testId;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public BlazeMeterExporter(String apiKey, String testId) {
        this.apiKey = apiKey;
        this.testId = testId;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void export(String title, AggregatedMetrics metrics) {
        // Convert to BlazeMeter format
        BlazeMeterPayload payload = convertToPayload(metrics);
        
        // POST to BlazeMeter API
        Request request = new Request.Builder()
            .url("https://a.blazemeter.com/api/v4/sessions/" + testId + "/reports")
            .addHeader("X-API-Key", apiKey)
            .post(RequestBody.create(
                objectMapper.writeValueAsString(payload),
                MediaType.parse("application/json")))
            .build();
            
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("BlazeMeter export failed: {}", response.code());
            }
        }
    }
    
    private BlazeMeterPayload convertToPayload(AggregatedMetrics metrics) {
        // Map VajraPulse metrics to BlazeMeter schema
        return new BlazeMeterPayload(
            metrics.totalExecutions(),
            metrics.successRate(),
            extractPercentiles(metrics)
        );
    }
}
```

**Configuration:**
- API key
- Test ID
- Session ID
- Upload interval

---

### Phase 4: Update Worker Module

#### 4.1 Remove Hardcoded Console Dependency

**Before:**
```java
// In VajraPulseWorker.java or MetricsPipeline
ConsoleMetricsExporter exporter = new ConsoleMetricsExporter();
```

**After:**
```java
// MetricsPipeline.Builder already accepts any exporter
MetricsPipeline.builder()
    .addExporter(new ConsoleMetricsExporter())  // User provides
    .build();
```

#### 4.2 Update Worker build.gradle.kts

```gradle
dependencies {
    api(project(":vajrapulse-api"))
    api(project(":vajrapulse-core"))
    
    // Console bundled by default
    api(project(":vajrapulse-exporter-console"))
    
    // Optional: users can add in their own build.gradle
    // runtimeOnly(project(":vajrapulse-exporter-opentelemetry"))
    // runtimeOnly(project(":vajrapulse-exporter-blazemeter"))
}
```

---

### Phase 5: Service Provider Interface (Optional Advanced)

For automatic discovery of exporters:

#### 5.1 Define SPI in Core

**`com.vajrapulse.core.metrics.MetricsExporterProvider`:**
```java
public interface MetricsExporterProvider {
    String name();
    MetricsExporter create(Map<String, String> config);
}
```

#### 5.2 Implement in Each Exporter

**Console:**
```java
// META-INF/services/com.vajrapulse.core.metrics.MetricsExporterProvider
com.vajrapulse.exporter.console.ConsoleExporterProvider

public class ConsoleExporterProvider implements MetricsExporterProvider {
    @Override
    public String name() { return "console"; }
    
    @Override
    public MetricsExporter create(Map<String, String> config) {
        return new ConsoleMetricsExporter();
    }
}
```

**OpenTelemetry:**
```java
public class OtelExporterProvider implements MetricsExporterProvider {
    @Override
    public String name() { return "opentelemetry"; }
    
    @Override
    public MetricsExporter create(Map<String, String> config) {
        String endpoint = config.get("endpoint");
        String serviceName = config.getOrDefault("service.name", "vajrapulse");
        return new OpenTelemetryExporter(endpoint, serviceName);
    }
}
```

#### 5.3 Auto-Discovery

```java
public class ExporterFactory {
    public static MetricsExporter create(String name, Map<String, String> config) {
        ServiceLoader<MetricsExporterProvider> loader = 
            ServiceLoader.load(MetricsExporterProvider.class);
            
        for (MetricsExporterProvider provider : loader) {
            if (provider.name().equals(name)) {
                return provider.create(config);
            }
        }
        throw new IllegalArgumentException("Unknown exporter: " + name);
    }
}
```

**Usage:**
```java
// Auto-discover and create
MetricsExporter exporter = ExporterFactory.create("opentelemetry", Map.of(
    "endpoint", "http://localhost:4318/v1/metrics",
    "service.name", "my-load-test"
));
```

---

## Pre-1.0 Design Decision: Pure Separation

| Feature | Pure Separation (Option A) | Why It Wins |
|---------|---------------------------|-------------|
| **Module Count** | 6 modules | Clean boundaries |
| **Worker JAR** | ~700 KB (NO exporters) | Minimal! |
| **Pluggability** | ✅ Perfect | Zero coupling |
| **Flexibility** | ✅ Maximum | Users choose everything |
| **Breaking Changes** | ✅ Allowed | Pre-1.0! |

**Pre-1.0 Advantage:** We can choose the cleanest design without compatibility baggage!

---

## Detailed Implementation Checklist

### Immediate Actions (Phase 1)

- [ ] Remove duplicate `MetricsExporter.java` from `vajrapulse-exporter-console/src/main/java/`
- [ ] Verify `PeriodicMetricsReporter` has zero hardcoded exporters ✅
- [ ] Verify `MetricsPipeline` accepts any exporter via builder ✅
- [ ] Update worker module to use `api(project(...))` for console exporter

### OpenTelemetry Exporter (Phase 2)

- [ ] Create module structure
- [ ] Add build.gradle.kts with OTLP dependencies
- [ ] Implement `OpenTelemetryExporter`
- [ ] Add configuration builder (endpoint, service name, headers)
- [ ] Map `AggregatedMetrics` → OTLP metrics format
- [ ] Add Spock tests with mock OTLP collector
- [ ] Create README with configuration examples
- [ ] Add to settings.gradle.kts

### BlazeMeter Exporter (Phase 3)

- [ ] Create module structure
- [ ] Add build.gradle.kts with HTTP client + JSON deps
- [ ] Implement `BlazeMeterExporter`
- [ ] Add configuration (API key, test ID, session ID)
- [ ] Map `AggregatedMetrics` → BlazeMeter JSON schema
- [ ] Add Spock tests with MockWebServer
- [ ] Create README with API setup guide
- [ ] Add to settings.gradle.kts

### Worker Integration (Phase 4)

- [ ] Update worker build.gradle to bundle console only
- [ ] Document how users add optional exporters
- [ ] Update example projects to show exporter usage
- [ ] Add CLI flags for exporter selection (if using SPI)

### Service Provider Interface (Phase 5 - Optional)

- [ ] Define `MetricsExporterProvider` interface in core
- [ ] Implement provider in console exporter
- [ ] Implement provider in OTLP exporter
- [ ] Implement provider in BlazeMeter exporter
- [ ] Create `ExporterFactory` with ServiceLoader
- [ ] Add CLI integration for dynamic exporter loading

### Documentation

- [ ] Update DESIGN.md with exporter architecture
- [ ] Create EXPORTER_GUIDE.md (how to add custom exporters)
- [ ] Update METRICS_PIPELINE_FLOW.md with exporter examples
- [ ] Add README per exporter module
- [ ] Document configuration options per exporter

### Testing

- [ ] Spock tests for each exporter
- [ ] Integration test: MetricsPipeline + multiple exporters
- [ ] Integration test: PeriodicReporter + OTLP exporter
- [ ] Performance test: overhead of multiple exporters
- [ ] Example project using OTLP + Console simultaneously

---

## Example User Scenarios

### Scenario 1: Console Only (Default)

```gradle
dependencies {
    implementation("com.vajrapulse:vajrapulse-worker:1.0.0")
    // Console exporter already bundled
}
```

```java
MetricsPipeline pipeline = MetricsPipeline.builder()
    .addExporter(new ConsoleMetricsExporter())
    .build();
```

### Scenario 2: OpenTelemetry Integration

```gradle
dependencies {
    implementation("com.vajrapulse:vajrapulse-worker:1.0.0")
    implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry:1.0.0")
}
```

```java
MetricsExporter otelExporter = new OpenTelemetryExporter(
    "http://localhost:4318/v1/metrics",
    "my-load-test"
);

MetricsPipeline pipeline = MetricsPipeline.builder()
    .addExporter(otelExporter)
    .withPeriodic(Duration.ofSeconds(10))
    .build();
```

### Scenario 3: Multiple Exporters

```gradle
dependencies {
    implementation("com.vajrapulse:vajrapulse-worker:1.0.0")
    implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry:1.0.0")
    implementation("com.vajrapulse:vajrapulse-exporter-blazemeter:1.0.0")
}
```

```java
MetricsPipeline pipeline = MetricsPipeline.builder()
    .addExporter(new ConsoleMetricsExporter())              // Local visibility
    .addExporter(new OpenTelemetryExporter(...))            // OTLP backend
    .addExporter(new BlazeMeterExporter(...))               // BlazeMeter cloud
    .withPeriodic(Duration.ofSeconds(5))
    .build();
    
// All three exporters receive metrics simultaneously
pipeline.run(task, loadPattern);
```

### Scenario 4: CLI with Auto-Discovery

```bash
java -jar vajrapulse-worker-all.jar run \
  --task com.example.MyTask \
  --load-pattern static --tps 100 --duration 60s \
  --exporter console \
  --exporter opentelemetry \
  --otel-endpoint http://localhost:4318/v1/metrics \
  --otel-service-name my-test
```

---

## No Migration Path Needed!

**Pre-1.0 Status:** No users to migrate! We can design it right from scratch.

**Clean slate approach:**
1. Delete old assumptions
2. Implement cleanest design
3. Update all examples
4. Ship it!

---

## Performance Considerations

### Export Overhead

| Exporter | Per-Export Latency | Memory Overhead | Network I/O |
|----------|-------------------|-----------------|-------------|
| Console | ~1 ms | 0 KB | None |
| OTLP | ~5-10 ms | ~50 KB buffer | Yes (HTTP) |
| BlazeMeter | ~10-20 ms | ~30 KB buffer | Yes (HTTPS) |

### Recommendations

1. **Async Export**: Consider moving export to separate thread pool for OTLP/BlazeMeter
2. **Batching**: Buffer metrics and send in batches (OTLP supports this natively)
3. **Circuit Breaker**: If remote endpoint fails, disable temporarily to avoid blocking
4. **Backpressure**: Drop metrics if export queue is full (don't block test execution)

---

## Alternative Approaches Considered

### 1. Micrometer Registry Direct Export

**Idea:** Let users provide a Micrometer registry, use built-in exporters.

**Rejected because:**
- ❌ Loses VajraPulse's simplified metrics model
- ❌ Exposes Micrometer internals to users
- ❌ More complex configuration
- ❌ Micrometer registries poll, we want push

### 2. Callback-Based Export

**Idea:** Users register callbacks instead of exporter objects.

**Rejected because:**
- ❌ Lambdas in API (violates VajraPulse principles)
- ❌ Less testable
- ❌ No state management per exporter

### 3. Single "Universal" Exporter

**Idea:** One exporter with multiple backends configured via enum.

**Rejected because:**
- ❌ Tight coupling
- ❌ Large JAR with all dependencies
- ❌ Can't extend with custom exporters

---

## Custom Exporter Guide

For users wanting to create their own exporter:

### Step 1: Implement Interface

```java
public class MyCustomExporter implements MetricsExporter {
    @Override
    public void export(String title, AggregatedMetrics metrics) {
        // Your export logic here
    }
}
```

### Step 2: Add to Pipeline

```java
MetricsPipeline.builder()
    .addExporter(new MyCustomExporter())
    .build();
```

### Step 3: (Optional) Create Module

```
my-vajrapulse-exporter/
├── build.gradle.kts
└── src/main/java/
    └── com/mycompany/vajrapulse/
        └── MyCustomExporter.java
```

```gradle
dependencies {
    api("com.vajrapulse:vajrapulse-core:1.0.0")
    // Your exporter-specific dependencies
}
```

---

## Timeline Estimate

| Phase | Effort | Calendar Time |
|-------|--------|---------------|
| Phase 1: Cleanup | 1 hour | 1 day |
| Phase 2: OTLP Exporter | 8 hours | 3 days |
| Phase 3: BlazeMeter Exporter | 6 hours | 2 days |
| Phase 4: Worker Update | 2 hours | 1 day |
| Phase 5: SPI (optional) | 4 hours | 2 days |
| Testing & Docs | 4 hours | 2 days |
| **Total** | **25 hours** | **~2 weeks** |

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| OTLP version conflicts | Medium | High | Use shaded JAR for exporter |
| BlazeMeter API changes | Low | Medium | Version lock dependencies |
| JAR size bloat | Low | Low | Separate artifacts |
| Breaking changes | Low | High | Maintain backward compat |
| Complex configuration | Medium | Medium | Provide builders + examples |

---

## Success Metrics

- ✅ Console exporter: 0 external dependencies
- ✅ OTLP exporter: < 3 MB total size
- ✅ BlazeMeter exporter: < 2 MB total size
- ✅ Worker JAR (default): < 2 MB
- ✅ Export overhead: < 10ms per snapshot
- ✅ Zero breaking changes for existing users
- ✅ 3+ exporters available
- ✅ Users can create custom exporters in < 1 hour

---

## Conclusion

**Recommended Approach:** **Option C (Hybrid)** with:
- Console exporter bundled with worker (default, lightweight)
- Optional OpenTelemetry exporter (separate artifact)
- Optional BlazeMeter exporter (separate artifact)
- Service Provider Interface for CLI auto-discovery
- Maintain full backward compatibility

This design maximizes **modularity**, **pluggability**, and **user choice** while keeping the default experience simple and the JAR sizes minimal.
