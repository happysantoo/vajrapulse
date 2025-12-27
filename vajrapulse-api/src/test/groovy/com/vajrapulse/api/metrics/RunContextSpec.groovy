package com.vajrapulse.api.metrics

import spock.lang.Specification
import spock.lang.Timeout
import java.time.Instant

@Timeout(10)
class RunContextSpec extends Specification {

    def "should create RunContext with all values"() {
        given: "run context parameters"
        def runId = "test-run-123"
        def startTime = Instant.now()
        def taskClass = "HttpLoadTest"
        def loadPatternType = "StaticLoad"
        def configuration = [tps: 100.0, duration: "5m"]
        
        when: "creating RunContext"
        def context = RunContext.of(runId, startTime, taskClass, loadPatternType, configuration)
        
        then: "all values are stored correctly"
        context.runId() == runId
        context.startTime() == startTime
        context.endTime() == null
        context.taskClass() == taskClass
        context.loadPatternType() == loadPatternType
        context.configuration() == configuration
        context.systemInfo() != null
        context.systemInfo().javaVersion() != "unknown"
    }
    
    def "should create RunContext with end time"() {
        given: "run context parameters with end time"
        def runId = "test-run-456"
        def startTime = Instant.now().minusSeconds(60)
        def endTime = Instant.now()
        def taskClass = "MyTask"
        def loadPatternType = "RampUpLoad"
        def configuration = [startTps: 10.0, endTps: 100.0]
        def systemInfo = SystemInfo.current()
        
        when: "creating RunContext with end time"
        def context = RunContext.of(runId, startTime, endTime, taskClass, loadPatternType, configuration, systemInfo)
        
        then: "all values including end time are stored"
        context.runId() == runId
        context.startTime() == startTime
        context.endTime() == endTime
        context.taskClass() == taskClass
        context.loadPatternType() == loadPatternType
    }
    
    def "should create empty RunContext"() {
        when: "creating empty context"
        def context = RunContext.empty()
        
        then: "context has unknown/default values"
        context.runId() == "unknown"
        context.startTime() == Instant.EPOCH
        context.endTime() == null
        context.taskClass() == "unknown"
        context.loadPatternType() == "unknown"
        context.configuration().isEmpty()
        context.systemInfo().javaVersion() == "unknown"
    }
    
    def "should create immutable configuration copy"() {
        given: "mutable configuration map"
        def originalConfig = new HashMap<String, Object>()
        originalConfig.put("tps", 100.0)
        
        when: "creating context and modifying original"
        def context = RunContext.of("run-1", Instant.now(), "Task", "Pattern", originalConfig)
        originalConfig.put("newKey", "newValue")
        
        then: "context configuration is not affected"
        !context.configuration().containsKey("newKey")
        context.configuration().size() == 1
    }
    
    def "should throw on null required parameters"() {
        when: "creating context with null runId"
        RunContext.of(null, Instant.now(), "Task", "Pattern", [:])
        
        then: "exception is thrown"
        thrown(NullPointerException)
    }
}
