# BlazeMeter Integration: Strategic Plan

**Date**: 2025-01-XX  
**Focus**: BlazeMeter as Primary Distributed Execution Platform  
**Approach**: Two-pronged integration (Exporter + Executor)

---

## Executive Summary

**Strategy**: Integrate VajraPulse with BlazeMeter in two complementary ways:

1. **BlazeMeter Exporter** - Export VajraPulse metrics to BlazeMeter (standalone mode)
2. **BlazeMeter Executor** - Run VajraPulse as BlazeMeter custom executor (distributed mode)

**Benefits**:
- ✅ Enterprise-ready distributed execution (no custom orchestrator needed)
- ✅ Rich dashboard and reporting (BlazeMeter UI)
- ✅ Cloud infrastructure (no server management)
- ✅ Multi-region testing capabilities
- ✅ Historical test data storage
- ✅ Team collaboration features

**Timeline**: 4 weeks total
- Week 1-2: BlazeMeter Exporter
- Week 3-4: BlazeMeter Executor Integration

---

## Architecture Overview

### Two Integration Patterns

```
┌─────────────────────────────────────────────────────────────┐
│                    Integration Pattern 1                     │
│              BlazeMeter Exporter (Standalone)                │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  VajraPulse Worker (Standalone)                     │   │
│  │  - Runs locally or in CI/CD                         │   │
│  │  - Executes load test                               │   │
│  │  - Collects metrics                                 │   │
│  └──────────────────┬──────────────────────────────────┘   │
│                     │                                        │
│                     │ Exports metrics                        │
│                     ▼                                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  BlazeMeter Exporter                                │   │
│  │  - Converts metrics to BlazeMeter format           │   │
│  │  - POSTs to BlazeMeter API                          │   │
│  └──────────────────┬──────────────────────────────────┘   │
│                     │                                        │
│                     │ HTTP POST                              │
│                     ▼                                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  BlazeMeter Cloud                                   │   │
│  │  - Receives metrics                                 │   │
│  │  - Stores in test session                           │   │
│  │  - Displays in dashboard                            │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                    Integration Pattern 2                     │
│          BlazeMeter Executor (Distributed Mode)             │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  BlazeMeter Cloud Platform                          │   │
│  │                                                       │   │
│  │  ┌─────────────────────────────────────────────┐ │   │
│  │  │  Test Controller                              │ │   │
│  │  │  - Test configuration                         │ │   │
│  │  │  - Executor allocation                        │ │   │
│  │  │  - Load distribution                          │ │   │
│  │  │  - Metrics aggregation                        │ │   │
│  │  └──────────────┬────────────────────────────────┘ │   │
│  │                 │                                    │   │
│  │        ┌────────┼────────┐                         │   │
│  │        │        │        │                          │   │
│  │  ┌─────▼────┐ ┌─▼──────┐ ┌─▼────────┐            │   │
│  │  │Executor 1│ │Executor│ │Executor N│            │   │
│  │  │          │ │   2    │ │          │            │   │
│  │  └─────┬────┘ └───┬────┘ └────┬─────┘            │   │
│  └────────┼──────────┼────────────┼───────────────────┘   │
│           │          │            │                        │
│           │          │            │                         │
│  ┌────────▼──────────▼────────────▼───────────────────┐ │
│  │  VajraPulse Executor Wrapper                        │ │
│  │  - Registers with BlazeMeter                        │ │
│  │  - Receives test configuration                      │ │
│  │  - Runs VajraPulse worker                           │ │
│  │  - Reports metrics back to BlazeMeter              │ │
│  └──────────────────────────────────────────────────────┘ │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Phase 1: BlazeMeter Exporter (Weeks 1-2)

### Overview
Export VajraPulse metrics to BlazeMeter for visualization and analysis. Works in standalone mode.

### Module Structure
```
vajrapulse-exporter-blazemeter/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── vajrapulse/
│   │               └── exporter/
│   │                   └── blazemeter/
│   │                       ├── BlazeMeterExporter.java
│   │                       ├── BlazeMeterConfig.java
│   │                       ├── BlazeMeterClient.java
│   │                       ├── BlazeMeterPayload.java
│   │                       └── BlazeMeterMetricsMapper.java
│   └── test/
│       └── groovy/
│           └── com/
│               └── vajrapulse/
│                   └── exporter/
│                       └── blazemeter/
│                           ├── BlazeMeterExporterSpec.groovy
│                           └── BlazeMeterClientSpec.groovy
├── build.gradle.kts
└── README.md
```

### Week 1: Core Implementation

#### Day 1-2: Module Setup & Dependencies

**Tasks**:
1. Create module structure
2. Add dependencies to `build.gradle.kts`
3. Update `settings.gradle.kts`

**Dependencies**:
```kotlin
// vajrapulse-exporter-blazemeter/build.gradle.kts
dependencies {
    api(project(":vajrapulse-core"))
    
    // HTTP client for BlazeMeter API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.0")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    
    // Testing
    testImplementation("org.spockframework:spock-core:2.4-M4-groovy-4.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

#### Day 3-4: BlazeMeter API Client

**File**: `BlazeMeterClient.java`

```java
package com.vajrapulse.exporter.blazemeter;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Client for interacting with BlazeMeter API.
 * 
 * <p>Handles authentication, session management, and metrics reporting.
 */
public final class BlazeMeterClient {
    private static final Logger logger = LoggerFactory.getLogger(BlazeMeterClient.class);
    private static final String BASE_URL = "https://a.blazemeter.com/api/v4";
    
    private final OkHttpClient httpClient;
    private final String apiKeyId;
    private final String apiKeySecret;
    private final ObjectMapper objectMapper;
    
    public BlazeMeterClient(String apiKeyId, String apiKeySecret) {
        this.apiKeyId = apiKeyId;
        this.apiKeySecret = apiKeySecret;
        this.objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * Creates a new test session in BlazeMeter.
     * 
     * @param testId the BlazeMeter test ID
     * @param sessionName optional session name
     * @return session ID
     */
    public String createSession(String testId, String sessionName) throws IOException {
        String url = BASE_URL + "/sessions";
        
        String json = objectMapper.writeValueAsString(Map.of(
            "testId", testId,
            "name", sessionName != null ? sessionName : "VajraPulse Session"
        ));
        
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(json, MediaType.get("application/json")))
            .addHeader("Authorization", createAuthHeader())
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create session: " + response.code());
            }
            
            SessionResponse sessionResponse = objectMapper.readValue(
                response.body().string(), 
                SessionResponse.class
            );
            
            return sessionResponse.sessionId;
        }
    }
    
    /**
     * Reports metrics to BlazeMeter session.
     * 
     * @param sessionId the session ID
     * @param payload the metrics payload
     */
    public void reportMetrics(String sessionId, BlazeMeterPayload payload) throws IOException {
        String url = BASE_URL + "/sessions/" + sessionId + "/reports";
        
        String json = objectMapper.writeValueAsString(payload);
        
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(json, MediaType.get("application/json")))
            .addHeader("Authorization", createAuthHeader())
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("Failed to report metrics: {} - {}", 
                    response.code(), response.body().string());
                throw new IOException("Failed to report metrics: " + response.code());
            }
        }
    }
    
    /**
     * Updates session status.
     * 
     * @param sessionId the session ID
     * @param status the status (RUNNING, COMPLETED, FAILED)
     */
    public void updateSessionStatus(String sessionId, String status) throws IOException {
        String url = BASE_URL + "/sessions/" + sessionId;
        
        String json = objectMapper.writeValueAsString(Map.of("status", status));
        
        Request request = new Request.Builder()
            .url(url)
            .patch(RequestBody.create(json, MediaType.get("application/json")))
            .addHeader("Authorization", createAuthHeader())
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("Failed to update session status: {}", response.code());
            }
        }
    }
    
    private String createAuthHeader() {
        String credentials = apiKeyId + ":" + apiKeySecret;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encoded;
    }
    
    // Inner classes for API responses
    private static class SessionResponse {
        public String sessionId;
    }
}
```

#### Day 5: Metrics Mapper

**File**: `BlazeMeterMetricsMapper.java`

```java
package com.vajrapulse.exporter.blazemeter;

import com.vajrapulse.core.metrics.AggregatedMetrics;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Maps VajraPulse metrics to BlazeMeter format.
 */
public final class BlazeMeterMetricsMapper {
    
    public BlazeMeterPayload map(AggregatedMetrics metrics, String runId) {
        BlazeMeterPayload payload = new BlazeMeterPayload();
        payload.timestamp = Instant.now().toEpochMilli();
        payload.runId = runId;
        
        // Map success metrics
        payload.successCount = metrics.totalSuccess();
        payload.successLatency = Map.of(
            "p50", metrics.successLatencyP50().toMillis(),
            "p95", metrics.successLatencyP95().toMillis(),
            "p99", metrics.successLatencyP99().toMillis(),
            "min", metrics.successLatencyMin().toMillis(),
            "max", metrics.successLatencyMax().toMillis(),
            "mean", metrics.successLatencyMean().toMillis()
        );
        
        // Map failure metrics
        payload.failureCount = metrics.totalFailures();
        if (metrics.totalFailures() > 0) {
            payload.failureLatency = Map.of(
                "p50", metrics.failureLatencyP50().toMillis(),
                "p95", metrics.failureLatencyP95().toMillis(),
                "p99", metrics.failureLatencyP99().toMillis(),
                "mean", metrics.failureLatencyMean().toMillis()
            );
        }
        
        // Map throughput
        payload.throughput = metrics.throughput();
        
        // Map error rate
        payload.errorRate = metrics.errorRate();
        
        return payload;
    }
}
```

### Week 2: Exporter Implementation & Testing

#### Day 1-2: BlazeMeter Exporter

**File**: `BlazeMeterExporter.java`

```java
package com.vajrapulse.exporter.blazemeter;

import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exports VajraPulse metrics to BlazeMeter.
 * 
 * <p>This exporter sends metrics to BlazeMeter cloud platform for
 * visualization, analysis, and historical storage.
 */
public final class BlazeMeterExporter implements MetricsExporter {
    private static final Logger logger = LoggerFactory.getLogger(BlazeMeterExporter.class);
    
    private final BlazeMeterClient client;
    private final BlazeMeterMetricsMapper mapper;
    private final String testId;
    private final String sessionId;
    private final String runId;
    private final ExecutorService executor;
    
    public BlazeMeterExporter(BlazeMeterConfig config) throws IOException {
        this.client = new BlazeMeterClient(config.apiKeyId(), config.apiKeySecret());
        this.mapper = new BlazeMeterMetricsMapper();
        this.testId = config.testId();
        this.runId = config.runId() != null ? config.runId() : generateRunId();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        
        // Create session
        this.sessionId = client.createSession(testId, "VajraPulse Run: " + runId);
        logger.info("Created BlazeMeter session: {} for test: {}", sessionId, testId);
        
        // Mark session as running
        client.updateSessionStatus(sessionId, "RUNNING");
    }
    
    @Override
    public void export(AggregatedMetrics metrics) {
        CompletableFuture.runAsync(() -> {
            try {
                BlazeMeterPayload payload = mapper.map(metrics, runId);
                client.reportMetrics(sessionId, payload);
                logger.debug("Exported metrics to BlazeMeter session: {}", sessionId);
            } catch (IOException e) {
                logger.error("Failed to export metrics to BlazeMeter", e);
            }
        }, executor);
    }
    
    @Override
    public void flush() {
        // Wait for pending exports
        executor.shutdown();
        try {
            executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public void close() throws IOException {
        try {
            flush();
            // Mark session as completed
            client.updateSessionStatus(sessionId, "COMPLETED");
            logger.info("BlazeMeter session {} marked as completed", sessionId);
        } catch (Exception e) {
            logger.error("Error closing BlazeMeter exporter", e);
            client.updateSessionStatus(sessionId, "FAILED");
        }
    }
    
    private String generateRunId() {
        return "run-" + System.currentTimeMillis();
    }
}
```

#### Day 3-4: Configuration & Builder

**File**: `BlazeMeterConfig.java`

```java
package com.vajrapulse.exporter.blazemeter;

/**
 * Configuration for BlazeMeter exporter.
 */
public record BlazeMeterConfig(
    String apiKeyId,
    String apiKeySecret,
    String testId,
    String runId
) {
    public static BlazeMeterConfigBuilder builder() {
        return new BlazeMeterConfigBuilder();
    }
    
    public static class BlazeMeterConfigBuilder {
        private String apiKeyId;
        private String apiKeySecret;
        private String testId;
        private String runId;
        
        public BlazeMeterConfigBuilder apiKeyId(String apiKeyId) {
            this.apiKeyId = apiKeyId;
            return this;
        }
        
        public BlazeMeterConfigBuilder apiKeySecret(String apiKeySecret) {
            this.apiKeySecret = apiKeySecret;
            return this;
        }
        
        public BlazeMeterConfigBuilder testId(String testId) {
            this.testId = testId;
            return this;
        }
        
        public BlazeMeterConfigBuilder runId(String runId) {
            this.runId = runId;
            return this;
        }
        
        public BlazeMeterConfig build() {
            if (apiKeyId == null || apiKeySecret == null || testId == null) {
                throw new IllegalArgumentException(
                    "apiKeyId, apiKeySecret, and testId are required");
            }
            return new BlazeMeterConfig(apiKeyId, apiKeySecret, testId, runId);
        }
    }
}
```

#### Day 5: Testing

**File**: `BlazeMeterExporterSpec.groovy`

```groovy
package com.vajrapulse.exporter.blazemeter

import com.vajrapulse.core.metrics.AggregatedMetrics
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import spock.lang.Specification

class BlazeMeterExporterSpec extends Specification {
    MockWebServer mockServer
    BlazeMeterExporter exporter
    
    def setup() {
        mockServer = new MockWebServer()
        mockServer.start()
    }
    
    def cleanup() {
        mockServer.shutdown()
    }
    
    def "exports metrics to BlazeMeter"() {
        given:
        mockServer.enqueue(new okhttp3.mockwebserver.MockResponse()
            .setResponseCode(200)
            .setBody('{"sessionId": "test-session-123"}'))
        mockServer.enqueue(new okhttp3.mockwebserver.MockResponse()
            .setResponseCode(200))
        
        def config = BlazeMeterConfig.builder()
            .apiKeyId("test-key-id")
            .apiKeySecret("test-secret")
            .testId("test-123")
            .build()
        
        exporter = new BlazeMeterExporter(config)
        
        def metrics = AggregatedMetrics.builder()
            .totalSuccess(1000)
            .totalFailures(10)
            .build()
        
        when:
        exporter.export(metrics)
        exporter.flush()
        
        then:
        mockServer.requestCount == 2
        // Verify session creation
        // Verify metrics reporting
    }
}
```

---

## Phase 2: BlazeMeter Executor Integration (Weeks 3-4)

### Overview
Run VajraPulse as a BlazeMeter custom executor. BlazeMeter orchestrates multiple executors for distributed testing.

### Module Structure
```
vajrapulse-executor-blazemeter/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── vajrapulse/
│   │               └── executor/
│   │                   └── blazemeter/
│   │                       ├── BlazeMeterExecutor.java
│   │                       ├── BlazeMeterExecutorConfig.java
│   │                       ├── BlazeMeterTestController.java
│   │                       └── BlazeMeterExecutorClient.java
│   └── test/
│       └── groovy/
│           └── com/
│               └── vajrapulse/
│                   └── executor/
│                       └── blazemeter/
│                           └── BlazeMeterExecutorSpec.groovy
├── build.gradle.kts
└── README.md
```

### Week 3: Executor Core

#### Day 1-2: BlazeMeter Executor Client

**File**: `BlazeMeterExecutorClient.java`

```java
package com.vajrapulse.executor.blazemeter;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Client for BlazeMeter executor API.
 * 
 * <p>Handles executor registration, test configuration retrieval,
 * and result reporting.
 */
public final class BlazeMeterExecutorClient {
    private static final Logger logger = LoggerFactory.getLogger(BlazeMeterExecutorClient.class);
    private static final String BASE_URL = "https://a.blazemeter.com/api/v4";
    
    private final OkHttpClient httpClient;
    private final String apiKeyId;
    private final String apiKeySecret;
    private final ObjectMapper objectMapper;
    private final String executorId;
    
    public BlazeMeterExecutorClient(String apiKeyId, String apiKeySecret, String executorId) {
        this.apiKeyId = apiKeyId;
        this.apiKeySecret = apiKeySecret;
        this.executorId = executorId;
        this.objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .build();
    }
    
    /**
     * Registers this executor with BlazeMeter.
     * 
     * @param testId the test ID to register for
     * @return executor registration response
     */
    public ExecutorRegistration register(String testId) throws IOException {
        String url = BASE_URL + "/executors/register";
        
        String json = objectMapper.writeValueAsString(Map.of(
            "testId", testId,
            "executorId", executorId,
            "type", "vajrapulse"
        ));
        
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(json, MediaType.get("application/json")))
            .addHeader("Authorization", createAuthHeader())
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to register executor: " + response.code());
            }
            
            return objectMapper.readValue(
                response.body().string(),
                ExecutorRegistration.class
            );
        }
    }
    
    /**
     * Polls for test configuration.
     * 
     * @return test configuration when test starts, null if not ready
     */
    public BlazeMeterTestConfig pollForTestConfig() throws IOException {
        String url = BASE_URL + "/executors/" + executorId + "/test-config";
        
        Request request = new Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", createAuthHeader())
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 204) {
                // No content - test not started yet
                return null;
            }
            
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get test config: " + response.code());
            }
            
            return objectMapper.readValue(
                response.body().string(),
                BlazeMeterTestConfig.class
            );
        }
    }
    
    /**
     * Reports executor status to BlazeMeter.
     * 
     * @param status the status (IDLE, RUNNING, COMPLETED, FAILED)
     * @param metrics optional metrics snapshot
     */
    public void reportStatus(String status, ExecutorMetrics metrics) throws IOException {
        String url = BASE_URL + "/executors/" + executorId + "/status";
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", status);
        if (metrics != null) {
            payload.put("metrics", metrics);
        }
        
        String json = objectMapper.writeValueAsString(payload);
        
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(json, MediaType.get("application/json")))
            .addHeader("Authorization", createAuthHeader())
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warn("Failed to report status: {}", response.code());
            }
        }
    }
    
    /**
     * Reports final results to BlazeMeter.
     * 
     * @param results the test results
     */
    public void reportResults(ExecutorResults results) throws IOException {
        String url = BASE_URL + "/executors/" + executorId + "/results";
        
        String json = objectMapper.writeValueAsString(results);
        
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(json, MediaType.get("application/json")))
            .addHeader("Authorization", createAuthHeader())
            .addHeader("Content-Type", "application/json")
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to report results: " + response.code());
            }
        }
    }
    
    private String createAuthHeader() {
        String credentials = apiKeyId + ":" + apiKeySecret;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encoded;
    }
    
    // Inner classes
    public static class ExecutorRegistration {
        public String executorId;
        public String testId;
        public String status;
    }
    
    public static class BlazeMeterTestConfig {
        public String testId;
        public String taskClass;
        public double targetTps;
        public String loadPattern;
        public Map<String, Object> patternConfig;
        public Duration duration;
        public Map<String, String> environment;
    }
    
    public static class ExecutorMetrics {
        public long totalExecutions;
        public long successCount;
        public long failureCount;
        public double throughput;
        public Map<String, Long> latencyPercentiles;
    }
    
    public static class ExecutorResults {
        public String status;
        public ExecutorMetrics finalMetrics;
        public String errorMessage;
    }
}
```

#### Day 3-4: BlazeMeter Executor

**File**: `BlazeMeterExecutor.java`

```java
package com.vajrapulse.executor.blazemeter;

import com.vajrapulse.api.LoadPattern;
import com.vajrapulse.api.Task;
import com.vajrapulse.core.engine.ExecutionEngine;
import com.vajrapulse.core.metrics.MetricsCollector;
import com.vajrapulse.core.metrics.AggregatedMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * VajraPulse executor that runs as a BlazeMeter custom executor.
 * 
 * <p>This executor:
 * 1. Registers with BlazeMeter
 * 2. Waits for test configuration
 * 3. Runs VajraPulse test
 * 4. Reports metrics back to BlazeMeter
 */
public final class BlazeMeterExecutor {
    private static final Logger logger = LoggerFactory.getLogger(BlazeMeterExecutor.class);
    
    private final BlazeMeterExecutorClient client;
    private final String executorId;
    
    public BlazeMeterExecutor(BlazeMeterExecutorConfig config) {
        this.executorId = config.executorId() != null 
            ? config.executorId() 
            : "vajrapulse-" + System.currentTimeMillis();
        this.client = new BlazeMeterExecutorClient(
            config.apiKeyId(),
            config.apiKeySecret(),
            executorId
        );
    }
    
    /**
     * Runs the executor lifecycle.
     */
    public void run() throws Exception {
        logger.info("Starting BlazeMeter executor: {}", executorId);
        
        // 1. Register with BlazeMeter
        String testId = getTestIdFromConfig();
        BlazeMeterExecutorClient.ExecutorRegistration registration = 
            client.register(testId);
        logger.info("Registered executor {} for test {}", executorId, testId);
        
        // 2. Report IDLE status
        client.reportStatus("IDLE", null);
        
        // 3. Poll for test configuration
        BlazeMeterExecutorClient.BlazeMeterTestConfig testConfig = 
            waitForTestConfig();
        
        if (testConfig == null) {
            logger.warn("Test configuration not received, exiting");
            return;
        }
        
        logger.info("Received test configuration: {}", testConfig);
        
        // 4. Report RUNNING status
        client.reportStatus("RUNNING", null);
        
        // 5. Create and run VajraPulse test
        try {
            runVajraPulseTest(testConfig);
            
            // 6. Report COMPLETED status
            client.reportStatus("COMPLETED", null);
        } catch (Exception e) {
            logger.error("Test execution failed", e);
            client.reportStatus("FAILED", null);
            throw e;
        }
    }
    
    private BlazeMeterExecutorClient.BlazeMeterTestConfig waitForTestConfig() 
            throws InterruptedException, IOException {
        logger.info("Polling for test configuration...");
        
        int maxAttempts = 300; // 5 minutes with 1 second intervals
        int attempt = 0;
        
        while (attempt < maxAttempts) {
            BlazeMeterExecutorClient.BlazeMeterTestConfig config = 
                client.pollForTestConfig();
            
            if (config != null) {
                return config;
            }
            
            Thread.sleep(1000); // Wait 1 second
            attempt++;
            
            if (attempt % 10 == 0) {
                logger.debug("Still waiting for test configuration... (attempt {})", attempt);
            }
        }
        
        return null;
    }
    
    private void runVajraPulseTest(
            BlazeMeterExecutorClient.BlazeMeterTestConfig testConfig) 
            throws Exception {
        
        // Load task class
        Class<?> taskClass = Class.forName(testConfig.taskClass);
        Task task = (Task) taskClass.getDeclaredConstructor().newInstance();
        
        // Create load pattern
        LoadPattern loadPattern = createLoadPattern(testConfig);
        
        // Create metrics collector
        MetricsCollector metricsCollector = new MetricsCollector();
        
        // Create execution engine
        ExecutionEngine engine = new ExecutionEngine(
            task,
            loadPattern,
            metricsCollector
        );
        
        // Run test
        engine.run();
        
        // Get final metrics
        AggregatedMetrics finalMetrics = metricsCollector.snapshot();
        
        // Report results
        BlazeMeterExecutorClient.ExecutorResults results = 
            new BlazeMeterExecutorClient.ExecutorResults();
        results.status = "COMPLETED";
        results.finalMetrics = convertMetrics(finalMetrics);
        
        client.reportResults(results);
        
        logger.info("Test completed successfully");
    }
    
    private LoadPattern createLoadPattern(
            BlazeMeterExecutorClient.BlazeMeterTestConfig config) {
        // Map BlazeMeter config to VajraPulse LoadPattern
        // Implementation depends on pattern type
        switch (config.loadPattern.toLowerCase()) {
            case "static":
                return new StaticLoad(config.targetTps, config.duration);
            case "ramp":
                // Extract ramp config from patternConfig
                return new RampUpLoad(/* ... */);
            // ... other patterns
            default:
                throw new IllegalArgumentException(
                    "Unsupported load pattern: " + config.loadPattern);
        }
    }
    
    private BlazeMeterExecutorClient.ExecutorMetrics convertMetrics(
            AggregatedMetrics metrics) {
        BlazeMeterExecutorClient.ExecutorMetrics executorMetrics = 
            new BlazeMeterExecutorClient.ExecutorMetrics();
        executorMetrics.totalExecutions = metrics.totalExecutions();
        executorMetrics.successCount = metrics.totalSuccess();
        executorMetrics.failureCount = metrics.totalFailures();
        executorMetrics.throughput = metrics.throughput();
        executorMetrics.latencyPercentiles = Map.of(
            "p50", metrics.successLatencyP50().toMillis(),
            "p95", metrics.successLatencyP95().toMillis(),
            "p99", metrics.successLatencyP99().toMillis()
        );
        return executorMetrics;
    }
    
    private String getTestIdFromConfig() {
        // Get from environment variable or config file
        return System.getenv("BLAZEMETER_TEST_ID");
    }
}
```

### Week 4: Integration & Documentation

#### Day 1-2: Main Entry Point

**File**: `BlazeMeterExecutorMain.java`

```java
package com.vajrapulse.executor.blazemeter;

public class BlazeMeterExecutorMain {
    public static void main(String[] args) {
        BlazeMeterExecutorConfig config = BlazeMeterExecutorConfig.fromEnvironment();
        BlazeMeterExecutor executor = new BlazeMeterExecutor(config);
        
        try {
            executor.run();
        } catch (Exception e) {
            System.err.println("Executor failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
```

#### Day 3-4: Documentation & Examples

**File**: `README.md`

```markdown
# VajraPulse BlazeMeter Executor

Run VajraPulse as a BlazeMeter custom executor for distributed load testing.

## Setup

1. Get BlazeMeter API credentials
2. Configure executor
3. Deploy executor instances
4. Create test in BlazeMeter UI

## Usage

```bash
export BLAZEMETER_API_KEY_ID=your-key-id
export BLAZEMETER_API_KEY_SECRET=your-secret
export BLAZEMETER_TEST_ID=test-123

java -jar vajrapulse-executor-blazemeter.jar
```

## BlazeMeter Test Configuration

In BlazeMeter UI, configure test with:
- Executor type: Custom
- Executor image/command: VajraPulse executor
- Number of executors: 5 (for distributed test)
- Test configuration: JSON with task class, load pattern, etc.
```

#### Day 5: Integration Testing

Create integration tests with mock BlazeMeter API server.

---

## Updated Strategic Roadmap

### Revised Phase 2: Enterprise Readiness (Weeks 5-8)

**Focus**: BlazeMeter Integration (Primary Distributed Execution Method)

#### Week 5-6: BlazeMeter Exporter
- ✅ Module structure
- ✅ API client
- ✅ Metrics mapper
- ✅ Exporter implementation
- ✅ Testing

#### Week 7-8: BlazeMeter Executor
- ✅ Executor client
- ✅ Executor lifecycle
- ✅ Test configuration handling
- ✅ Results reporting
- ✅ Documentation

**Deliverables**:
1. `vajrapulse-exporter-blazemeter` module
2. `vajrapulse-executor-blazemeter` module
3. Comprehensive documentation
4. Example configurations
5. Integration guide

---

## Success Metrics

### Technical
- ✅ BlazeMeter exporter sends metrics successfully
- ✅ BlazeMeter executor registers and runs tests
- ✅ Metrics appear in BlazeMeter dashboard
- ✅ Distributed tests run across multiple executors

### Adoption
- ✅ Documentation complete
- ✅ Example configurations provided
- ✅ Integration guide published
- ✅ BlazeMeter users can adopt easily

---

## Next Steps

1. **Week 1**: Start BlazeMeter Exporter implementation
2. **Week 3**: Start BlazeMeter Executor implementation
3. **Week 5**: Integration testing with real BlazeMeter account
4. **Week 6**: Documentation and examples
5. **Week 7**: Release 0.10.0 with BlazeMeter integration

---

*This plan provides a complete BlazeMeter integration strategy for distributed execution.*

