package com.vajrapulse.core.logging;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Lightweight structured logging utility emitting single-line JSON without external deps.
 * Avoids object allocation explosions: builds JSON with StringBuilder.
 * 
 * <p>Automatically includes trace correlation fields (trace_id, span_id, run_id) when
 * tracing is enabled and OpenTelemetry context is available. This enables log aggregation
 * tools to correlate logs with traces.
 * 
 * <p>Example output:
 * <pre>
 * {"message":"Task execution completed","run_id":"abc123","trace_id":"def456","span_id":"ghi789","iteration":42}
 * </pre>
 */
public final class StructuredLogger {
    private StructuredLogger() {}

    /**
     * Logs an INFO message with structured fields.
     * 
     * @param source the source class for logger identification
     * @param message the log message
     * @param fields additional structured fields (may be null)
     */
    public static void info(Class<?> source, String message, Map<String, ?> fields) {
        log(LoggerFactory.getLogger(source), "INFO", message, fields, null, null);
    }
    
    /**
     * Logs a DEBUG message with structured fields.
     * 
     * @param source the source class for logger identification
     * @param message the log message
     * @param fields additional structured fields (may be null)
     */
    public static void debug(Class<?> source, String message, Map<String, ?> fields) {
        log(LoggerFactory.getLogger(source), "DEBUG", message, fields, null, null);
    }
    
    /**
     * Logs a TRACE message with structured fields.
     * 
     * @param source the source class for logger identification
     * @param message the log message
     * @param fields additional structured fields (may be null)
     */
    public static void trace(Class<?> source, String message, Map<String, ?> fields) {
        log(LoggerFactory.getLogger(source), "TRACE", message, fields, null, null);
    }
    
    /**
     * Logs an ERROR message with structured fields and exception.
     * 
     * @param source the source class for logger identification
     * @param message the log message
     * @param fields additional structured fields (may be null)
     * @param t the throwable (may be null)
     */
    public static void error(Class<?> source, String message, Map<String, ?> fields, Throwable t) {
        log(LoggerFactory.getLogger(source), "ERROR", message, fields, t, null);
    }
    
    /**
     * Logs with trace correlation support.
     * 
     * <p>This overload includes runId for correlation. Trace ID and span ID are
     * automatically extracted from OpenTelemetry context if available.
     * 
     * @param source the source class
     * @param level the log level
     * @param message the log message
     * @param fields additional structured fields
     * @param runId the run ID for correlation (may be null)
     */
    public static void logWithRunId(Class<?> source, String level, String message, Map<String, ?> fields, String runId) {
        log(LoggerFactory.getLogger(source), level, message, fields, null, runId);
    }
    
    /**
     * Logs with explicit span for trace correlation.
     * 
     * <p>This overload extracts trace_id and span_id from the provided span.
     * 
     * @param source the source class
     * @param level the log level
     * @param message the log message
     * @param fields additional structured fields
     * @param span the OpenTelemetry span for correlation (may be invalid/null)
     * @param runId the run ID for correlation (may be null)
     */
    public static void logWithSpan(Class<?> source, String level, String message, Map<String, ?> fields, Span span, String runId) {
        log(LoggerFactory.getLogger(source), level, message, fields, null, runId, span);
    }

    private static void log(Logger logger, String level, String message, Map<String, ?> fields, Throwable t, String runId) {
        log(logger, level, message, fields, t, runId, null);
    }
    
    private static void log(Logger logger, String level, String message, Map<String, ?> fields, Throwable t, String runId, Span span) {
        if (!isEnabled(logger, level)) return;
        String json = buildJson(message, fields, t, runId, span);
        switch (level) {
            case "TRACE" -> logger.trace(json);
            case "DEBUG" -> logger.debug(json);
            case "INFO" -> logger.info(json);
            case "ERROR" -> logger.error(json, t);
            default -> logger.info(json);
        }
    }

    private static boolean isEnabled(Logger logger, String level) {
        return switch (level) {
            case "TRACE" -> logger.isTraceEnabled();
            case "DEBUG" -> logger.isDebugEnabled();
            case "INFO" -> logger.isInfoEnabled();
            case "ERROR" -> true;
            default -> true;
        };
    }

    private static String buildJson(String message, Map<String, ?> fields, Throwable t, String runId, Span span) {
        StringBuilder sb = new StringBuilder(256); // Increased for trace correlation fields
        sb.append('{');
        appendField(sb, "message", message, true);
        
        // Add trace correlation fields if available
        if (runId != null && !runId.isBlank()) {
            appendField(sb, "run_id", runId, true);
        }
        
        // Extract trace_id and span_id from OpenTelemetry context or span
        String traceId = extractTraceId(span);
        String spanId = extractSpanId(span);
        
        if (traceId != null) {
            appendField(sb, "trace_id", traceId, true);
        }
        if (spanId != null) {
            appendField(sb, "span_id", spanId, true);
        }
        
        if (fields != null) {
            for (var entry : fields.entrySet()) {
                appendField(sb, entry.getKey(), entry.getValue(), true);
            }
        }
        if (t != null) {
            appendField(sb, "error", t.getClass().getSimpleName() + ":" + sanitize(t.getMessage()), true);
        }
        // trim trailing comma if present
        int last = sb.length() - 1;
        if (last >= 0 && sb.charAt(last) == ',') {
            sb.setCharAt(last, '}');
        } else {
            sb.append('}');
        }
        return sb.toString();
    }
    
    /**
     * Extracts trace ID from OpenTelemetry span or current context.
     * 
     * @param span the span to extract from (may be invalid/null)
     * @return trace ID as hex string, or null if not available
     */
    private static String extractTraceId(Span span) {
        if (span != null && span.isRecording()) {
            SpanContext spanContext = span.getSpanContext();
            if (spanContext.isValid()) {
                return spanContext.getTraceId();
            }
        }
        // Try to get from current context
        try {
            Span currentSpan = Span.current();
            if (currentSpan != null && currentSpan.isRecording()) {
                SpanContext spanContext = currentSpan.getSpanContext();
                if (spanContext.isValid()) {
                    return spanContext.getTraceId();
                }
            }
        } catch (Exception e) {
            // Context not available, ignore
        }
        return null;
    }
    
    /**
     * Extracts span ID from OpenTelemetry span or current context.
     * 
     * @param span the span to extract from (may be invalid/null)
     * @return span ID as hex string, or null if not available
     */
    private static String extractSpanId(Span span) {
        if (span != null && span.isRecording()) {
            SpanContext spanContext = span.getSpanContext();
            if (spanContext.isValid()) {
                return spanContext.getSpanId();
            }
        }
        // Try to get from current context
        try {
            Span currentSpan = Span.current();
            if (currentSpan != null && currentSpan.isRecording()) {
                SpanContext spanContext = currentSpan.getSpanContext();
                if (spanContext.isValid()) {
                    return spanContext.getSpanId();
                }
            }
        } catch (Exception e) {
            // Context not available, ignore
        }
        return null;
    }

    private static void appendField(StringBuilder sb, String key, Object value, boolean trailingComma) {
        sb.append('"').append(escape(key)).append('"').append(':');
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else {
            sb.append('"').append(escape(String.valueOf(value))).append('"');
        }
        if (trailingComma) sb.append(',');
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    private static String sanitize(String s) { return s == null ? "" : s; }
}
