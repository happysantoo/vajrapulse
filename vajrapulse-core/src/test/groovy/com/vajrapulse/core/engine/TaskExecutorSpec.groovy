package com.vajrapulse.core.engine

import com.vajrapulse.api.task.TaskLifecycle
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.api.task.TaskResultSuccess
import com.vajrapulse.api.task.TaskResultFailure
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(10)
class TaskExecutorSpec extends Specification {

    def "should execute task successfully and capture metrics"() {
        given: "a simple successful task"
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() {}
            
            @Override
            TaskResult execute(long iteration) {
                return TaskResult.success("test-data")
            }
            
            @Override
            void teardown() {}
        }
        def executor = new TaskExecutor(task)
        
        when: "executing the task"
        def metrics = executor.executeWithMetrics(0)
        
        then: "metrics show success"
        metrics.isSuccess()
        !metrics.isFailure()
        metrics.durationNanos() > 0
        metrics.iteration() == 0
        metrics.result() instanceof TaskResultSuccess
        ((TaskResultSuccess) metrics.result()).data() == "test-data"
    }
    
    def "should capture failure when task fails"() {
        given: "a task that returns failure"
        def error = new RuntimeException("test error")
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() {}
            
            @Override
            TaskResult execute(long iteration) {
                return TaskResult.failure(error)
            }
            
            @Override
            void teardown() {}
        }
        def executor = new TaskExecutor(task)
        
        when: "executing the task"
        def metrics = executor.executeWithMetrics(0)
        
        then: "metrics show failure"
        !metrics.isSuccess()
        metrics.isFailure()
        metrics.result() instanceof TaskResultFailure
        ((TaskResultFailure) metrics.result()).error() == error
    }
    
    def "should catch exceptions and wrap in failure"() {
        given: "a task that throws exception"
        def error = new RuntimeException("uncaught exception")
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() {}
            
            @Override
            TaskResult execute(long iteration) throws Exception {
                throw error
            }
            
            @Override
            void teardown() {}
        }
        def executor = new TaskExecutor(task)
        
        when: "executing the task"
        def metrics = executor.executeWithMetrics(0)
        
        then: "exception is wrapped in failure"
        metrics.isFailure()
        metrics.result() instanceof TaskResultFailure
        ((TaskResultFailure) metrics.result()).error() == error
    }
    
    def "should track iteration number"() {
        given: "a simple task"
        TaskLifecycle task = new TaskLifecycle() {
            @Override
            void init() {}
            
            @Override
            TaskResult execute(long iteration) {
                return TaskResult.success()
            }
            
            @Override
            void teardown() {}
        }
        def executor = new TaskExecutor(task)
        
        when: "executing multiple iterations"
        def metrics0 = executor.executeWithMetrics(0)
        def metrics5 = executor.executeWithMetrics(5)
        def metrics10 = executor.executeWithMetrics(10)
        
        then: "iteration numbers are tracked"
        metrics0.iteration() == 0
        metrics5.iteration() == 5
        metrics10.iteration() == 10
    }
}
