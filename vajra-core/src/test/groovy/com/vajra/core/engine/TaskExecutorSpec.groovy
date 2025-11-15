package com.vajra.core.engine

import com.vajra.api.Task
import com.vajra.api.TaskResult
import spock.lang.Specification

class TaskExecutorSpec extends Specification {

    def "should execute task successfully and capture metrics"() {
        given: "a simple successful task"
        Task task = new Task() {
            @Override
            TaskResult execute() {
                return TaskResult.success("test-data")
            }
        }
        def executor = new TaskExecutor(task)
        
        when: "executing the task"
        def metrics = executor.executeWithMetrics(0)
        
        then: "metrics show success"
        metrics.isSuccess()
        !metrics.isFailure()
        metrics.durationNanos() > 0
        metrics.iteration() == 0
        metrics.result() instanceof TaskResult.Success
        ((TaskResult.Success) metrics.result()).data() == "test-data"
    }
    
    def "should capture failure when task fails"() {
        given: "a task that returns failure"
        def error = new RuntimeException("test error")
        Task task = new Task() {
            @Override
            TaskResult execute() {
                return TaskResult.failure(error)
            }
        }
        def executor = new TaskExecutor(task)
        
        when: "executing the task"
        def metrics = executor.executeWithMetrics(0)
        
        then: "metrics show failure"
        !metrics.isSuccess()
        metrics.isFailure()
        metrics.result() instanceof TaskResult.Failure
        ((TaskResult.Failure) metrics.result()).error() == error
    }
    
    def "should catch exceptions and wrap in failure"() {
        given: "a task that throws exception"
        def error = new RuntimeException("uncaught exception")
        Task task = new Task() {
            @Override
            TaskResult execute() throws Exception {
                throw error
            }
        }
        def executor = new TaskExecutor(task)
        
        when: "executing the task"
        def metrics = executor.executeWithMetrics(0)
        
        then: "exception is wrapped in failure"
        metrics.isFailure()
        metrics.result() instanceof TaskResult.Failure
        ((TaskResult.Failure) metrics.result()).error() == error
    }
    
    def "should track iteration number"() {
        given: "a simple task"
        Task task = new Task() {
            @Override
            TaskResult execute() {
                return TaskResult.success()
            }
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
