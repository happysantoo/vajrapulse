# VajraPulse: Roadmap to 1.0 Release

## 🎯 Current Status (Pre-1.0)

**Version**: 0.9.0  
**Branch**: `init`  
**Philosophy**: Breaking changes acceptable - clean code over compatibility

### ✅ Completed (Phase 1 Foundation)

#### Core Modules
- ✅ `vajrapulse-api` - Zero-dependency Task SDK (15 KB)
- ✅ `vajrapulse-core` - Execution engine with Micrometer (200 KB)
- ✅ `vajrapulse-exporter-console` - Console output (30 KB)
- ✅ `vajrapulse-worker` - CLI application (1.6 MB fat JAR)

#### Features Implemented
- ✅ Task interface with lifecycle (setup/execute/cleanup)
- ✅ Sealed TaskResult (Success/Failure)
- ✅ Virtual threads for I/O-bound tasks
- ✅ Platform threads for CPU-bound tasks (@PlatformThreads annotation)
- ✅ Three load patterns: Static, Ramp-up, Ramp-sustain
- ✅ Micrometer-based metrics collection
- ✅ Percentile normalization (3 decimals, sorted)
- ✅ **MetricsPipeline** - High-level builder API
- ✅ **Pluggable MetricsExporter** interface
- ✅ **PeriodicMetricsReporter** - Live metrics updates
- ✅ Immediate live snapshot option
- ✅ Configurable SLO buckets & percentiles
- ✅ Builder validation (prevents misconfiguration)

#### Quality & Testing
- ✅ 23 Spock tests (100% passing)
- ✅ Gradle 9 build system
- ✅ Java 21 features (records, sealed types, pattern matching)
- ✅ Comprehensive documentation
- ✅ Working HTTP load test example

### 📋 Architecture Documents Created
- ✅ PHASE1_IMPLEMENTATION_PLAN.md
- ✅ PHASE1_COMPLETE.md
- ✅ DESIGN.md
- ✅ IMPLEMENTATION_UPDATES.md
- ✅ EXPORTER_ARCHITECTURE_PLAN.md (Pre-1.0 clean design)
- ✅ EXPORTER_QUICK_REFERENCE.md
- ✅ EXPORTER_ACTION_ITEMS.md
- ✅ PRE_1.0_BREAKING_CHANGES.md
- ✅ Copilot instructions updated with pre-1.0 philosophy

---

## 🚀 Path to 1.0 Release

### Guiding Principles for Pre-1.0 Work

1. **Clean Architecture First** - Break things if it makes code cleaner
2. **Establish Patterns** - Set standards that will last post-1.0
3. **Minimize Dependencies** - Keep JARs lean
4. **Plugin Everything** - Exporters, strategies, protocols
5. **Document Thoroughly** - Examples, guides, API docs
6. **Test Rigorously** - 100% coverage of core logic
7. **Performance Matters** - Virtual threads, lock-free structures

---

## 📅 Release Plan

### Phase 0: Pre-1.0 Clean Sweep (CURRENT) ⏳

**Status**: In Progress  
**Duration**: 1-2 days  
**Branch**: `init` → `pre-1.0-cleanup`

#### Goals
- 🔥 Remove all cruft and establish clean foundation
- 🔥 Fix architectural issues while we can break things
- 🔥 Establish plugin patterns for exporters

#### Tasks

**A. Breaking Changes (Accepted Pre-1.0)**

- [ ] **Delete duplicate MetricsExporter interface**
  ```bash
  rm vajrapulse-exporter-console/src/main/java/com/vajrapulse/exporter/console/MetricsExporter.java
  ```
  
- [ ] **Remove console from worker dependencies** 🔥
  - Update `vajrapulse-worker/build.gradle.kts`
  - Remove `api(project(":vajrapulse-exporter-console"))`
  - Worker JAR: 1.5 MB → 700 KB (50% reduction!)
  
- [ ] **Update all examples to add console explicitly**
  - `examples/http-load-test/build.gradle.kts`
  - Add `implementation(project(":vajrapulse-exporter-console"))`
  - Add explicit import in code
  
- [ ] **Run full test suite**
  - Fix any breaks from console removal
  - Verify all tests pass
  - Check example runs successfully

**B. Documentation Updates**

- [ ] Update README.md with breaking changes
- [ ] Add migration guide (trivial: 1 dep + 1 import)
- [ ] Update CHANGELOG with 0.9.x → 1.0.0 breaking changes

**Outcome**: Clean plugin architecture established ✅

---

### Phase 1: Enhanced Exporters 📊

**Status**: Next  
**Duration**: 1 week  
**Target Version**: 0.10.0

#### Goals
- Implement OpenTelemetry exporter
- Implement BlazeMeter exporter (optional)
- Establish exporter plugin pattern
- Service Provider Interface (optional)

#### Module 1: OpenTelemetry Exporter

**Dependencies**:
```gradle
implementation("io.opentelemetry:opentelemetry-api:1.32.0")
implementation("io.opentelemetry:opentelemetry-sdk:1.32.0")
implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.32.0")
implementation("io.opentelemetry:opentelemetry-sdk-metrics:1.32.0")
```
**Size**: ~2.5 MB

**Tasks**:
- [ ] Create `vajrapulse-exporter-opentelemetry` module
- [ ] Implement `OpenTelemetryExporter`
- [ ] Configuration builder (endpoint, service name, headers)
- [ ] Convert `AggregatedMetrics` → OTLP format
- [ ] Add Spock tests with mock OTLP collector
- [ ] Create README with configuration examples
- [ ] Integration test with real collector (Docker Compose)

**Example Usage**:
```java
MetricsExporter otlp = new OpenTelemetryExporter(
    "http://localhost:4318/v1/metrics",
    "my-load-test"
);

MetricsPipeline.builder()
    .addExporter(new ConsoleMetricsExporter())
    .addExporter(otlp)
    .withPeriodic(Duration.ofSeconds(10))
    .build()
    .run(task, loadPattern);
```

#### Module 2: BlazeMeter Exporter (Optional)

**Dependencies**:
```gradle
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
```
**Size**: ~2 MB

**Tasks**:
- [ ] Create `vajrapulse-exporter-blazemeter` module
- [ ] Implement `BlazeMeterExporter`
- [ ] Configuration (API key, test ID, session ID)
- [ ] Convert `AggregatedMetrics` → BlazeMeter JSON
- [ ] Add Spock tests with MockWebServer
- [ ] Create README with API setup guide

#### Module 3: Service Provider Interface (Optional)

**Tasks**:
- [ ] Define `MetricsExporterProvider` in core
- [ ] Implement provider in console exporter
- [ ] Implement provider in OTLP exporter
- [ ] Implement provider in BlazeMeter exporter
- [ ] Create `ExporterFactory` with ServiceLoader
- [ ] Add CLI integration for dynamic loading

**Outcome**: Professional metrics export capabilities ✅

---

### Phase 2: Additional Examples & Documentation 📚

**Status**: Parallel with Phase 1  
**Duration**: 3 days  
**Target Version**: 0.10.0

#### Goals
- Complete example suite
- Comprehensive user documentation
- API documentation (JavaDoc)

#### Tasks

**A. Complete Examples**

- [ ] **Database Load Test Example**
  - Virtual threads with JDBC
  - HikariCP connection pool
  - PostgreSQL or H2 database
  - README with setup instructions
  
- [ ] **CPU-Bound Test Example**
  - Platform threads with `@PlatformThreads`
  - Encryption/compression workload
  - Demonstrates CPU vs I/O thread strategy
  
- [ ] **gRPC Load Test Example**
  - Virtual threads
  - gRPC client configuration
  - Unary and streaming RPCs
  
- [ ] **Multi-Exporter Example**
  - Console + OTLP simultaneously
  - Demonstrates exporter composition

**B. Documentation**

- [ ] **User Guide** (`USER_GUIDE.md`)
  - Getting started
  - Writing your first task
  - Load patterns explained
  - Metrics exporters guide
  - Thread strategy selection
  - Common patterns & best practices
  
- [ ] **API Documentation**
  - JavaDoc for all public APIs
  - `./gradlew javadoc`
  - Host on GitHub Pages
  
- [ ] **Integration Guide** (`INTEGRATION_GUIDE.md`)
  - CI/CD integration examples
  - Docker deployment
  - Kubernetes deployment
  - Grafana dashboard setup
  
- [ ] **Custom Exporter Guide** (`CUSTOM_EXPORTER_GUIDE.md`)
  - How to implement MetricsExporter
  - Service Provider Interface
  - Publishing to Maven Central

**C. Code Quality**

- [ ] Increase test coverage to 100%
- [ ] Add mutation testing (Pitest)
- [ ] Static analysis (SpotBugs, ErrorProne)
- [ ] Code formatting (Spotless)

**Outcome**: Professional documentation & examples ✅

---

### Phase 3: Performance & Optimization ⚡

**Status**: After Phase 1 & 2  
**Duration**: 1 week  
**Target Version**: 0.11.0

#### Goals
- Benchmark and optimize hot paths
- Reduce memory allocations
- Lock-free data structures
- Performance regression tests

#### Tasks

**A. Benchmarking**

- [ ] JMH benchmarks for core components
  - TaskExecutor overhead
  - MetricsCollector performance
  - RateController precision
  - Load pattern calculation
  
- [ ] Baseline measurements
  - Throughput: Target 100k+ TPS per worker
  - Latency overhead: < 1ms p99
  - Memory: < 100 MB baseline
  - Virtual threads: 1M+ concurrent

**B. Optimizations**

- [ ] Replace synchronized with lock-free structures
  - `LongAdder` for counters
  - `ConcurrentHashMap` where appropriate
  - Atomic references for state
  
- [ ] Minimize object allocations in hot paths
  - Reuse buffers
  - Object pools for high-frequency objects
  - Primitive specializations
  
- [ ] Optimize Micrometer usage
  - Pre-size histograms correctly
  - Batch metric updates
  - Async export to avoid blocking

**C. Performance Tests**

- [ ] Add performance regression tests
- [ ] Continuous benchmarking in CI
- [ ] Memory leak detection
- [ ] Virtual thread pinning detection

**Outcome**: Production-grade performance ✅

---

### Phase 4: Stabilization & Polish 💎

**Status**: After Phase 3  
**Duration**: 1 week  
**Target Version**: 0.12.0 (Release Candidate)

#### Goals
- Fix all known bugs
- Complete feature freeze
- Comprehensive testing
- Final API review

#### Tasks

**A. Bug Fixes & Edge Cases**

- [ ] Review all GitHub issues
- [ ] Fix any outstanding bugs
- [ ] Handle edge cases:
  - Zero TPS
  - Very short durations
  - Task exceptions in setup/cleanup
  - Exporter failures
  
- [ ] Graceful degradation
  - Continue on exporter failure
  - Circuit breaker for failing exporters
  - Fallback to console on error

**B. API Review**

- [ ] Review all public interfaces
- [ ] Ensure consistency
- [ ] Remove deprecated code (none in pre-1.0!)
- [ ] Finalize naming conventions
- [ ] Ensure backward compatibility post-1.0

**C. Testing**

- [ ] Full integration testing
- [ ] Multi-platform testing (Linux, macOS, Windows)
- [ ] Different JDK vendors (Temurin, GraalVM, etc.)
- [ ] Load testing the load tester (meta!)
  - 1M+ virtual threads
  - 100k+ TPS sustained
  - Multi-hour runs

**D. Security**

- [ ] Dependency vulnerability scan
- [ ] Update all dependencies to latest
- [ ] Security best practices review
- [ ] No hardcoded credentials in examples

**E. Packaging**

- [ ] Maven Central publication setup
- [ ] GPG signing for artifacts
- [ ] Checksums and signatures
- [ ] Docker images published
  - `vajrapulse/worker:1.0.0`
  - `vajrapulse/worker:latest`

**Outcome**: Release candidate ready ✅

---

### Phase 5: Release 1.0.0 🎉

**Status**: Final  
**Duration**: 2-3 days  
**Target Date**: TBD

#### Goals
- Official 1.0.0 release
- Public announcement
- Marketing materials
- Community building

#### Tasks

**A. Release Preparation**

- [ ] Final version number update (1.0.0)
- [ ] CHANGELOG complete
- [ ] Release notes written
- [ ] All documentation updated
- [ ] GitHub release created
- [ ] Tag `v1.0.0` in git

**B. Artifacts**

- [ ] Publish to Maven Central
  ```gradle
  com.vajrapulse:vajrapulse-api:1.0.0
  com.vajrapulse:vajrapulse-core:1.0.0
  com.vajrapulse:vajrapulse-exporter-console:1.0.0
  com.vajrapulse:vajrapulse-exporter-opentelemetry:1.0.0
  com.vajrapulse:vajrapulse-worker:1.0.0
  ```
  
- [ ] Docker Hub publication
  ```
  docker pull vajrapulse/worker:1.0.0
  docker pull vajrapulse/worker:latest
  ```
  
- [ ] GitHub Releases
  - Fat JAR download
  - Native binaries (optional)
  - Source archives

**C. Announcement**

- [ ] Blog post / announcement
- [ ] Twitter/social media
- [ ] Reddit (r/java, r/programming)
- [ ] Hacker News submission
- [ ] Java Weekly newsletter

**D. Community**

- [ ] GitHub Discussions enabled
- [ ] Contributing guide
- [ ] Code of conduct
- [ ] Issue templates
- [ ] PR templates
- [ ] First-timer friendly issues labeled

**Outcome**: VajraPulse 1.0.0 released! 🚀

---

## 📊 Feature Matrix: Pre-1.0 vs 1.0 vs Post-1.0

| Feature | Pre-1.0 (Current) | 1.0.0 Target | Post-1.0 Future |
|---------|-------------------|--------------|-----------------|
| **Core Task SDK** | ✅ Complete | ✅ Stable API | Backward compatible |
| **Virtual Threads** | ✅ Working | ✅ Production-ready | Enhanced monitoring |
| **Platform Threads** | ✅ Working | ✅ Production-ready | Auto-detection |
| **Load Patterns** | ✅ 3 patterns | ✅ Validated | Custom plugins |
| **Metrics Pipeline** | ✅ Complete | ✅ Optimized | Real-time streaming |
| **Console Exporter** | ✅ Working | ✅ Polished | Color output |
| **OTLP Exporter** | ❌ Not started | ✅ Complete | Enhanced traces |
| **BlazeMeter Exporter** | ❌ Not started | ⚠️ Optional | Full integration |
| **Examples** | ✅ HTTP only | ✅ Full suite | Community examples |
| **Documentation** | ✅ Good | ✅ Excellent | Video tutorials |
| **Performance** | ✅ Good | ✅ Benchmarked | Continuously optimized |
| **Standalone Mode** | ✅ Working | ✅ Production | - |
| **Orchestrated Mode** | ❌ Not started | ❌ Post-1.0 | Phase 6 |
| **P2P Mode** | ❌ Not started | ❌ Post-1.0 | Phase 7 |
| **Web UI** | ❌ Not started | ❌ Post-1.0 | Phase 8 |
| **Native Compilation** | ❌ Not started | ❌ Post-1.0 | Phase 9 |

---

## 🎯 Success Criteria for 1.0.0

### Technical
- ✅ 100% test coverage of core logic
- ✅ Zero compiler warnings
- ✅ No critical bugs
- ✅ Performance benchmarks met:
  - 100k+ TPS per worker
  - < 1ms instrumentation overhead
  - 1M+ virtual threads sustained
  - < 100 MB baseline memory

### Documentation
- ✅ Complete API documentation (JavaDoc)
- ✅ User guide with examples
- ✅ Integration guide
- ✅ Custom exporter guide
- ✅ Migration guide (pre-1.0 → 1.0)

### Packaging
- ✅ Published to Maven Central
- ✅ Docker images on Docker Hub
- ✅ GitHub release with binaries
- ✅ Versioned documentation

### Quality
- ✅ All examples working
- ✅ Multi-platform tested
- ✅ Security review complete
- ✅ Dependencies up-to-date

---

## 🗓️ Timeline Estimate

| Phase | Duration | Start | End | Deliverable |
|-------|----------|-------|-----|-------------|
| **Phase 0: Clean Sweep** | 2 days | Week 1 | Week 1 | 0.9.1 (breaking) |
| **Phase 1: Exporters** | 1 week | Week 1 | Week 2 | 0.10.0 |
| **Phase 2: Docs & Examples** | 3 days | Week 1 | Week 2 | 0.10.0 |
| **Phase 3: Performance** | 1 week | Week 2 | Week 3 | 0.11.0 |
| **Phase 4: Stabilization** | 1 week | Week 3 | Week 4 | 0.12.0-RC1 |
| **Phase 5: Release** | 2-3 days | Week 4 | Week 4 | **1.0.0** 🎉 |
| **Total** | **~4 weeks** | - | - | - |

**Estimated 1.0.0 Release**: 4-5 weeks from now

---

## 🚧 Post-1.0 Roadmap (Future Phases)

### Phase 6: Distributed Orchestration (1.1.0)

**Status**: Design complete, implementation planned post-1.0  
**Design Document**: `documents/architecture/DISTRIBUTED_TESTING_DESIGN.md`

**Approach**: Leverage existing platforms (Kubernetes, BlazeMeter) rather than building custom orchestrator.
- Orchestrator component
- Load distribution engine
- Worker registration & discovery
- Centralized metrics aggregation
- Web UI dashboard

### Phase 7: Peer-to-Peer Mode (1.2.0)
- Leader election
- Gossip protocol
- Decentralized coordination
- Auto-scaling support

### Phase 8: Advanced Features (1.3.0)
- Custom load pattern plugins
- Assertions framework
- Data-driven testing
- Test scenarios (multi-stage)

### Phase 9: Native Compilation (1.4.0)
- GraalVM native image
- Platform-specific optimizations
- Startup time < 50ms
- Memory footprint < 30 MB

---

## 📋 Decision Log

### Pre-1.0 Decisions Made

1. **Pure Separation for Exporters** ✅
   - Decision: Remove console from worker deps (breaking)
   - Rationale: Clean plugin architecture
   - Impact: Worker JAR 50% smaller
   - Status: Accepted (pre-1.0 allows this)

2. **Micrometer over HdrHistogram** ✅
   - Decision: Use Micrometer as metrics API
   - Rationale: Industry standard, future-proof
   - Trade-off: +200 KB but more flexible
   - Status: Implemented

3. **MetricsPipeline Builder** ✅
   - Decision: High-level orchestration API
   - Rationale: Reduces boilerplate in examples
   - Status: Implemented

4. **Virtual Threads as Default** ✅
   - Decision: Default to virtual threads
   - Rationale: Most load tests are I/O-bound
   - Override: `@PlatformThreads` for CPU work
   - Status: Implemented

5. **Standalone First** ✅
   - Decision: 1.0 focuses on standalone mode
   - Rationale: Simpler, faster to ship
   - Future: Orchestrated/P2P in 1.1+
   - Status: Accepted

### Pending Decisions

1. **BlazeMeter Exporter in 1.0?**
   - Option A: Include (more complete)
   - Option B: Post-1.0 (faster release)
   - Recommendation: Optional for 1.0, can add later
   - Status: To be decided

2. **Service Provider Interface**
   - Option A: Include (more flexible)
   - Option B: Post-1.0 (simpler for now)
   - Recommendation: Optional for 1.0
   - Status: To be decided

3. **Native Image in 1.0?**
   - Option A: Include (better UX)
   - Option B: Post-1.0 (complex)
   - Recommendation: Post-1.0 (Phase 9)
   - Status: Decided - Post-1.0

---

## 🎓 Lessons Learned (Pre-1.0)

### What Went Well
- ✅ Clean architecture from start
- ✅ Comprehensive design documentation
- ✅ Minimal dependencies kept JAR small
- ✅ Java 21 features properly utilized
- ✅ Spock tests are expressive
- ✅ MetricsPipeline API is ergonomic

### What Could Be Better
- ⚠️ Should have removed console from worker sooner
- ⚠️ Duplicate interface caught late
- ⚠️ Could use more integration tests
- ⚠️ Performance benchmarks should be automated

### What We'll Do Differently
- ✅ Establish plugin patterns earlier
- ✅ Performance benchmarks from day 1
- ✅ More frequent integration testing
- ✅ Continuous documentation updates

---

## 📞 Next Steps (Immediate)

### This Week

1. **Phase 0: Clean Sweep** 🔥
   - [ ] Delete duplicate MetricsExporter
   - [ ] Remove console from worker
   - [ ] Update examples
   - [ ] Fix tests
   - [ ] Commit & push to `pre-1.0-cleanup` branch

2. **Phase 1: Start OTLP Exporter**
   - [ ] Create module structure
   - [ ] Add dependencies
   - [ ] Basic implementation
   - [ ] Initial tests

3. **Phase 2: Additional Examples**
   - [ ] Database example
   - [ ] CPU-bound example

### Next Week

1. **Complete OTLP Exporter**
2. **Consider BlazeMeter Exporter**
3. **Documentation Sprint**
4. **Performance Benchmarks**

---

## 🎯 Commitment to 1.0

**Target**: Ship 1.0.0 within 4-5 weeks  
**Focus**: Quality over speed  
**Promise**: No rushed release - it's ready when it's ready  
**Post-1.0**: Maintain backward compatibility  

---

**Let's build something awesome! 🚀**
