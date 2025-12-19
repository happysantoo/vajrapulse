package com.vajrapulse.api

import spock.lang.Specification
import spock.lang.Timeout
import com.vajrapulse.api.task.TaskResult
import com.vajrapulse.api.task.TaskResultSuccess
import com.vajrapulse.api.task.TaskResultFailure

@Timeout(10)
class TaskResultSpec extends Specification {

    def "should create success with data"() {
        given: "some result data"
        def data = "test-data"
        
        when: "creating a success result"
        def result = com.vajrapulse.api.task.TaskResult.success(data)
        
        then: "result contains the data"
        result instanceof TaskResultSuccess
        result.data() == data
    }
    
    def "should create success without data"() {
        when: "creating a success result with no data"
        def result = com.vajrapulse.api.task.TaskResult.success()
        
        then: "result has null data"
        result instanceof TaskResultSuccess
        result.data() == null
    }
    
    def "should create failure with error"() {
        given: "an exception"
        def error = new RuntimeException("test error")
        
        when: "creating a failure result"
        def result = com.vajrapulse.api.task.TaskResult.failure(error)
        
        then: "result contains the error"
        result instanceof TaskResultFailure
        result.error() == error
    }
    
    def "should support pattern matching"() {
        given: "success and failure results"
        def success = com.vajrapulse.api.task.TaskResult.success("data")
        def failure = com.vajrapulse.api.task.TaskResult.failure(new Exception("test"))
        
        when: "checking success type"
        def successData = success instanceof TaskResultSuccess ? success.data() : null
        
        and: "checking failure type"
        def failureError = failure instanceof TaskResultFailure ? failure.error().message : null
        
        then: "types are correct"
        success instanceof TaskResultSuccess
        successData == "data"
        
        and: "failure has error"
        failure instanceof TaskResultFailure
        failureError == "test"
    }
}
