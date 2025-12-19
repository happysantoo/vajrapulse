package com.vajrapulse.api

import spock.lang.Specification
import spock.lang.Timeout
import com.vajrapulse.api.pattern.adaptive.LoggingAdaptivePatternListener
import com.vajrapulse.api.pattern.adaptive.AdaptivePhase
import com.vajrapulse.api.pattern.adaptive.PhaseTransitionEvent
import com.vajrapulse.api.pattern.adaptive.TpsChangeEvent
import com.vajrapulse.api.pattern.adaptive.StabilityDetectedEvent
import com.vajrapulse.api.pattern.adaptive.RecoveryEvent
import java.util.logging.Logger

@Timeout(10)
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Handler

/**
 * Tests for LoggingAdaptivePatternListener.
 */
class LoggingAdaptivePatternListenerSpec extends Specification {
    
    def "should create listener with default logger"() {
        when:
        def listener = new LoggingAdaptivePatternListener()
        
        then:
        listener != null
        noExceptionThrown()
    }
    
    def "should create listener with custom logger"() {
        given:
        def logger = Logger.getLogger("test.logger")
        
        when:
        def listener = new LoggingAdaptivePatternListener(logger)
        
        then:
        listener != null
        noExceptionThrown()
    }
    
    def "should reject null logger"() {
        when:
        new LoggingAdaptivePatternListener(null)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Logger must not be null")
    }
    
    def "should log phase transition"() {
        given:
        def logRecords = []
        def handler = new Handler() {
            @Override
            void publish(LogRecord record) {
                logRecords.add(record)
            }
            
            @Override
            void flush() {}
            
            @Override
            void close() throws SecurityException {}
        }
        def logger = Logger.getLogger("test.phase.transition")
        logger.addHandler(handler)
        logger.setLevel(Level.INFO)
        def listener = new LoggingAdaptivePatternListener(logger)
        def event = new PhaseTransitionEvent(
            AdaptivePhase.RAMP_UP,
            AdaptivePhase.RAMP_DOWN,
            150.0,
            1000L
        )
        
        when:
        listener.onPhaseTransition(event)
        handler.flush()
        
        then:
        logRecords.size() == 1
        logRecords[0].getMessage().contains("Phase transition")
        logRecords[0].getMessage().contains("RAMP_UP")
        logRecords[0].getMessage().contains("RAMP_DOWN")
        logRecords[0].getMessage().contains("150.00")
    }
    
    def "should log TPS change"() {
        given:
        def logRecords = []
        def handler = new Handler() {
            @Override
            void publish(LogRecord record) {
                logRecords.add(record)
            }
            
            @Override
            void flush() {}
            
            @Override
            void close() throws SecurityException {}
        }
        def logger = Logger.getLogger("test.tps.change")
        logger.addHandler(handler)
        logger.setLevel(Level.INFO)
        def listener = new LoggingAdaptivePatternListener(logger)
        def event = new TpsChangeEvent(
            100.0,
            150.0,
            AdaptivePhase.RAMP_UP,
            1000L
        )
        
        when:
        listener.onTpsChange(event)
        handler.flush()
        
        then:
        logRecords.size() == 1
        logRecords[0].getMessage().contains("TPS change")
        logRecords[0].getMessage().contains("100.00")
        logRecords[0].getMessage().contains("150.00")
        logRecords[0].getMessage().contains("50.00") // delta
        logRecords[0].getMessage().contains("RAMP_UP")
    }
    
    def "should log stability detected"() {
        given:
        def logRecords = []
        def handler = new Handler() {
            @Override
            void publish(LogRecord record) {
                logRecords.add(record)
            }
            
            @Override
            void flush() {}
            
            @Override
            void close() throws SecurityException {}
        }
        def logger = Logger.getLogger("test.stability")
        logger.addHandler(handler)
        logger.setLevel(Level.INFO)
        def listener = new LoggingAdaptivePatternListener(logger)
        def event = new StabilityDetectedEvent(200.0, 1000L)
        
        when:
        listener.onStabilityDetected(event)
        handler.flush()
        
        then:
        logRecords.size() == 1
        logRecords[0].getMessage().contains("Stable TPS detected")
        logRecords[0].getMessage().contains("200.00")
    }
    
    def "should log recovery"() {
        given:
        def logRecords = []
        def handler = new Handler() {
            @Override
            void publish(LogRecord record) {
                logRecords.add(record)
            }
            
            @Override
            void flush() {}
            
            @Override
            void close() throws SecurityException {}
        }
        def logger = Logger.getLogger("test.recovery")
        logger.addHandler(handler)
        logger.setLevel(Level.INFO)
        def listener = new LoggingAdaptivePatternListener(logger)
        def event = new RecoveryEvent(300.0, 150.0, 1000L)
        
        when:
        listener.onRecovery(event)
        handler.flush()
        
        then:
        logRecords.size() == 1
        logRecords[0].getMessage().contains("Recovery")
        logRecords[0].getMessage().contains("150.00")
        logRecords[0].getMessage().contains("300.00")
    }
    
    def "should handle negative TPS delta in log message"() {
        given:
        def logRecords = []
        def handler = new Handler() {
            @Override
            void publish(LogRecord record) {
                logRecords.add(record)
            }
            
            @Override
            void flush() {}
            
            @Override
            void close() throws SecurityException {}
        }
        def logger = Logger.getLogger("test.negative.delta")
        logger.addHandler(handler)
        logger.setLevel(Level.INFO)
        def listener = new LoggingAdaptivePatternListener(logger)
        def event = new TpsChangeEvent(
            150.0,
            50.0,
            AdaptivePhase.RAMP_DOWN,
            1000L
        )
        
        when:
        listener.onTpsChange(event)
        handler.flush()
        
        then:
        logRecords.size() == 1
        logRecords[0].getMessage().contains("-100.00") // negative delta
        logRecords[0].getMessage().contains("RAMP_DOWN")
    }
}


