package com.vajrapulse.core.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically captures a metrics snapshot and sends it to a provided {@link MetricsExporter}.
 * <p>Designed to run during long tests to provide live visibility. Uses a single daemon
 * scheduler thread. Caller is responsible for lifecycle (start/stop or try-with-resources).
 */
public final class PeriodicMetricsReporter implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PeriodicMetricsReporter.class);

    private final MetricsCollector metricsCollector;
    private final MetricsExporter exporter;
    private final Duration interval;
    private final boolean fireImmediately;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates a reporter with default 5 second interval.
     */
    public PeriodicMetricsReporter(MetricsCollector metricsCollector, MetricsExporter exporter) {
        this(metricsCollector, exporter, Duration.ofSeconds(5), false);
    }

    /**
     * Creates a reporter with custom interval.
     * @param metricsCollector collector to snapshot
     * @param exporter destination exporter
     * @param interval reporting cadence
     */
    public PeriodicMetricsReporter(MetricsCollector metricsCollector, MetricsExporter exporter, Duration interval) {
        this(metricsCollector, exporter, interval, false);
    }

    /**
     * Creates a reporter with custom interval and optional immediate first snapshot.
     */
    public PeriodicMetricsReporter(MetricsCollector metricsCollector, MetricsExporter exporter, Duration interval, boolean fireImmediately) {
        this.metricsCollector = metricsCollector;
        this.exporter = exporter;
        this.interval = interval;
        this.fireImmediately = fireImmediately;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "periodic-metrics-reporter");
            t.setDaemon(true);
            return t;
        });
    }

    /** Starts periodic reporting. */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting periodic metrics reporting every {}", interval);
            if (fireImmediately) {
                scheduler.execute(this::reportSafe);
            }
            scheduler.scheduleAtFixedRate(this::reportSafe, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void reportSafe() {
        try {
            var snapshot = metricsCollector.snapshot();
            if (snapshot.totalExecutions() > 0) {
                exporter.export("Live Metrics", snapshot);
            }
        } catch (Exception e) {
            logger.error("Periodic metrics export failed", e);
        }
    }

    /** Stops periodic reporting. */
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

    @Override
    public void close() {
        stop();
    }
}
