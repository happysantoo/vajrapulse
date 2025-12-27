package com.vajrapulse.api

import spock.lang.Specification
import spock.lang.Timeout
import com.vajrapulse.api.pattern.adaptive.AdaptiveConfig
import java.time.Duration

/**
 * Tests for AdaptiveConfig.
 */
@Timeout(10)
class AdaptiveConfigSpec extends Specification {
    
    def "should create valid config with defaults"() {
        when:
        def config = AdaptiveConfig.defaults()
        
        then:
        config.initialTps() == 100.0
        config.maxTps() == 5000.0
        config.minTps() == 10.0
        config.rampIncrement() == 50.0
        config.rampDecrement() == 100.0
        config.rampInterval() == Duration.ofMinutes(1)
        config.sustainDuration() == Duration.ofMinutes(10)
        config.stableIntervalsRequired() == 3
        config.initialRampDuration() == Duration.ZERO
    }
    
    def "should create valid custom config"() {
        when:
        def config = new AdaptiveConfig(
            200.0,  // initialTps
            10000.0,  // maxTps
            5.0,  // minTps
            75.0,  // rampIncrement
            150.0,  // rampDecrement
            Duration.ofSeconds(30),  // rampInterval
            Duration.ofMinutes(5),  // sustainDuration
            5,  // stableIntervalsRequired
            Duration.ofSeconds(30)  // initialRampDuration
        )
        
        then:
        config.initialTps() == 200.0
        config.maxTps() == 10000.0
        config.minTps() == 5.0
        config.rampIncrement() == 75.0
        config.rampDecrement() == 150.0
        config.rampInterval() == Duration.ofSeconds(30)
        config.sustainDuration() == Duration.ofMinutes(5)
        config.stableIntervalsRequired() == 5
        config.initialRampDuration() == Duration.ofSeconds(30)
    }
    
    def "should create config with initialRampDuration"() {
        when:
        def config = new AdaptiveConfig(
            100.0,
            5000.0,
            10.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            3,
            Duration.ofSeconds(30)
        )
        
        then:
        config.initialRampDuration() == Duration.ofSeconds(30)
    }
    
    def "should reject negative initialRampDuration"() {
        when:
        new AdaptiveConfig(
            100.0,
            5000.0,
            10.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            3,
            Duration.ofSeconds(-1) // Negative duration
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Initial ramp duration must be non-negative")
    }
    
    def "should allow zero initialRampDuration"() {
        when:
        def config = new AdaptiveConfig(
            100.0,
            5000.0,
            10.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            3,
            Duration.ZERO // Zero duration (no initial ramp)
        )
        
        then:
        config.initialRampDuration() == Duration.ZERO
    }
    
    def "should reject invalid initialTps"() {
        when:
        new AdaptiveConfig(
            0.0,  // invalid
            5000.0,
            10.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            3,
            Duration.ZERO
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Initial TPS must be positive")
    }
    
    def "should reject invalid rampIncrement"() {
        when:
        new AdaptiveConfig(
            100.0,
            5000.0,
            10.0,
            -10.0,  // invalid
            100.0,
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            3,
            Duration.ZERO
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Ramp increment must be positive")
    }
    
    def "should reject invalid rampInterval"() {
        when:
        new AdaptiveConfig(
            100.0,
            5000.0,
            10.0,
            50.0,
            100.0,
            null,  // invalid
            Duration.ofMinutes(10),
            3,
            Duration.ZERO
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Ramp interval must be positive")
    }
    
    def "should reject invalid stableIntervalsRequired"() {
        when:
        new AdaptiveConfig(
            100.0,
            5000.0,
            10.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            0,  // invalid (< 1)
            Duration.ZERO
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Stable intervals required must be at least 1")
    }
    
    def "should reject minTps >= maxTps"() {
        when:
        new AdaptiveConfig(
            100.0,
            100.0,  // maxTps
            100.0,  // minTps (>= maxTps)
            50.0,
            100.0,
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            3,
            Duration.ZERO
        )
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Min TPS must be less than max TPS")
    }
    
    def "should accept infinite maxTps"() {
        when:
        def config = new AdaptiveConfig(
            100.0,
            Double.POSITIVE_INFINITY,
            10.0,
            50.0,
            100.0,
            Duration.ofMinutes(1),
            Duration.ofMinutes(10),
            3,
            Duration.ZERO
        )
        
        then:
        Double.isInfinite(config.maxTps())
    }
}
