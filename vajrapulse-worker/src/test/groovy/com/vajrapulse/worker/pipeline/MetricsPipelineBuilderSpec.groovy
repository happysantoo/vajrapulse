package com.vajrapulse.worker.pipeline

import com.vajrapulse.api.pattern.StaticLoad
import com.vajrapulse.api.task.Task
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.core.metrics.MetricsCollector
import com.vajrapulse.core.metrics.MetricsExporter
import spock.lang.Specification

import java.time.Duration

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

    def "builder should create pipeline with runId"() {
        given:
        def exporter = Mock(MetricsExporter)
        def runId = UUID.randomUUID().toString()

        when:
        def pipeline = MetricsPipeline.builder()
            .withRunId(runId)
            .addExporter(exporter)
            .build()

        then:
        pipeline != null
        noExceptionThrown()
    }

    def "builder should create pipeline with immediate live mode"() {
        given:
        def exporter = Mock(MetricsExporter)

        when:
        def pipeline = MetricsPipeline.builder()
            .addExporter(exporter)
            .withImmediateLive(true)
            .build()

        then:
        pipeline != null
        noExceptionThrown()
    }

    def "builder should create pipeline with custom collector"() {
        given:
        def collector = new MetricsCollector()
        def exporter = Mock(MetricsExporter)

        when:
        def pipeline = MetricsPipeline.builder()
            .withCollector(collector)
            .addExporter(exporter)
            .build()

        then:
        pipeline != null
    }

    def "pipeline should run task and return metrics"() {
        given:
        def task = new Task() {
            @Override
            TaskResult execute() throws Exception {
                return TaskResult.success("test-data")
            }
        }
        def loadPattern = new StaticLoad(10.0, Duration.ofMillis(100))
        def exporter = Mock(MetricsExporter)
        def pipeline = MetricsPipeline.builder()
            .addExporter(exporter)
            .build()

        when:
        def metrics = pipeline.run(task, loadPattern)

        then:
        metrics != null
        metrics.totalExecutions() > 0
    }
}
