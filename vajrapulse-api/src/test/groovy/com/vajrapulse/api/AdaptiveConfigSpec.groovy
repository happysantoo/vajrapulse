package com.vajrapulse.api

import spock.lang.Specification
import com.vajrapulse.api.pattern.adaptive.AdaptiveConfig
import java.time.Duration

/**
 * Tests for AdaptiveConfig.
 */
class AdaptiveConfigSpec extends Specification {
    
    def "should create valid config with defaults"() {
        when:
        def config = AdaptiveConfig.defaults()
        
        then:
        config.initialTps() == 100.0
        config.rampIncrement() == 50.0
        config.rampDecrement() == 100.0
        config.rampInterval() == Duration.ofMinutes(1)
        config.maxTps() == 5000.0
        config.minTps() == 10.0
        config.sustainDuration() == Duration.ofMinutes(10)
        config.errorThreshold() == 0.01
        config.backpressureRampUpThreshold() == 0.3
        config.backpressureRampDownThreshold() == 0.7
        config.stableIntervalsRequired() == 3
        config.tpsTolerance() == 50.0
        config.recoveryTpsRatio() == 0.5
    }
    
    def "should create valid custom config"() {
        when:
        def config = new AdaptiveConfig(
            200.0,
            75.0,
            150.0,
            Duration.ofSeconds(30),
            10000.0,
            5.0,
            Duration.ofMinutes(5),
            0.02,
            0.2,
            0.8,
            5,
            100.0,
            0.3
        )
        
        then:
        config.initialTps() == 200.0
        config.rampIncrement() == 75.0
        config.rampDecrement() == 150.0
    }
    
    def "should reject invalid initialTps"() {
        when:
        new AdaptiveConfig(
            0.0,  // invalid
            50.0,
            100.0,
            Duration.ofMinutes(1),
            5000.0,
            10.0,
            Duration.ofMinutes(10),
            0.01,
            0.3,
            0.7,
            3,
            50.0,
            0.5
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Initial TPS must be positive")
    }
    
    def "should reject invalid rampIncrement"() {
        when:
        new AdaptiveConfig(
            100.0,
            -10.0,  // invalid
            100.0,
            Duration.ofMinutes(1),
            5000.0,
            10.0,
            Duration.ofMinutes(10),
            0.01,
            0.3,
            0.7,
            3,
            50.0,
            0.5
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Ramp increment must be positive")
    }
    
    def "should reject invalid rampInterval"() {
        when:
        new AdaptiveConfig(
            100.0,
            50.0,
            100.0,
            null,  // invalid
            5000.0,
            10.0,
            Duration.ofMinutes(10),
            0.01,
            0.3,
            0.7,
            3,
            50.0,
            0.5
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Ramp interval must be positive")
    }
    
    def "should reject invalid errorThreshold"() {
        when:
        new AdaptiveConfig(
            100.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            5000.0,
            10.0,
            Duration.ofMinutes(10),
            1.5,  // invalid (> 1.0)
            0.3,
            0.7,
            3,
            50.0,
            0.5
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Error threshold must be between 0.0 and 1.0")
    }
    
    def "should reject invalid backpressure thresholds"() {
        when:
        new AdaptiveConfig(
            100.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            5000.0,
            10.0,
            Duration.ofMinutes(10),
            0.01,
            0.8,  // invalid (>= rampDownThreshold)
            0.7,
            3,
            50.0,
            0.5
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Backpressure ramp up threshold must be less than ramp down threshold")
    }
    
    def "should reject invalid stableIntervalsRequired"() {
        when:
        new AdaptiveConfig(
            100.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            5000.0,
            10.0,
            Duration.ofMinutes(10),
            0.01,
            0.3,
            0.7,
            0,  // invalid (< 1)
            50.0,
            0.5
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Stable intervals required must be at least 1")
    }
    
    def "should reject minTps >= maxTps"() {
        when:
        new AdaptiveConfig(
            100.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            100.0,  // maxTps
            100.0,  // minTps (>= maxTps)
            Duration.ofMinutes(10),
            0.01,
            0.3,
            0.7,
            3,
            50.0,
            0.5
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Min TPS must be less than max TPS")
    }
    
    def "should accept infinite maxTps"() {
        when:
        def config = new AdaptiveConfig(
            100.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            Double.POSITIVE_INFINITY,
            10.0,
            Duration.ofMinutes(10),
            0.01,
            0.3,
            0.7,
            3,
            50.0,
            0.5
        )
        
        then:
        Double.isInfinite(config.maxTps())
    }
}

