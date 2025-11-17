package com.vajrapulse.core.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for VajraPulse load testing framework.
 * 
 * <p>This immutable record holds all configuration settings for the framework.
 * Configuration can be loaded from:
 * <ul>
 *   <li>{@code vajrapulse.conf.yml} or {@code vajrapulse.conf.json}</li>
 *   <li>Environment variables with prefix {@code VAJRAPULSE_}</li>
 *   <li>Programmatic builder for testing</li>
 * </ul>
 * 
 * <p>Environment variables override file configuration. Naming convention:
 * <pre>
 * File:     execution.drainTimeout
 * Env var:  VAJRAPULSE_EXECUTION_DRAIN_TIMEOUT
 * </pre>
 * 
 * <p>Example YAML:
 * <pre>{@code
 * execution:
 *   drainTimeout: 5s
 *   forceTimeout: 10s
 *   defaultThreadPool: virtual
 * 
 * observability:
 *   tracingEnabled: true
 *   metricsEnabled: true
 *   structuredLogging: true
 * }</pre>
 * 
 * @since 0.9.0
 */
public record VajraPulseConfig(
    ExecutionConfig execution,
    ObservabilityConfig observability
) {
    
    /**
     * Creates a configuration with validation.
     * 
     * @param execution execution settings
     * @param observability observability settings
     * @throws IllegalArgumentException if validation fails
     */
    public VajraPulseConfig {
        Objects.requireNonNull(execution, "execution config cannot be null");
        Objects.requireNonNull(observability, "observability config cannot be null");
    }
    
    /**
     * Returns default configuration with sensible values.
     * 
     * @return default configuration
     */
    public static VajraPulseConfig defaults() {
        return new VajraPulseConfig(
            ExecutionConfig.defaults(),
            ObservabilityConfig.defaults()
        );
    }
    
    /**
     * Execution-related configuration.
     */
    public record ExecutionConfig(
        Duration drainTimeout,
        Duration forceTimeout,
        ThreadPoolStrategy defaultThreadPool,
        int platformThreadPoolSize
    ) {
        
        public ExecutionConfig {
            Objects.requireNonNull(drainTimeout, "drainTimeout cannot be null");
            Objects.requireNonNull(forceTimeout, "forceTimeout cannot be null");
            Objects.requireNonNull(defaultThreadPool, "defaultThreadPool cannot be null");
            
            if (drainTimeout.isNegative() || drainTimeout.isZero()) {
                throw new IllegalArgumentException("drainTimeout must be positive: " + drainTimeout);
            }
            if (forceTimeout.isNegative() || forceTimeout.isZero()) {
                throw new IllegalArgumentException("forceTimeout must be positive: " + forceTimeout);
            }
            if (platformThreadPoolSize < -1 || platformThreadPoolSize == 0) {
                throw new IllegalArgumentException(
                    "platformThreadPoolSize must be -1 (auto) or positive: " + platformThreadPoolSize
                );
            }
        }
        
        public static ExecutionConfig defaults() {
            return new ExecutionConfig(
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                ThreadPoolStrategy.VIRTUAL,
                -1 // Auto-detect CPU count
            );
        }
    }
    
    /**
     * Observability configuration.
     */
    public record ObservabilityConfig(
        boolean tracingEnabled,
        boolean metricsEnabled,
        boolean structuredLogging,
        String otlpEndpoint,
        double tracingSampleRate
    ) {
        
        public ObservabilityConfig {
            if (tracingSampleRate < 0.0 || tracingSampleRate > 1.0) {
                throw new IllegalArgumentException(
                    "tracingSampleRate must be between 0.0 and 1.0: " + tracingSampleRate
                );
            }
        }
        
        public static ObservabilityConfig defaults() {
            return new ObservabilityConfig(
                false, // Tracing disabled by default
                true,  // Metrics always enabled
                true,  // Structured logging enabled
                "http://localhost:4318", // Default OTLP endpoint
                0.05   // 5% sampling
            );
        }
    }
    
    /**
     * Thread pool strategy.
     */
    public enum ThreadPoolStrategy {
        /** Virtual threads (Java 21+) - recommended for I/O-bound tasks */
        VIRTUAL,
        
        /** Platform threads - for CPU-bound tasks */
        PLATFORM,
        
        /** Automatic selection based on task annotations */
        AUTO
    }
}
