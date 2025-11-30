package com.vajrapulse.core.backpressure

import com.vajrapulse.api.BackpressureHandler
import spock.lang.Specification
import java.time.Duration

class BackpressureHandlersSpec extends Specification {
    
    def "DROP handler should return DROPPED result"() {
        given:
        def handler = BackpressureHandlers.DROP
        def context = BackpressureHandler.BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(1L, 0.8, context)
        
        then:
        result == BackpressureHandler.HandlingResult.DROPPED
    }
    
    def "QUEUE handler should return QUEUED result"() {
        given:
        def handler = BackpressureHandlers.QUEUE
        def context = BackpressureHandler.BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(1L, 0.8, context)
        
        then:
        result == BackpressureHandler.HandlingResult.QUEUED
    }
    
    def "REJECT handler should return REJECTED result"() {
        given:
        def handler = BackpressureHandlers.REJECT
        def context = BackpressureHandler.BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(1L, 0.8, context)
        
        then:
        result == BackpressureHandler.HandlingResult.REJECTED
    }
    
    def "DEGRADE handler should return DEGRADED result"() {
        given:
        def handler = BackpressureHandlers.DEGRADE
        def context = BackpressureHandler.BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(1L, 0.8, context)
        
        then:
        result == BackpressureHandler.HandlingResult.DEGRADED
    }
    
    def "retry handler should return RETRY result"() {
        given:
        def handler = BackpressureHandlers.retry(Duration.ofSeconds(1), 3)
        def context = BackpressureHandler.BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(1L, 0.8, context)
        
        then:
        result == BackpressureHandler.HandlingResult.RETRY
    }
    
    def "retry handler should throw exception when retryDelay is null"() {
        when:
        BackpressureHandlers.retry(null, 3)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "retry handler should throw exception when retryDelay is negative"() {
        when:
        BackpressureHandlers.retry(Duration.ofSeconds(-1), 3)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "retry handler should throw exception when maxRetries is negative"() {
        when:
        BackpressureHandlers.retry(Duration.ofSeconds(1), -1)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "threshold handler should return ACCEPTED when backpressure is below queue threshold"() {
        given:
        def handler = BackpressureHandlers.threshold(0.5, 0.7, 0.9)
        def context = BackpressureHandler.BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(1L, 0.3, context)
        
        then:
        result == BackpressureHandler.HandlingResult.ACCEPTED
    }
    
    def "threshold handler should return QUEUED when backpressure is between queue and reject thresholds"() {
        given:
        def handler = BackpressureHandlers.threshold(0.5, 0.7, 0.9)
        def context = BackpressureHandler.BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(1L, 0.6, context)
        
        then:
        result == BackpressureHandler.HandlingResult.QUEUED
    }
    
    def "threshold handler should return REJECTED when backpressure is between reject and drop thresholds"() {
        given:
        def handler = BackpressureHandlers.threshold(0.5, 0.7, 0.9)
        def context = BackpressureHandler.BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(1L, 0.8, context)
        
        then:
        result == BackpressureHandler.HandlingResult.REJECTED
    }
    
    def "threshold handler should return DROPPED when backpressure is above drop threshold"() {
        given:
        def handler = BackpressureHandlers.threshold(0.5, 0.7, 0.9)
        def context = BackpressureHandler.BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(1L, 0.95, context)
        
        then:
        result == BackpressureHandler.HandlingResult.DROPPED
    }
    
    def "threshold handler should throw exception when queueThreshold is invalid"() {
        when:
        BackpressureHandlers.threshold(-0.1, 0.7, 0.9)
        
        then:
        thrown(IllegalArgumentException)
        
        when:
        BackpressureHandlers.threshold(1.1, 0.7, 0.9)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "threshold handler should throw exception when rejectThreshold is invalid"() {
        when:
        BackpressureHandlers.threshold(0.5, -0.1, 0.9)
        
        then:
        thrown(IllegalArgumentException)
        
        when:
        BackpressureHandlers.threshold(0.5, 1.1, 0.9)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "threshold handler should throw exception when dropThreshold is invalid"() {
        when:
        BackpressureHandlers.threshold(0.5, 0.7, -0.1)
        
        then:
        thrown(IllegalArgumentException)
        
        when:
        BackpressureHandlers.threshold(0.5, 0.7, 1.1)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "threshold handler should throw exception when thresholds are not in ascending order"() {
        when:
        BackpressureHandlers.threshold(0.7, 0.5, 0.9) // queue >= reject
        
        then:
        thrown(IllegalArgumentException)
        
        when:
        BackpressureHandlers.threshold(0.5, 0.9, 0.7) // reject >= drop
        
        then:
        thrown(IllegalArgumentException)
        
        when:
        BackpressureHandlers.threshold(0.7, 0.7, 0.9) // queue == reject
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "handlers should be thread-safe"() {
        given:
        def handler = BackpressureHandlers.DROP
        def context = BackpressureHandler.BackpressureContext.of(100L, 5.0)
        def results = Collections.synchronizedList(new ArrayList())
        
        when:
        def threads = (1..10).collect {
            Thread.start {
                100.times {
                    results.add(handler.handle(it, 0.8, context))
                }
            }
        }
        threads*.join()
        
        then:
        results.size() == 1000
        results.every { it == BackpressureHandler.HandlingResult.DROPPED }
    }
}

