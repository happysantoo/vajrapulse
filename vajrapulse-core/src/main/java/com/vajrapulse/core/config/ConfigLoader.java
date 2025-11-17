package com.vajrapulse.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads VajraPulse configuration from files and environment variables.
 * 
 * <p>Configuration loading order (later overrides earlier):
 * <ol>
 *   <li>Default values from {@link VajraPulseConfig#defaults()}</li>
 *   <li>File: {@code vajrapulse.conf.yml} or {@code vajrapulse.conf.json}</li>
 *   <li>Environment variables with {@code VAJRAPULSE_} prefix</li>
 * </ol>
 * 
 * <p>File locations searched (in order):
 * <ul>
 *   <li>Current directory: {@code ./vajrapulse.conf.yml}</li>
 *   <li>User home: {@code ~/.vajrapulse/vajrapulse.conf.yml}</li>
 *   <li>System: {@code /etc/vajrapulse/vajrapulse.conf.yml}</li>
 * </ul>
 * 
 * <p>Environment variable mapping:
 * <pre>
 * execution.drainTimeout        → VAJRAPULSE_EXECUTION_DRAIN_TIMEOUT
 * observability.tracingEnabled  → VAJRAPULSE_OBSERVABILITY_TRACING_ENABLED
 * </pre>
 * 
 * <p>Duration format: {@code 5s}, {@code 10m}, {@code 1h}, {@code 500ms}
 * 
 * <p>Example usage:
 * <pre>{@code
 * VajraPulseConfig config = ConfigLoader.load();
 * Duration timeout = config.execution().drainTimeout();
 * }</pre>
 * 
 * @since 0.9.0
 */
public final class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    
    private static final String CONFIG_FILE_YAML = "vajrapulse.conf.yml";
    private static final String CONFIG_FILE_JSON = "vajrapulse.conf.json";
    private static final String ENV_PREFIX = "VAJRAPULSE_";
    
    private ConfigLoader() {
        // Utility class
    }
    
    /**
     * Loads configuration from default locations with environment overrides.
     * 
     * <p>If no configuration file is found, returns defaults with environment overrides.
     * 
     * @return loaded and validated configuration
     * @throws ConfigurationException if configuration is invalid
     */
    public static VajraPulseConfig load() {
        return load(null);
    }
    
    /**
     * Loads configuration from specified file with environment overrides.
     * 
     * @param configPath path to configuration file, or null for default locations
     * @return loaded and validated configuration
     * @throws ConfigurationException if configuration is invalid
     */
    public static VajraPulseConfig load(Path configPath) {
        List<String> errors = new ArrayList<>();
        
        try {
            // Start with defaults
            VajraPulseConfig config = VajraPulseConfig.defaults();
            logger.debug("Loaded default configuration");
            
            // Load from file if available
            Path resolvedPath = configPath != null ? configPath : findConfigFile();
            if (resolvedPath != null) {
                config = loadFromFile(resolvedPath);
                logger.info("Loaded configuration from: {}", resolvedPath);
            } else {
                logger.info("No configuration file found, using defaults");
            }
            
            // Apply environment variable overrides
            config = applyEnvironmentOverrides(config, errors);
            
            // Validate
            validate(config, errors);
            
            if (!errors.isEmpty()) {
                throw new ConfigurationException("Configuration validation failed", errors);
            }
            
            return config;
            
        } catch (ConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigurationException("Failed to load configuration: " + e.getMessage(), e);
        }
    }
    
    /**
     * Finds configuration file in default locations.
     * 
     * @return path to config file, or null if not found
     */
    private static Path findConfigFile() {
        List<Path> searchPaths = List.of(
            Paths.get(CONFIG_FILE_YAML),
            Paths.get(CONFIG_FILE_JSON),
            Paths.get(System.getProperty("user.home"), ".vajrapulse", CONFIG_FILE_YAML),
            Paths.get(System.getProperty("user.home"), ".vajrapulse", CONFIG_FILE_JSON),
            Paths.get("/etc/vajrapulse", CONFIG_FILE_YAML),
            Paths.get("/etc/vajrapulse", CONFIG_FILE_JSON)
        );
        
        for (Path path : searchPaths) {
            if (Files.exists(path) && Files.isReadable(path)) {
                logger.debug("Found config file: {}", path);
                return path;
            }
        }
        
        return null;
    }
    
    /**
     * Loads configuration from file.
     */
    private static VajraPulseConfig loadFromFile(Path path) throws IOException {
        Yaml yaml = new Yaml();
        
        try (InputStream input = Files.newInputStream(path)) {
            Map<String, Object> data = yaml.load(input);
            if (data == null || data.isEmpty()) {
                logger.warn("Configuration file is empty: {}", path);
                return VajraPulseConfig.defaults();
            }
            
            return parseConfig(data);
        }
    }
    
    /**
     * Parses configuration from map structure.
     */
    @SuppressWarnings("unchecked")
    private static VajraPulseConfig parseConfig(Map<String, Object> data) {
        // Parse execution config
        Map<String, Object> execMap = (Map<String, Object>) data.get("execution");
        VajraPulseConfig.ExecutionConfig execution = execMap != null
            ? parseExecutionConfig(execMap)
            : VajraPulseConfig.ExecutionConfig.defaults();
        
        // Parse observability config
        Map<String, Object> obsMap = (Map<String, Object>) data.get("observability");
        VajraPulseConfig.ObservabilityConfig observability = obsMap != null
            ? parseObservabilityConfig(obsMap)
            : VajraPulseConfig.ObservabilityConfig.defaults();
        
        return new VajraPulseConfig(execution, observability);
    }
    
    private static VajraPulseConfig.ExecutionConfig parseExecutionConfig(Map<String, Object> map) {
        VajraPulseConfig.ExecutionConfig defaults = VajraPulseConfig.ExecutionConfig.defaults();
        
        Duration drainTimeout = parseDuration(
            (String) map.get("drainTimeout"),
            defaults.drainTimeout()
        );
        
        Duration forceTimeout = parseDuration(
            (String) map.get("forceTimeout"),
            defaults.forceTimeout()
        );
        
        VajraPulseConfig.ThreadPoolStrategy strategy = parseThreadPoolStrategy(
            (String) map.get("defaultThreadPool"),
            defaults.defaultThreadPool()
        );
        
        Integer poolSize = (Integer) map.get("platformThreadPoolSize");
        int platformPoolSize = poolSize != null ? poolSize : defaults.platformThreadPoolSize();
        
        return new VajraPulseConfig.ExecutionConfig(
            drainTimeout,
            forceTimeout,
            strategy,
            platformPoolSize
        );
    }
    
    private static VajraPulseConfig.ObservabilityConfig parseObservabilityConfig(Map<String, Object> map) {
        VajraPulseConfig.ObservabilityConfig defaults = VajraPulseConfig.ObservabilityConfig.defaults();
        
        Boolean tracing = (Boolean) map.get("tracingEnabled");
        boolean tracingEnabled = tracing != null ? tracing : defaults.tracingEnabled();
        
        Boolean metrics = (Boolean) map.get("metricsEnabled");
        boolean metricsEnabled = metrics != null ? metrics : defaults.metricsEnabled();
        
        Boolean logging = (Boolean) map.get("structuredLogging");
        boolean structuredLogging = logging != null ? logging : defaults.structuredLogging();
        
        String endpoint = (String) map.get("otlpEndpoint");
        String otlpEndpoint = endpoint != null ? endpoint : defaults.otlpEndpoint();
        
        Object sampleRate = map.get("tracingSampleRate");
        double tracingSampleRate = sampleRate != null
            ? (sampleRate instanceof Number ? ((Number) sampleRate).doubleValue() : Double.parseDouble(sampleRate.toString()))
            : defaults.tracingSampleRate();
        
        return new VajraPulseConfig.ObservabilityConfig(
            tracingEnabled,
            metricsEnabled,
            structuredLogging,
            otlpEndpoint,
            tracingSampleRate
        );
    }
    
    /**
     * Parses duration string (e.g., "5s", "10m", "1h", "500ms").
     */
    private static Duration parseDuration(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        
        value = value.trim();
        
        if (value.endsWith("ms")) {
            long millis = Long.parseLong(value.substring(0, value.length() - 2));
            return Duration.ofMillis(millis);
        } else if (value.endsWith("s")) {
            long seconds = Long.parseLong(value.substring(0, value.length() - 1));
            return Duration.ofSeconds(seconds);
        } else if (value.endsWith("m")) {
            long minutes = Long.parseLong(value.substring(0, value.length() - 1));
            return Duration.ofMinutes(minutes);
        } else if (value.endsWith("h")) {
            long hours = Long.parseLong(value.substring(0, value.length() - 1));
            return Duration.ofHours(hours);
        } else {
            throw new IllegalArgumentException("Invalid duration format: " + value + 
                " (expected format: 5s, 10m, 1h, 500ms)");
        }
    }
    
    private static VajraPulseConfig.ThreadPoolStrategy parseThreadPoolStrategy(String value, VajraPulseConfig.ThreadPoolStrategy defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        
        try {
            return VajraPulseConfig.ThreadPoolStrategy.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid thread pool strategy: " + value + 
                " (expected: virtual, platform, auto)");
        }
    }
    
    /**
     * Applies environment variable overrides to configuration.
     */
    private static VajraPulseConfig applyEnvironmentOverrides(VajraPulseConfig config, List<String> errors) {
        VajraPulseConfig.ExecutionConfig execution = config.execution();
        VajraPulseConfig.ObservabilityConfig observability = config.observability();
        
        // Execution overrides
        String drainTimeoutEnv = System.getenv("VAJRAPULSE_EXECUTION_DRAIN_TIMEOUT");
        if (drainTimeoutEnv != null) {
            try {
                execution = new VajraPulseConfig.ExecutionConfig(
                    parseDuration(drainTimeoutEnv, execution.drainTimeout()),
                    execution.forceTimeout(),
                    execution.defaultThreadPool(),
                    execution.platformThreadPoolSize()
                );
                logger.debug("Override: execution.drainTimeout = {}", drainTimeoutEnv);
            } catch (Exception e) {
                errors.add("Invalid VAJRAPULSE_EXECUTION_DRAIN_TIMEOUT: " + e.getMessage());
            }
        }
        
        String forceTimeoutEnv = System.getenv("VAJRAPULSE_EXECUTION_FORCE_TIMEOUT");
        if (forceTimeoutEnv != null) {
            try {
                execution = new VajraPulseConfig.ExecutionConfig(
                    execution.drainTimeout(),
                    parseDuration(forceTimeoutEnv, execution.forceTimeout()),
                    execution.defaultThreadPool(),
                    execution.platformThreadPoolSize()
                );
                logger.debug("Override: execution.forceTimeout = {}", forceTimeoutEnv);
            } catch (Exception e) {
                errors.add("Invalid VAJRAPULSE_EXECUTION_FORCE_TIMEOUT: " + e.getMessage());
            }
        }
        
        String threadPoolEnv = System.getenv("VAJRAPULSE_EXECUTION_DEFAULT_THREAD_POOL");
        if (threadPoolEnv != null) {
            try {
                execution = new VajraPulseConfig.ExecutionConfig(
                    execution.drainTimeout(),
                    execution.forceTimeout(),
                    parseThreadPoolStrategy(threadPoolEnv, execution.defaultThreadPool()),
                    execution.platformThreadPoolSize()
                );
                logger.debug("Override: execution.defaultThreadPool = {}", threadPoolEnv);
            } catch (Exception e) {
                errors.add("Invalid VAJRAPULSE_EXECUTION_DEFAULT_THREAD_POOL: " + e.getMessage());
            }
        }
        
        String poolSizeEnv = System.getenv("VAJRAPULSE_EXECUTION_PLATFORM_THREAD_POOL_SIZE");
        if (poolSizeEnv != null) {
            try {
                execution = new VajraPulseConfig.ExecutionConfig(
                    execution.drainTimeout(),
                    execution.forceTimeout(),
                    execution.defaultThreadPool(),
                    Integer.parseInt(poolSizeEnv)
                );
                logger.debug("Override: execution.platformThreadPoolSize = {}", poolSizeEnv);
            } catch (Exception e) {
                errors.add("Invalid VAJRAPULSE_EXECUTION_PLATFORM_THREAD_POOL_SIZE: " + e.getMessage());
            }
        }
        
        // Observability overrides
        String tracingEnabledEnv = System.getenv("VAJRAPULSE_OBSERVABILITY_TRACING_ENABLED");
        if (tracingEnabledEnv != null) {
            observability = new VajraPulseConfig.ObservabilityConfig(
                Boolean.parseBoolean(tracingEnabledEnv),
                observability.metricsEnabled(),
                observability.structuredLogging(),
                observability.otlpEndpoint(),
                observability.tracingSampleRate()
            );
            logger.debug("Override: observability.tracingEnabled = {}", tracingEnabledEnv);
        }
        
        String metricsEnabledEnv = System.getenv("VAJRAPULSE_OBSERVABILITY_METRICS_ENABLED");
        if (metricsEnabledEnv != null) {
            observability = new VajraPulseConfig.ObservabilityConfig(
                observability.tracingEnabled(),
                Boolean.parseBoolean(metricsEnabledEnv),
                observability.structuredLogging(),
                observability.otlpEndpoint(),
                observability.tracingSampleRate()
            );
            logger.debug("Override: observability.metricsEnabled = {}", metricsEnabledEnv);
        }
        
        String loggingEnv = System.getenv("VAJRAPULSE_OBSERVABILITY_STRUCTURED_LOGGING");
        if (loggingEnv != null) {
            observability = new VajraPulseConfig.ObservabilityConfig(
                observability.tracingEnabled(),
                observability.metricsEnabled(),
                Boolean.parseBoolean(loggingEnv),
                observability.otlpEndpoint(),
                observability.tracingSampleRate()
            );
            logger.debug("Override: observability.structuredLogging = {}", loggingEnv);
        }
        
        String otlpEndpointEnv = System.getenv("VAJRAPULSE_OBSERVABILITY_OTLP_ENDPOINT");
        if (otlpEndpointEnv != null) {
            observability = new VajraPulseConfig.ObservabilityConfig(
                observability.tracingEnabled(),
                observability.metricsEnabled(),
                observability.structuredLogging(),
                otlpEndpointEnv,
                observability.tracingSampleRate()
            );
            logger.debug("Override: observability.otlpEndpoint = {}", otlpEndpointEnv);
        }
        
        String sampleRateEnv = System.getenv("VAJRAPULSE_OBSERVABILITY_TRACING_SAMPLE_RATE");
        if (sampleRateEnv != null) {
            try {
                observability = new VajraPulseConfig.ObservabilityConfig(
                    observability.tracingEnabled(),
                    observability.metricsEnabled(),
                    observability.structuredLogging(),
                    observability.otlpEndpoint(),
                    Double.parseDouble(sampleRateEnv)
                );
                logger.debug("Override: observability.tracingSampleRate = {}", sampleRateEnv);
            } catch (Exception e) {
                errors.add("Invalid VAJRAPULSE_OBSERVABILITY_TRACING_SAMPLE_RATE: " + e.getMessage());
            }
        }
        
        return new VajraPulseConfig(execution, observability);
    }
    
    /**
     * Validates configuration.
     */
    private static void validate(VajraPulseConfig config, List<String> errors) {
        // Additional validation beyond constructor checks
        if (config.execution().forceTimeout().compareTo(config.execution().drainTimeout()) < 0) {
            errors.add("execution.forceTimeout (" + config.execution().forceTimeout() + 
                ") must be >= execution.drainTimeout (" + config.execution().drainTimeout() + ")");
        }
    }
}
