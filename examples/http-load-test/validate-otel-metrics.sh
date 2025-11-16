#!/usr/bin/env bash
# Validates that VajraPulse metrics were received by OpenTelemetry Collector
# Usage: ./validate-otel-metrics.sh [timeout_seconds]

set -e

TIMEOUT=${1:-30}
METRICS_FILE="/tmp/otel-collector-data/metrics.json"
START_TIME=$(date +%s)
EXPECTED_METRICS=(
    "vajrapulse.executions.total"
    "vajrapulse.executions.success"
    "vajrapulse.executions.failure"
    "vajrapulse.success.rate"
    "vajrapulse.latency.success"
    "vajrapulse.latency.failure"
)

echo "🔍 Validating OpenTelemetry metrics reception..."
echo "   Timeout: ${TIMEOUT}s"
echo "   Metrics file: ${METRICS_FILE}"
echo ""

# Wait for metrics file to be created
while [ ! -f "$METRICS_FILE" ]; do
    ELAPSED=$(($(date +%s) - START_TIME))
    if [ $ELAPSED -gt $TIMEOUT ]; then
        echo "❌ FAILED: Metrics file not created within ${TIMEOUT}s"
        echo "   Collector may not be running or not receiving metrics"
        exit 1
    fi
    echo "   Waiting for collector to write metrics... ($ELAPSED/${TIMEOUT}s)"
    sleep 1
done

echo "✅ Metrics file found"
echo ""

# Check if file has content
if [ ! -s "$METRICS_FILE" ]; then
    echo "❌ FAILED: Metrics file is empty"
    echo "   Collector may not have received any metrics yet"
    exit 1
fi

echo "📊 Checking for expected metrics..."
FOUND_COUNT=0

for metric in "${EXPECTED_METRICS[@]}"; do
    if grep -q "\"${metric}\"" "$METRICS_FILE"; then
        echo "   ✅ Found: ${metric}"
        ((FOUND_COUNT++))
    else
        echo "   ⚠️  Missing: ${metric}"
    fi
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ $FOUND_COUNT -eq ${#EXPECTED_METRICS[@]} ]; then
    echo "✅ SUCCESS: All expected metrics received!"
    echo "   Found ${FOUND_COUNT}/${#EXPECTED_METRICS[@]} metrics"
    echo ""
    echo "📋 Sample metrics excerpt:"
    head -20 "$METRICS_FILE" | tail -10
    exit 0
else
    echo "⚠️  PARTIAL: Found ${FOUND_COUNT}/${#EXPECTED_METRICS[@]} metrics"
    echo ""
    echo "📋 Full metrics output:"
    cat "$METRICS_FILE"
    exit 1
fi
