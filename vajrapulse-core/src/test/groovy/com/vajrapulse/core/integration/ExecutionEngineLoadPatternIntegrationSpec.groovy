package com.vajrapulse.core.integration

import com.vajrapulse.api.*
import com.vajrapulse.core.engine.ExecutionEngine
import com.vajrapulse.core.metrics.MetricsCollector
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive integration tests for ExecutionEngine with all load pattern combinations.
 * 
 * <p>Tests verify:
 * <ul>
 *   <li>All load patterns work correctly with ExecutionEngine</li>
 *   <li>Metrics are accurately collected</li>
 *   <li>Thread strategies (VirtualThreads, PlatformThreads, default) work correctly</li>
 *   <li>Queue depth tracking is accurate</li>
 *   <li>Shutdown behavior is graceful</li>
 * </ul>
 */
@Timeout(30)
class ExecutionEngineLoadPatternIntegrationSpec extends Specification {

    // Test task implementations
    
    @VirtualThreads
    static class VirtualThreadSuccessTask implements Task {
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(5) // Simulate I/O
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    @PlatformThreads(poolSize = 4)
    static class PlatformThreadSuccessTask implements Task {
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            // CPU-bound simulation
            Thread.sleep(2)
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    static class DefaultThreadSuccessTask implements Task {
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(3)
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    @VirtualThreads
    static class MixedResultTask implements Task {
        private final AtomicInteger counter = new AtomicInteger(0)
        
        @Override
        void setup() {}
        
        @Override
        TaskResult execute() throws Exception {
            Thread.sleep(5)
            int count = counter.incrementAndGet()
            if (count % 5 == 0) {
                return TaskResult.failure(new RuntimeException("Simulated failure #" + count))
            }
            return TaskResult.success("ok")
        }
        
        @Override
        void cleanup() {}
    }
    
    // Load pattern test implementations
    
    static class ShortStaticLoad implements LoadPattern {
        private final double tps
        private final Duration duration
        
        ShortStaticLoad(double tps, Duration duration) {
            this.tps = tps
            this.duration = duration
        }
        
        @Override
        double calculateTps(long elapsedMillis) {
            return elapsedMillis < duration.toMillis() ? tps : 0.0
        }
        
        @Override
        Duration getDuration() {
            return duration
        }
    }
    
    static class ShortRampUpLoad implements LoadPattern {
        private final double maxTps
        private final Duration rampDuration
        
        ShortRampUpLoad(double maxTps, Duration rampDuration) {
            this.maxTps = maxTps
            this.rampDuration = rampDuration
        }
        
        @Override
        double calculateTps(long elapsedMillis) {
            long rampMillis = rampDuration.toMillis()
            if (elapsedMillis >= rampMillis) {
                return maxTps
            }
            return maxTps * elapsedMillis / (double) rampMillis
        }
        
        @Override
        Duration getDuration() {
            return rampDuration
        }
    }
    
    static class ShortRampUpToMaxLoad implements LoadPattern {
        private final double maxTps
        private final Duration rampDuration
        private final Duration sustainDuration
        
        ShortRampUpToMaxLoad(double maxTps, Duration rampDuration, Duration sustainDuration) {
            this.maxTps = maxTps
            this.rampDuration = rampDuration
            this.sustainDuration = sustainDuration
        }
        
        @Override
        double calculateTps(long elapsedMillis) {
            long rampMillis = rampDuration.toMillis()
            if (elapsedMillis >= rampMillis) {
                return maxTps
            }
            return maxTps * elapsedMillis / (double) rampMillis
        }
        
        @Override
        Duration getDuration() {
            return rampDuration.plus(sustainDuration)
        }
    }
    
    static class ShortStepLoad implements LoadPattern {
        private final List<StepLoad.Step> steps
        
        ShortStepLoad(List<StepLoad.Step> steps) {
            this.steps = steps
        }
        
        @Override
        double calculateTps(long elapsedMillis) {
            if (elapsedMillis < 0) return 0.0
            long remaining = elapsedMillis
            for (StepLoad.Step s : steps) {
                long d = s.duration().toMillis()
                if (remaining < d) {
                    return s.rate()
                }
                remaining -= d
            }
            return 0.0
        }
        
        @Override
        Duration getDuration() {
            long total = 0L
            for (StepLoad.Step s : steps) {
                total += s.duration().toMillis()
            }
            return Duration.ofMillis(total)
        }
    }
    
    static class ShortSineWaveLoad implements LoadPattern {
        private final double meanRate
        private final double amplitude
        private final Duration totalDuration
        private final Duration period
        
        ShortSineWaveLoad(double meanRate, double amplitude, Duration totalDuration, Duration period) {
            this.meanRate = meanRate
            this.amplitude = amplitude
            this.totalDuration = totalDuration
            this.period = period
        }
        
        @Override
        double calculateTps(long elapsedMillis) {
            if (elapsedMillis < 0) return 0.0
            long p = period.toMillis()
            double angle = 2.0 * Math.PI * (elapsedMillis % p) / (double) p
            double value = meanRate + amplitude * Math.sin(angle)
            return value < 0.0 ? 0.0 : value
        }
        
        @Override
        Duration getDuration() {
            return totalDuration
        }
    }
    
    static class ShortSpikeLoad implements LoadPattern {
        private final double baseRate
        private final double spikeRate
        private final Duration totalDuration
        private final Duration spikeInterval
        private final Duration spikeDuration
        
        ShortSpikeLoad(double baseRate, double spikeRate, Duration totalDuration, 
                      Duration spikeInterval, Duration spikeDuration) {
            this.baseRate = baseRate
            this.spikeRate = spikeRate
            this.totalDuration = totalDuration
            this.spikeInterval = spikeInterval
            this.spikeDuration = spikeDuration
        }
        
        @Override
        double calculateTps(long elapsedMillis) {
            if (elapsedMillis < 0) return 0.0
            long intervalMs = spikeInterval.toMillis()
            long durMs = spikeDuration.toMillis()
            long position = elapsedMillis % intervalMs
            return position < durMs ? spikeRate : baseRate
        }
        
        @Override
        Duration getDuration() {
            return totalDuration
        }
    }
    
    // Test cases
    
    @Unroll
    def "should execute StaticLoad pattern with #threadStrategy task"() {
        given: "a task with #threadStrategy and StaticLoad pattern"
        Task task = taskInstance
        LoadPattern load = new ShortStaticLoad(50.0, Duration.ofMillis(200))
        MetricsCollector collector = MetricsCollector.createWithRunId("static-${threadStrategy}", [0.50d, 0.95d, 0.99d] as double[])
        
        when: "running the engine"
        ExecutionEngine engine = new ExecutionEngine(task, load, collector)
        engine.run()
        def snapshot = collector.snapshot()
        
        then: "metrics are collected correctly"
        snapshot.totalExecutions() > 0
        snapshot.successCount() == snapshot.totalExecutions()
        snapshot.failureCount() == 0
        snapshot.queueSize() == 0 // Queue should be empty after completion
        snapshot.successPercentiles().size() == 3
        snapshot.queueWaitPercentiles().size() == 3
        
        where:
        threadStrategy | taskInstance
        "VirtualThreads" | new VirtualThreadSuccessTask()
        "PlatformThreads" | new PlatformThreadSuccessTask()
        "Default" | new DefaultThreadSuccessTask()
    }
    
    @Unroll
    def "should execute RampUpLoad pattern with #patternName"() {
        given: "a task and RampUpLoad pattern"
        Task task = new VirtualThreadSuccessTask()
        LoadPattern load = new ShortRampUpLoad(maxTps, rampDuration)
        MetricsCollector collector = MetricsCollector.createWithRunId("rampup-${patternName}", [0.50d, 0.95d] as double[])
        
        when: "running the engine"
        ExecutionEngine engine = new ExecutionEngine(task, load, collector)
        engine.run()
        def snapshot = collector.snapshot()
        
        then: "metrics show ramp-up behavior"
        snapshot.totalExecutions() > 0
        snapshot.successCount() == snapshot.totalExecutions()
        snapshot.elapsedMillis() >= rampDuration.toMillis()
        
        where:
        patternName | maxTps | rampDuration
        "LowRate" | 20.0 | Duration.ofMillis(150)
        "MediumRate" | 50.0 | Duration.ofMillis(200)
        "HighRate" | 100.0 | Duration.ofMillis(250)
    }
    
    @Unroll
    def "should execute RampUpToMaxLoad pattern with sustain"() {
        given: "a task and RampUpToMaxLoad pattern"
        Task task = new VirtualThreadSuccessTask()
        LoadPattern load = new ShortRampUpToMaxLoad(50.0, Duration.ofMillis(100), Duration.ofMillis(100))
        MetricsCollector collector = MetricsCollector.createWithRunId("rampup-sustain", [0.50d, 0.95d] as double[])
        
        when: "running the engine"
        ExecutionEngine engine = new ExecutionEngine(task, load, collector)
        engine.run()
        def snapshot = collector.snapshot()
        
        then: "metrics show ramp-up and sustain"
        snapshot.totalExecutions() > 0
        snapshot.successCount() == snapshot.totalExecutions()
        snapshot.elapsedMillis() >= 200 // Should be at least ramp + sustain
    }
    
    @Unroll
    def "should execute StepLoad pattern with #stepCount steps"() {
        given: "a task and StepLoad pattern"
        Task task = new VirtualThreadSuccessTask()
        List<StepLoad.Step> steps = stepList
        LoadPattern load = new ShortStepLoad(steps)
        MetricsCollector collector = MetricsCollector.createWithRunId("step-${stepCount}", [0.50d, 0.95d] as double[])
        
        when: "running the engine"
        ExecutionEngine engine = new ExecutionEngine(task, load, collector)
        engine.run()
        def snapshot = collector.snapshot()
        
        then: "metrics show step behavior"
        snapshot.totalExecutions() > 0
        snapshot.successCount() == snapshot.totalExecutions()
        
        where:
        stepCount | stepList
        2 | [new StepLoad.Step(20.0, Duration.ofMillis(50)), new StepLoad.Step(50.0, Duration.ofMillis(50))]
        3 | [new StepLoad.Step(10.0, Duration.ofMillis(40)), new StepLoad.Step(30.0, Duration.ofMillis(40)), new StepLoad.Step(60.0, Duration.ofMillis(40))]
    }
    
    def "should execute SineWaveLoad pattern"() {
        given: "a task and SineWaveLoad pattern"
        Task task = new VirtualThreadSuccessTask()
        LoadPattern load = new ShortSineWaveLoad(30.0, 15.0, Duration.ofMillis(200), Duration.ofMillis(50))
        MetricsCollector collector = MetricsCollector.createWithRunId("sine", [0.50d, 0.95d] as double[])
        
        when: "running the engine"
        ExecutionEngine engine = new ExecutionEngine(task, load, collector)
        engine.run()
        def snapshot = collector.snapshot()
        
        then: "metrics show sinusoidal behavior"
        snapshot.totalExecutions() > 0
        snapshot.successCount() == snapshot.totalExecutions()
        snapshot.elapsedMillis() >= 200
    }
    
    def "should execute SpikeLoad pattern"() {
        given: "a task and SpikeLoad pattern"
        Task task = new VirtualThreadSuccessTask()
        LoadPattern load = new ShortSpikeLoad(20.0, 80.0, Duration.ofMillis(200), Duration.ofMillis(50), Duration.ofMillis(10))
        MetricsCollector collector = MetricsCollector.createWithRunId("spike", [0.50d, 0.95d] as double[])
        
        when: "running the engine"
        ExecutionEngine engine = new ExecutionEngine(task, load, collector)
        engine.run()
        def snapshot = collector.snapshot()
        
        then: "metrics show spike behavior"
        snapshot.totalExecutions() > 0
        snapshot.successCount() == snapshot.totalExecutions()
        snapshot.elapsedMillis() >= 200
    }
    
    def "should track queue depth correctly"() {
        given: "a task with slow execution and high TPS"
        Task slowTask = new Task() {
            @Override
            void setup() {}
            
            @Override
            TaskResult execute() throws Exception {
                Thread.sleep(20) // Slow execution
                return TaskResult.success("ok")
            }
            
            @Override
            void cleanup() {}
        }
        LoadPattern load = new ShortStaticLoad(100.0, Duration.ofMillis(100))
        MetricsCollector collector = MetricsCollector.createWithRunId("queue-test", [0.50d] as double[])
        
        when: "running the engine"
        ExecutionEngine engine = new ExecutionEngine(slowTask, load, collector)
        engine.run()
        def snapshot = collector.snapshot()
        
        then: "queue depth is tracked and eventually zero"
        snapshot.queueSize() == 0 // Should be empty after completion
        snapshot.totalExecutions() > 0
        // Queue wait time should be recorded
        snapshot.queueWaitPercentiles().size() > 0
    }
    
    def "should handle mixed success/failure results"() {
        given: "a task that fails sometimes"
        Task task = new MixedResultTask()
        LoadPattern load = new ShortStaticLoad(50.0, Duration.ofMillis(200))
        MetricsCollector collector = MetricsCollector.createWithRunId("mixed", [0.50d, 0.95d, 0.99d] as double[])
        
        when: "running the engine"
        ExecutionEngine engine = new ExecutionEngine(task, load, collector)
        engine.run()
        def snapshot = collector.snapshot()
        
        then: "both success and failure metrics are recorded"
        snapshot.totalExecutions() > 0
        snapshot.successCount() > 0
        snapshot.failureCount() > 0
        snapshot.successCount() + snapshot.failureCount() == snapshot.totalExecutions()
        snapshot.successPercentiles().size() > 0
        snapshot.failurePercentiles().size() > 0
        snapshot.successRate() > 0
        snapshot.failureRate() > 0
    }
    
    def "should handle graceful shutdown"() {
        given: "a task and long-running pattern"
        Task task = new VirtualThreadSuccessTask()
        LoadPattern load = new ShortStaticLoad(50.0, Duration.ofMillis(1000))
        MetricsCollector collector = MetricsCollector.createWithRunId("shutdown", [0.50d] as double[])
        ExecutionEngine engine = new ExecutionEngine(task, load, collector)
        
        when: "stopping the engine early"
        Thread.start {
            sleep(100)
            engine.stop()
        }
        engine.run()
        def snapshot = collector.snapshot()
        
        then: "execution stops gracefully"
        snapshot.totalExecutions() > 0
        snapshot.totalExecutions() < 50 // Should be less than full duration would produce
    }
    
    def "should calculate TPS metrics correctly"() {
        given: "a task and known load pattern"
        Task task = new VirtualThreadSuccessTask()
        LoadPattern load = new ShortStaticLoad(50.0, Duration.ofMillis(200))
        MetricsCollector collector = MetricsCollector.createWithRunId("tps-test", [0.50d] as double[])
        
        when: "running the engine"
        ExecutionEngine engine = new ExecutionEngine(task, load, collector)
        engine.run()
        def snapshot = collector.snapshot()
        
        then: "TPS calculations are reasonable"
        snapshot.totalExecutions() > 0
        snapshot.responseTps() > 0
        snapshot.successTps() > 0
        snapshot.successTps() <= snapshot.responseTps()
        // Response TPS should be close to target (allowing for overhead)
        snapshot.responseTps() > 0 && snapshot.responseTps() < 100
    }
}

