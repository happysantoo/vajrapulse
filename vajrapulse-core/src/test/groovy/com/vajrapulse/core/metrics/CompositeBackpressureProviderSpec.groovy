package com.vajrapulse.core.metrics

import com.vajrapulse.api.backpressure.BackpressureProvider
import spock.lang.Specification
import spock.lang.Subject

class CompositeBackpressureProviderSpec extends Specification {
    
    @Subject
    CompositeBackpressureProvider provider
    
    def "should return maximum backpressure from all providers"() {
        given:
        def provider1 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.3
            getBackpressureDescription() >> "Provider 1: 30%"
        }
        def provider2 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.7
            getBackpressureDescription() >> "Provider 2: 70%"
        }
        def provider3 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.5
            getBackpressureDescription() >> "Provider 3: 50%"
        }
        provider = new CompositeBackpressureProvider(provider1, provider2, provider3)
        
        when:
        def backpressure = provider.getBackpressureLevel()
        
        then:
        backpressure == 0.7
    }
    
    def "should return 0.0 when all providers return 0.0"() {
        given:
        def provider1 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.0
        }
        def provider2 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.0
        }
        provider = new CompositeBackpressureProvider(provider1, provider2)
        
        when:
        def backpressure = provider.getBackpressureLevel()
        
        then:
        backpressure == 0.0
    }
    
    def "should combine descriptions from all providers"() {
        given:
        def provider1 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.3
            getBackpressureDescription() >> "Provider 1: 30%"
        }
        def provider2 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.7
            getBackpressureDescription() >> "Provider 2: 70%"
        }
        provider = new CompositeBackpressureProvider(provider1, provider2)
        
        when:
        def description = provider.getBackpressureDescription()
        
        then:
        description != null
        description.contains("Provider 1: 30%")
        description.contains("Provider 2: 70%")
    }
    
    def "should handle null descriptions gracefully"() {
        given:
        def provider1 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.3
            getBackpressureDescription() >> null
        }
        def provider2 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.7
            getBackpressureDescription() >> "Provider 2: 70%"
        }
        provider = new CompositeBackpressureProvider(provider1, provider2)
        
        when:
        def description = provider.getBackpressureDescription()
        
        then:
        description != null
        description.contains("Provider 2: 70%")
        !description.contains("null")
    }
    
    def "should handle empty descriptions gracefully"() {
        given:
        def provider1 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.3
            getBackpressureDescription() >> ""
        }
        def provider2 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.7
            getBackpressureDescription() >> "Provider 2: 70%"
        }
        provider = new CompositeBackpressureProvider(provider1, provider2)
        
        when:
        def description = provider.getBackpressureDescription()
        
        then:
        description != null
        description.contains("Provider 2: 70%")
    }
    
    def "should throw exception when providers array is null"() {
        when:
        new CompositeBackpressureProvider((BackpressureProvider[]) null)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception when providers array is empty"() {
        when:
        new CompositeBackpressureProvider()
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception when any provider is null"() {
        given:
        def provider1 = Mock(BackpressureProvider)
        
        when:
        new CompositeBackpressureProvider(provider1, null)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should work with single provider"() {
        given:
        def provider1 = Mock(BackpressureProvider) {
            getBackpressureLevel() >> 0.5
            getBackpressureDescription() >> "Single provider"
        }
        provider = new CompositeBackpressureProvider(provider1)
        
        when:
        def backpressure = provider.getBackpressureLevel()
        def description = provider.getBackpressureDescription()
        
        then:
        backpressure == 0.5
        description == "Single provider"
    }
}

