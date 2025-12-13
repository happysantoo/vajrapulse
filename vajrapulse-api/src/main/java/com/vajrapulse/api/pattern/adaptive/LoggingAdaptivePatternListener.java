package com.vajrapulse.api.pattern.adaptive;

import java.util.logging.Logger;

/**
 * Simple logging listener for adaptive load pattern events.
 * 
 * <p>This listener logs all phase transitions and TPS changes to a logger,
 * making it easy to track pattern behavior during execution.
 * 
 * <p>Example:
 * <pre>{@code
 * AdaptiveLoadPattern pattern = AdaptiveLoadPattern.builder()
 *     .metricsProvider(metrics)
 *     .listener(new LoggingAdaptivePatternListener())
 *     .build();
 * }</pre>
 * 
 * <p>Output example:
 * <pre>
 * INFO: Phase transition: RAMP_UP -> RAMP_DOWN at 150.00 TPS
 * INFO: TPS change: 150.00 -> 50.00 (delta: -100.00) [RAMP_DOWN]
 * INFO: Stable TPS detected: 100.00
 * INFO: Recovery: 50.00 TPS (last known good: 200.00)
 * </pre>
 * 
 * <p><strong>Thread Safety:</strong> This class is thread-safe.
 * 
 * @since 0.9.10
 */
public final class LoggingAdaptivePatternListener implements AdaptivePatternListener {
    private final Logger logger;
    
    /**
     * Creates a new logging listener using the default logger.
     */
    public LoggingAdaptivePatternListener() {
        this(Logger.getLogger(LoggingAdaptivePatternListener.class.getName()));
    }
    
    /**
     * Creates a new logging listener using the specified logger.
     * 
     * @param logger the logger to use for logging events
     * @throws IllegalArgumentException if logger is null
     */
    public LoggingAdaptivePatternListener(Logger logger) {
        if (logger == null) {
            throw new IllegalArgumentException("Logger must not be null");
        }
        this.logger = logger;
    }
    
    @Override
    public void onPhaseTransition(PhaseTransitionEvent event) {
        logger.info(String.format(
            "Phase transition: %s -> %s at %.2f TPS",
            event.from(), event.to(), event.tps()
        ));
    }
    
    @Override
    public void onTpsChange(TpsChangeEvent event) {
        double delta = event.newTps() - event.previousTps();
        logger.info(String.format(
            "TPS change: %.2f -> %.2f (delta: %.2f) [%s]",
            event.previousTps(), event.newTps(), delta, event.phase()
        ));
    }
    
    @Override
    public void onStabilityDetected(StabilityDetectedEvent event) {
        logger.info(String.format(
            "Stable TPS detected: %.2f",
            event.stableTps()
        ));
    }
    
    @Override
    public void onRecovery(RecoveryEvent event) {
        logger.info(String.format(
            "Recovery: %.2f TPS (last known good: %.2f)",
            event.recoveryTps(), event.lastKnownGoodTps()
        ));
    }
}

