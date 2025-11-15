package com.vajrapulse.exporter.console;

import com.vajrapulse.core.metrics.AggregatedMetrics;
import com.vajrapulse.core.metrics.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically reports metrics to the console during test execution.
 * 
 * <p>This reporter runs in the background and displays live metrics
 * at configurable intervals, allowing you to monitor progress during
 * long-running tests.
 * 
 * <p>Example usage:
 * <pre>{@code
 * MetricsCollector collector = new MetricsCollector();
 * PeriodicMetricsReporter reporter = new PeriodicMetricsReporter(
 *     collector, 
 *     Duration.ofSeconds(5)
 * );
 * 
 * reporter.start();
 * // ... run test ...
 * reporter.stop();
 * }</pre>
 */
public final class PeriodicMetricsReporter implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PeriodicMetricsReporter.class);
    
    private final MetricsCollector metricsCollector;
    private final Duration interval;
    private final ConsoleMetricsExporter exporter;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    
    /**
     * Creates a reporter with default 5-second interval.
     * 
     * @param metricsCollector the metrics collector to report from
     */
    public PeriodicMetricsReporter(MetricsCollector metricsCollector) {
        this(metricsCollector, Duration.ofSeconds(5));
    }
    
    /**
     * Creates a reporter with custom interval.
     * 
     * @param metricsCollector the metrics collector to report from
     * @param interval how often to report metrics
     */
    public PeriodicMetricsReporter(MetricsCollector metricsCollector, Duration interval) {
        this.metricsCollector = metricsCollector;
        this.interval = interval;
        this.exporter = new ConsoleMetricsExporter();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "periodic-metrics-reporter");
            t.setDaemon(true);
            return t;
        });
        this.running = new AtomicBoolean(false);
    }
    
    /**
     * Starts periodic reporting.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting periodic metrics reporting every {}", interval);
            
            scheduler.scheduleAtFixedRate(
                this::reportMetrics,
                interval.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }
    
    /**
     * Stops periodic reporting.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping periodic metrics reporting");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void reportMetrics() {
        try {
            AggregatedMetrics metrics = metricsCollector.snapshot();
            
            // Only report if we have data
            if (metrics.totalExecutions() > 0) {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("📊 LIVE METRICS UPDATE");
                System.out.println("=".repeat(60));
                
                exporter.export(metrics);
            }
        } catch (Exception e) {
            logger.error("Error reporting metrics", e);
        }
    }
    
    @Override
    public void close() {
        stop();
    }
}
