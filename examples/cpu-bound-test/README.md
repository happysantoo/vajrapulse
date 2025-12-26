# CPU-Bound Load Test Example

This example demonstrates how to perform load testing on CPU-intensive operations using VajraPulse with platform threads.

## Features

- **Platform Threads**: Uses `@PlatformThreads` for CPU-bound operations
- **Encryption/Decryption**: AES-GCM encryption workload
- **Compression**: Deflate/Inflate compression workload
- **CPU Utilization**: Designed to stress CPU cores

## Quick Start

```bash
./gradlew :examples:cpu-bound-test:run
```

This runs at 10 TPS for 30 seconds by default.

## Custom Configuration

### Command-Line Arguments

```bash
# Run at 20 TPS for 60 seconds
./gradlew :examples:cpu-bound-test:run --args "20"
```

## Why Platform Threads?

This example uses `@PlatformThreads` instead of `@VirtualThreads` because:

1. **CPU-Bound Work**: Encryption and compression are CPU-intensive
2. **Virtual Thread Limitations**: Virtual threads are designed for I/O-bound tasks
3. **True Parallelism**: Platform threads allow parallel execution on multiple CPU cores
4. **No Blocking I/O**: No network or file I/O that would benefit from virtual threads

### When to Use Each Thread Strategy

| Thread Strategy | Use Case | Example |
|----------------|----------|---------|
| `@VirtualThreads` | I/O-bound operations | HTTP requests, database queries, file I/O |
| `@PlatformThreads` | CPU-bound operations | Encryption, compression, calculations |
| Default (Virtual) | Mixed or unknown | General-purpose tasks |

## What It Tests

The example performs CPU-intensive operations:

1. **Encrypt**: AES-GCM encryption of 1KB data
2. **Compress**: Deflate compression of encrypted data
3. **Decompress**: Inflate decompression
4. **Decrypt**: AES-GCM decryption
5. **Verify**: Data integrity check

Each iteration processes 1KB of data through the full pipeline.

## Performance Characteristics

### Expected Behavior

- **CPU Utilization**: High (near 100% on available cores)
- **Latency**: Higher than I/O-bound tasks (milliseconds)
- **Throughput**: Limited by CPU capacity
- **Scaling**: Scales with number of CPU cores

### Monitoring

Monitor CPU usage during the test:
```bash
# Linux/macOS
top -p $(pgrep -f CpuBoundTestRunner)

# Or use htop
htop -p $(pgrep -f CpuBoundTestRunner)
```

## Tuning Tips

1. **TPS Rate**: Start low (5-10 TPS) and increase based on CPU capacity
2. **Core Count**: Higher TPS works better on multi-core systems
3. **Thread Pool Size**: Platform thread pool size can be configured in ExecutionEngine
4. **Data Size**: Adjust `testData` size in `CpuBoundTest.java` for different workloads

## Expected Output

```
Starting CPU-bound load test:
  TPS: 10.0
  Duration: PT30S
  Thread Strategy: Platform Threads (CPU-bound)

CpuBoundTest init completed - encryption/compression ready
Starting load test runId=... pattern=StaticLoad duration=PT30S
...
=== Final Results ===
[Console metrics output]
```

## Extending the Example

### Add More CPU Work

```java
// Add mathematical computation
private double computePi(int iterations) {
    double pi = 0.0;
    for (int i = 0; i < iterations; i++) {
        pi += Math.pow(-1, i) / (2 * i + 1);
    }
    return pi * 4;
}
```

### Add Image Processing

```java
// Add image processing (requires additional dependencies)
private BufferedImage processImage(BufferedImage image) {
    // Apply filters, transformations, etc.
    return image;
}
```

## See Also

- [HTTP Load Test Example](../http-load-test/README.md) - I/O-bound example
- [Database Load Test Example](../database-load-test/README.md) - I/O-bound example
- [VajraPulse Main Documentation](../../README.md)
