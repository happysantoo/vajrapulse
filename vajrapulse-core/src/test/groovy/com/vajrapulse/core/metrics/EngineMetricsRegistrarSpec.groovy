package com.vajrapulse.core.metrics

import com.vajrapulse.api.pattern.LoadPattern
import com.vajrapulse.api.pattern.StaticLoad
import com.vajrapulse.api.task.VirtualThreads
import com.vajrapulse.core.engine.RateController
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(10)

import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.Executors

class EngineMetricsRegistrarSpec extends Specification {

    def "should register health metrics with runId"() {
        given: "a meter registry and suppliers"
        MeterRegistry registry = new SimpleMeterRegistry()
        String runId = "test-run-123"
        def stateSupplier = { -> 1 } // RUNNING
        def startTimeSupplier = { -> System.currentTimeMillis() - 1000 }

        when: "registering health metrics"
        def healthMetrics = EngineMetricsRegistrar.registerHealthMetrics(
            registry, runId, stateSupplier, startTimeSupplier)

        then: "metrics are registered with runId tags"
        registry.find("vajrapulse.engine.state").tag("run_id", runId).gauge() != null
        registry.find("vajrapulse.engine.uptime").tag("run_id", runId).timer() != null
        registry.find("vajrapulse.engine.uptime.ms").tag("run_id", runId).gauge() != null
        registry.find("vajrapulse.engine.lifecycle.events").tag("run_id", runId).tag("event", "start").counter() != null
        registry.find("vajrapulse.engine.lifecycle.events").tag("run_id", runId).tag("event", "stop").counter() != null
        registry.find("vajrapulse.engine.lifecycle.events").tag("run_id", runId).tag("event", "complete").counter() != null

        and: "health metrics object is returned"
        healthMetrics != null
        healthMetrics.lifecycleStartCounter() != null
        healthMetrics.lifecycleStopCounter() != null
        healthMetrics.lifecycleCompleteCounter() != null
        healthMetrics.uptimeTimer() != null
    }

    def "should register health metrics without runId"() {
        given: "a meter registry and suppliers"
        MeterRegistry registry = new SimpleMeterRegistry()
        def stateSupplier = { -> 0 } // STOPPED
        def startTimeSupplier = { -> 0L }

        when: "registering health metrics without runId"
        def healthMetrics = EngineMetricsRegistrar.registerHealthMetrics(
            registry, null, stateSupplier, startTimeSupplier)

        then: "metrics are registered without runId tags"
        registry.find("vajrapulse.engine.state").gauge() != null
        registry.find("vajrapulse.engine.uptime").timer() != null
        registry.find("vajrapulse.engine.uptime.ms").gauge() != null
        registry.find("vajrapulse.engine.lifecycle.events").tag("event", "start").counter() != null
        registry.find("vajrapulse.engine.lifecycle.events").tag("event", "stop").counter() != null
        registry.find("vajrapulse.engine.lifecycle.events").tag("event", "complete").counter() != null

        and: "no runId tags are present"
        registry.find("vajrapulse.engine.state").tag("run_id", "test-run-123").gauge() == null
    }

    @VirtualThreads
    static class VirtualThreadTestTask {}

    def "should register executor metrics for ThreadPoolExecutor"() {
        given: "a ThreadPoolExecutor and task class"
        MeterRegistry registry = new SimpleMeterRegistry()
        ThreadPoolExecutor executor = Executors.newFixedThreadPool(5) as ThreadPoolExecutor
        Class<?> taskClass = VirtualThreadTestTask.class
        String runId = "executor-test"

        when: "registering executor metrics"
        EngineMetricsRegistrar.registerExecutorMetrics(executor, taskClass, registry, runId)

        then: "executor metrics are registered with virtual thread type"
        registry.find("vajrapulse.executor.active.threads")
            .tag("thread_type", "virtual")
            .tag("run_id", runId)
            .gauge() != null
        registry.find("vajrapulse.executor.pool.size")
            .tag("thread_type", "virtual")
            .tag("run_id", runId)
            .gauge() != null
        registry.find("vajrapulse.executor.pool.core.size")
            .tag("thread_type", "virtual")
            .tag("run_id", runId)
            .gauge() != null
        registry.find("vajrapulse.executor.pool.max.size")
            .tag("thread_type", "virtual")
            .tag("run_id", runId)
            .gauge() != null
        registry.find("vajrapulse.executor.queue.size")
            .tag("thread_type", "virtual")
            .tag("run_id", runId)
            .gauge() != null

        cleanup:
        executor.shutdown()
    }

    static class PlatformThreadTestTask {}

    def "should register executor metrics for platform threads"() {
        given: "a ThreadPoolExecutor and task class without VirtualThreads annotation"
        MeterRegistry registry = new SimpleMeterRegistry()
        ThreadPoolExecutor executor = Executors.newFixedThreadPool(5) as ThreadPoolExecutor
        Class<?> taskClass = PlatformThreadTestTask.class
        String runId = "platform-test"

        when: "registering executor metrics"
        EngineMetricsRegistrar.registerExecutorMetrics(executor, taskClass, registry, runId)

        then: "executor metrics are registered with platform thread type"
        registry.find("vajrapulse.executor.active.threads")
            .tag("thread_type", "platform")
            .tag("run_id", runId)
            .gauge() != null

        cleanup:
        executor.shutdown()
    }

    def "should not register executor metrics for non-ThreadPoolExecutor"() {
        given: "an ExecutorService that is not a ThreadPoolExecutor"
        MeterRegistry registry = new SimpleMeterRegistry()
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()
        Class<?> taskClass = PlatformThreadTestTask.class

        when: "registering executor metrics"
        EngineMetricsRegistrar.registerExecutorMetrics(executor, taskClass, registry, null)

        then: "no executor metrics are registered"
        registry.find("vajrapulse.executor.active.threads").gauge() == null
        registry.find("vajrapulse.executor.pool.size").gauge() == null

        cleanup:
        executor.shutdown()
    }

    def "should register rate controller metrics"() {
        given: "a rate controller and load pattern"
        MeterRegistry registry = new SimpleMeterRegistry()
        LoadPattern loadPattern = new StaticLoad(100.0, Duration.ofSeconds(30))
        RateController rateController = new RateController(loadPattern)
        String runId = "rate-test"

        when: "registering rate controller metrics"
        EngineMetricsRegistrar.registerRateControllerMetrics(
            rateController, loadPattern, registry, runId)

        then: "rate controller metrics are registered with runId"
        registry.find("vajrapulse.rate.target_tps")
            .tag("run_id", runId)
            .gauge() != null
        registry.find("vajrapulse.rate.actual_tps")
            .tag("run_id", runId)
            .gauge() != null
        registry.find("vajrapulse.rate.tps_error")
            .tag("run_id", runId)
            .gauge() != null
    }

    def "should register rate controller metrics without runId"() {
        given: "a rate controller and load pattern"
        MeterRegistry registry = new SimpleMeterRegistry()
        LoadPattern loadPattern = new StaticLoad(50.0, Duration.ofSeconds(10))
        RateController rateController = new RateController(loadPattern)

        when: "registering rate controller metrics without runId"
        EngineMetricsRegistrar.registerRateControllerMetrics(
            rateController, loadPattern, registry, null)

        then: "rate controller metrics are registered without runId tags"
        registry.find("vajrapulse.rate.target_tps").gauge() != null
        registry.find("vajrapulse.rate.actual_tps").gauge() != null
        registry.find("vajrapulse.rate.tps_error").gauge() != null

        and: "no runId tags are present"
        registry.find("vajrapulse.rate.target_tps").tag("run_id", "test").gauge() == null
    }

    def "should handle blank runId as null"() {
        given: "a meter registry with blank runId"
        MeterRegistry registry = new SimpleMeterRegistry()
        def stateSupplier = { -> 1 }
        def startTimeSupplier = { -> System.currentTimeMillis() }

        when: "registering health metrics with blank runId"
        EngineMetricsRegistrar.registerHealthMetrics(
            registry, "   ", stateSupplier, startTimeSupplier)

        then: "metrics are registered without runId tags"
        registry.find("vajrapulse.engine.state").tag("run_id", "   ").gauge() == null
        registry.find("vajrapulse.engine.state").gauge() != null
    }
}

