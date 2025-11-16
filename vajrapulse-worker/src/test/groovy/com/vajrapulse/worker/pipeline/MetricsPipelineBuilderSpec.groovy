package com.vajrapulse.worker.pipeline

import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification

class MetricsPipelineBuilderSpec extends Specification {

    def "should throw when withCollector combined with withPercentiles or withSloBuckets"() {
        when:
        MetricsPipeline.builder()
                .withCollector(new MetricsCollector())
                .withPercentiles(0.5d, 0.95d)
                .build()

        then:
        thrown(IllegalStateException)

        when:
        MetricsPipeline.builder()
                .withCollector(new MetricsCollector())
                .withSloBuckets(java.time.Duration.ofMillis(10))
                .build()

        then:
        thrown(IllegalStateException)
    }
}
