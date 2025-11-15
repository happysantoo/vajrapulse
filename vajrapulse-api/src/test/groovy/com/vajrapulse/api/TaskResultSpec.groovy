package com.vajrapulse.api

import spock.lang.Specification

class TaskResultSpec extends Specification {

    def "should create success with data"() {
        given: "some result data"
        def data = "test-data"
        
        when: "creating a success result"
        def result = TaskResult.success(data)
        
        then: "result contains the data"
        result instanceof TaskResult.Success
        result.data() == data
    }
    
    def "should create success without data"() {
        when: "creating a success result with no data"
        def result = TaskResult.success()
        
        then: "result has null data"
        result instanceof TaskResult.Success
        result.data() == null
    }
    
    def "should create failure with error"() {
        given: "an exception"
        def error = new RuntimeException("test error")
        
        when: "creating a failure result"
        def result = TaskResult.failure(error)
        
        then: "result contains the error"
        result instanceof TaskResult.Failure
        result.error() == error
    }
    
    def "should support pattern matching"() {
        given: "success and failure results"
        def success = TaskResult.success("data")
        def failure = TaskResult.failure(new Exception("test"))
        
        when: "checking success type"
        def successData = success instanceof TaskResult.Success ? success.data() : null
        
        and: "checking failure type"
        def failureError = failure instanceof TaskResult.Failure ? failure.error().message : null
        
        then: "types are correct"
        success instanceof TaskResult.Success
        successData == "data"
        
        and: "failure has error"
        failure instanceof TaskResult.Failure
        failureError == "test"
    }
}
