# Trace Replay Load Pattern Implementation Plan

**Release**: 0.9.6  
**Priority**: HIGH  
**Source**: Architecture Documents  
**Effort**: 4-6 days

## Problem Statement

Currently, VajraPulse supports synthetic load patterns (static, ramp, step, sine, spike, adaptive) but cannot replay real traffic patterns from production logs. This limits the ability to:
- Test with realistic traffic distributions
- Replay production incidents
- Validate system behavior under actual conditions
- Reproduce specific traffic patterns

## Solution Overview

Create a `TraceReplayLoad` pattern that:
1. Reads traffic timestamps from log files (CSV, JSON, or custom format)
2. Replays traffic at original timestamps or scaled timestamps
3. Handles missing data gracefully
4. Supports time scaling (faster/slower replay)
5. Provides statistics on replay accuracy

## Design

### 1. Trace Replay Load Pattern

```java
public class TraceReplayLoad implements LoadPattern {
    private final List<TraceEvent> events;
    private final Duration replayDuration;
    private final double timeScale; // 1.0 = original speed, 2.0 = 2x faster
    private final long startTimeMillis;
    
    public TraceReplayLoad(Path logFile, Duration replayDuration, double timeScale) {
        this.events = parseLogFile(logFile);
        this.replayDuration = replayDuration;
        this.timeScale = timeScale;
        this.startTimeMillis = System.currentTimeMillis();
    }
    
    @Override
    public double calculateTps(long elapsedMillis) {
        // Calculate TPS based on events in current time window
        long scaledElapsed = (long)(elapsedMillis * timeScale);
        long windowStart = scaledElapsed - 1000; // 1 second window
        long windowEnd = scaledElapsed;
        
        long eventsInWindow = events.stream()
            .filter(e -> e.timestamp >= windowStart && e.timestamp <= windowEnd)
            .count();
        
        return eventsInWindow; // Events per second
    }
    
    @Override
    public Duration getDuration() {
        return replayDuration;
    }
}
```

### 2. Trace Event Structure

```java
public record TraceEvent(
    long timestamp,      // Original timestamp (milliseconds since epoch)
    String endpoint,     // Optional: endpoint identifier
    String method,       // Optional: HTTP method
    int statusCode,      // Optional: response status
    long durationNanos   // Optional: original duration
) {}
```

### 3. Log File Parsers

**CSV Format**:
```csv
timestamp,endpoint,method,status_code,duration_nanos
1699123456000,/api/users,GET,200,50000000
1699123456100,/api/orders,POST,201,75000000
1699123456200,/api/products,GET,200,30000000
```

**JSON Format**:
```json
[
  {"timestamp": 1699123456000, "endpoint": "/api/users", "method": "GET", "status_code": 200, "duration_nanos": 50000000},
  {"timestamp": 1699123456100, "endpoint": "/api/orders", "method": "POST", "status_code": 201, "duration_nanos": 75000000}
]
```

### 4. Parser Interface

```java
public interface TraceParser {
    List<TraceEvent> parse(Path logFile) throws IOException;
}

public class CsvTraceParser implements TraceParser { ... }
public class JsonTraceParser implements TraceParser { ... }
public class CustomTraceParser implements TraceParser { ... }
```

## Implementation Steps

### Phase 1: Core Pattern (Day 1-2)

1. **Create TraceEvent record**
   - Define structure
   - Add validation
   - Add helper methods

2. **Create TraceParser interface**
   - Define parsing contract
   - Implement CSV parser
   - Implement JSON parser

3. **Create TraceReplayLoad class**
   - Implement LoadPattern interface
   - Parse log file on construction
   - Implement calculateTps() with time windowing
   - Handle time scaling

### Phase 2: Advanced Features (Day 3)

1. **Time Scaling**
   - Support faster/slower replay
   - Maintain relative timing
   - Handle edge cases

2. **Filtering**
   - Filter by endpoint
   - Filter by method
   - Filter by time range

3. **Statistics**
   - Track replay accuracy
   - Compare original vs replayed TPS
   - Report discrepancies

### Phase 3: Example & Integration (Day 4)

1. **Create Trace Replay Example**
   - Generate sample trace file
   - Demonstrate replay
   - Show statistics

2. **Integration with ExecutionEngine**
   - Verify compatibility
   - Test with real tasks
   - Performance testing

### Phase 4: Documentation & Testing (Day 5-6)

1. **Documentation**
   - User guide
   - Format specifications
   - Best practices

2. **Testing**
   - Unit tests for parser
   - Unit tests for pattern
   - Integration tests
   - Performance tests

## Example Usage

### Basic Usage

```java
// Replay at original speed
LoadPattern pattern = new TraceReplayLoad(
    Paths.get("production-traffic.csv"),
    Duration.ofMinutes(10),
    1.0  // Original speed
);

// Replay 2x faster
LoadPattern pattern = new TraceReplayLoad(
    Paths.get("production-traffic.csv"),
    Duration.ofMinutes(5),  // Half duration
    2.0  // 2x speed
);

// Replay with filtering
LoadPattern pattern = TraceReplayLoad.builder()
    .logFile(Paths.get("production-traffic.csv"))
    .duration(Duration.ofMinutes(10))
    .timeScale(1.0)
    .filterEndpoint("/api/users")
    .filterMethod("GET")
    .build();
```

### Generating Trace Files

```java
// Example: Generate trace file from access logs
public class AccessLogToTrace {
    public void convert(Path accessLog, Path traceFile) throws IOException {
        // Parse access log format (Apache/Nginx)
        // Convert to trace format
        // Write to CSV/JSON
    }
}
```

## Trace File Format Specifications

### CSV Format

**Required Columns**:
- `timestamp` (long): Milliseconds since epoch

**Optional Columns**:
- `endpoint` (string): Endpoint path
- `method` (string): HTTP method
- `status_code` (int): Response status code
- `duration_nanos` (long): Request duration in nanoseconds

**Example**:
```csv
timestamp,endpoint,method,status_code,duration_nanos
1699123456000,/api/users,GET,200,50000000
1699123456100,/api/orders,POST,201,75000000
```

### JSON Format

**Required Fields**:
- `timestamp` (number): Milliseconds since epoch

**Optional Fields**:
- `endpoint` (string)
- `method` (string)
- `status_code` (number)
- `duration_nanos` (number)

**Example**:
```json
[
  {
    "timestamp": 1699123456000,
    "endpoint": "/api/users",
    "method": "GET",
    "status_code": 200,
    "duration_nanos": 50000000
  }
]
```

## Advanced Features

### 1. Time Scaling

```java
// Replay 10x faster (compress 1 hour into 6 minutes)
LoadPattern pattern = new TraceReplayLoad(
    logFile,
    Duration.ofMinutes(6),
    10.0
);
```

### 2. Filtering

```java
LoadPattern pattern = TraceReplayLoad.builder()
    .logFile(logFile)
    .duration(Duration.ofMinutes(10))
    .filter(e -> e.endpoint().startsWith("/api/"))
    .filter(e -> e.statusCode() < 400)
    .build();
```

### 3. Statistics

```java
TraceReplayLoad pattern = new TraceReplayLoad(...);
// ... run test ...
TraceReplayStatistics stats = pattern.getStatistics();
System.out.println("Original TPS: " + stats.originalAverageTps());
System.out.println("Replayed TPS: " + stats.replayedAverageTps());
System.out.println("Accuracy: " + stats.accuracy() + "%");
```

## Performance Considerations

1. **Memory Usage**
   - Load events into memory (efficient for reasonable file sizes)
   - For very large files, consider streaming approach
   - Provide option to sample events

2. **Calculation Efficiency**
   - Use sorted events for efficient time window queries
   - Consider indexing by time ranges
   - Cache recent calculations

3. **File Parsing**
   - Parse once on construction
   - Cache parsed events
   - Support streaming for large files

## Testing Strategy

1. **Unit Tests**
   - Test CSV parser
   - Test JSON parser
   - Test calculateTps() with various scenarios
   - Test time scaling
   - Test filtering

2. **Integration Tests**
   - Test with ExecutionEngine
   - Test with real trace files
   - Test performance

3. **Example Tests**
   - Verify example works
   - Verify statistics are accurate

## Files to Create/Modify

### New Files
- `vajrapulse-api/src/main/java/com/vajrapulse/api/TraceReplayLoad.java`
- `vajrapulse-api/src/main/java/com/vajrapulse/api/TraceEvent.java`
- `vajrapulse-api/src/main/java/com/vajrapulse/api/TraceParser.java`
- `vajrapulse-api/src/main/java/com/vajrapulse/api/CsvTraceParser.java`
- `vajrapulse-api/src/main/java/com/vajrapulse/api/JsonTraceParser.java`
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/TraceReplayLoadSpec.groovy`
- `vajrapulse-api/src/test/groovy/com/vajrapulse/api/TraceParserSpec.groovy`
- `examples/trace-replay-load-test/` - Example directory

### Modified Files
- `vajrapulse-worker/src/main/java/com/vajrapulse/worker/LoadPatternFactory.java` - Add trace replay support

## Success Criteria

- [ ] TraceReplayLoad implements LoadPattern correctly
- [ ] CSV parser works correctly
- [ ] JSON parser works correctly
- [ ] Time scaling works correctly
- [ ] Filtering works correctly
- [ ] Statistics are accurate
- [ ] Example works end-to-end
- [ ] Documentation complete
- [ ] Tests pass with ≥90% coverage

## Future Enhancements (Post-0.9.6)

- Support for more log formats (Apache, Nginx, etc.)
- Real-time trace replay from streaming sources
- Trace replay with endpoint-specific patterns
- Trace replay with correlation IDs
- Distributed trace replay

