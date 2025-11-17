package com.vajrapulse.api

import spock.lang.Specification

class TaskIdentitySpec extends Specification {

    def "should create identity with name only"() {
        when:
        def identity = TaskIdentity.of("test-task")

        then:
        identity.name() == "test-task"
        identity.tags().isEmpty()
    }

    def "should create identity with single tag"() {
        when:
        def identity = TaskIdentity.of("test-task", "env", "prod")

        then:
        identity.name() == "test-task"
        identity.tags().size() == 1
        identity.tags().get("env") == "prod"
    }

    def "should create identity with multiple tags"() {
        given:
        def tags = ["env": "staging", "region": "us-west-2"]

        when:
        def identity = new TaskIdentity("complex-task", tags)

        then:
        identity.name() == "complex-task"
        identity.tags().size() == 2
        identity.tags().get("env") == "staging"
        identity.tags().get("region") == "us-west-2"
    }

    def "should treat null tags as empty map"() {
        when:
        def identity = new TaskIdentity("simple", null)

        then:
        identity.tags() != null
        identity.tags().isEmpty()
    }

    def "should copy tags defensively"() {
        given:
        def mutableTags = new HashMap<String, String>()
        mutableTags.put("key", "original")
        def identity = new TaskIdentity("task", mutableTags)

        when:
        mutableTags.put("key", "modified")
        mutableTags.put("newKey", "newValue")

        then:
        identity.tags().get("key") == "original"
        identity.tags().size() == 1
    }

    def "should reject null name"() {
        when:
        new TaskIdentity(null, [:])

        then:
        thrown(NullPointerException)
    }

    def "should reject blank name"() {
        when:
        new TaskIdentity("   ", [:])

        then:
        thrown(IllegalArgumentException)
    }

    def "should handle empty tags map"() {
        when:
        def identity = new TaskIdentity("task", [:])

        then:
        identity.tags().isEmpty()
    }
}
