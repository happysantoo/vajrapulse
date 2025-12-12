package com.vajrapulse.core.metrics

import com.vajrapulse.api.metrics.BackpressureHandler
import com.vajrapulse.api.metrics.BackpressureHandlingResult
import com.vajrapulse.api.metrics.BackpressureContext
import spock.lang.Specification
import java.time.Duration

class BackpressureHandlersSpec extends Specification {
    
    def "DROP handler should return DROPPED result"() {
        given:
        def handler = BackpressureHandlers.DROP
        def context = BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(0.8, context)
        
        then:
        result == BackpressureHandlingResult.DROPPED
    }
    
    def "QUEUE handler should return QUEUED result"() {
        given:
        def handler = BackpressureHandlers.QUEUE
        def context = BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(0.8, context)
        
        then:
        result == BackpressureHandlingResult.QUEUED
    }
    
    def "REJECT handler should return REJECTED result"() {
        given:
        def handler = BackpressureHandlers.REJECT
        def context = BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(0.8, context)
        
        then:
        result == BackpressureHandlingResult.REJECTED
    }
    
    def "threshold handler should return ACCEPTED when backpressure is below queue threshold"() {
        given:
        def handler = BackpressureHandlers.threshold(0.5, 0.7, 0.9)
        def context = BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(0.3, context)
        
        then:
        result == BackpressureHandlingResult.ACCEPTED
    }
    
    def "threshold handler should return QUEUED when backpressure is between queue and reject thresholds"() {
        given:
        def handler = BackpressureHandlers.threshold(0.5, 0.7, 0.9)
        def context = BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(0.6, context)
        
        then:
        result == BackpressureHandlingResult.QUEUED
    }
    
    def "threshold handler should return REJECTED when backpressure is between reject and drop thresholds"() {
        given:
        def handler = BackpressureHandlers.threshold(0.5, 0.7, 0.9)
        def context = BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(0.8, context)
        
        then:
        result == BackpressureHandlingResult.REJECTED
    }
    
    def "threshold handler should return DROPPED when backpressure is above drop threshold"() {
        given:
        def handler = BackpressureHandlers.threshold(0.5, 0.7, 0.9)
        def context = BackpressureContext.of(100L, 5.0)
        
        when:
        def result = handler.handle(0.95, context)
        
        then:
        result == BackpressureHandlingResult.DROPPED
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
        def context = BackpressureContext.of(100L, 5.0)
        def results = Collections.synchronizedList(new ArrayList())
        
        when:
        def threads = (1..10).collect {
            Thread.start {
                100.times {
                    results.add(handler.handle(0.8, context))
                }
            }
        }
        threads*.join()
        
        then:
        results.size() == 1000
        results.every { it == BackpressureHandlingResult.DROPPED }
    }
}

