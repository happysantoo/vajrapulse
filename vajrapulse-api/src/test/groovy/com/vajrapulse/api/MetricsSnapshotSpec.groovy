package com.vajrapulse.api

import spock.lang.Specification
import spock.lang.Timeout
import com.vajrapulse.api.pattern.adaptive.MetricsSnapshot

/**
 * Tests for MetricsSnapshot.
 */
@Timeout(10)
class MetricsSnapshotSpec extends Specification {
    
    def "should create valid snapshot"() {
        when:
        def snapshot = new MetricsSnapshot(0.01, 0.005, 0.3, 1000L)
        
        then:
        snapshot.failureRate() == 0.01
        snapshot.recentFailureRate() == 0.005
        snapshot.backpressure() == 0.3
        snapshot.totalExecutions() == 1000L
    }
    
    def "should reject invalid failureRate"() {
        when:
        new MetricsSnapshot(1.5, 0.005, 0.3, 1000L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Failure rate must be between 0.0 and 1.0")
    }
    
    def "should reject invalid recentFailureRate"() {
        when:
        new MetricsSnapshot(0.01, -0.1, 0.3, 1000L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Recent failure rate must be between 0.0 and 1.0")
    }
    
    def "should reject invalid backpressure"() {
        when:
        new MetricsSnapshot(0.01, 0.005, 1.5, 1000L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Backpressure must be between 0.0 and 1.0")
    }
    
    def "should reject negative totalExecutions"() {
        when:
        new MetricsSnapshot(0.01, 0.005, 0.3, -1L)
        
        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Total executions must be non-negative")
    }
    
    def "should accept boundary values"() {
        when:
        def snapshot1 = new MetricsSnapshot(0.0, 0.0, 0.0, 0L)
        def snapshot2 = new MetricsSnapshot(1.0, 1.0, 1.0, Long.MAX_VALUE)
        
        then:
        snapshot1.failureRate() == 0.0
        snapshot2.failureRate() == 1.0
    }
}

