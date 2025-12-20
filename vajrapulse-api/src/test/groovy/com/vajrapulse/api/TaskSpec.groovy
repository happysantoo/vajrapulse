package com.vajrapulse.api

import com.vajrapulse.api.task.Task
import com.vajrapulse.api.task.TaskResult
import spock.lang.Specification
import spock.lang.Timeout

@Timeout(10)
class TaskSpec extends Specification {

    def "should execute default setup successfully"() {
        given:
        Task task = new com.vajrapulse.api.task.Task() {
            @Override
            com.vajrapulse.api.task.TaskResult execute() throws Exception {
                return com.vajrapulse.api.task.TaskResult.success()
            }
        }

        when:
        task.setup()

        then:
        noExceptionThrown()
    }

    def "should execute default cleanup successfully"() {
        given:
        Task task = new com.vajrapulse.api.task.Task() {
            @Override
            com.vajrapulse.api.task.TaskResult execute() throws Exception {
                return com.vajrapulse.api.task.TaskResult.success()
            }
        }

        when:
        task.cleanup()

        then:
        noExceptionThrown()
    }

    def "should call setup, execute, cleanup lifecycle"() {
        given:
        def lifecycle = []
        Task task = new com.vajrapulse.api.task.Task() {
            @Override
            void setup() {
                lifecycle << "setup"
            }

            @Override
            com.vajrapulse.api.task.TaskResult execute() throws Exception {
                lifecycle << "execute"
                return com.vajrapulse.api.task.TaskResult.success("data")
            }

            @Override
            void cleanup() {
                lifecycle << "cleanup"
            }
        }

        when:
        task.setup()
        def result = task.execute()
        task.cleanup()

        then:
        lifecycle == ["setup", "execute", "cleanup"]
        result instanceof com.vajrapulse.api.task.TaskResultSuccess
    }
}
