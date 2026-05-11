# VajraPulse Test Framework - Design Documentation

## Executive Summary

VajraPulse is a distributed load testing framework designed for high-scale performance testing with flexible deployment models. Built on **Java 21**, it leverages **virtual threads** for massive concurrency with minimal resource overhead, while also supporting platform threads for CPU-intensive workloads. The framework supports both orchestrated and peer-to-peer distributed testing modes with comprehensive metrics collection and reporting.

### Technology Stack
- **Language**: Java 21
- **Concurrency**: Virtual Threads (Project Loom) for I/O-bound tasks, Platform Threads for CPU-bound tasks
- **Build**: Maven/Gradle
- **Observability**: OpenTelemetry, Micrometer
- **Communication**: gRPC, HTTP/REST

### Product scope (1.0.0 vs future)

As of **1.0.0**, the shipping product is a **single-process** load runner (library + worker CLI) with optional exporters. Sections below that describe an **orchestrator**, **multi-worker distribution**, or **P2P coordination** reflect the **long-term architecture vision**; see [`documents/roadmap/POST_1.0_BACKLOG.md`](../roadmap/POST_1.0_BACKLOG.md) and [`CHANGELOG.md`](../../CHANGELOG.md) for what is scheduled after 1.0.0.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Core Components](#core-components)
3. [Architecture Designs](#architecture-designs)
4. [Design Alternatives](#design-alternatives)
5. [Extensibility Points](#extensibility-points)
6. [Component Details](#component-details)
7. [Deployment Scenarios](#deployment-scenarios)

---

## System Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         VAJRA TEST FRAMEWORK                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌────────────────────┐              ┌──────────────────────────┐  │
│  │   Orchestrator     │              │    Test Workers          │  │
│  │   (Optional)       │◄────────────►│    (Core Engine)         │  │
│  │                    │              │                          │  │
│  │  • Load Manager    │              │  • Task SDK              │  │
│  │  • Distribution    │              │  • Execution Engine      │  │
│  │  • UI Dashboard    │              │  • Metrics Processor     │  │
│  │  • Coordination    │              │  • P2P Coordination      │  │
│  └────────────────────┘              └──────────────────────────┘  │
│                                                                       │
│                      ┌──────────────────────┐                       │
│                      │  Metrics & Reporting  │                       │
│                      │                       │                       │
│                      │  • OpenTelemetry     │                       │
│                      │  • BlazeMeter        │                       │
│                      │  • Console           │                       │
│                      │  • Grafana           │                       │
│                      └──────────────────────┘                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Core Components

### 1. Task SDK (Worker Library)

The foundation of test execution - a lean, extensible interface for defining test scenarios.

```java
┌─────────────────────────────────────────────────────────────┐
│                        Task Interface                        │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  public interface Task {                             │   │
│  │    void setup() throws Exception;                    │   │
│  │    TaskResult execute() throws Exception;            │   │
│  │    void cleanup() throws Exception;                  │   │
│  │  }                                                    │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  public sealed interface TaskResult {                │   │
│  │                                                       │   │
│  │    record Success(Object data) implements TaskResult;│   │
│  │    record Failure(Throwable error)                   │   │
│  │                    implements TaskResult;            │   │
│  │                                                       │   │
│  │    // Helper factory methods                         │   │
│  │    static TaskResult success() { ... }               │   │
│  │    static TaskResult success(Object data) { ... }    │   │
│  │    static TaskResult failure(Throwable e) { ... }    │   │
│  │  }                                                    │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  NOTE: Latency, timestamp, and metrics are captured by       │
│        TaskExecutor - NOT part of TaskResult!                │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

**Key Design Principles:**
- **Simplicity**: Only 3 essential methods, minimal TaskResult
- **Separation of Concerns**: Task focuses on business logic; executor handles metrics
- **Java 21 Features**: Sealed interfaces, records for immutability
- **Flexibility**: Task writers control test data generation completely
- **Lifecycle Management**: Clear setup/teardown hooks

### 2. Test Worker Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        TEST WORKER (Java 21)                      │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              Configuration Manager                          │ │
│  │  • CLI Args Parser  • Config Files  • Env Variables        │ │
│  │  • Java Properties  • YAML/HOCON Support                   │ │
│  └─────────────────┬──────────────────────────────────────────┘ │
│                    │                                              │
│  ┌─────────────────▼──────────────────────────────────────────┐ │
│  │              Execution Engine                               │ │
│  │                                                              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │ │
│  │  │  Rate        │  │  Scheduler   │  │  Thread      │    │ │
│  │  │  Controller  │  │              │  │  Strategy    │    │ │
│  │  │              │  │  • Ramp-up   │  │              │    │ │
│  │  │  • Target    │  │  • Steady    │  │  • VIRTUAL   │    │ │
│  │  │    TPS       │  │  • Ramp-down │  │    (I/O)     │    │ │
│  │  │  • Duration  │  │  • Warmup    │  │  • PLATFORM  │    │ │
│  │  │  • Pacing    │  │              │  │    (CPU)     │    │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘    │ │
│  │                                                              │ │
│  │  Virtual Thread Pool (I/O-bound tasks):                    │ │
│  │    Executors.newVirtualThreadPerTaskExecutor()             │ │
│  │    • Millions of lightweight threads                        │ │
│  │    • Minimal memory footprint                               │ │
│  │    • Perfect for HTTP, DB, messaging workloads             │ │
│  │                                                              │ │
│  │  Platform Thread Pool (CPU-bound tasks):                   │ │
│  │    ForkJoinPool.commonPool() or custom ThreadPoolExecutor  │ │
│  │    • Fixed size = Runtime.availableProcessors()            │ │
│  │    • For compute-intensive operations                       │ │
│  └──────────────────────────────────────────────────────────┘ │
│                    │                                              │
│  ┌─────────────────▼──────────────────────────────────────────┐ │
│  │              Task Executor (Instrumented)                   │ │
│  │                                                              │ │
│  │  Wraps task execution with automatic instrumentation:      │ │
│  │                                                              │ │
│  │  start = System.nanoTime()                                  │ │
│  │  try {                                                       │ │
│  │    result = task.execute()  ◄── User's Task (Pure Logic)   │ │
│  │    recordSuccess(result, duration)                          │ │
│  │  } catch (Exception e) {                                    │ │
│  │    recordFailure(e, duration)                               │ │
│  │  }                                                           │ │
│  │  duration = System.nanoTime() - start                       │ │
│  │                                                              │ │
│  │  Task Writer Controls:                                      │ │
│  │  • Test data generation (no framework interference)         │ │
│  │  • Request construction                                     │ │
│  │  • Custom validation logic                                  │ │
│  │  • State management                                         │ │
│  └──────────────────────────┬───────────────────────────────────┘│
│                             │                                     │
│  ┌──────────────────────────▼──────────────────────────────────┐ │
│  │           Metrics Processor & Aggregator                    │ │
│  │                                                              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │ │
│  │  │  Collector   │  │  Aggregator  │  │  Reservoir   │    │ │
│  │  │  (Micrometer)│  │  (HdrHisto)  │  │  (Sampling)  │    │ │
│  │  │              │  │              │  │              │    │ │
│  │  │  • Results   │  │  • Count     │  │  • Histogram │    │ │
│  │  │  • Latency   │  │  • Min/Max   │  │  • P50/P95   │    │ │
│  │  │  • Success   │  │  • Mean      │  │  • P99/P999  │    │ │
│  │  │  • Errors    │  │  • StdDev    │  │  • T-Digest  │    │ │
│  │  │  • Timestamp │  │              │  │              │    │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘    │ │
│  └──────────────────────────┬───────────────────────────────────┘│
│                             │                                     │
│  ┌──────────────────────────▼──────────────────────────────────┐ │
│  │              Metrics Exporters                              │ │
│  │                                                              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │ │
│  │  │ OpenTelemetry│  │ BlazeMeter   │  │  Console     │    │ │
│  │  │              │  │              │  │              │    │ │
│  │  │  • OTLP      │  │  • HTTP API  │  │  • Stdout    │    │ │
│  │  │  • Traces    │  │  • Cloud     │  │  • Summary   │    │ │
│  │  │  • Metrics   │  │  • Sessions  │  │  • Real-time │    │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘    │ │
│  │                                                              │ │
│  │  ┌──────────────────────────────────────────────────────┐ │ │
│  │  │         Custom Exporter Plugin Interface            │ │ │
│  │  └──────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────┘ │
│                             │                                     │
│  ┌──────────────────────────▼──────────────────────────────────┐ │
│  │          Coordination Layer (Optional)                      │ │
│  │                                                              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │ │
│  │  │ Orchestrator │  │  P2P Mode    │  │  Standalone  │    │ │
│  │  │   Client     │  │              │  │    Mode      │    │ │
│  │  │              │  │  • Discovery │  │              │    │ │
│  │  │  • RPC/gRPC  │  │  • Gossip    │  │  • No Coord  │    │ │
│  │  │  • Commands  │  │  • Election  │  │  • Local     │    │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘    │ │
│  └──────────────────────────────────────────────────────────────┘│
│                                                                    │
└──────────────────────────────────────────────────────────────────┘
```

### 3. Orchestrator Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     TEST ORCHESTRATOR                             │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                   Web UI / API Gateway                      │ │
│  │                                                              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │ │
│  │  │   Test       │  │   Worker     │  │   Results    │    │ │
│  │  │   Config     │  │   Manager    │  │   Viewer     │    │ │
│  │  │   Form       │  │   Dashboard  │  │              │    │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘    │ │
│  └──────────────────────────┬───────────────────────────────────┘│
│                             │                                     │
│  ┌──────────────────────────▼──────────────────────────────────┐ │
│  │              Load Distribution Engine                       │ │
│  │                                                              │ │
│  │  ┌────────────────────────────────────────────────────┐   │ │
│  │  │  Load Calculator                                    │   │ │
│  │  │  ────────────────                                   │   │ │
│  │  │  Total TPS: 10,000                                 │   │ │
│  │  │  Available Workers: 5                              │   │ │
│  │  │  Per-Worker TPS: 10,000 / 5 = 2,000               │   │ │
│  │  │                                                     │   │ │
│  │  │  Distribution Strategy:                            │   │ │
│  │  │   • Equal Split (default)                          │   │ │
│  │  │   • Weighted (by worker capacity)                  │   │ │
│  │  │   • Custom (user-defined)                          │   │ │
│  │  └────────────────────────────────────────────────────┘   │ │
│  └──────────────────────────────────────────────────────────────┘│
│                             │                                     │
│  ┌──────────────────────────▼──────────────────────────────────┐ │
│  │              Worker Registry & Discovery                    │ │
│  │                                                              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │ │
│  │  │  Registry    │  │  Health      │  │  Capacity    │    │ │
│  │  │              │  │  Checker     │  │  Monitor     │    │ │
│  │  │  • Workers   │  │              │  │              │    │ │
│  │  │  • Status    │  │  • Heartbeat │  │  • CPU/Mem   │    │ │
│  │  │  • Metadata  │  │  • Liveness  │  │  • Network   │    │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘    │ │
│  └──────────────────────────────────────────────────────────────┘│
│                             │                                     │
│  ┌──────────────────────────▼──────────────────────────────────┐ │
│  │           Test Execution Coordinator                        │ │
│  │                                                              │ │
│  │  ┌────────────────────────────────────────────────────┐   │ │
│  │  │  State Machine                                      │   │ │
│  │  │  ────────────                                       │   │ │
│  │  │  CREATED → DISTRIBUTING → RUNNING → STOPPING       │   │ │
│  │  │             → COMPLETED / FAILED                    │   │ │
│  │  │                                                     │   │ │
│  │  │  • Synchronize Start/Stop                          │   │ │
│  │  │  • Handle Worker Failures                          │   │ │
│  │  │  • Redistribute Load on Worker Loss                │   │ │
│  │  └────────────────────────────────────────────────────┘   │ │
│  └──────────────────────────────────────────────────────────────┘│
│                             │                                     │
│  ┌──────────────────────────▼──────────────────────────────────┐ │
│  │           Communication Layer (gRPC/REST)                   │ │
│  │                                                              │ │
│  │     Orchestrator ←──────────────────────────► Workers       │ │
│  │                                                              │ │
│  │  Commands:                      Responses:                  │ │
│  │  • START_TEST                   • ACK                       │ │
│  │  • STOP_TEST                    • STATUS                    │ │
│  │  • GET_STATUS                   • METRICS                   │ │
│  │  • UPDATE_LOAD                  • ERROR                     │ │
│  └──────────────────────────────────────────────────────────────┘│
│                             │                                     │
│  ┌──────────────────────────▼──────────────────────────────────┐ │
│  │              Results Aggregation & Storage                  │ │
│  │                                                              │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │ │
│  │  │  Real-time   │  │  Historical  │  │  Export      │    │ │
│  │  │  Aggregator  │  │  Storage     │  │              │    │ │
│  │  │              │  │              │  │  • Reports   │    │ │
│  │  │  • Stream    │  │  • Database  │  │  • CSV/JSON  │    │ │
│  │  │  • Combine   │  │  • Time      │  │  • Grafana   │    │ │
│  │  │  • Display   │  │    Series    │  │              │    │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘    │ │
│  └──────────────────────────────────────────────────────────────┘│
│                                                                    │
└──────────────────────────────────────────────────────────────────┘
```

---

## Architecture Designs

### Design 1: Centralized Orchestration (Recommended for Enterprise)

```
                        ┌─────────────────────────┐
                        │   Web UI / Dashboard    │
                        │   (User Interface)      │
                        └───────────┬─────────────┘
                                    │
                                    │ Configure Test
                                    │ (10,000 TPS)
                                    ▼
                        ┌─────────────────────────┐
                        │    ORCHESTRATOR         │
                        │                         │
                        │  • Load Distributor     │
                        │  • Worker Manager       │
                        │  • Result Aggregator    │
                        └───────────┬─────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
    ┌───────────────▼──┐  ┌────────▼────────┐  ┌──▼───────────────┐
    │   WORKER 1       │  │   WORKER 2      │  │   WORKER 3...5   │
    │                  │  │                 │  │                  │
    │   2,000 TPS      │  │   2,000 TPS     │  │   2,000 TPS each │
    │                  │  │                 │  │                  │
    │  ┌────────────┐  │  │  ┌────────────┐ │  │  ┌────────────┐ │
    │  │ Task SDK   │  │  │  │ Task SDK   │ │  │  │ Task SDK   │ │
    │  ├────────────┤  │  │  ├────────────┤ │  │  ├────────────┤ │
    │  │ Metrics    │  │  │  │ Metrics    │ │  │  │ Metrics    │ │
    │  └──────┬─────┘  │  │  └──────┬─────┘ │  │  └──────┬─────┘ │
    └─────────┼────────┘  └─────────┼───────┘  └─────────┼───────┘
              │                     │                     │
              │                     │                     │
              └─────────────────────┼─────────────────────┘
                                    │
                                    ▼
                        ┌─────────────────────────┐
                        │   Metrics Backend       │
                        │                         │
                        │  • OpenTelemetry        │
                        │  • Time Series DB       │
                        │  • Grafana Dashboard    │
                        └─────────────────────────┘
```

**Advantages:**
- ✅ Centralized control and monitoring
- ✅ Simplified coordination
- ✅ Easy load redistribution
- ✅ Historical test management
- ✅ User-friendly interface

**Disadvantages:**
- ❌ Single point of failure (orchestrator)
- ❌ Additional infrastructure component
- ❌ Network overhead for coordination

**Best For:**
- Enterprise environments
- Teams with multiple test scenarios
- Need for historical analysis
- Non-technical users

---

### Design 2: Peer-to-Peer Coordination (Cloud-Native)

```
┌────────────────────────────────────────────────────────────────┐
│                    Service Discovery                            │
│                 (Consul / etcd / K8s Service)                   │
└──────┬─────────────────────┬─────────────────────┬─────────────┘
       │                     │                     │
       │   Register/         │   Heartbeat/        │   Coordination
       │   Discover          │   Health            │   Events
       │                     │                     │
┌──────▼─────────┐   ┌───────▼────────┐   ┌───────▼────────┐
│   WORKER 1     │◄──┤   WORKER 2     │◄──┤   WORKER 3     │
│   (Leader)     │──►│                │──►│                │
│                │   │                │   │                │
│  • Gossip      │   │  • Sync        │   │  • Coordinate  │
│  • Election    │   │  • Share       │   │  • Execute     │
│  • Distribute  │   │    State       │   │                │
│                │   │                │   │                │
│  ┌──────────┐ │   │  ┌──────────┐  │   │  ┌──────────┐  │
│  │ Task SDK │ │   │  │ Task SDK │  │   │  │ Task SDK │  │
│  └────┬─────┘ │   │  └────┬─────┘  │   │  └────┬─────┘  │
└───────┼───────┘   └───────┼────────┘   └───────┼────────┘
        │                   │                    │
        └───────────────────┼────────────────────┘
                            │
                            ▼
              ┌──────────────────────────┐
              │  Shared Metrics Store    │
              │                          │
              │  • OpenTelemetry         │
              │  • Prometheus            │
              │  • Grafana               │
              └──────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│            Leader Election & Coordination Protocol             │
│                                                                 │
│  1. Workers discover each other via service registry          │
│  2. Leader election (Raft/Paxos/Bully algorithm)              │
│  3. Leader calculates load distribution                        │
│  4. Gossip protocol broadcasts configuration                   │
│  5. All workers execute in sync                                │
│  6. Metrics push to shared backend                             │
└────────────────────────────────────────────────────────────────┘
```

**Advantages:**
- ✅ No single point of failure
- ✅ Auto-scaling friendly
- ✅ Cloud-native architecture
- ✅ Self-healing capabilities
- ✅ Kubernetes-ready

**Disadvantages:**
- ❌ Complex coordination logic
- ❌ Split-brain scenarios possible
- ❌ Requires service discovery infrastructure
- ❌ Debugging challenges

**Best For:**
- Cloud deployments
- Kubernetes environments
- Auto-scaling requirements
- DevOps teams

---

### Design 3: Standalone Mode (Simplest)

```
    ┌─────────────────────────────────────┐
    │        Command Line Interface        │
    │                                      │
    │  $ vajrapulse-worker run \               │
    │      --tps 5000 \                   │
    │      --duration 300s \              │
    │      --ramp-up 60s \                │
    │      --task my_test.py              │
    └──────────────────┬──────────────────┘
                       │
                       ▼
           ┌──────────────────────┐
           │   STANDALONE WORKER   │
           │                       │
           │  ┌─────────────────┐ │
           │  │  Task SDK       │ │
           │  │  • execute()    │ │
           │  │  • setup()      │ │
           │  │  • cleanup()    │ │
           │  └────────┬────────┘ │
           │           │           │
           │  ┌────────▼────────┐ │
           │  │  Load Generator │ │
           │  │  • 5000 TPS     │ │
           │  │  • Ramp up      │ │
           │  └────────┬────────┘ │
           │           │           │
           │  ┌────────▼────────┐ │
           │  │ Metrics Processor│ │
           │  └────────┬────────┘ │
           └───────────┼──────────┘
                       │
           ┌───────────┼───────────┐
           │           │           │
     ┌─────▼─────┐ ┌──▼──────┐ ┌─▼────────┐
     │ Console   │ │  OTLP   │ │BlazeMeter│
     │ Output    │ │ Export  │ │  Export  │
     └───────────┘ └─────────┘ └──────────┘
```

**Advantages:**
- ✅ Extremely simple to use
- ✅ No infrastructure dependencies
- ✅ Quick testing and validation
- ✅ Easy CI/CD integration
- ✅ Low resource overhead

**Disadvantages:**
- ❌ Limited scale (single worker)
- ❌ No distributed coordination
- ❌ Manual aggregation for multi-worker

**Best For:**
- Development testing
- CI/CD pipelines
- Quick validations
- Small-scale tests

---

## Design Alternatives

### Alternative 1: Hybrid Architecture

Combines orchestrator with P2P fallback:

```
                    ┌──────────────────┐
                    │  ORCHESTRATOR    │
                    │  (Primary Mode)  │
                    └────────┬─────────┘
                             │
                 ┌───────────┼───────────┐
                 │           │           │
         ┌───────▼──┐  ┌─────▼────┐  ┌──▼───────┐
         │ Worker 1 │  │ Worker 2 │  │ Worker 3 │
         └────┬─────┘  └─────┬────┘  └─────┬────┘
              │              │             │
              └──────────────┼─────────────┘
                             │
                    P2P Gossip Protocol
                    (Fallback if orchestrator fails)
```

**Key Features:**
- Orchestrator manages when healthy
- Workers detect orchestrator failure
- Automatic P2P mode activation
- Seamless failover

---

### Alternative 2: Lambda/Serverless Architecture

```
┌────────────────────────────────────────────────┐
│            API Gateway / Event Bridge           │
└────────────────┬───────────────────────────────┘
                 │
     ┌───────────┼───────────┐
     │           │           │
┌────▼────┐ ┌────▼────┐ ┌───▼─────┐
│Lambda 1 │ │Lambda 2 │ │Lambda N │  (Auto-scaled)
│ Worker  │ │ Worker  │ │ Worker  │
└────┬────┘ └────┬────┘ └────┬────┘
     │           │           │
     └───────────┼───────────┘
                 │
       ┌─────────▼──────────┐
       │  CloudWatch/X-Ray  │
       │  OpenTelemetry     │
       └────────────────────┘
```

**Benefits:**
- Infinite scalability
- Pay-per-use
- No infrastructure management

**Limitations:**
- Cold start latency
- Execution time limits
- Cost at high scale

---

### Alternative 3: Plugin-Based Modular Design

```
┌────────────────────────────────────────────────────────┐
│                    VAJRA CORE                          │
│                   (Minimal Runtime)                    │
└───────────┬────────────────────────────────────────────┘
            │
            │  Plugin Registry & Loader
            │
    ┌───────┼───────┬───────────┬──────────┐
    │       │       │           │          │
┌───▼───┐ ┌▼────┐ ┌▼────────┐ ┌▼───────┐ ┌▼────────┐
│Task   │ │Load │ │Metrics  │ │Coord   │ │Custom   │
│Plugins│ │Gen  │ │Exporters│ │Plugins │ │Plugins  │
└───────┘ └─────┘ └─────────┘ └────────┘ └─────────┘

Plugin Examples:
• HTTP Task Plugin
• gRPC Task Plugin  
• Kafka Task Plugin
• Database Task Plugin
• Custom Protocol Plugins
```

**Extensibility:**
- Load plugins dynamically
- Community contributions
- Domain-specific extensions
- Zero core changes needed

---

## Extensibility Points

### 🔌 1. Task Interface Extensibility

```java
// Core Task Interface (Minimal)
public interface Task {
    void setup() throws Exception;
    TaskResult execute() throws Exception;
    void cleanup() throws Exception;
}

// Extended for HTTP (with virtual threads)
public abstract class HTTPTask implements Task {
    protected HttpClient httpClient;  // Java 21 HttpClient
    
    @Override
    public void setup() {
        httpClient = HttpClient.newBuilder()
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
    }
    
    // Subclasses implement test logic
    protected abstract HttpRequest buildRequest();
    protected abstract TaskResult validateResponse(HttpResponse<?> response);
}

// Extended for Database
public abstract class DatabaseTask implements Task {
    protected DataSource dataSource;
    
    @Override
    public void setup() throws Exception {
        // Connection pool configured by user
        this.dataSource = createDataSource();
    }
    
    protected abstract DataSource createDataSource();
    protected abstract TaskResult executeQuery();
}

// CPU-Intensive Task (uses platform threads)
public abstract class CPUBoundTask implements Task {
    // Framework will execute on platform thread pool
    // when task is annotated with @CPUBound
    
    @Override
    public TaskResult execute() throws Exception {
        // Heavy computation, encryption, compression, etc.
        return performComputation();
    }
    
    protected abstract TaskResult performComputation();
}

// User controls test data generation completely
public class CustomAPITest implements Task {
    private Iterator<TestData> dataIterator;
    
    @Override
    public void setup() {
        // User's custom test data generation
        this.dataIterator = MyTestDataGenerator.create(
            "data.csv",  // from file
            MyDataFactory::generate,  // or factory
            // Framework doesn't interfere!
        );
    }
    
    @Override
    public TaskResult execute() {
        TestData data = dataIterator.next();  // User's control
        // Make request with data
        return result;
    }
}
```

**Extension Mechanism:**
- Interface-based design
- Inherit and override
- Add domain-specific methods
- Java 21 features (virtual threads, records, sealed types)
- User controls test data generation
- Framework only handles execution and metrics

---

### 🔌 2. Metrics Exporter Extensibility

```
┌─────────────────────────────────────────────────────┐
│          MetricsExporter Interface                  │
├─────────────────────────────────────────────────────┤
│  - export(metrics: MetricsBatch) -> void           │
│  - configure(config: Dict) -> void                  │
│  - flush() -> void                                  │
│  - close() -> void                                  │
└─────────────────────────────────────────────────────┘
                        △
                        │ Implements
        ┌───────────────┼───────────────┐
        │               │               │
┌───────┴────────┐ ┌────┴──────┐ ┌─────┴────────┐
│ OTLP Exporter  │ │BlazeMeter │ │CustomExporter│
│                │ │ Exporter  │ │  (Plugin)    │
│ • OTLP Proto   │ │ • API     │ │ • Your Logic │
│ • gRPC/HTTP    │ │ • Cloud   │ │ • Any Backend│
└────────────────┘ └───────────┘ └──────────────┘
```

**Extension Points:**
- Standard interface
- Plugin registration system
- Configuration injection
- Async support

**Example Custom Exporter:**
```java
public class DatadogExporter implements MetricsExporter {
    private DatadogClient client;
    
    @Override
    public void configure(Map<String, Object> config) {
        String apiKey = (String) config.get("datadog_api_key");
        this.client = new DatadogClient(apiKey);
    }
    
    @Override
    public void export(MetricsBatch metrics) {
        // Send to Datadog API (async with virtual threads)
        Thread.startVirtualThread(() -> 
            client.send(metrics)
        );
    }
    
    @Override
    public void flush() {
        client.flush();
    }
}
```

---

### 🔌 3. Load Distribution Strategy Extensibility

```
Strategy Pattern for Load Distribution:

┌──────────────────────────────────────┐
│  LoadDistributionStrategy Interface  │
├──────────────────────────────────────┤
│  calculate(total_tps, workers)       │
│    -> Map<Worker, int>               │
└──────────────────────────────────────┘
              △
              │
    ┌─────────┼──────────┬──────────────┐
    │         │          │              │
┌───┴────┐ ┌──┴──────┐ ┌─┴─────────┐ ┌─┴────────┐
│ Equal  │ │Weighted │ │ Capacity  │ │ Custom   │
│ Split  │ │ Round   │ │ Based     │ │ Strategy │
│        │ │ Robin   │ │           │ │          │
└────────┘ └─────────┘ └───────────┘ └──────────┘
```

**Examples:**

1. **Equal Split** (Default)
   ```
   Total: 10,000 TPS
   Workers: 5
   Result: 2,000 TPS each
   ```

2. **Weighted Distribution**
   ```
   Worker 1 (weight: 2): 4,000 TPS
   Worker 2 (weight: 1): 2,000 TPS  
   Worker 3 (weight: 1): 2,000 TPS
   Worker 4 (weight: 1): 2,000 TPS
   ```

3. **Capacity-Based**
   ```
   Worker 1 (CPU: 80%): 1,500 TPS
   Worker 2 (CPU: 40%): 3,000 TPS
   Worker 3 (CPU: 60%): 2,000 TPS
   Worker 4 (CPU: 50%): 2,500 TPS
   ```

---

### 🔌 4. Coordination Protocol Extensibility

```
┌────────────────────────────────────────┐
│    CoordinationProtocol Interface      │
├────────────────────────────────────────┤
│  - register_worker()                   │
│  - discover_peers()                    │
│  - elect_leader()                      │
│  - broadcast_config()                  │
│  - synchronize_start()                 │
└────────────────────────────────────────┘
              △
              │
    ┌─────────┼──────────┬──────────────┐
    │         │          │              │
┌───┴─────┐ ┌─┴───────┐ ┌┴──────────┐ ┌─┴────────┐
│ gRPC    │ │ Gossip  │ │ Kafka     │ │ Custom   │
│ Protocol│ │ Protocol│ │ Streams   │ │ Protocol │
└─────────┘ └─────────┘ └───────────┘ └──────────┘
```

**Pluggable Coordination:**
- HTTP/REST for simplicity
- gRPC for performance
- Gossip for P2P
- Message queues for decoupling

---

### 🔌 5. Scheduler Extensibility

```
Load Profile Types:

┌─────────────────────────────────────────────────┐
│              Scheduler Interface                 │
├─────────────────────────────────────────────────┤
│  generate_schedule(config) -> Schedule          │
└─────────────────────────────────────────────────┘
                    △
                    │
        ┌───────────┼───────────┬─────────────┐
        │           │           │             │
  ┌─────┴─────┐ ┌───┴────┐ ┌────┴──────┐ ┌───┴────────┐
  │  Constant │ │ Ramp   │ │ Spike     │ │ Custom     │
  │  Load     │ │ Up/Down│ │ Pattern   │ │ Schedule   │
  └───────────┘ └────────┘ └───────────┘ └────────────┘
```

**Examples:**

```
Constant Load:
TPS │████████████████████████████
    └────────────────────────────► Time

Ramp Up:
TPS │         ┌────────────────────
    │        /
    │       /
    │      /
    └─────────────────────────────► Time

Spike Pattern:
TPS │     ┌─┐      ┌─┐      ┌─┐
    │     │ │      │ │      │ │
    │─────┘ └──────┘ └──────┘ └──► Time

Step Pattern:
TPS │         ┌────────┐
    │    ┌────┤        │
    │────┤    │        └──────────► Time
```

---

### 🔌 6. Storage Backend Extensibility

```
┌────────────────────────────────────────┐
│      ResultsStorage Interface          │
├────────────────────────────────────────┤
│  - save_test_run(metadata)             │
│  - save_metrics(run_id, metrics)       │
│  - query_results(filters)              │
│  - get_historical_data(test_name)      │
└────────────────────────────────────────┘
              △
              │
    ┌─────────┼──────────┬──────────────┐
    │         │          │              │
┌───┴──────┐ ┌┴────────┐ ┌┴───────────┐ ┌┴────────┐
│ PostgreSQL│ │InfluxDB │ │ TimescaleDB│ │S3/Blob  │
│          │ │         │ │            │ │Storage  │
└──────────┘ └─────────┘ └────────────┘ └─────────┘
```

---

### 🔌 7. Authentication & Security Extensibility

```
┌────────────────────────────────────────┐
│      AuthenticationProvider            │
├────────────────────────────────────────┤
│  - authenticate(credentials)           │
│  - authorize(user, resource)           │
└────────────────────────────────────────┘
              △
              │
    ┌─────────┼──────────┬──────────────┐
    │         │          │              │
┌───┴──────┐ ┌┴────────┐ ┌┴───────────┐ ┌┴────────┐
│ API Key  │ │ OAuth2  │ │ mTLS       │ │ Custom  │
│          │ │ /OIDC   │ │ Certs      │ │ Auth    │
└──────────┘ └─────────┘ └────────────┘ └─────────┘
```

---

## Component Details

### Virtual Threads vs Platform Threads Strategy

```
┌────────────────────────────────────────────────────────────────┐
│              THREAD STRATEGY SELECTION                          │
└────────────────────────────────────────────────────────────────┘

Task Type Classification:

┌─────────────────────────────────────────────────────────────┐
│ I/O-BOUND TASKS → Virtual Threads                           │
├─────────────────────────────────────────────────────────────┤
│ • HTTP/REST API calls                                       │
│ • Database queries (JDBC with blocking I/O)                 │
│ • Message queue operations (Kafka, RabbitMQ)                │
│ • File I/O operations                                        │
│ • Network calls (gRPC, WebSocket)                           │
│                                                              │
│ Characteristics:                                             │
│ • Spend time waiting for I/O                                │
│ • Low CPU usage                                              │
│ • High concurrency (millions of virtual threads)            │
│                                                              │
│ Executor:                                                    │
│   Executors.newVirtualThreadPerTaskExecutor()               │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ CPU-BOUND TASKS → Platform Threads                          │
├─────────────────────────────────────────────────────────────┤
│ • Encryption/Decryption                                      │
│ • Compression/Decompression                                  │
│ • Complex calculations                                       │
│ • Image/Video processing                                     │
│ • Heavy JSON/XML parsing                                     │
│                                                              │
│ Characteristics:                                             │
│ • Continuous CPU usage                                       │
│ • No blocking I/O                                            │
│ • Limited concurrency (# of CPU cores)                      │
│                                                              │
│ Executor:                                                    │
│   new ThreadPoolExecutor(                                    │
│     Runtime.getRuntime().availableProcessors(),             │
│     ... ForkJoinPool.commonPool()                           │
│   )                                                          │
└─────────────────────────────────────────────────────────────┘

Configuration:

// Option 1: Annotation-based
@VirtualThreads  // Default
public class APILoadTest implements Task { ... }

@PlatformThreads  // For CPU-bound
public class EncryptionTest implements Task { ... }

// Option 2: Configuration-based
vajra:
  executor:
    type: VIRTUAL  # or PLATFORM
    
// Option 3: Auto-detection (future)
// Framework profiles task and switches executor automatically
```

### Java 21 Feature Utilization

```java
// 1. Virtual Threads (Project Loom)
var executor = Executors.newVirtualThreadPerTaskExecutor();
executor.submit(() -> task.execute());

// 2. Structured Concurrency (Preview)
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Future<TaskResult> task1 = scope.fork(() -> task.execute());
    Future<TaskResult> task2 = scope.fork(() -> task.execute());
    
    scope.join();  // Wait for all
    scope.throwIfFailed();
}

// 3. Scoped Values (Preview) - Better than ThreadLocal
private static final ScopedValue<ExecutionContext> CONTEXT = 
    ScopedValue.newInstance();

ScopedValue.where(CONTEXT, executionContext)
    .run(() -> task.execute());

// 4. Records for Immutable Data
public record ExecutionMetrics(
    long startTime,
    long endTime,
    TaskResult result,
    Optional<Throwable> error
) {
    public Duration duration() {
        return Duration.ofNanos(endTime - startTime);
    }
}

// 5. Sealed Interfaces for TaskResult
public sealed interface TaskResult 
    permits Success, Failure {
    
    record Success(Object data) implements TaskResult {}
    record Failure(Throwable error) implements TaskResult {}
}

// 6. Pattern Matching for Switch
switch (taskResult) {
    case Success(var data) -> recordSuccess(data);
    case Failure(var error) -> recordFailure(error);
}
```

### Metrics Processor Deep Dive

```
┌───────────────────────────────────────────────────────────────┐
│                   METRICS PROCESSING PIPELINE                  │
└───────────────────────────────────────────────────────────────┘

   Task.execute() returns TaskResult
       │
       ▼
┌─────────────────┐
│  Task Executor  │  ◄── Wraps every execute() call
│  (Instrumented) │
│                 │      Captures automatically:
│  start = nanos()│      • Start timestamp
│  result = exec()│      • End timestamp  
│  end = nanos()  │      • Latency (end - start)
│                 │      • Success/Failure
│  Creates:       │      • Exception details
│  ExecutionMetrics│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Raw Collector  │  ◄── ExecutionMetrics (not TaskResult!)
│  (Micrometer)   │
│                 │      TaskResult only contains:
│  • Timestamp    │      • Success/Failure indicator
│  • Latency      │      • Optional result data
│  • Success/Fail │      • Optional error
│  • Result Data  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Aggregator     │  ◄── Window-based (1s, 5s, 1m)
│                 │
│  • Count        │      Per Window:
│  • Sum          │      ┌──────────────────┐
│  • Min/Max      │      │ TPS: 2,000       │
│  • Running Avg  │      │ P50: 45ms        │
└────────┬────────┘      │ P95: 120ms       │
         │               │ P99: 250ms       │
         ▼               │ Error%: 0.5%     │
┌─────────────────┐      └──────────────────┘
│  Reservoir      │
│  Sampling       │  ◄── Statistical sampling
│                 │      for percentiles
│  • Histogram    │
│  • HDR         │
│  • T-Digest     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Formatter     │  ◄── Convert to exporter format
│                 │
│  • OTLP Proto   │
│  • JSON         │
│  • Custom       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Exporters     │  ◄── Push to backends
│                 │
│  • Batch        │
│  • Async        │
│  • Retry        │
└─────────────────┘
```

---

### OpenTelemetry Integration

```
┌────────────────────────────────────────────────────────────┐
│                  OPENTELEMETRY INTEGRATION                  │
└────────────────────────────────────────────────────────────┘

Worker Process
    │
    ├─► Traces
    │   │
    │   ├─► Span: Test Execution
    │   │   ├─► Span: Task Setup
    │   │   ├─► Span: Task Execute (repeated)
    │   │   └─► Span: Task Cleanup
    │   │
    │   └─► Context Propagation across workers
    │
    ├─► Metrics
    │   │
    │   ├─► Counter: total_requests
    │   ├─► Counter: failed_requests
    │   ├─► Histogram: request_latency
    │   ├─► Gauge: active_virtual_users
    │   └─► Custom Metrics
    │
    └─► Logs (Structured)
        │
        ├─► Error logs with trace correlation
        └─► Debug logs

                    │
                    ▼
        ┌────────────────────┐
        │  OTLP Collector    │
        │                    │
        │  • Receive         │
        │  • Process         │
        │  • Export          │
        └─────────┬──────────┘
                  │
      ┌───────────┼───────────┐
      │           │           │
┌─────▼────┐ ┌────▼────┐ ┌───▼──────┐
│ Jaeger   │ │Prometheus│ │ Grafana  │
│ (Traces) │ │(Metrics) │ │(Visualize│
└──────────┘ └─────────┘ └──────────┘
```

**Key Benefits:**
- Distributed tracing across workers
- Correlation between metrics and traces
- Standard observability stack
- Vendor-neutral

---

### Grafana Dashboard Layout

```
┌────────────────────────────────────────────────────────────┐
│              VAJRA TEST DASHBOARD - Grafana                 │
├────────────────────────────────────────────────────────────┤
│                                                             │
│  Test: API Load Test        Status: ● Running              │
│  Duration: 5m 23s          Workers: 5 Active               │
│                                                             │
├────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌──────────────────────────┐   │
│  │  Throughput (TPS)   │  │  Response Time (ms)       │   │
│  │                     │  │                           │   │
│  │  10,000 ┤   ╱─────  │  │   500 ┤                   │   │
│  │         │  ╱        │  │       │        ╱──╲       │   │
│  │   5,000 ┤ ╱         │  │   250 ┤   ╱──╱    ╲──    │   │
│  │         │╱          │  │       │  ╱             ╲  │   │
│  │       0 └───────────│  │     0 └──────────────────│   │
│  └─────────────────────┘  └──────────────────────────┘   │
│                                                             │
├────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────┐ │
│  │  Latency Percentiles                                 │ │
│  │                                                       │ │
│  │  P50:  45ms  ████████████░░░░░░░░                   │ │
│  │  P75:  78ms  ██████████████░░░░░░                   │ │
│  │  P95: 156ms  ████████████████████░                  │ │
│  │  P99: 289ms  ████████████████████████░              │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                             │
├────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌──────────────────────────┐   │
│  │  Success Rate       │  │  Error Distribution       │   │
│  │                     │  │                           │   │
│  │      99.5%          │  │  Timeout:    45%          │   │
│  │    ┌──────┐         │  │  5xx:        30%          │   │
│  │    │ ██████│         │  │  Connection: 25%          │   │
│  │    │ ██████│ OK     │  │                           │   │
│  │    │ ███░░ │ Fail   │  │  [Pie Chart]              │   │
│  │    └──────┘         │  │                           │   │
│  └─────────────────────┘  └──────────────────────────┘   │
│                                                             │
├────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────┐ │
│  │  Worker Status                                       │ │
│  │                                                       │ │
│  │  Worker-1  ● Running   2,000 TPS   CPU: 65%  Mem:45% │ │
│  │  Worker-2  ● Running   2,000 TPS   CPU: 68%  Mem:42% │ │
│  │  Worker-3  ● Running   2,000 TPS   CPU: 71%  Mem:48% │ │
│  │  Worker-4  ● Running   2,000 TPS   CPU: 69%  Mem:44% │ │
│  │  Worker-5  ● Running   2,000 TPS   CPU: 67%  Mem:46% │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
```

---

## Deployment Scenarios

### Scenario 1: Kubernetes Deployment

```yaml
# Orchestrator Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vajrapulse-orchestrator
spec:
  replicas: 2  # HA setup
  template:
    spec:
      containers:
      - name: orchestrator
        image: vajra/orchestrator:latest
        
---
# Worker Deployment (Auto-scaling)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vajrapulse-worker
spec:
  replicas: 5  # Can be auto-scaled
  template:
    spec:
      containers:
      - name: worker
        image: vajra/worker:latest
        
---
# Horizontal Pod Autoscaler
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: vajrapulse-worker-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: vajrapulse-worker
  minReplicas: 5
  maxReplicas: 50
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

### Scenario 2: Docker Compose (Local Dev)

```yaml
version: '3.8'
services:
  orchestrator:
    image: vajra/orchestrator:latest
    ports:
      - "8080:8080"
    environment:
      - JAVA_TOOL_OPTIONS=-XX:+UseZGC -Xmx2g
      - WORKERS=worker-1,worker-2,worker-3
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
      
  worker-1:
    image: vajra/worker:latest
    environment:
      - JAVA_TOOL_OPTIONS=-XX:+UseZGC -Xmx4g
      - ORCHESTRATOR_URL=http://orchestrator:8080
      - WORKER_ID=worker-1
      - THREAD_STRATEGY=VIRTUAL  # for I/O-bound tasks
    deploy:
      resources:
        limits:
          cpus: '4'
          memory: 4G
      
  worker-2:
    image: vajra/worker:latest
    environment:
      - JAVA_TOOL_OPTIONS=-XX:+UseZGC -Xmx4g
      - ORCHESTRATOR_URL=http://orchestrator:8080
      - WORKER_ID=worker-2
      - THREAD_STRATEGY=VIRTUAL
    deploy:
      resources:
        limits:
          cpus: '4'
          memory: 4G
      
  worker-3:
    image: vajra/worker:latest
    environment:
      - JAVA_TOOL_OPTIONS=-XX:+UseG1GC -Xmx8g
      - ORCHESTRATOR_URL=http://orchestrator:8080
      - WORKER_ID=worker-3
      - THREAD_STRATEGY=PLATFORM  # for CPU-bound tasks
    deploy:
      resources:
        limits:
          cpus: '8'
          memory: 8G
      
  otel-collector:
    image: otel/opentelemetry-collector:latest
    
  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
```

**Note**: Virtual threads work best with ZGC or G1GC. For CPU-bound workloads on platform threads, configure appropriate heap and GC settings.

### Scenario 3: Programmatic Execution (Recommended for Examples)

VajraPulse examples demonstrate programmatic usage where test tasks can be executed directly without CLI:

```java
// Example: examples/http-load-test/src/main/java/com/example/http/HttpLoadTestRunner.java

public class HttpLoadTestRunner {
    public static void main(String[] args) throws Exception {
        // Create task instance
        HttpLoadTest task = new HttpLoadTest();
        
        // Configure load pattern
        LoadPattern loadPattern = new StaticLoad(100.0, Duration.ofSeconds(30));
        
        // Create metrics collector
        MetricsCollector metricsCollector = new MetricsCollector();
        
        // Start periodic reporting (every 5 seconds)
        try (PeriodicMetricsReporter reporter = 
                new PeriodicMetricsReporter(metricsCollector, Duration.ofSeconds(5))) {
            
            reporter.start();
            
            // Run load test
            try (ExecutionEngine engine = 
                    new ExecutionEngine(task, loadPattern, metricsCollector)) {
                engine.run();
            }
        }
        
        // Export final results
        AggregatedMetrics metrics = metricsCollector.snapshot();
        ConsoleMetricsExporter exporter = new ConsoleMetricsExporter();
        exporter.export("HTTP Load Test Results", metrics);
    }
}
```

**Build Configuration** (examples use vajrapulse-worker as dependency):

```gradle
dependencies {
    // Worker dependency brings in all required modules transitively via 'api'
    implementation(files("../../vajrapulse-worker/build/libs/vajrapulse-worker-1.0.0-SNAPSHOT.jar"))
}

application {
    mainClass.set("com.example.http.HttpLoadTestRunner")
}
```

**Key Benefits**:
- No CLI parsing needed for simple examples
- Direct IDE execution support
- Easy debugging with breakpoints
- Clear demonstration of API usage
- Suitable for integration tests
- Educational examples

**Running Examples**:
```bash
# From example directory
./gradlew run

# Or build and run directly
./gradlew build
java -jar build/libs/example.jar
```

### Scenario 4: CI/CD Integration

```yaml
# GitHub Actions Example
name: Load Test
on: [push]

jobs:
  load-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          
      - name: Build Test Task
        run: |
          mvn clean package
          
      - name: Run VajraPulse Worker
        run: |
          java -jar vajrapulse-worker.jar run \
            --task-jar target/my-load-test.jar \
            --task-class com.example.MyAPITest \
            --tps 1000 \
            --duration 60s \
            --thread-strategy virtual \
            --exporter console
            
      - name: Check Results
        run: |
          if [ $FAILURE_RATE -gt 1 ]; then
            exit 1
          fi
```

---

## Summary of Extensibility

### 🎯 Key Extensibility Dimensions

| Dimension | Extension Mechanism | Examples |
|-----------|-------------------|----------|
| **Tasks** | Abstract Base Class | HTTP, gRPC, Database, Kafka tasks |
| **Exporters** | Plugin Interface | Datadog, New Relic, custom backends |
| **Distribution** | Strategy Pattern | Equal, weighted, capacity-based |
| **Coordination** | Protocol Interface | gRPC, Gossip, REST, message queues |
| **Scheduling** | Scheduler Interface | Constant, ramp, spike, custom |
| **Storage** | Storage Interface | PostgreSQL, InfluxDB, S3 |
| **Auth** | Provider Interface | API key, OAuth, mTLS |

### 🚀 Design Highlights

1. **Multi-Mode Operation**: Orchestrated, P2P, Standalone
2. **Cloud-Native**: Kubernetes-ready, auto-scaling support
3. **Observable**: Deep OpenTelemetry integration
4. **Extensible**: Plugin architecture throughout
5. **Vendor-Neutral**: No lock-in to specific tools
6. **Developer-Friendly**: Simple SDK, clear interfaces
7. **Production-Ready**: HA, fault tolerance, monitoring

---

## Recommended Implementation Phases

### Phase 1: Core Foundation
- ✅ Task SDK with 3 methods (Java interface)
- ✅ Simplified TaskResult (Success/Failure only)
- ✅ Task Executor with automatic instrumentation
- ✅ Virtual thread execution engine
- ✅ Platform thread support for CPU-bound tasks
- ✅ Console exporter
- ✅ Standalone mode
- ✅ User-controlled test data generation

### Phase 2: Metrics & Observability
- ✅ Metrics processor with aggregation
- ✅ OpenTelemetry exporter
- ✅ BlazeMeter exporter
- ✅ Basic Grafana dashboard

### Phase 3: Distribution
- ✅ Orchestrator with load distribution
- ✅ Worker registration & discovery
- ✅ gRPC communication
- ✅ Web UI

### Phase 4: Advanced Features
- ✅ P2P coordination mode
- ✅ Auto-scaling support
- ✅ Historical data storage
- ✅ Advanced scheduling

### Phase 5: Extensibility
- ✅ Plugin system
- ✅ Custom exporter SDK
- ✅ Task libraries
- ✅ Community contributions

---

**This design provides a solid foundation for building VajraPulse with maximum flexibility and extensibility while maintaining simplicity where needed.**
