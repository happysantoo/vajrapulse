# Migration Guide: Task to TaskLifecycle

**Version**: 0.9.5  
**Status**: Migration Guide  
**Removal Target**: 0.9.6

---

## Overview

The `Task` interface has been deprecated in favor of `TaskLifecycle`, which provides clearer lifecycle semantics and better alignment with modern Java patterns. This guide helps you migrate existing `Task` implementations to `TaskLifecycle`.

---

## Why Migrate?

1. **Clearer Semantics**: `TaskLifecycle` uses explicit method names (`init`, `teardown`) instead of generic ones (`setup`, `cleanup`)
2. **Iteration Awareness**: `execute(long iteration)` provides iteration numbers for correlation IDs, test data selection, etc.
3. **Future-Proof**: `Task` will be removed in 0.9.6
4. **Better Documentation**: `TaskLifecycle` has comprehensive JavaDoc with examples

---

## Quick Reference

| Task (Deprecated) | TaskLifecycle (New) | Notes |
|-------------------|---------------------|-------|
| `setup()` | `init()` | Called once before executions |
| `execute()` | `execute(long iteration)` | Called repeatedly; iteration starts at 0 |
| `cleanup()` | `teardown()` | Called once after executions |

---

## Migration Steps

### Step 1: Change Interface Declaration

**Before:**
```java
@VirtualThreads
public class MyHttpTest implements Task {
    // ...
}
```

**After:**
```java
@VirtualThreads
public class MyHttpTest implements TaskLifecycle {
    // ...
}
```

### Step 2: Rename Methods

**Before:**
```java
@Override
public void setup() throws Exception {
    // Initialize resources
    this.client = HttpClient.newHttpClient();
}

@Override
public TaskResult execute() throws Exception {
    // Execute test
    return TaskResult.success();
}

@Override
public void cleanup() throws Exception {
    // Cleanup resources
    if (client != null) {
        client.close();
    }
}
```

**After:**
```java
@Override
public void init() throws Exception {
    // Initialize resources
    this.client = HttpClient.newHttpClient();
}

@Override
public TaskResult execute(long iteration) throws Exception {
    // Execute test
    // iteration parameter available for correlation IDs, etc.
    return TaskResult.success();
}

@Override
public void teardown() throws Exception {
    // Cleanup resources
    if (client != null) {
        client.close();
    }
}
```

### Step 3: Use Iteration Parameter (Optional)

The `execute(long iteration)` method provides an iteration number that starts at 0. You can use it for:

**Correlation IDs:**
```java
@Override
public TaskResult execute(long iteration) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.example.com/status"))
        .header("X-Correlation-ID", "test-" + iteration)
        .GET()
        .build();
    // ...
}
```

**Test Data Selection:**
```java
private final List<String> testData = Arrays.asList("user1", "user2", "user3");

@Override
public TaskResult execute(long iteration) throws Exception {
    String userId = testData.get((int) (iteration % testData.size()));
    // Use userId in request
}
```

**Debugging:**
```java
@Override
public TaskResult execute(long iteration) throws Exception {
    if (iteration % 1000 == 0) {
        logger.debug("Completed {} iterations", iteration);
    }
    // ...
}
```

---

## Complete Example

### Before (Task Interface)

```java
package com.example.http;

import com.vajrapulse.api.Task;
import com.vajrapulse.api.TaskResult;
import com.vajrapulse.api.VirtualThreads;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

@VirtualThreads
public class HttpLoadTest implements Task {
    private HttpClient client;
    
    @Override
    public void setup() throws Exception {
        this.client = HttpClient.newHttpClient();
    }
    
    @Override
    public TaskResult execute() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/status"))
            .GET()
            .build();
        
        HttpResponse<String> response = client.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return TaskResult.success();
        } else {
            return TaskResult.failure(
                new RuntimeException("HTTP " + response.statusCode())
            );
        }
    }
    
    @Override
    public void cleanup() throws Exception {
        // HttpClient doesn't need explicit close
    }
}
```

### After (TaskLifecycle Interface)

```java
package com.example.http;

import com.vajrapulse.api.TaskLifecycle;
import com.vajrapulse.api.TaskResult;
import com.vajrapulse.api.VirtualThreads;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

@VirtualThreads
public class HttpLoadTest implements TaskLifecycle {
    private HttpClient client;
    
    @Override
    public void init() throws Exception {
        this.client = HttpClient.newHttpClient();
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/status"))
            .header("X-Iteration", String.valueOf(iteration))
            .GET()
            .build();
        
        HttpResponse<String> response = client.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return TaskResult.success();
        } else {
            return TaskResult.failure(
                new RuntimeException("HTTP " + response.statusCode())
            );
        }
    }
    
    @Override
    public void teardown() throws Exception {
        // HttpClient doesn't need explicit close
    }
}
```

---

## Backward Compatibility

**Good News**: Existing `Task` implementations continue to work! The framework automatically adapts them:

- `setup()` → `init()`
- `execute()` → `execute(0)`, `execute(1)`, ... (iteration ignored)
- `cleanup()` → `teardown()`

You can migrate at your own pace, but we recommend migrating before 0.9.6 to avoid breaking changes.

---

## Common Patterns

### Pattern 1: Resource Initialization

**Task:**
```java
private HttpClient client;

@Override
public void setup() throws Exception {
    this.client = HttpClient.newHttpClient();
}
```

**TaskLifecycle:**
```java
private HttpClient client;

@Override
public void init() throws Exception {
    this.client = HttpClient.newHttpClient();
}
```

### Pattern 2: Iteration-Based Correlation

**Task:**
```java
private long iteration = 0;

@Override
public TaskResult execute() throws Exception {
    long currentIteration = iteration++;
    // Use currentIteration
}
```

**TaskLifecycle:**
```java
@Override
public TaskResult execute(long iteration) throws Exception {
    // Use iteration parameter directly
}
```

### Pattern 3: Resource Cleanup

**Task:**
```java
@Override
public void cleanup() throws Exception {
    if (client != null) {
        client.close();
    }
}
```

**TaskLifecycle:**
```java
@Override
public void teardown() throws Exception {
    if (client != null) {
        client.close();
    }
}
```

---

## FAQ

### Q: Do I need to migrate immediately?

**A**: No. `Task` will continue to work until 0.9.6. However, we recommend migrating before then to avoid breaking changes.

### Q: Can I use both interfaces?

**A**: Yes, but it's not recommended. `Task` extends `TaskLifecycle`, so if you implement `Task`, you're also implementing `TaskLifecycle`. However, you should choose one and stick with it.

### Q: What happens to the iteration parameter if I don't use it?

**A**: Nothing. You can ignore it if you don't need it. The framework will still call your method with the iteration number.

### Q: Will my existing code break?

**A**: No. Existing `Task` implementations continue to work. The framework adapts them automatically.

### Q: When will Task be removed?

**A**: `Task` will be removed in version 0.9.6. Migrate before then to avoid breaking changes.

---

## Additional Resources

- [TaskLifecycle JavaDoc](../vajrapulse-api/src/main/java/com/vajrapulse/api/TaskLifecycle.java)
- [Task JavaDoc](../vajrapulse-api/src/main/java/com/vajrapulse/api/Task.java) (deprecated)
- [Examples](../examples/)

---

**Last Updated**: 2025-01-XX  
**Next Review**: Before 0.9.6 release

