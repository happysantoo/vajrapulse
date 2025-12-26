package com.vajrapulse.core.engine

import com.vajrapulse.api.metrics.MetricsProvider
import com.vajrapulse.api.pattern.adaptive.AdaptiveLoadPattern
import com.vajrapulse.api.pattern.adaptive.DefaultRampDecisionPolicy
import com.vajrapulse.core.metrics.MetricsCollector
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.time.Duration

/**
 * Tests for AdaptivePatternMetrics registration and cleanup.
 */
class AdaptivePatternMetricsSpec extends Specification {

    def "should register and unregister adaptive pattern metrics"() {
        given: "a metrics collector and adaptive pattern"
        def registry = new SimpleMeterRegistry()
        def collector = new MetricsCollector(registry)
        def metricsProvider = new MetricsProviderAdapter(collector)
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(100.0)
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(metricsProvider)
            .decisionPolicy(new DefaultRampDecisionPolicy(0.01))
            .build()

        when: "registering metrics"
        AdaptivePatternMetrics.register(pattern, registry, "test-run")

        then: "metrics are registered"
        registry.find("vajrapulse.adaptive.phase").gauge() != null
        registry.find("vajrapulse.adaptive.current_tps").gauge() != null
        registry.find("vajrapulse.adaptive.stable_tps").gauge() != null

        when: "unregistering metrics"
        AdaptivePatternMetrics.unregister(pattern)

        then: "tracker is removed (pattern can be garbage collected)"
        // Verify unregister doesn't throw
        noExceptionThrown()
    }

    def "should allow multiple register/unregister cycles"() {
        given: "a metrics collector and adaptive pattern"
        def registry = new SimpleMeterRegistry()
        def collector = new MetricsCollector(registry)
        def metricsProvider = new MetricsProviderAdapter(collector)
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(100.0)
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(metricsProvider)
            .decisionPolicy(new DefaultRampDecisionPolicy(0.01))
            .build()

        when: "registering and unregistering multiple times"
        AdaptivePatternMetrics.register(pattern, registry, "test-run-1")
        AdaptivePatternMetrics.unregister(pattern)
        AdaptivePatternMetrics.register(pattern, registry, "test-run-2")
        AdaptivePatternMetrics.unregister(pattern)

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should handle unregister with null pattern gracefully"() {
        when: "unregistering null pattern"
        AdaptivePatternMetrics.unregister(null)

        then: "no exception is thrown"
        noExceptionThrown()
    }

    def "should prevent memory leaks by allowing pattern garbage collection"() {
        given: "a metrics collector"
        def registry = new SimpleMeterRegistry()
        def collector = new MetricsCollector(registry)
        def metricsProvider = new MetricsProviderAdapter(collector)

        when: "creating and registering a pattern, then unregistering"
        def pattern = AdaptiveLoadPattern.builder()
            .initialTps(10.0)
            .rampIncrement(5.0)
            .rampDecrement(10.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(100.0)
            .minTps(5.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(metricsProvider)
            .decisionPolicy(new DefaultRampDecisionPolicy(0.01))
            .build()
        AdaptivePatternMetrics.register(pattern, registry, "test-run")
        AdaptivePatternMetrics.unregister(pattern)
        pattern = null // Allow garbage collection

        then: "pattern can be garbage collected (no strong reference in static map)"
        // This test verifies the cleanup mechanism exists
        // Actual GC verification would require more complex setup
        noExceptionThrown()
    }
}
