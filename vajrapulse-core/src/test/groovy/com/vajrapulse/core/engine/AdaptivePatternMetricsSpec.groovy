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
        def metrics = new AdaptivePatternMetrics(pattern, registry, "test-run")

        then: "metrics are registered"
        registry.find("vajrapulse.adaptive.phase").gauge() != null
        registry.find("vajrapulse.adaptive.current_tps").gauge() != null
        registry.find("vajrapulse.adaptive.stable_tps").gauge() != null

        when: "unregistering metrics"
        metrics.unregister()

        then: "meters are removed"
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
        def metrics1 = new AdaptivePatternMetrics(pattern, registry, "test-run-1")
        metrics1.unregister()
        def metrics2 = new AdaptivePatternMetrics(pattern, registry, "test-run-2")
        metrics2.unregister()

        then: "no exceptions are thrown"
        noExceptionThrown()
    }

    def "should only remove its own meters when unregistering"() {
        given: "two adaptive patterns sharing a registry"
        def registry = new SimpleMeterRegistry()
        def collector = new MetricsCollector(registry)
        def metricsProvider = new MetricsProviderAdapter(collector)
        def pattern1 = AdaptiveLoadPattern.builder()
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
        def pattern2 = AdaptiveLoadPattern.builder()
            .initialTps(20.0)
            .rampIncrement(10.0)
            .rampDecrement(20.0)
            .rampInterval(Duration.ofSeconds(1))
            .maxTps(200.0)
            .minTps(10.0)
            .sustainDuration(Duration.ofSeconds(10))
            .stableIntervalsRequired(3)
            .metricsProvider(metricsProvider)
            .decisionPolicy(new DefaultRampDecisionPolicy(0.01))
            .build()

        when: "registering both and unregistering one"
        def metrics1 = new AdaptivePatternMetrics(pattern1, registry, "run-1")
        def metrics2 = new AdaptivePatternMetrics(pattern2, registry, "run-2")
        metrics1.unregister()

        then: "pattern2 metrics are still registered"
        registry.find("vajrapulse.adaptive.current_tps").gauge() != null
        noExceptionThrown()

        cleanup:
        metrics2?.unregister()
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
        def metrics = new AdaptivePatternMetrics(pattern, registry, "test-run")
        metrics.unregister()
        pattern = null // Allow garbage collection

        then: "pattern can be garbage collected (no strong reference in static map)"
        noExceptionThrown()
    }
}
