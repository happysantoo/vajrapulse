package com.vajra.core.engine;

import com.vajra.api.LoadPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Controls the rate of task execution according to a load pattern.
 * 
 * <p>This class calculates the appropriate delay between executions to achieve
 * the target TPS defined by the load pattern. It adjusts dynamically as the
 * pattern changes over time (e.g., during ramp-up).
 * 
 * <p>Thread-safe for concurrent rate control.
 */
public final class RateController {
    private static final Logger logger = LoggerFactory.getLogger(RateController.class);
    
    private final LoadPattern loadPattern;
    private final long testStartNanos;
    private final AtomicLong executionCount;
    
    public RateController(LoadPattern loadPattern) {
        this.loadPattern = loadPattern;
        this.testStartNanos = System.nanoTime();
        this.executionCount = new AtomicLong(0);
    }
    
    /**
     * Waits until the next execution should occur based on the load pattern.
     * 
     * <p>This method calculates:
     * <ol>
     *   <li>Elapsed time since test start</li>
     *   <li>Target TPS at this point</li>
     *   <li>Expected execution count based on TPS</li>
     *   <li>Delay needed to stay on track</li>
     * </ol>
     */
    public void waitForNext() {
        long currentCount = executionCount.incrementAndGet();
        long elapsedNanos = System.nanoTime() - testStartNanos;
        long elapsedMillis = elapsedNanos / 1_000_000;
        
        double targetTps = loadPattern.calculateTps(elapsedMillis);
        if (targetTps <= 0) {
            return;
        }
        
        // Calculate expected execution count based on elapsed time and target TPS
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        long expectedCount = (long) (targetTps * elapsedSeconds);
        
        // If we're ahead of schedule, sleep
        if (currentCount > expectedCount) {
            long nanosPerExecution = (long) (1_000_000_000.0 / targetTps);
            long targetNanos = testStartNanos + (currentCount * nanosPerExecution);
            long sleepNanos = targetNanos - System.nanoTime();
            
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
    }
    
    /**
     * Returns the current target TPS.
     * 
     * @return current target transactions per second
     */
    public double getCurrentTps() {
        long elapsedMillis = (System.nanoTime() - testStartNanos) / 1_000_000;
        return loadPattern.calculateTps(elapsedMillis);
    }
    
    /**
     * Returns the total elapsed time in milliseconds.
     * 
     * @return elapsed time in milliseconds
     */
    public long getElapsedMillis() {
        return (System.nanoTime() - testStartNanos) / 1_000_000;
    }
}
