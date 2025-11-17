package com.vajrapulse.core.engine

import com.vajrapulse.api.LoadPattern
import com.vajrapulse.api.StaticLoad
import spock.lang.Specification

import java.time.Duration

class RateControllerSpec extends Specification {

    def "should calculate current TPS from load pattern"() {
        given: "a static load of 100 TPS"
        def load = new StaticLoad(100.0, Duration.ofSeconds(10))
        def controller = new RateController(load)
        
        expect: "current TPS matches static value"
        controller.getCurrentTps() == 100.0
        
        when: "some time passes"
        Thread.sleep(100)
        
        then: "TPS remains constant"
        controller.getCurrentTps() == 100.0
    }
    
    def "should track elapsed time"() {
        given: "a rate controller"
        def load = new StaticLoad(100.0, Duration.ofSeconds(10))
        def controller = new RateController(load)
        
        and: "initial elapsed time"
        def initialElapsed = controller.getElapsedMillis()
        
        when: "some time passes"
        Thread.sleep(100)
        
        then: "elapsed time increases"
        controller.getElapsedMillis() > initialElapsed
        controller.getElapsedMillis() >= 100
    }
    
    def "should control rate for low TPS"() {
        given: "a rate controller with 10 TPS (100ms per execution)"
        def load = new StaticLoad(10.0, Duration.ofSeconds(2))
        def controller = new RateController(load)
        
        when: "executing rapidly"
        def start = System.nanoTime()
        5.times {
            controller.waitForNext()
        }
        def elapsed = (System.nanoTime() - start) / 1_000_000
        
        then: "rate is controlled (~400ms for 5 executions at 10 TPS)"
        elapsed >= 300  // Allow some timing variance
        elapsed <= 600
    }
}
