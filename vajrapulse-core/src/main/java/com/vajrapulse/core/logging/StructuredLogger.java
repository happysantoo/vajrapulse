package com.vajrapulse.core.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.StringJoiner;

/**
 * Lightweight structured logging utility emitting single-line JSON without external deps.
 * Avoids object allocation explosions: builds JSON with StringBuilder.
 */
public final class StructuredLogger {
    private StructuredLogger() {}

    public static void info(Class<?> source, String message, Map<String, ?> fields) {
        log(LoggerFactory.getLogger(source), "INFO", message, fields);
    }
    public static void debug(Class<?> source, String message, Map<String, ?> fields) {
        log(LoggerFactory.getLogger(source), "DEBUG", message, fields);
    }
    public static void trace(Class<?> source, String message, Map<String, ?> fields) {
        log(LoggerFactory.getLogger(source), "TRACE", message, fields);
    }
    public static void error(Class<?> source, String message, Map<String, ?> fields, Throwable t) {
        log(LoggerFactory.getLogger(source), "ERROR", message, fields, t);
    }

    private static void log(Logger logger, String level, String message, Map<String, ?> fields) {
        log(logger, level, message, fields, null);
    }
    private static void log(Logger logger, String level, String message, Map<String, ?> fields, Throwable t) {
        if (!isEnabled(logger, level)) return;
        String json = buildJson(message, fields, t);
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

    private static String buildJson(String message, Map<String, ?> fields, Throwable t) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        appendField(sb, "message", message, true);
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
