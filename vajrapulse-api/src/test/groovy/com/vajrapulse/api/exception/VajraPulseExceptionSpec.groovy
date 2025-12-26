package com.vajrapulse.api.exception

import spock.lang.Specification

/**
 * Tests for VajraPulse exception hierarchy.
 */
class VajraPulseExceptionSpec extends Specification {
    
    def "VajraPulseException should create with message"() {
        when: "creating exception with message"
        def exception = new VajraPulseException("Test message")
        
        then: "message should be set"
        exception.message == "Test message"
        exception.cause == null
    }
    
    def "VajraPulseException should create with message and cause"() {
        given: "a cause exception"
        def cause = new RuntimeException("Root cause")
        
        when: "creating exception with message and cause"
        def exception = new VajraPulseException("Test message", cause)
        
        then: "message and cause should be set"
        exception.message == "Test message"
        exception.cause == cause
    }
    
    def "ValidationException should create with message"() {
        when: "creating validation exception"
        def exception = new ValidationException("Invalid parameter")
        
        then: "should be instance of VajraPulseException"
        exception instanceof VajraPulseException
        exception.message == "Invalid parameter"
    }
    
    def "ValidationException should create with message and cause"() {
        given: "a cause exception"
        def cause = new IllegalArgumentException("Root cause")
        
        when: "creating validation exception with cause"
        def exception = new ValidationException("Invalid parameter", cause)
        
        then: "should have message and cause"
        exception.message == "Invalid parameter"
        exception.cause == cause
    }
    
    def "ExecutionException should create with message"() {
        when: "creating execution exception"
        def exception = new ExecutionException("Execution failed")
        
        then: "should be instance of VajraPulseException"
        exception instanceof VajraPulseException
        exception.message == "Execution failed"
    }
    
    def "ExecutionException should create with message and cause"() {
        given: "a cause exception"
        def cause = new RuntimeException("Root cause")
        
        when: "creating execution exception with cause"
        def exception = new ExecutionException("Execution failed", cause)
        
        then: "should have message and cause"
        exception.message == "Execution failed"
        exception.cause == cause
    }
}
