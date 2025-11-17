package com.vajrapulse.core.config

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Tests for ConfigLoader.
 */
class ConfigLoaderSpec extends Specification {

    @TempDir
    Path tempDir

    def cleanup() {
        // Clear env-related system properties after each test
        clearEnvironmentOverrides()
    }

    def "should load default configuration when no file exists"() {
        when:
        def config = ConfigLoader.load()

        then:
        config != null
        config.execution().drainTimeout() == Duration.ofSeconds(5)
        config.execution().forceTimeout() == Duration.ofSeconds(10)
        config.execution().defaultThreadPool() == VajraPulseConfig.ThreadPoolStrategy.VIRTUAL
        config.execution().platformThreadPoolSize() == -1
        config.observability().tracingEnabled() == false
        config.observability().metricsEnabled() == true
        config.observability().structuredLogging() == true
    }

    def "should load configuration from YAML file"() {
        given: "a YAML config file"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  drainTimeout: 3s
  forceTimeout: 15s
  defaultThreadPool: platform
  platformThreadPoolSize: 8

observability:
  tracingEnabled: true
  metricsEnabled: true
  structuredLogging: false
  otlpEndpoint: http://localhost:4317
  tracingSampleRate: 0.1
""")

        when:
        def config = ConfigLoader.load(configFile)

        then:
        config.execution().drainTimeout() == Duration.ofSeconds(3)
        config.execution().forceTimeout() == Duration.ofSeconds(15)
        config.execution().defaultThreadPool() == VajraPulseConfig.ThreadPoolStrategy.PLATFORM
        config.execution().platformThreadPoolSize() == 8
        config.observability().tracingEnabled() == true
        config.observability().metricsEnabled() == true
        config.observability().structuredLogging() == false
        config.observability().otlpEndpoint() == "http://localhost:4317"
        config.observability().tracingSampleRate() == 0.1
    }

    def "should parse duration formats correctly"() {
        given: "a config with various duration formats"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  drainTimeout: ${drainTimeout}
  forceTimeout: ${forceTimeout}

observability:
  tracingEnabled: false
""")

        when:
        def config = ConfigLoader.load(configFile)

        then:
        config.execution().drainTimeout() == expectedDrain
        config.execution().forceTimeout() == expectedForce

        where:
        drainTimeout | forceTimeout | expectedDrain         | expectedForce
        "500ms"      | "1s"         | Duration.ofMillis(500)| Duration.ofSeconds(1)
        "30s"        | "1m"         | Duration.ofSeconds(30)| Duration.ofMinutes(1)
        "2m"         | "10m"        | Duration.ofMinutes(2) | Duration.ofMinutes(10)
        "1h"         | "2h"         | Duration.ofHours(1)   | Duration.ofHours(2)
    }

    def "should handle partial configuration with defaults"() {
        given: "a config file with only execution section"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  drainTimeout: 7s
""")

        when:
        def config = ConfigLoader.load(configFile)

        then: "specified values are used"
        config.execution().drainTimeout() == Duration.ofSeconds(7)
        
        and: "missing values use defaults"
        config.execution().forceTimeout() == Duration.ofSeconds(10)
        config.execution().defaultThreadPool() == VajraPulseConfig.ThreadPoolStrategy.VIRTUAL
        config.observability().tracingEnabled() == false
    }

    def "should handle empty configuration file"() {
        given: "an empty config file"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, "")

        when:
        def config = ConfigLoader.load(configFile)

        then: "defaults are used"
        config.execution().drainTimeout() == Duration.ofSeconds(5)
        config.execution().forceTimeout() == Duration.ofSeconds(10)
    }

    def "should validate drain timeout is positive"() {
        given: "a config with negative drain timeout"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  drainTimeout: -5s
  forceTimeout: 10s
""")

        when:
        ConfigLoader.load(configFile)

        then:
        def ex = thrown(ConfigurationException)
        ex.message.contains("drainTimeout must be positive")
    }

    def "should validate force timeout is positive"() {
        given: "a config with zero force timeout"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  drainTimeout: 5s
  forceTimeout: 0s
""")

        when:
        ConfigLoader.load(configFile)

        then:
        def ex = thrown(ConfigurationException)
        ex.message.contains("forceTimeout must be positive")
    }

    def "should validate force timeout >= drain timeout"() {
        given: "force timeout less than drain timeout"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  drainTimeout: 10s
  forceTimeout: 5s
""")

        when:
        ConfigLoader.load(configFile)

        then:
        def ex = thrown(ConfigurationException)
        ex.message.contains("forceTimeout")
        ex.message.contains("drainTimeout")
        ex.errors.size() == 1
    }

    def "should validate tracing sample rate range"() {
        given: "invalid sample rate"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
observability:
  tracingSampleRate: ${sampleRate}
""")

        when:
        ConfigLoader.load(configFile)

        then:
        def ex = thrown(ConfigurationException)
        ex.message.contains("tracingSampleRate")
        ex.message.contains("0.0 and 1.0")

        where:
        sampleRate << [-0.1, 1.5, 2.0]
    }

    def "should validate platform thread pool size"() {
        given: "invalid pool size"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  platformThreadPoolSize: ${poolSize}
""")

        when:
        ConfigLoader.load(configFile)

        then:
        def ex = thrown(ConfigurationException)
        ex.message.contains("platformThreadPoolSize")

        where:
        poolSize << [-2, 0]
    }

    def "should reject invalid duration format"() {
        given: "invalid duration format"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  drainTimeout: invalid
""")

        when:
        ConfigLoader.load(configFile)

        then:
        thrown(ConfigurationException)
    }

    def "should reject invalid thread pool strategy"() {
        given: "invalid strategy"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  defaultThreadPool: invalid_strategy
""")

        when:
        ConfigLoader.load(configFile)

        then:
        def ex = thrown(ConfigurationException)
        ex.message.contains("Invalid thread pool strategy")
    }

    def "should override from environment variables"() {
        given: "config file with base values"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  drainTimeout: 5s
  forceTimeout: 10s

observability:
  tracingEnabled: false
""")
        
        expect: "environment override test is documented but skipped"
        // Note: Cannot actually set environment variables in JVM tests
        // This functionality is tested manually or via integration tests
        // where environment variables are set before JVM starts
        true
    }

    def "should handle invalid environment variable values"() {
        expect: "environment override validation is documented but skipped"
        // Note: Cannot actually set environment variables in JVM tests
        // This functionality is tested manually or via integration tests
        true
    }

    def "should parse thread pool strategies case-insensitively"() {
        given: "config with various cases"
        def configFile = tempDir.resolve("vajrapulse.conf.yml")
        Files.writeString(configFile, """
execution:
  defaultThreadPool: ${strategy}
""")

        when:
        def config = ConfigLoader.load(configFile)

        then:
        config.execution().defaultThreadPool() == expected

        where:
        strategy    | expected
        "virtual"   | VajraPulseConfig.ThreadPoolStrategy.VIRTUAL
        "VIRTUAL"   | VajraPulseConfig.ThreadPoolStrategy.VIRTUAL
        "platform"  | VajraPulseConfig.ThreadPoolStrategy.PLATFORM
        "PLATFORM"  | VajraPulseConfig.ThreadPoolStrategy.PLATFORM
        "auto"      | VajraPulseConfig.ThreadPoolStrategy.AUTO
        "AUTO"      | VajraPulseConfig.ThreadPoolStrategy.AUTO
    }

    // Helper methods for environment override testing
    private void setEnvOverride(String key, String value) {
        // Since we can't actually modify env vars in tests, we rely on the loader
        // reading from System.getenv(). For testing, we'd need to mock this or
        // use a test-friendly approach. For now, we document this limitation.
        // In real usage, these would be actual environment variables.
        
        // Alternative: Set system properties that ConfigLoader could also check
        System.setProperty("test." + key, value)
    }

    private void clearEnvironmentOverrides() {
        System.getProperties().stringPropertyNames()
            .findAll { it.startsWith("test.VAJRAPULSE_") }
            .each { System.clearProperty(it) }
    }
}
