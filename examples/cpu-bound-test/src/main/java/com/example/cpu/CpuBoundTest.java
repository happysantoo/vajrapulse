package com.example.cpu;

import com.vajrapulse.api.task.TaskLifecycle;
import com.vajrapulse.api.task.TaskResult;
import com.vajrapulse.api.task.PlatformThreads;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Example CPU-bound load test using encryption and compression.
 * 
 * <p>This task performs CPU-intensive operations to test system performance
 * under CPU load. It demonstrates:
 * <ul>
 *   <li>Using @PlatformThreads for CPU-bound operations</li>
 *   <li>Encryption/decryption workloads</li>
 *   <li>Compression/decompression workloads</li>
 *   <li>Proper resource management</li>
 * </ul>
 * 
 * <p><strong>Why Platform Threads?</strong> CPU-bound operations benefit from
 * platform threads because:
 * <ul>
 *   <li>Virtual threads are designed for I/O-bound tasks</li>
 *   <li>CPU-bound work blocks the carrier thread</li>
 *   <li>Platform threads allow true parallelism on multi-core systems</li>
 * </ul>
 * 
 * @since 0.9.10
 */
@PlatformThreads
public class CpuBoundTest implements TaskLifecycle {
    
    private SecretKey secretKey;
    private Cipher encryptCipher;
    private Cipher decryptCipher;
    private byte[] testData;
    
    /**
     * Default constructor for CpuBoundTest.
     * Initializes the task for use with VajraPulse execution engine.
     */
    public CpuBoundTest() {
        // Default constructor - initialization happens in init()
    }
    
    @Override
    public void init() throws Exception {
        // Generate encryption key
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        secretKey = keyGen.generateKey();
        
        // Initialize ciphers
        encryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
        encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey);
        
        decryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKey);
        
        // Generate test data (1KB of random bytes)
        testData = new byte[1024];
        new SecureRandom().nextBytes(testData);
        
        System.out.println("CpuBoundTest init completed - encryption/compression ready");
    }
    
    @Override
    public TaskResult execute(long iteration) throws Exception {
        // Perform CPU-intensive operations:
        // 1. Encrypt data
        // 2. Compress data
        // 3. Decompress data
        // 4. Decrypt data
        
        try {
            // 1. Encrypt
            byte[] encrypted = encryptCipher.doFinal(testData);
            
            // 2. Compress
            byte[] compressed = compress(encrypted);
            
            // 3. Decompress
            byte[] decompressed = decompress(compressed);
            
            // 4. Decrypt
            byte[] decrypted = decryptCipher.doFinal(decompressed);
            
            // Verify data integrity
            if (!java.util.Arrays.equals(testData, decrypted)) {
                return TaskResult.failure(new RuntimeException("Data integrity check failed"));
            }
            
            return TaskResult.success("Encrypted/compressed/decompressed/decrypted " + 
                testData.length + " bytes");
        } catch (Exception e) {
            return TaskResult.failure(e);
        }
    }
    
    /**
     * Compresses data using Deflater.
     */
    private byte[] compress(byte[] data) throws Exception {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        
        byte[] buffer = new byte[data.length];
        int compressedLength = deflater.deflate(buffer);
        deflater.end();
        
        byte[] result = new byte[compressedLength];
        System.arraycopy(buffer, 0, result, 0, compressedLength);
        return result;
    }
    
    /**
     * Decompresses data using Inflater.
     */
    private byte[] decompress(byte[] compressed) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        
        byte[] buffer = new byte[compressed.length * 2];
        int decompressedLength = inflater.inflate(buffer);
        inflater.end();
        
        byte[] result = new byte[decompressedLength];
        System.arraycopy(buffer, 0, result, 0, decompressedLength);
        return result;
    }
    
    @Override
    public void teardown() throws Exception {
        // Clear sensitive data
        if (secretKey != null) {
            // Key is automatically cleared when GC'd
            secretKey = null;
        }
        encryptCipher = null;
        decryptCipher = null;
        testData = null;
        
        System.out.println("CpuBoundTest teardown completed");
    }
}
