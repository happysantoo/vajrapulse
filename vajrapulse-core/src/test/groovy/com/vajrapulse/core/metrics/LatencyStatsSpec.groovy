package com.vajrapulse.core.metrics

import spock.lang.Specification
import spock.lang.Timeout

@Timeout(10)
class LatencyStatsSpec extends Specification {

    def "should create LatencyStats with all values"() {
        given: "latency values in nanoseconds"
        def meanNanos = 10_000_000.0  // 10ms
        def stdDevNanos = 2_000_000.0  // 2ms
        def minNanos = 1_000_000.0     // 1ms
        def maxNanos = 50_000_000.0    // 50ms
        def count = 1000L
        
        when: "creating LatencyStats"
        def stats = new LatencyStats(meanNanos, stdDevNanos, minNanos, maxNanos, count)
        
        then: "all values are stored correctly"
        stats.mean() == meanNanos
        stats.stdDev() == stdDevNanos
        stats.min() == minNanos
        stats.max() == maxNanos
        stats.count() == count
    }
    
    def "should convert nanoseconds to milliseconds correctly"() {
        given: "stats with known nanosecond values"
        def stats = new LatencyStats(
            10_000_000.0,  // 10ms mean
            2_000_000.0,   // 2ms stddev
            1_000_000.0,   // 1ms min
            50_000_000.0,  // 50ms max
            1000L
        )
        
        when: "getting millisecond values"
        def meanMs = stats.meanMillis()
        def stdDevMs = stats.stdDevMillis()
        def minMs = stats.minMillis()
        def maxMs = stats.maxMillis()
        
        then: "values are converted correctly"
        meanMs == 10.0
        stdDevMs == 2.0
        minMs == 1.0
        maxMs == 50.0
    }
    
    def "should calculate coefficient of variation correctly"() {
        given: "stats with known mean and stddev"
        def stats = new LatencyStats(
            10_000_000.0,  // 10ms mean
            2_000_000.0,   // 2ms stddev (20% of mean)
            1_000_000.0,
            50_000_000.0,
            1000L
        )
        
        when: "calculating CV"
        def cv = stats.coefficientOfVariation()
        
        then: "CV is correct (20%)"
        cv == 20.0
    }
    
    def "should return 0 CV when mean is 0"() {
        given: "stats with zero mean"
        def stats = new LatencyStats(0.0, 1_000_000.0, 0.0, 0.0, 0L)
        
        when: "calculating CV"
        def cv = stats.coefficientOfVariation()
        
        then: "CV is 0"
        cv == 0.0
    }
    
    def "should indicate hasData correctly"() {
        expect: "hasData returns correct value based on count"
        new LatencyStats(10.0, 1.0, 1.0, 100.0, 100L).hasData() == true
        new LatencyStats(0.0, 0.0, 0.0, 0.0, 0L).hasData() == false
        new LatencyStats(10.0, 1.0, 1.0, 100.0, 1L).hasData() == true
    }
    
    def "should create empty stats correctly"() {
        when: "creating empty stats"
        def stats = LatencyStats.empty()
        
        then: "all values are zero"
        stats.mean() == 0.0
        stats.stdDev() == 0.0
        stats.min() == 0.0
        stats.max() == 0.0
        stats.count() == 0L
        !stats.hasData()
    }
}
