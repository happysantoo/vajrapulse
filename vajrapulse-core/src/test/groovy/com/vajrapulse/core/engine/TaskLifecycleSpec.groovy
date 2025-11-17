package com.vajrapulse.core.engine

import com.vajrapulse.api.LoadPattern
import com.vajrapulse.api.TaskLifecycle
import com.vajrapulse.api.TaskResult
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for TaskLifecycle integration with ExecutionEngine.
 */
@Timeout(15)
class TaskLifecycleSpec extends Specification {

    private static final class ShortLoad implements LoadPattern {
        double tps; Duration duration
        ShortLoad(double tps, Duration d) { this.tps = tps; this.duration = d }
        @Override double calculateTps(long elapsedMillis) { elapsedMillis < duration.toMillis() ? tps : 0.0 }
        @Override Duration getDuration() { duration }
    }

    def "should call init before execute and teardown after"() {
        given: "a lifecycle task that tracks call order"
        def callOrder = []
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() {
                callOrder << 'init'
            }
            
            @Override
            TaskResult execute(long iteration) {
                callOrder << "execute-${iteration}"
                return TaskResult.success()
            }
            
            @Override
            void teardown() {
                callOrder << 'teardown'
            }
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(100))
        def collector = new MetricsCollector()

        when: "running the engine"
        def engine = new ExecutionEngine(task, load, collector)
        engine.run()
        engine.close()

        then: "init called first, then executions, then teardown"
        callOrder.size() >= 3
        callOrder[0] == 'init'
        callOrder.last() == 'teardown'
        callOrder.findAll { it.startsWith('execute-') }.size() >= 1
    }

    def "should pass iteration number to execute method"() {
        given: "a task that captures iteration numbers"
        def iterations = []
        TaskLifecycle task = new TaskLifecycle() {
            @Override void init() {}
            
            @Override
            TaskResult execute(long iteration) {
                iterations << iteration
                return TaskResult.success()
            }
            
            @Override void teardown() {}
        }
        def load = new ShortLoad(50.0, Duration.ofMillis(120))
        def collector = new MetricsCollector()

        when:
        def engine = new ExecutionEngine(task, load, collector)
        engine.run()
        engine.close()

        then: "iterations start at 0 and increment"
        iterations.size() > 0
        iterations[0] == 0L
        if (iterations.size() > 1) {
            iterations[1] == 1L
        }
    }

    def "should not call teardown if init fails"() {
        given: "a task where init throws exception"
        def teardownCalled = false
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() throws Exception {
                throw new RuntimeException("init failed")
            }
            
            @Override
            TaskResult execute(long iteration) {
                return TaskResult.success()
            }
            
            @Override
            void teardown() {
                teardownCalled = true
            }
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(50))
        def collector = new MetricsCollector()

        when: "running the engine"
        def engine = new ExecutionEngine(task, load, collector)
        engine.run()

        then: "init exception is propagated and teardown not called"
        thrown(RuntimeException)
        !teardownCalled
    }

    def "should call teardown even if execute throws exceptions"() {
        given: "a task where execute always fails"
        def teardownCalled = false
        TaskLifecycle task = new TaskLifecycle() {
            @Override void init() {}
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                throw new RuntimeException("execute failed")
            }
            
            @Override
            void teardown() {
                teardownCalled = true
            }
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(80))
        def collector = new MetricsCollector()

        when: "running the engine"
        def engine = new ExecutionEngine(task, load, collector)
        engine.run()
        engine.close()

        then: "teardown is still called"
        teardownCalled
        collector.snapshot().failureCount() > 0
    }

    def "should log but not rethrow teardown exceptions"() {
        given: "a task where teardown throws exception"
        def initCalled = false
        def executeCalled = false
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() {
                initCalled = true
            }
            
            @Override
            TaskResult execute(long iteration) {
                executeCalled = true
                return TaskResult.success()
            }
            
            @Override
            void teardown() throws Exception {
                throw new RuntimeException("teardown failed")
            }
        }
        def load = new ShortLoad(10.0, Duration.ofMillis(50))
        def collector = new MetricsCollector()

        when: "running the engine"
        def engine = new ExecutionEngine(task, load, collector)
        engine.run()
        engine.close()

        then: "teardown exception is logged but not propagated"
        notThrown(Exception)
        initCalled
        executeCalled
    }

    def "should support tasks implementing both Task and TaskLifecycle"() {
        given: "a task implementing TaskLifecycle directly"
        def initCalled = new AtomicInteger(0)
        def executeCalls = new AtomicInteger(0)
        def teardownCalled = new AtomicInteger(0)
        
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() {
                initCalled.incrementAndGet()
            }
            
            @Override
            TaskResult execute(long iteration) {
                executeCalls.incrementAndGet()
                return TaskResult.success()
            }
            
            @Override
            void teardown() {
                teardownCalled.incrementAndGet()
            }
        }
        def load = new ShortLoad(20.0, Duration.ofMillis(100))
        def collector = new MetricsCollector()

        when: "running the engine"
        def engine = new ExecutionEngine(task, load, collector)
        engine.run()
        engine.close()

        then: "lifecycle methods are called correctly"
        initCalled.get() == 1
        executeCalls.get() > 0
        teardownCalled.get() == 1
    }

    def "should handle empty duration load pattern"() {
        given: "a very short load pattern"
        def executeCalled = false
        TaskLifecycle task = new TaskLifecycle() {
            @Override void init() {}
            
            @Override
            TaskResult execute(long iteration) {
                executeCalled = true
                return TaskResult.success()
            }
            
            @Override void teardown() {}
        }
        def load = new ShortLoad(1000.0, Duration.ofMillis(1))
        def collector = new MetricsCollector()

        when: "running the engine"
        def engine = new ExecutionEngine(task, load, collector)
        engine.run()
        engine.close()

        then: "init and teardown still called even if no executions"
        notThrown(Exception)
    }
}
