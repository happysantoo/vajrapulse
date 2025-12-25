package com.example.grpc;

import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.TaskResult;
import com.vajrapulse.api.task.VirtualThreads;
import com.example.grpc.proto.ExampleServiceGrpc;
import com.example.grpc.proto.Request;
import com.example.grpc.proto.Response;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example gRPC load test using virtual threads.
 * 
 * <p>This task performs gRPC operations to test gRPC service performance
 * under load. It demonstrates:
 * <ul>
 *   <li>Using @VirtualThreads for I/O-bound gRPC operations</li>
 *   <li>Unary RPC calls</li>
 *   <li>Streaming RPC calls (server, client, bidirectional)</li>
 *   <li>Proper channel management</li>
 * </ul>
 * 
 * <p><strong>Note:</strong> This example requires a running gRPC server.
 * For testing, you can use a mock server or a real gRPC service.
 * 
 * @since 0.9.10
 */
@VirtualThreads
public class GrpcLoadTest implements TaskLifecycle {
    
    private ManagedChannel channel;
    private ExampleServiceGrpc.ExampleServiceBlockingStub blockingStub;
    private ExampleServiceGrpc.ExampleServiceStub asyncStub;
    private String serverAddress;
    private int testId = 1;
    
    /**
     * Default constructor for GrpcLoadTest.
     * Initializes the task for use with VajraPulse execution engine.
     */
    public GrpcLoadTest() {
        // Default constructor - initialization happens in init()
    }
    
    @Override
    public void init() throws Exception {
        // Get server address from environment or use default
        serverAddress = System.getenv("GRPC_SERVER_ADDRESS");
        if (serverAddress == null || serverAddress.isBlank()) {
            serverAddress = System.getProperty("grpc.server.address", "localhost:50051");
        }
        
        // Create gRPC channel
        channel = ManagedChannelBuilder.forTarget(serverAddress)
            .usePlaintext()  // For testing - use TLS in production
            .build();
        
        // Create stubs
        blockingStub = ExampleServiceGrpc.newBlockingStub(channel);
        asyncStub = ExampleServiceGrpc.newStub(channel);
        
        System.out.println("GrpcLoadTest init completed - connected to " + serverAddress);
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        // Perform unary RPC call (simplest case)
        // For streaming examples, see executeStreaming() method
        
        try {
            Request request = Request.newBuilder()
                .setId(testId++)
                .setMessage("Test message " + iteration)
                .setTimestamp(System.currentTimeMillis())
                .build();
            
            // Unary call - synchronous
            Response response = blockingStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .unaryCall(request);
            
            if (response.getSuccess()) {
                return TaskResult.success("gRPC call succeeded: " + response.getMessage());
            } else {
                return TaskResult.failure(new RuntimeException("gRPC call failed"));
            }
        } catch (Exception e) {
            // Handle gRPC errors gracefully
            return TaskResult.failure(e);
        }
    }
    
    /**
     * Example of server streaming RPC (not used by default, but available).
     */
    private TaskResult executeServerStreaming(long iteration) throws Exception {
        Request request = Request.newBuilder()
            .setId(testId++)
            .setMessage("Stream request " + iteration)
            .setTimestamp(System.currentTimeMillis())
            .build();
        
        AtomicInteger responseCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        
        StreamObserver<Response> responseObserver = new StreamObserver<Response>() {
            @Override
            public void onNext(Response response) {
                responseCount.incrementAndGet();
            }
            
            @Override
            public void onError(Throwable t) {
                latch.countDown();
            }
            
            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };
        
        asyncStub.serverStreaming(request, responseObserver);
        
        // Wait for stream to complete (with timeout)
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        
        if (completed && responseCount.get() > 0) {
            return TaskResult.success("Received " + responseCount.get() + " stream responses");
        } else {
            return TaskResult.failure(new RuntimeException("Stream failed or timed out"));
        }
    }
    
    @Override
    public void teardown() throws Exception {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        System.out.println("GrpcLoadTest teardown completed");
    }
}
