package com.vajrapulse.core.metrics

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Timeout

@Timeout(10)
class QueueBackpressureProviderSpec extends Specification {
    
    @Subject
    QueueBackpressureProvider provider
    
    def "should return 0.0 backpressure when queue is empty"() {
        given:
        provider = new QueueBackpressureProvider({ 0L }, 1000)
        
        when:
        def backpressure = provider.getBackpressureLevel()
        
        then:
        backpressure == 0.0
    }
    
    def "should return 0.0 backpressure when queue depth is negative"() {
        given:
        provider = new QueueBackpressureProvider({ -5L }, 1000)
        
        when:
        def backpressure = provider.getBackpressureLevel()
        
        then:
        backpressure == 0.0
    }
    
    def "should return 0.5 backpressure when queue is half full"() {
        given:
        provider = new QueueBackpressureProvider({ 500L }, 1000)
        
        when:
        def backpressure = provider.getBackpressureLevel()
        
        then:
        backpressure == 0.5
    }
    
    def "should return 1.0 backpressure when queue is at max"() {
        given:
        provider = new QueueBackpressureProvider({ 1000L }, 1000)
        
        when:
        def backpressure = provider.getBackpressureLevel()
        
        then:
        backpressure == 1.0
    }
    
    def "should cap backpressure at 1.0 when queue exceeds max"() {
        given:
        provider = new QueueBackpressureProvider({ 2000L }, 1000)
        
        when:
        def backpressure = provider.getBackpressureLevel()
        
        then:
        backpressure == 1.0
    }
    
    def "should provide description with queue depth and backpressure"() {
        given:
        provider = new QueueBackpressureProvider({ 750L }, 1000)
        
        when:
        def description = provider.getBackpressureDescription()
        
        then:
        description != null
        description.contains("750")
        description.contains("1000")
        description.contains("75.0")
    }
    
    def "should throw exception when queueDepthSupplier is null"() {
        when:
        new QueueBackpressureProvider(null, 1000)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception when maxQueueDepth is zero"() {
        when:
        new QueueBackpressureProvider({ 0L }, 0)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception when maxQueueDepth is negative"() {
        when:
        new QueueBackpressureProvider({ 0L }, -1)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should call supplier each time getBackpressureLevel is called"() {
        given:
        def callCount = 0
        provider = new QueueBackpressureProvider({ 
            callCount++
            return 100L 
        }, 1000)
        
        when:
        provider.getBackpressureLevel()
        provider.getBackpressureLevel()
        provider.getBackpressureLevel()
        
        then:
        callCount == 3
    }
}

