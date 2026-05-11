package com.vajrapulse.api.backpressure

import spock.lang.Specification
import spock.lang.Timeout

@Timeout(5)
class BackpressureContextSpec extends Specification {

    def "should create context with null customMetrics defaulting to empty map"() {
        when:
        def ctx = new BackpressureContext(10L, 100L, 5L, 20L, 2.5, null)

        then:
        ctx.queueDepth() == 10L
        ctx.maxQueueDepth() == 100L
        ctx.activeConnections() == 5L
        ctx.maxConnections() == 20L
        ctx.errorRate() == 2.5
        ctx.customMetrics().isEmpty()
    }

    def "should create context with customMetrics making defensive copy"() {
        given:
        def original = new LinkedHashMap<String, Object>(["key": "value"])

        when:
        def ctx = new BackpressureContext(5L, 50L, 2L, 10L, 1.0, original)

        then:
        ctx.customMetrics() == ["key": "value"]

        when: "modifying original map"
        original.put("key2", "value2")

        then: "context's map is unchanged"
        ctx.customMetrics().size() == 1
        !ctx.customMetrics().containsKey("key2")
    }

    def "should create context with empty customMetrics"() {
        when:
        def ctx = new BackpressureContext(0L, 0L, 0L, 0L, 0.0, [:])

        then:
        ctx.customMetrics().isEmpty()
    }

    def "should create context via static of factory"() {
        when:
        def ctx = BackpressureContext.of(42L, 3.5)

        then:
        ctx.queueDepth() == 42L
        ctx.errorRate() == 3.5
        ctx.maxQueueDepth() == 0L
        ctx.activeConnections() == 0L
        ctx.maxConnections() == 0L
        ctx.customMetrics().isEmpty()
    }

    def "should return immutable customMetrics"() {
        given:
        def ctx = new BackpressureContext(1L, 1L, 1L, 1L, 1.0, ["k": "v"])

        when:
        ctx.customMetrics().put("new", "val")

        then:
        thrown(UnsupportedOperationException)
    }
}
