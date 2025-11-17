# VajraPulse Exporter Architecture - Action Items (Pre-1.0)

## 🚨 Pre-1.0 Status: Breaking Changes Are GOOD!

**We haven't released 1.0 yet!** This means:
- ✅ **Break everything** if it makes code cleaner
- ✅ **Delete old code** instead of deprecating
- ✅ **Refactor boldly** - no users to upset
- ✅ **Get the design RIGHT** before stabilizing

**Goal:** Cleanest possible architecture, not backwards compatibility!

## 🔍 Current Architecture Analysis

### ✅ What's Good

1. **Clean Interface in Core**
   - `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/MetricsExporter.java` exists
   - Already abstract and pluggable
   
2. **Pluggable Reporter**
   - `PeriodicMetricsReporter` accepts any `MetricsExporter` ✅
   - No hardcoded implementations

3. **Flexible Pipeline**
   - `MetricsPipeline.Builder.addExporter()` accepts multiple exporters ✅
   - Already supports simultaneous export to multiple destinations

### 🔥 What We're Fixing (Pre-1.0 Freedom!)

1. **Duplicate Interface** 🔴
   - File: `/vajrapulse-exporter-console/src/main/java/com/vajrapulse/exporter/console/MetricsExporter.java`
   - Problem: Same interface in both core and console
   - **Action:** DELETE the duplicate (no deprecation needed!)

2. **Console Coupled to Worker** 🔴
   - Problem: Worker has compile-time dependency on console exporter
   - **Breaking Fix:** Remove console from worker deps entirely
   - **Why Pre-1.0 Allows This:** We can fix coupling issues NOW before release!

3. **No Separation** 🟡
   - Worker bundles console by default
   - **Clean Design:** Worker should bundle NOTHING, users choose exporters
   - **Breaking Change:** Update all examples to explicitly add console
   - **Pre-1.0 Win:** Perfect time to establish clean boundaries!

---

## 🎯 Clean Architecture (Pure Separation - Pre-1.0 Design)

```
┌─────────────────────────────────────────────────────────────────┐
│                      vajrapulse-api                              │
│                    (0 dependencies)                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                     vajrapulse-core                              │
│              (micrometer + slf4j only)                           │
│                                                                  │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ interface MetricsExporter                              │    │
│  │   void export(String title, AggregatedMetrics metrics) │    │
│  └────────────────────────────────────────────────────────┘    │
│                                                                  │
│  • MetricsCollector                                              │
│  • AggregatedMetrics                                             │
│  • PeriodicMetricsReporter (accepts any MetricsExporter)         │
└────────────────┬───────────┬────────────┬────────────────────────┘
                 │           │            │
       ┌─────────▼─────┐ ┌──▼──────┐ ┌───▼────────────┐
       │   Console     │ │  OTLP   │ │  BlazeMeter    │
       │   Exporter    │ │ Exporter│ │   Exporter     │
       │               │ │         │ │                │
       │  ~50 KB       │ │ ~2.5 MB │ │    ~2 MB       │
       │  0 deps       │ │ OTLP SDK│ │ HTTP + JSON    │
       └───────────────┘ └─────────┘ └────────────────┘
                 │           │            │
                 │           │            │
       ┌─────────▼───────────▼────────────▼────────┐
       │   Users add exporters explicitly           │
       │   in their build.gradle                    │
       └────────────────────────────────────────────┘
               │
       ┌───────▼────────────────────────────────────┐
       │        vajrapulse-worker                    │
       │   (~700 KB, NO exporters bundled!)         │
       │                                              │
       │  • MetricsPipeline                          │
       │  • CLI (optional SPI for auto-discovery)    │
       └─────────────────────────────────────────────┘
```

**Key Difference from "Compatible" Design:**
- ❌ Old: Console bundled with worker (~1.5 MB)
- ✅ New: NO exporters bundled (~700 KB)
- **Pre-1.0 Allows:** Clean separation without migration pain!

```
┌─────────────────────────────────────────────────────────────────┐
│                      vajrapulse-api                              │
│                    (0 dependencies)                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                     vajrapulse-core                              │
│              (micrometer + slf4j only)                           │
│                                                                  │
│  ┌────────────────────────────────────────────────────────┐    │
│  │ interface MetricsExporter                              │    │
│  │   void export(String title, AggregatedMetrics metrics) │    │
│  └────────────────────────────────────────────────────────┘    │
│                                                                  │
│  • MetricsCollector                                              │
│  • AggregatedMetrics                                             │
│  • PeriodicMetricsReporter (accepts any MetricsExporter)         │
└────────────────┬───────────┬────────────┬────────────────────────┘
                 │           │            │
       ┌─────────▼─────┐ ┌──▼──────┐ ┌───▼────────────┐
       │   Console     │ │  OTLP   │ │  BlazeMeter    │
       │   Exporter    │ │ Exporter│ │   Exporter     │
       │               │ │         │ │                │
       │  ~50 KB       │ │ ~2.5 MB │ │    ~2 MB       │
       │  0 deps       │ │ OTLP SDK│ │ HTTP + JSON    │
       └───────┬───────┘ └────┬────┘ └────┬───────────┘
               │              │            │
               │        ┌─────▼────────────▼──────┐
               │        │   Optional (added       │
               │        │   by users when needed) │
               │        └─────────────────────────┘
               │
       ┌───────▼────────────────────────────────────┐
       │        vajrapulse-worker                    │
       │   (bundles console by default)              │
       │                                              │
       │  • MetricsPipeline                          │
       │  • CLI (optional SPI for auto-discovery)    │
       └─────────────────────────────────────────────┘
```

---

## 📋 Implementation Checklist

### ✅ Phase 0: Clean Sweep - Breaking Changes (2 hours)

**Pre-1.0 Freedom: Break everything for cleaner design!**

- [ ] **Delete duplicate interface**
  ```bash
  rm vajrapulse-exporter-console/src/main/java/com/vajrapulse/exporter/console/MetricsExporter.java
  ```

- [ ] **Verify ConsoleMetricsExporter imports**
  ```java
  // Should have this import:
  import com.vajrapulse.core.metrics.MetricsExporter;
  
  // Should NOT have:
  // import com.vajrapulse.exporter.console.MetricsExporter;
  ```

- [ ] **🔥 BREAKING: Remove console from worker dependencies**
  ```gradle
  // vajrapulse-worker/build.gradle.kts
  dependencies {
      // Remove this line:
      // implementation(project(":vajrapulse-exporter-console"))
      
      implementation(project(":vajrapulse-api"))
      implementation(project(":vajrapulse-core"))
      // Console is now OPTIONAL - users add it themselves!
  }
  ```

- [ ] **🔥 BREAKING: Update examples to explicitly add console**
  ```gradle
  // examples/http-load-test/build.gradle.kts
  dependencies {
      implementation(project(":vajrapulse-worker"))
      implementation(project(":vajrapulse-exporter-console"))  // ADD THIS!
  }
  ```

- [ ] **Update example code**
  ```java
  // examples/http-load-test/.../HttpLoadTestExample.java
  import com.vajrapulse.exporter.console.ConsoleMetricsExporter;  // ADD THIS!
  
  MetricsPipeline.builder()
      .addExporter(new ConsoleMetricsExporter())  // Now required!
      .build()
      .run(task, loadPattern);
  ```

- [ ] **Run tests**
  ```bash
  ./gradlew :vajrapulse-exporter-console:test
  ./gradlew :vajrapulse-worker:test
  ./gradlew :http-load-test:build  # Should still work after adding console dep
  ```

- [ ] **Commit breaking cleanup**
  ```bash
  git add -A
  git commit -m "BREAKING: remove console exporter from worker (pre-1.0 clean architecture)"
  ```

**Result:** Worker JAR shrinks from ~1.5 MB → ~700 KB! 🎉

---

### 🔧 Phase 1: OpenTelemetry Exporter (1 week)

#### 1.1 Module Structure
```bash
mkdir -p vajrapulse-exporter-opentelemetry/src/main/java/com/vajrapulse/exporter/otel
mkdir -p vajrapulse-exporter-opentelemetry/src/test/groovy/com/vajrapulse/exporter/otel
touch vajrapulse-exporter-opentelemetry/build.gradle.kts
touch vajrapulse-exporter-opentelemetry/README.md
```

#### 1.2 Build File
```gradle
// vajrapulse-exporter-opentelemetry/build.gradle.kts
dependencies {
    api(project(":vajrapulse-core"))
    
    implementation("io.opentelemetry:opentelemetry-api:1.32.0")
    implementation("io.opentelemetry:opentelemetry-sdk:1.32.0")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.32.0")
    implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.32.0")
    
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

#### 1.3 Update settings.gradle.kts
```gradle
include("vajrapulse-exporter-opentelemetry")
```

#### 1.4 Implementation Files

**Main exporter:**
- `OpenTelemetryExporter.java` - Core exporter implementation
- `OtelConfiguration.java` - Configuration builder
- `MetricsConverter.java` - Convert AggregatedMetrics → OTLP format

**SPI (optional):**
- `OtelExporterProvider.java` - Service provider
- `META-INF/services/com.vajrapulse.core.metrics.MetricsExporterProvider`

**Tests:**
- `OpenTelemetryExporterSpec.groovy` - Unit tests
- `OtelIntegrationSpec.groovy` - Integration tests with mock collector

**Documentation:**
- `README.md` - Setup guide, examples, configuration reference

---

### 🔧 Phase 2: BlazeMeter Exporter (1 week)

#### 2.1 Module Structure
```bash
mkdir -p vajrapulse-exporter-blazemeter/src/main/java/com/vajrapulse/exporter/blazemeter
mkdir -p vajrapulse-exporter-blazemeter/src/test/groovy/com/vajrapulse/exporter/blazemeter
touch vajrapulse-exporter-blazemeter/build.gradle.kts
touch vajrapulse-exporter-blazemeter/README.md
```

#### 2.2 Build File
```gradle
// vajrapulse-exporter-blazemeter/build.gradle.kts
dependencies {
    api(project(":vajrapulse-core"))
    
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

#### 2.3 Update settings.gradle.kts
```gradle
include("vajrapulse-exporter-blazemeter")
```

#### 2.4 Implementation Files

**Main exporter:**
- `BlazeMeterExporter.java` - Core exporter
- `BlazeMeterConfig.java` - Configuration
- `BlazeMeterPayload.java` - JSON payload model

**SPI (optional):**
- `BlazeMeterExporterProvider.java`
- `META-INF/services/...`

**Tests:**
- `BlazeMeterExporterSpec.groovy`
- `BlazeMeterApiSpec.groovy` - Mock API tests

**Documentation:**
- `README.md` - API setup, authentication, examples

---

### 🔧 Phase 3: Service Provider Interface (Optional, 2 days)

#### 3.1 Define SPI in Core

**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/MetricsExporterProvider.java`
```java
public interface MetricsExporterProvider {
    String name();
    MetricsExporter create(Map<String, String> config);
    default String description() { return ""; }
}
```

#### 3.2 Create Factory

**File:** `vajrapulse-core/src/main/java/com/vajrapulse/core/metrics/ExporterFactory.java`
```java
public final class ExporterFactory {
    public static List<String> available() {
        // List all registered providers
    }
    
    public static MetricsExporter create(String name, Map<String, String> config) {
        // ServiceLoader discovery
    }
}
```

#### 3.3 Update Each Exporter

Add provider implementation + META-INF/services registration for:
- Console
- OpenTelemetry
- BlazeMeter

#### 3.4 CLI Integration

Update `VajraPulseWorker.java` to support:
```bash
--exporter console
--exporter opentelemetry --otel-endpoint http://localhost:4318
--exporter blazemeter --bz-api-key xxx --bz-test-id yyy
```

---

## 📊 Comparison Table (Pre-1.0 Breaking Changes)

| Feature | Current (0.9.0) | After Phase 0 | After Phase 1 | After Phase 2 | After Phase 3 |
|---------|-----------------|---------------|---------------|---------------|---------------|
| **Modules** | 4 | 4 | 5 | 6 | 6 |
| **Exporters** | 1 (console) | 1 (console) | 2 (+OTLP) | 3 (+BlazeMeter) | 3 |
| **Duplicate Interface** | ❌ Yes | ✅ No | ✅ No | ✅ No | ✅ No |
| **Pluggable** | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes |
| **Console Bundled?** | ✅ Yes | 🔥 NO (breaking) | ❌ No | ❌ No | ❌ No |
| **Auto-discovery** | ❌ No | ❌ No | ❌ No | ❌ No | ✅ Yes |
| **Worker JAR Size** | ~1.5 MB | 🎉 ~700 KB | ~700 KB | ~700 KB | ~700 KB |
| **Console Size** | (bundled) | +50 KB (optional) | +50 KB | +50 KB | +50 KB |
| **Optional Exporters** | N/A | N/A | +2.5 MB | +2 MB | +varies |

---

## 🚀 Quick Start (Pre-1.0 Design - All Exporters Optional!)

### Scenario 1: Console Only
```gradle
dependencies {
    implementation("com.vajrapulse:vajrapulse-worker:0.9.0")
    implementation("com.vajrapulse:vajrapulse-exporter-console:0.9.0")  // NOW REQUIRED!
}
```

```java
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;  // Explicit import

MetricsPipeline.builder()
    .addExporter(new ConsoleMetricsExporter())  // Must add explicitly
    .build()
    .run(task, loadPattern);
```

### Scenario 2: Add OpenTelemetry
```gradle
dependencies {
    implementation("com.vajrapulse:vajrapulse-worker:0.9.0")
    implementation("com.vajrapulse:vajrapulse-exporter-console:0.9.0")
    implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry:0.9.0")
}
```

```java
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import com.vajrapulse.exporter.otel.OpenTelemetryExporter;

MetricsExporter otel = new OpenTelemetryExporter(
    "http://localhost:4318/v1/metrics",
    "my-load-test"
);

MetricsPipeline.builder()
    .addExporter(new ConsoleMetricsExporter())
    .addExporter(otel)
    .withPeriodic(Duration.ofSeconds(10))
    .build()
    .run(task, loadPattern);
```

### Scenario 3: All Three Exporters
```gradle
dependencies {
    implementation("com.vajrapulse:vajrapulse-worker:0.9.0")
    implementation("com.vajrapulse:vajrapulse-exporter-console:0.9.0")
    implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry:0.9.0")
    implementation("com.vajrapulse:vajrapulse-exporter-blazemeter:0.9.0")
}
```

```java
import com.vajrapulse.exporter.console.ConsoleMetricsExporter;
import com.vajrapulse.exporter.otel.OpenTelemetryExporter;
import com.vajrapulse.exporter.blazemeter.BlazeMeterExporter;

MetricsPipeline.builder()
    .addExporter(new ConsoleMetricsExporter())
    .addExporter(new OpenTelemetryExporter("http://...", "service"))
    .addExporter(new BlazeMeterExporter("api-key", "test-id"))
    .withPeriodic(Duration.ofSeconds(5))
    .build()
    .run(task, loadPattern);
```

### Scenario 4: No Console (Pure OTLP)
```gradle
dependencies {
    implementation("com.vajrapulse:vajrapulse-worker:0.9.0")
    // NO console dependency!
    implementation("com.vajrapulse:vajrapulse-exporter-opentelemetry:0.9.0")
}
```

```java
import com.vajrapulse.exporter.otel.OpenTelemetryExporter;

MetricsPipeline.builder()
    .addExporter(new OpenTelemetryExporter("http://...", "service"))
    .withPeriodic(Duration.ofSeconds(10))
    .build()
    .run(task, loadPattern);
```

---

## 🎓 Key Takeaways (Pre-1.0 Clean Design)

### Design Principles ✅
1. **Interface in core, implementations separate** ✅
2. **PeriodicMetricsReporter accepts any exporter** ✅
3. **MetricsPipeline supports multiple exporters** ✅
4. **Each exporter = separate artifact** ✅
5. **🔥 ALL exporters are optional (including console!)** ✅
6. **🔥 Breaking changes are GOOD pre-1.0** ✅

### Module Responsibilities
```
vajrapulse-core:         Interface + Core logic (NO exporters)
vajrapulse-exporter-*:   Implementation only (ALL optional)
vajrapulse-worker:       Orchestration + CLI (NO exporters bundled)
```

### Dependency Flow (Pre-1.0 Clean Architecture)
```
User Project
  └── vajrapulse-worker (~700 KB, NO exporters!)
       └── vajrapulse-core (interface)
       
User MUST explicitly add at least one exporter:
  └── vajrapulse-exporter-console (most common)
  OR
  └── vajrapulse-exporter-opentelemetry
  OR
  └── vajrapulse-exporter-blazemeter
  OR
  └── (any combination of the above)
```

---

## 📝 Next Actions (In Order)

1. **Review** this plan with the team
2. **Delete** duplicate MetricsExporter.java (Phase 0)
3. **Verify** all tests pass
4. **Decide** whether to implement OTLP exporter (Phase 1)
5. **Decide** whether to implement BlazeMeter exporter (Phase 2)
6. **Decide** whether to add SPI (Phase 3)
7. **Document** usage examples
8. **Release** new versions

---

## 📚 Reference Documents

- **Full Architecture Plan**: `EXPORTER_ARCHITECTURE_PLAN.md` (detailed 350+ lines)
- **Quick Reference**: `EXPORTER_QUICK_REFERENCE.md` (summary with examples)
- **This Document**: `EXPORTER_ACTION_ITEMS.md` (current state + todos)

---

## ⚠️ Important Notes (Pre-1.0 Freedom!)

### 🔥 Breaking Changes Are Expected!
- **Console is NO LONGER bundled** - users must explicitly add it
- **Existing code WILL break** - Phase 0 removes console from worker
- **This is GOOD** - cleaner architecture before 1.0 release
- **No migration path needed** - just update build.gradle and add console import

### Independent Versioning (All Pre-1.0)
- Core: v0.9.x
- Console exporter: v0.9.x (optional, not bundled)
- OTLP exporter: v0.9.x (independent release)
- BlazeMeter exporter: v0.9.x (independent release)

### Performance Considerations
- Console: ~1ms export latency (no overhead)
- OTLP: ~10ms sync / ~2ms async (consider AsyncMetricsExporter wrapper)
- BlazeMeter: ~15ms sync / ~3ms async (consider AsyncMetricsExporter wrapper)

### Why Breaking Changes Now?
- ✅ No released 1.0 yet - perfect time to fix architecture!
- ✅ Worker JAR shrinks by 50% (~1.5 MB → ~700 KB)
- ✅ Establishes true plugin architecture from the start
- ✅ Makes dependencies explicit (no hidden bundled code)
- ✅ Easier to test worker in isolation

---

## 🎯 Next Actions (Pre-1.0 Breaking Changes)

1. **✅ Review** this plan - clean design confirmed
2. **🔥 Delete duplicate interface** (Phase 0 step 1)
3. **🔥 Remove console from worker deps** (Phase 0 step 2) - BREAKING!
4. **🔥 Update examples** to add console explicitly (Phase 0 step 3)
5. **✅ Verify** all tests pass after breaking changes
6. **📝 Document** breaking changes in CHANGELOG or migration guide
7. **🚀 Commit** with clear "BREAKING" message
8. **Decide** whether to implement OTLP exporter (Phase 1)
9. **Decide** whether to implement BlazeMeter exporter (Phase 2)
10. **Decide** whether to add SPI (Phase 3)

---

**Status:** Ready to implement Phase 0 (breaking cleanup) immediately! 🔥

**Pre-1.0 Win:** Clean architecture established BEFORE first release! 🎉
