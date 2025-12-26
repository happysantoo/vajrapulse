# gRPC Load Test Example

This example demonstrates how to perform load testing on gRPC services using VajraPulse with virtual threads.

## Features

- **Virtual Threads**: Uses `@VirtualThreads` for efficient I/O-bound gRPC operations
- **Unary RPC**: Simple request/response calls
- **Streaming RPC**: Server, client, and bidirectional streaming examples
- **Protocol Buffers**: Uses protobuf for message serialization

## Prerequisites

1. **gRPC Server**: You need a running gRPC server to test against
2. **Protocol Buffers**: The build automatically generates Java code from `.proto` files

## Quick Start

### 1. Set Server Address

```bash
export GRPC_SERVER_ADDRESS="localhost:50051"
```

Or use a system property:
```bash
-Dgrpc.server.address=localhost:50051
```

### 2. Run the Test

```bash
./gradlew :examples:grpc-load-test:run
```

## Custom Configuration

### Command-Line Arguments

```bash
# Run at 100 TPS
./gradlew :examples:grpc-load-test:run --args "100"
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `GRPC_SERVER_ADDRESS` | gRPC server address | `localhost:50051` |

## What It Tests

The example performs gRPC operations:

1. **Unary Call**: Simple request/response RPC
2. **Server Streaming**: Server sends multiple responses (example code included)
3. **Client Streaming**: Client sends multiple requests (example code included)
4. **Bidirectional Streaming**: Both sides stream (example code included)

By default, only unary calls are executed. Modify `GrpcLoadTest.execute()` to use streaming methods.

## Protocol Buffer Definition

The example uses a simple proto definition (`example.proto`):

```protobuf
service ExampleService {
  rpc UnaryCall (Request) returns (Response);
  rpc ServerStreaming (Request) returns (stream Response);
  rpc ClientStreaming (stream Request) returns (Response);
  rpc BidirectionalStreaming (stream Request) returns (stream Response);
}
```

## Building

The build automatically:
1. Compiles `.proto` files to Java code
2. Generates gRPC service stubs
3. Includes all necessary dependencies

## Using with Real gRPC Services

### Example: Testing a Real Service

```bash
# Start your gRPC server
./your-grpc-server

# In another terminal, run the load test
export GRPC_SERVER_ADDRESS="localhost:8080"
./gradlew :examples:grpc-load-test:run
```

### Example: Testing with TLS

Modify `GrpcLoadTest.init()` to use TLS:

```java
channel = ManagedChannelBuilder.forTarget(serverAddress)
    .useTransportSecurity()  // Use TLS
    .build();
```

## Expected Output

```
Starting gRPC load test:
  TPS: 50.0
  Duration: PT30S
  Server: localhost:50051
  Thread Strategy: Virtual Threads (I/O-bound)

GrpcLoadTest init completed - connected to localhost:50051
Starting load test runId=... pattern=StaticLoad duration=PT30S
...
=== Final Results ===
[Console metrics output]
```

## Troubleshooting

### Connection Refused

**Problem**: "io.grpc.StatusRuntimeException: UNAVAILABLE"

**Solutions**:
1. Verify gRPC server is running: `netstat -an | grep 50051`
2. Check firewall rules
3. Verify server address is correct

### Protocol Buffer Errors

**Problem**: "Cannot find symbol: ExampleServiceGrpc"

**Solution**: Rebuild the project:
```bash
./gradlew :examples:grpc-load-test:clean :examples:grpc-load-test:build
```

### Timeout Errors

**Problem**: "DEADLINE_EXCEEDED"

**Solutions**:
1. Increase deadline in `execute()` method
2. Check server performance
3. Reduce TPS rate

## Extending the Example

### Add Custom RPC Methods

1. Update `example.proto`:
```protobuf
rpc CustomMethod (CustomRequest) returns (CustomResponse);
```

2. Rebuild to generate new stubs
3. Use the new stub in `GrpcLoadTest`

### Add Authentication

```java
channel = ManagedChannelBuilder.forTarget(serverAddress)
    .useTransportSecurity()
    .intercept(MetadataUtils.newAttachHeadersInterceptor(headers))
    .build();
```

### Add Retry Logic

```java
blockingStub = blockingStub
    .withRetryPolicy(RetryPolicy.builder()
        .maxAttempts(3)
        .build());
```

## See Also

- [HTTP Load Test Example](../http-load-test/README.md)
- [Database Load Test Example](../database-load-test/README.md)
- [gRPC Documentation](https://grpc.io/docs/)
- [Protocol Buffers Guide](https://protobuf.dev/getting-started/javatutorial/)
