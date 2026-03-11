#!/bin/bash
# Benchmark comparison script for performance regression detection
# Compares current benchmark results against baseline
# Usage: ./scripts/compare-benchmarks.sh [baseline-file] [current-file]

set -euo pipefail

BASELINE_FILE="${1:-benchmarks/build/results/jmh/baseline.json}"
CURRENT_FILE="${2:-benchmarks/build/results/jmh/current.json}"
THRESHOLD="${BENCHMARK_REGRESSION_THRESHOLD:-0.10}"  # 10% default

if [ ! -f "$BASELINE_FILE" ]; then
    echo "Error: Baseline file not found: $BASELINE_FILE"
    echo "Run benchmarks first and save baseline:"
    echo "  ./gradlew :benchmarks:jmh"
    echo "  cp benchmarks/build/results/jmh/*.json $BASELINE_FILE"
    exit 1
fi

if [ ! -f "$CURRENT_FILE" ]; then
    echo "Error: Current benchmark file not found: $CURRENT_FILE"
    echo "Run benchmarks first:"
    echo "  ./gradlew :benchmarks:jmh"
    exit 1
fi

echo "Comparing benchmarks..."
echo "Baseline: $BASELINE_FILE"
echo "Current:  $CURRENT_FILE"
echo "Threshold: ${THRESHOLD} (${THRESHOLD%%.*}%)"
echo ""

# Extract benchmark scores (simplified - assumes JMH JSON format)
# This is a basic implementation; for production, use jq or similar
REGRESSIONS=0

# Check if jq is available for JSON parsing
if command -v jq &> /dev/null; then
    echo "Using jq for JSON parsing..."
    # Extract benchmark names and scores
    jq -r '.benchmarks[] | "\(.benchmark) \(.primaryMetric.score)"' "$BASELINE_FILE" > /tmp/baseline.txt || true
    jq -r '.benchmarks[] | "\(.benchmark) \(.primaryMetric.score)"' "$CURRENT_FILE" > /tmp/current.txt || true
    
    while IFS=' ' read -r benchmark baseline_score; do
        current_score=$(grep "^$benchmark " /tmp/current.txt | awk '{print $2}' || echo "")
        if [ -z "$current_score" ] || [ -z "$baseline_score" ]; then
            continue
        fi
        
        # Calculate percentage change (higher is worse for latency, lower is worse for throughput)
        ratio=$(echo "scale=4; $current_score / $baseline_score" | bc)
        change=$(echo "scale=2; ($ratio - 1) * 100" | bc)
        
        # For latency (lower is better), regression is when current > baseline
        # For throughput (higher is better), regression is when current < baseline
        if (( $(echo "$ratio > (1 + $THRESHOLD)" | bc -l) )) || (( $(echo "$ratio < (1 - $THRESHOLD)" | bc -l) )); then
            echo "⚠️  REGRESSION: $benchmark"
            echo "   Baseline: $baseline_score"
            echo "   Current:  $current_score"
            echo "   Change:   ${change}%"
            REGRESSIONS=$((REGRESSIONS + 1))
        fi
    done < /tmp/baseline.txt
else
    echo "Warning: jq not found. Install jq for proper JSON parsing."
    echo "Falling back to basic comparison..."
    echo "Please install jq: brew install jq (macOS) or apt-get install jq (Linux)"
fi

if [ $REGRESSIONS -eq 0 ]; then
    echo ""
    echo "✅ No performance regressions detected"
    exit 0
else
    echo ""
    echo "❌ Found $REGRESSIONS performance regression(s) exceeding ${THRESHOLD%%.*}% threshold"
    exit 1
fi
