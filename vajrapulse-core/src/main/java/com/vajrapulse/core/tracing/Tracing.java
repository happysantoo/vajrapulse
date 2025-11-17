package com.vajrapulse.core.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal tracing bootstrap for VajraPulse.
 * <p>Enabled iff environment variable {@code VAJRAPULSE_TRACE_ENABLED=true}.
 * Endpoint override: {@code VAJRAPULSE_OTEL_TRACES_ENDPOINT} (default http://localhost:4317).
 * Sampling: currently always ON; future enhancement can add configurable sampler.
 */
public final class Tracing {
    private static final Logger logger = LoggerFactory.getLogger(Tracing.class);
    private static volatile OpenTelemetry openTelemetry;
    private static volatile Tracer tracer;
    private static final AttributeKey<String> RUN_ID = AttributeKey.stringKey("run_id");
    private static final AttributeKey<String> TASK_CLASS = AttributeKey.stringKey("task.class");
    private static final AttributeKey<String> LOAD_PATTERN = AttributeKey.stringKey("load.pattern");
    private static final AttributeKey<Long> ITERATION = AttributeKey.longKey("iteration");
    private static final AttributeKey<String> STATUS = AttributeKey.stringKey("status");

    private Tracing() {}

    /** Initializes tracing if enabled; safe to call multiple times. */
    public static void initIfEnabled(String runId) {
        if (tracer != null) {
            return; // already initialized
        }
        String enabled = System.getenv("VAJRAPULSE_TRACE_ENABLED");
        if (enabled == null) {
            enabled = System.getProperty("vajrapulse.trace.enabled");
        }
        if (!"true".equalsIgnoreCase(enabled)) {
            logger.info("Tracing disabled (VAJRAPULSE_TRACE_ENABLED not true)");
            return;
        }
        String endpoint = System.getenv("VAJRAPULSE_OTEL_TRACES_ENDPOINT");
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = System.getProperty("vajrapulse.otel.traces.endpoint", "http://localhost:4317");
        }
        try {
            OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build();
            Resource resource = Resource.getDefault().merge(Resource.create(Attributes.of(
                AttributeKey.stringKey("service.name"), "vajrapulse",
                AttributeKey.stringKey("service.version"), "0.9.0-SNAPSHOT"
            )));
            SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .setResource(resource)
                .build();
            openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
            tracer = openTelemetry.getTracer("vajrapulse");
            logger.info("Tracing initialized endpoint={} runId={}", endpoint, runId);
        } catch (Exception e) {
            logger.error("Failed to initialize tracing: {}", e.getMessage());
        }
    }

    public static boolean isEnabled() { return tracer != null; }

    /** Starts a scenario span (root). */
    public static Span startScenarioSpan(String runId, String taskClass, String loadPattern) {
        if (!isEnabled()) return Span.getInvalid();
        return tracer.spanBuilder("scenario")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(RUN_ID, runId)
            .setAttribute(TASK_CLASS, taskClass)
            .setAttribute(LOAD_PATTERN, loadPattern)
            .startSpan();
    }

    /** Starts an execution span child of a parent scenario span. */
    public static Span startExecutionSpan(Span parent, String runId, long iteration) {
        if (!isEnabled()) return Span.getInvalid();
        Context parentCtx = parent != null ? parent.storeInContext(Context.current()) : Context.current();
        return tracer.spanBuilder("execution")
            .setParent(parentCtx)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(RUN_ID, runId)
            .setAttribute(ITERATION, iteration)
            .startSpan();
    }

    public static void markSuccess(Span span) {
        if (!isEnabled() || !span.isRecording()) return;
        span.setAttribute(STATUS, "success");
    }

    public static void markFailure(Span span, Throwable error) {
        if (!isEnabled() || !span.isRecording()) return;
        span.setAttribute(STATUS, "failure");
        span.recordException(error);
        span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR);
    }
}
