package com.vajrapulse.api

import spock.lang.Specification
import java.time.Duration

class AssertionsSpec extends Specification {

    def "should validate latency assertion passes when below threshold"() {
        given:
        def assertion = Assertions.latency(0.95, Duration.ofMillis(100))
        def metrics = createMetrics(
            successPercentiles: [0.95d: 50_000_000.0d] // 50ms in nanos
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.passed()
    }

    def "should validate latency assertion fails when above threshold"() {
        given:
        def assertion = Assertions.latency(0.95, Duration.ofMillis(100))
        def metrics = createMetrics(
            successPercentiles: [0.95d: 150_000_000.0d] // 150ms in nanos
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.failed()
        result.message().contains("P95")
        result.message().contains("150.00ms")
        result.message().contains("100.00ms")
    }

    def "should validate latency assertion fails when percentile missing"() {
        given:
        def assertion = Assertions.latency(0.95, Duration.ofMillis(100))
        def metrics = createMetrics(
            successPercentiles: [0.50d: 10_000_000.0d] // Missing P95
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.failed()
        result.message().contains("not available")
    }

    def "should validate error rate assertion passes when below threshold"() {
        given:
        def assertion = Assertions.errorRate(0.01) // 1% max
        def metrics = createMetrics(
            totalExecutions: 1000L,
            failureCount: 5L // 0.5% error rate
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.passed()
    }

    def "should validate error rate assertion fails when above threshold"() {
        given:
        def assertion = Assertions.errorRate(0.01) // 1% max
        def metrics = createMetrics(
            totalExecutions: 1000L,
            failureCount: 20L // 2% error rate
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.failed()
        result.message().contains("Error rate")
        result.message().contains("2.00%")
        result.message().contains("1.00%")
    }

    def "should validate success rate assertion passes when above threshold"() {
        given:
        def assertion = Assertions.successRate(0.99) // 99% min
        def metrics = createMetrics(
            totalExecutions: 1000L,
            successCount: 995L // 99.5% success rate
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.passed()
    }

    def "should validate success rate assertion fails when below threshold"() {
        given:
        def assertion = Assertions.successRate(0.99) // 99% min
        def metrics = createMetrics(
            totalExecutions: 1000L,
            failureCount: 20L // 20 failures = 98% success rate (980/1000)
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.failed()
        result.message().contains("Success rate")
        result.message().contains("98.00%")
        result.message().contains("99.00%")
    }

    def "should validate throughput assertion passes when above threshold"() {
        given:
        def assertion = Assertions.throughput(1000.0)
        def metrics = createMetrics(
            elapsedMillis: 1000L,
            totalExecutions: 1500L // 1500 TPS
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.passed()
    }

    def "should validate throughput assertion fails when below threshold"() {
        given:
        def assertion = Assertions.throughput(1000.0)
        def metrics = createMetrics(
            elapsedMillis: 1000L,
            totalExecutions: 500L // 500 TPS
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.failed()
        result.message().contains("Throughput")
        result.message().contains("500.00 TPS")
        result.message().contains("1000.00 TPS")
    }

    def "should validate execution count assertion passes when above threshold"() {
        given:
        def assertion = Assertions.executionCount(1000L)
        def metrics = createMetrics(
            totalExecutions: 1500L
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.passed()
    }

    def "should validate execution count assertion fails when below threshold"() {
        given:
        def assertion = Assertions.executionCount(1000L)
        def metrics = createMetrics(
            totalExecutions: 500L
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.failed()
        result.message().contains("Total executions")
        result.message().contains("500")
        result.message().contains("1000")
    }

    def "should validate composite assertion passes when all pass"() {
        given:
        def assertion = Assertions.all(
            Assertions.latency(0.95, Duration.ofMillis(100)),
            Assertions.errorRate(0.01),
            Assertions.throughput(1000.0)
        )
        def metrics = createMetrics(
            successPercentiles: [0.95d: 50_000_000.0d],
            totalExecutions: 2000L,
            failureCount: 10L, // 0.5% error rate
            elapsedMillis: 1000L
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.passed()
    }

    def "should validate composite assertion fails when any fails"() {
        given:
        def assertion = Assertions.all(
            Assertions.latency(0.95, Duration.ofMillis(100)),
            Assertions.errorRate(0.01),
            Assertions.throughput(1000.0)
        )
        def metrics = createMetrics(
            successPercentiles: [0.95d: 150_000_000.0d], // Fails latency
            totalExecutions: 2000L,
            failureCount: 10L,
            elapsedMillis: 1000L
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.failed()
        result.message().contains("P95") // First failure message
    }

    def "should validate any assertion passes when at least one passes"() {
        given:
        def assertion = Assertions.any(
            Assertions.latency(0.95, Duration.ofMillis(50)), // This will fail
            Assertions.errorRate(0.01), // This will pass
            Assertions.throughput(5000.0) // This will fail
        )
        def metrics = createMetrics(
            successPercentiles: [0.95d: 100_000_000.0d], // 100ms > 50ms
            totalExecutions: 2000L,
            failureCount: 10L, // 0.5% < 1%
            elapsedMillis: 1000L // 2000 TPS < 5000 TPS
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.passed()
    }

    def "should validate any assertion fails when all fail"() {
        given:
        def assertion = Assertions.any(
            Assertions.latency(0.95, Duration.ofMillis(50)),
            Assertions.errorRate(0.01),
            Assertions.throughput(5000.0)
        )
        def metrics = createMetrics(
            successPercentiles: [0.95d: 100_000_000.0d], // All fail
            totalExecutions: 2000L,
            failureCount: 50L, // 2.5% > 1%
            elapsedMillis: 1000L // 2000 TPS < 5000 TPS
        )

        when:
        def result = assertion.evaluate(metrics)

        then:
        result.failed()
        result.message().contains("All assertions failed")
    }

    def "should reject invalid percentile"() {
        when:
        Assertions.latency(1.5, Duration.ofMillis(100))

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject negative max latency"() {
        when:
        Assertions.latency(0.95, Duration.ofMillis(-1))

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject invalid error rate"() {
        when:
        Assertions.errorRate(1.5)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject invalid success rate"() {
        when:
        Assertions.successRate(1.5)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject negative min TPS"() {
        when:
        Assertions.throughput(-1.0)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject negative min executions"() {
        when:
        Assertions.executionCount(-1L)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject null assertions array"() {
        when:
        Assertions.all((Assertion[]) null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject empty assertions array"() {
        when:
        Assertions.all()

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject assertions array with null"() {
        when:
        Assertions.all(Assertions.errorRate(0.01), null)

        then:
        thrown(IllegalArgumentException)
    }

    // Helper method to create test metrics
    private Metrics createMetrics(Map<String, Object> params) {
        return new Metrics() {
            private long getElapsedMillis() {
                return params.getOrDefault("elapsedMillis", 1000L) as Long
            }

            @Override
            long totalExecutions() {
                return params.getOrDefault("totalExecutions", 1000L) as Long
            }

            @Override
            long successCount() {
                long total = totalExecutions()
                long failures = failureCount()
                return total - failures
            }

            @Override
            long failureCount() {
                return params.getOrDefault("failureCount", 0L) as Long
            }

            @Override
            double successRate() {
                long total = totalExecutions()
                if (total == 0) return 0.0
                return (successCount() * 100.0) / total
            }

            @Override
            double failureRate() {
                long total = totalExecutions()
                if (total == 0) return 0.0
                return (failureCount() * 100.0) / total
            }

            @Override
            double responseTps() {
                long elapsed = getElapsedMillis()
                if (elapsed == 0) return 0.0
                return (totalExecutions() * 1000.0) / elapsed
            }

            @Override
            double successTps() {
                long elapsed = getElapsedMillis()
                if (elapsed == 0) return 0.0
                return (successCount() * 1000.0) / elapsed
            }

            @Override
            double failureTps() {
                long elapsed = getElapsedMillis()
                if (elapsed == 0) return 0.0
                return (failureCount() * 1000.0) / elapsed
            }

            @Override
            Map<Double, Double> successPercentiles() {
                return params.getOrDefault("successPercentiles", [:]) as Map<Double, Double>
            }

            @Override
            Map<Double, Double> failurePercentiles() {
                return params.getOrDefault("failurePercentiles", [:]) as Map<Double, Double>
            }
        }
    }
}

