package com.vajrapulse.api.pattern;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Step-based load pattern where TPS changes in discrete steps over time.
 *
 * <p>Each {@link Step} defines a target rate and how long it applies.
 * Steps are applied in the order provided. Total duration is the sum
 * of all step durations.
 *
 * <p>Example:
 * <pre>{@code
 * var pattern = new StepLoad(List.of(
 *     new StepLoad.Step(50.0, Duration.ofSeconds(30)),
 *     new StepLoad.Step(100.0, Duration.ofSeconds(45)),
 *     new StepLoad.Step(200.0, Duration.ofSeconds(60))
 * ));
 * }</pre>
 */
public record StepLoad(List<Step> steps) implements LoadPattern {

    public StepLoad {
        Objects.requireNonNull(steps, "steps");
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        // Create immutable copy to prevent external modification
        // This ensures the record's internal state cannot be changed after construction
        steps = List.copyOf(steps);
        for (Step s : steps) {
            LoadPatternValidator.validateTpsNonNegative("Step rate", s.rate());
            LoadPatternValidator.validateDuration("Step duration", s.duration());
        }
    }

    @Override
    public double calculateTps(long elapsedMillis) {
        if (elapsedMillis < 0) return 0.0;
        long remaining = elapsedMillis;
        for (Step s : steps) {
            long d = s.duration().toMillis();
            if (remaining < d) {
                return s.rate();
            }
            remaining -= d;
        }
        // Past the end – TPS goes to 0
        return 0.0;
    }

    @Override
    public Duration getDuration() {
        long total = 0L;
        for (Step s : steps) {
            total += s.duration().toMillis();
        }
        return Duration.ofMillis(total);
    }

    /** Single step definition. */
    public record Step(double rate, Duration duration) {
        public Step {
            Objects.requireNonNull(duration, "duration");
        }
    }
}
