# VajraPulse Quick Start

Get running in under 2 minutes!

## Prerequisites

- Java 21+
- Gradle 8+ (or use included wrapper)

## 1. Add Dependency

**Gradle (Kotlin DSL):**
```kotlin
dependencies {
    implementation("com.vajrapulse:vajrapulse-core:0.9.11")
}
```

**Maven:**
```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vajrapulse-core</artifactId>
    <version>0.9.11</version>
</dependency>
```

## 2. Create Your First Load Test

```java
import com.vajrapulse.api.task.*;
import com.vajrapulse.api.pattern.*;
import com.vajrapulse.core.engine.*;

import java.net.http.*;
import java.time.Duration;

public class MyFirstLoadTest implements Task {
    private HttpClient client;
    
    @Override
    public void setup() {
        client = HttpClient.newHttpClient();
    }
    
    @Override
    public TaskResult execute() {
        try {
            var request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://httpbin.org/get"))
                .GET()
                .build();
            var response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 
                ? TaskResult.success() 
                : TaskResult.failure("Status: " + response.statusCode());
        } catch (Exception e) {
            return TaskResult.failure(e.getMessage());
        }
    }
    
    public static void main(String[] args) throws Exception {
        var pattern = new RampUp(
            10.0,                      // Start: 10 TPS
            100.0,                     // End: 100 TPS  
            Duration.ofSeconds(30)     // Over 30 seconds
        );
        
        try (var engine = ExecutionEngine.create(new MyFirstLoadTest(), pattern)) {
            engine.run();
            var metrics = engine.getMetrics();
            System.out.printf("Total: %d, Success: %d, P99: %.2fms%n",
                metrics.totalExecutions(),
                metrics.successfulExecutions(),
                metrics.successPercentiles().getOrDefault(0.99, 0.0) / 1_000_000);
        }
    }
}
```

## 3. Run It

```bash
./gradlew run
# or
java --enable-preview -cp "build/libs/*" MyFirstLoadTest
```

## Expected Output

```
Starting load test...
[INFO] Load pattern: RampUp (10.0 → 100.0 TPS over 30s)
[INFO] Test completed
Total: 1650, Success: 1648, P99: 245.32ms
```

## Next Steps

- **Load Patterns**: Try `StaticLoad`, `StepLoad`, `AdaptiveLoadPattern`
- **Metrics Export**: Add `vajrapulse-exporter-console` for live stats
- **Examples**: See `examples/` directory for more scenarios
- **Docs**: Read the full [README](README.md)

## Troubleshooting

**"preview features" error?**
Add `--enable-preview` to Java args (required for Java 21 features)

**Low throughput?**
- Check target system capacity
- Increase JVM heap: `-Xmx2g`
- Use `@VirtualThreads` annotation (default)

**Connection errors?**
- Verify target URL is accessible
- Check firewall/proxy settings
- Reduce initial TPS and ramp up slower
