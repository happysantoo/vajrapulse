package com.vajrapulse.api

import spock.lang.Specification

class TaskSpec extends Specification {

    def "should execute default setup successfully"() {
        given:
        Task task = new Task() {
            @Override
            TaskResult execute() throws Exception {
                return TaskResult.success()
            }
        }

        when:
        task.setup()

        then:
        noExceptionThrown()
    }

    def "should execute default cleanup successfully"() {
        given:
        Task task = new Task() {
            @Override
            TaskResult execute() throws Exception {
                return TaskResult.success()
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
        Task task = new Task() {
            @Override
            void setup() {
                lifecycle << "setup"
            }

            @Override
            TaskResult execute() throws Exception {
                lifecycle << "execute"
                return TaskResult.success("data")
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
        result instanceof TaskResult.Success
    }
}
