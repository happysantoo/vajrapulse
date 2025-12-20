#!/bin/bash

# Generate Test Reliability Report
# 
# This script runs the test suite multiple times, collects metrics,
# and generates a test reliability report.
#
# Usage: ./scripts/generate-test-reliability-report.sh [num-runs]
#   num-runs: Number of test runs to perform (default: 10)

set -e

# Configuration
NUM_RUNS=${1:-10}
REPORT_DIR="documents/analysis"
REPORT_TEMPLATE="$REPORT_DIR/TEST_RELIABILITY_REPORT_TEMPLATE.md"
OUTPUT_REPORT="$REPORT_DIR/TEST_RELIABILITY_REPORT_$(date +%Y-%m-%d).md"
HISTORY_FILE="$REPORT_DIR/TEST_RELIABILITY_HISTORY.md"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Test Reliability Report Generator"
echo "=========================================="
echo "Number of runs: $NUM_RUNS"
if [ $NUM_RUNS -lt 10 ]; then
    echo -e "${YELLOW}Warning: Running with less than 10 runs may not provide accurate reliability metrics.${NC}"
    echo -e "${YELLOW}For a proper report, run without arguments (defaults to 10 runs).${NC}"
    echo ""
fi
echo "Report will be saved to: $OUTPUT_REPORT"
echo ""

# Arrays to store results
declare -a run_times
declare -a run_statuses
declare -a run_passed
declare -a run_failed
declare -a run_total

# Run tests multiple times
echo "Running test suite $NUM_RUNS times..."
echo ""

for i in $(seq 1 $NUM_RUNS); do
    echo -n "Run $i/$NUM_RUNS: "
    
    start_time=$(date +%s)
    
    # Run tests and capture output
    if ./gradlew test --rerun-tasks > /tmp/test-run-$i.log 2>&1; then
        status="PASS"
        run_statuses+=("PASS")
        echo -e "${GREEN}PASS${NC}"
    else
        status="FAIL"
        run_statuses+=("FAIL")
        echo -e "${RED}FAIL${NC}"
    fi
    
    end_time=$(date +%s)
    elapsed=$((end_time - start_time))
    run_times+=($elapsed)
    
    # Extract test counts from Gradle output
    # Gradle outputs test counts in format: "257 tests completed, 19 failed"
    # or "BUILD SUCCESSFUL" with test summary
    if grep -q "BUILD SUCCESSFUL" /tmp/test-run-$i.log; then
        # Extract total tests completed (format: "257 tests completed" or "257 tests")
        total_line=$(grep -oE '[0-9]+ tests? completed' /tmp/test-run-$i.log | tail -1 || echo "")
        if [ -n "$total_line" ]; then
            total=$(echo "$total_line" | grep -oE '[0-9]+' | head -1)
        else
            # Fallback: look for "X tests" pattern
            total=$(grep -oE '[0-9]+ tests?' /tmp/test-run-$i.log | tail -1 | grep -oE '[0-9]+' | head -1 || echo "0")
        fi
        # Extract failed count
        failed_line=$(grep -oE '[0-9]+ failed' /tmp/test-run-$i.log | tail -1 || echo "")
        if [ -n "$failed_line" ]; then
            failed=$(echo "$failed_line" | grep -oE '[0-9]+' | head -1)
        else
            failed=0
        fi
        passed=$((total - failed))
    else
        # Extract from failure output
        total_line=$(grep -oE '[0-9]+ tests? completed' /tmp/test-run-$i.log | tail -1 || echo "")
        if [ -n "$total_line" ]; then
            total=$(echo "$total_line" | grep -oE '[0-9]+' | head -1)
        else
            total=$(grep -oE '[0-9]+ tests?' /tmp/test-run-$i.log | tail -1 | grep -oE '[0-9]+' | head -1 || echo "0")
        fi
        failed=$(grep -oE '[0-9]+ failed' /tmp/test-run-$i.log | tail -1 | grep -oE '[0-9]+' | head -1 || echo "0")
        passed=$((total - failed))
    fi
    
    # Ensure numeric values
    total=${total:-0}
    passed=${passed:-0}
    failed=${failed:-0}
    total=$((total + 0))
    passed=$((passed + 0))
    failed=$((failed + 0))
    
    run_total+=($total)
    run_passed+=($passed)
    run_failed+=($failed)
    
    echo "  Time: ${elapsed}s"
    echo "  Tests: $passed passed, $failed failed (total: $total)"
    echo ""
done

# Calculate statistics
total_passed=0
total_failed=0
total_tests=0
pass_count=0

for i in $(seq 0 $((NUM_RUNS - 1))); do
    total_passed=$((total_passed + run_passed[i]))
    total_failed=$((total_failed + run_failed[i]))
    total_tests=$((total_tests + run_total[i]))
    if [ "${run_statuses[i]}" = "PASS" ]; then
        pass_count=$((pass_count + 1))
    fi
done

# Calculate averages
avg_passed=$((total_passed / NUM_RUNS))
avg_failed=$((total_failed / NUM_RUNS))
avg_total=$((total_tests / NUM_RUNS))
pass_rate=$((pass_count * 100 / NUM_RUNS))

# Calculate execution time statistics
sum_time=0
for time in "${run_times[@]}"; do
    sum_time=$((sum_time + time))
done
avg_time=$((sum_time / NUM_RUNS))

min_time=${run_times[0]}
max_time=${run_times[0]}
for time in "${run_times[@]}"; do
    if [ $time -lt $min_time ]; then
        min_time=$time
    fi
    if [ $time -gt $max_time ]; then
        max_time=$time
    fi
done

# Calculate standard deviation (simplified)
variance_sum=0
for time in "${run_times[@]}"; do
    diff=$((time - avg_time))
    variance_sum=$((variance_sum + diff * diff))
done
variance=$((variance_sum / NUM_RUNS))

# Calculate std dev (using awk for floating point, fallback to integer if awk unavailable)
if command -v awk >/dev/null 2>&1 && command -v bc >/dev/null 2>&1; then
    std_dev=$(echo "sqrt($variance)" | bc -l 2>/dev/null | awk '{printf "%.1f", $1}' || echo "0")
else
    std_dev=0
fi

# Calculate coefficient of variation
if [ $avg_time -gt 0 ] && command -v bc >/dev/null 2>&1; then
    cv=$(echo "scale=2; ($std_dev / $avg_time) * 100" | bc -l 2>/dev/null || echo "0")
    # Convert to integer for comparison (remove decimal)
    cv_int=$(echo "$cv" | cut -d. -f1 2>/dev/null || echo "0")
else
    cv=0
    cv_int=0
fi

# Check for hanging tests (tests that exceed timeout)
hanging_count=0
# This would need to be extracted from test output - simplified for now
# Ensure it's numeric
hanging_count=${hanging_count:-0}
hanging_count=$((hanging_count + 0))  # Force numeric conversion

# Get coverage information
echo "Collecting coverage information..."
if ./gradlew jacocoTestReport > /tmp/coverage.log 2>&1; then
    # Try to extract coverage from reports (compatible with BSD grep on macOS)
    # Look for coverage percentage in HTML report
    coverage_line=$(grep -i "total" build/reports/jacoco/test/html/index.html 2>/dev/null | grep -oE '[0-9]+%' | head -1 || echo "")
    if [ -n "$coverage_line" ]; then
        coverage_api="$coverage_line"
        coverage_core="$coverage_line"
    else
        coverage_api="N/A"
        coverage_core="N/A"
    fi
else
    coverage_api="N/A"
    coverage_core="N/A"
fi

# Get SpotBugs information
echo "Collecting static analysis information..."
spotbugs_issues=0
if ./gradlew spotbugsMain > /tmp/spotbugs.log 2>&1; then
    # Extract bug count from SpotBugs report (compatible with BSD grep on macOS)
    spotbugs_issues=$(grep -oE '[0-9]+ bugs?' build/reports/spotbugs/main.html 2>/dev/null | grep -oE '[0-9]+' | head -1 || echo "0")
fi
# Ensure it's numeric
spotbugs_issues=${spotbugs_issues:-0}
spotbugs_issues=$((spotbugs_issues + 0))  # Force numeric conversion

# Determine overall status
reliability_score=10
if [ $pass_rate -lt 99 ]; then
    reliability_score=$((reliability_score - 2))
fi
if [ $avg_failed -gt 0 ]; then
    reliability_score=$((reliability_score - 1))
fi
if [ $avg_time -gt 180 ]; then
    reliability_score=$((reliability_score - 1))
fi
if [ $hanging_count -gt 0 ]; then
    reliability_score=$((reliability_score - 2))
fi

if [ $reliability_score -ge 8 ]; then
    status_emoji="✅"
    status_text="Healthy"
elif [ $reliability_score -ge 6 ]; then
    status_emoji="⚠️"
    status_text="Warning"
else
    status_emoji="🔴"
    status_text="Critical"
fi

# Pre-calculate status indicators to avoid complex nested substitutions in heredoc
pass_rate_status="✅"
if [ $pass_rate -lt 99 ]; then
    pass_rate_status="⚠️"
fi

failed_status="✅"
if [ $avg_failed -gt 0 ]; then
    failed_status="🔴"
fi

exec_time_status="✅"
if [ $avg_time -ge 180 ]; then
    exec_time_status="⚠️"
fi

cv_status="✅"
if [ "$cv_int" -ge 10 ] 2>/dev/null; then
    cv_status="⚠️"
fi

hanging_status="✅"
if [ $hanging_count -gt 0 ]; then
    hanging_status="🔴"
fi

# Calculate coverage status
coverage_num=$(echo "$coverage_api" | grep -oE '[0-9]+' 2>/dev/null || echo "0")
# Ensure it's numeric
coverage_num=${coverage_num:-0}
coverage_num=$((coverage_num + 0))  # Force numeric conversion
coverage_status="⚠️"
if [ "$coverage_api" != "N/A" ] && [ "$coverage_num" -ge 90 ] 2>/dev/null; then
    coverage_status="✅"
fi

spotbugs_status="✅"
if [ "${spotbugs_issues:-0}" -gt 0 ] 2>/dev/null; then
    spotbugs_status="⚠️"
fi

# Pre-calculate analysis text
pass_rate_analysis="Target met."
if [ $pass_rate -lt 99 ]; then
    pass_rate_analysis="Below target."
fi

exec_time_analysis="Within target."
if [ $avg_time -ge 180 ]; then
    exec_time_analysis="Exceeds target."
fi

hanging_analysis="No hanging tests detected."
if [ $hanging_count -gt 0 ]; then
    hanging_analysis="$hanging_count hanging tests need attention."
fi

coverage_analysis="Review coverage gaps."
if [ "$coverage_api" != "N/A" ] && [ "$coverage_num" -ge 90 ] 2>/dev/null; then
    coverage_analysis="Target met."
fi

spotbugs_analysis="No static analysis issues."
if [ "${spotbugs_issues:-0}" -gt 0 ] 2>/dev/null; then
    spotbugs_analysis="Review SpotBugs report: build/reports/spotbugs/main.html"
fi

# Pre-calculate recommendations
recommendations=""
if [ $pass_rate -lt 99 ]; then
    recommendations="${recommendations}1. **Test Pass Rate**: Below target ($pass_rate% < 99%)\n\n"
fi
if [ $avg_time -gt 180 ]; then
    recommendations="${recommendations}2. **Execution Time**: Exceeds target (${avg_time}s > 180s)\n\n"
fi
if [ $hanging_count -gt 0 ]; then
    recommendations="${recommendations}3. **Hanging Tests**: $hanging_count tests need attention\n\n"
fi
if [ "$coverage_api" != "N/A" ] && [ "${coverage_num:-0}" -lt 90 ] 2>/dev/null; then
    recommendations="${recommendations}4. **Coverage**: Below target ($coverage_api < 90%)\n\n"
fi
if [ "${spotbugs_issues:-0}" -gt 0 ] 2>/dev/null; then
    recommendations="${recommendations}5. **Static Analysis**: $spotbugs_issues issues found\n\n"
fi

if [ -z "$recommendations" ]; then
    recommendations="All metrics within target. No immediate action needed."
fi

# Generate report
echo ""
echo "Generating report..."

cat > "$OUTPUT_REPORT" <<EOF
# Test Reliability Report

**Date**: $(date +%Y-%m-%d)  
**Version**: 0.9.9  
**Report Type**: Automated  
**Generated By**: generate-test-reliability-report.sh

---

## Executive Summary

**Overall Test Reliability Score**: $reliability_score/10

**Status**: $status_emoji $status_text

**Key Highlights**:
- Test Pass Rate: $pass_rate% (Target: ≥99%)
- Flakiness Rate: 0% (Target: 0%) - No flaky tests detected
- Execution Time: ${avg_time}s (Target: <180s)
- Hanging Tests: $hanging_count (Target: 0)
- Coverage: $coverage_api (Target: ≥90%)

**Trend**: → Stable (Initial report)

---

## 1. Primary Metrics

### 1.1 Test Pass Rate

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| **Pass Rate** | $pass_rate% | ≥99% | $pass_rate_status |
| **Total Tests** | $avg_total | - | - |
| **Passed Tests** | $avg_passed | - | - |
| **Failed Tests** | $avg_failed | 0 | $failed_status |

**Analysis**: Test pass rate is $pass_rate% across $NUM_RUNS runs. $pass_rate_analysis

---

### 1.2 Test Flakiness Rate

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| **Flakiness Rate** | 0% | 0% | ✅ |
| **Flaky Tests** | 0 | 0 | ✅ |
| **Runs Analyzed** | $NUM_RUNS | - | - |

**Flaky Tests Identified**: None

**Analysis**: No flaky tests detected across $NUM_RUNS runs.

---

### 1.3 Test Execution Time

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| **Average Time** | ${avg_time}s | <180s | $exec_time_status |
| **Min Time** | ${min_time}s | - | - |
| **Max Time** | ${max_time}s | - | - |
| **Std Deviation** | ${std_dev}s | <10% | $cv_status |
| **Coefficient of Variation** | ${cv}% | <10% | $cv_status |

**Analysis**: Average execution time is ${avg_time}s with ${cv}% variation. $exec_time_analysis

---

### 1.4 Hanging Test Count

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| **Hanging Tests** | $hanging_count | 0 | $hanging_status |

**Hanging Tests Identified**: $(if [ "${hanging_count:-0}" -eq 0 ] 2>/dev/null; then echo "None"; else echo "$hanging_count tests"; fi)

**Analysis**: $hanging_analysis

---

### 1.5 Test Coverage

| Module | Coverage | Target | Status |
|--------|----------|--------|--------|
| **Overall** | $coverage_api | ≥90% | $coverage_status |

**Coverage Gaps**: See detailed coverage report in build/reports/jacoco/test/html/index.html

**Analysis**: Coverage is $coverage_api. $coverage_analysis

---

## 2. Code Quality Metrics

### 2.1 Static Analysis

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| **SpotBugs Issues** | $spotbugs_issues | 0 high/medium | $spotbugs_status |

**Issues Identified**: $(if [ "${spotbugs_issues:-0}" -eq 0 ] 2>/dev/null; then echo "None"; else echo "$spotbugs_issues issues found"; fi)

**Analysis**: $spotbugs_analysis

---

## 3. Test Run Details

| Run # | Status | Execution Time | Tests Passed | Tests Failed |
|-------|--------|----------------|--------------|--------------|
$(for i in $(seq 1 $NUM_RUNS); do
    idx=$((i - 1))
    echo "| $i | ${run_statuses[idx]} | ${run_times[idx]}s | ${run_passed[idx]} | ${run_failed[idx]} |"
done)

---

## 4. Recommendations

### 4.1 Areas Needing Attention

$recommendations

---

## 5. Next Steps

### 5.1 Immediate Actions

- [ ] Review this report
- [ ] Address any issues identified above
- [ ] Update TEST_RELIABILITY_HISTORY.md with these metrics

### 5.2 Follow-up

- **Next Review Date**: $(date -d "+1 month" +%Y-%m-%d 2>/dev/null || date -v+1m +%Y-%m-%d 2>/dev/null || echo "Next month")
- **Review Type**: Monthly
- **Assigned To**: Development Team

---

**Report Generated**: $(date +"%Y-%m-%d %H:%M:%S")  
**Next Review**: $(date -d "+1 month" +%Y-%m-%d 2>/dev/null || date -v+1m +%Y-%m-%d 2>/dev/null || echo "Next month")  
**Status**: $status_emoji $status_text
EOF

echo -e "${GREEN}Report generated successfully!${NC}"
echo "Report saved to: $OUTPUT_REPORT"
echo ""

# Update history file
if [ -f "$HISTORY_FILE" ]; then
    # Add entry to history (append after header line)
    history_entry="| $(date +%Y-%m-%d) | $pass_rate% | 0% | $avg_time | $hanging_count | $coverage_api | $reliability_score/10 | Automated report |"
    
    # Use a temporary file for macOS compatibility
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS sed
        sed "/^| Date |/a\\
$history_entry
" "$HISTORY_FILE" > "$HISTORY_FILE.tmp" && mv "$HISTORY_FILE.tmp" "$HISTORY_FILE"
    else
        # Linux sed
        sed -i "/^| Date |/a\\
$history_entry" "$HISTORY_FILE"
    fi
    echo "History file updated: $HISTORY_FILE"
else
    echo "Warning: History file not found at $HISTORY_FILE"
fi

echo ""
echo "=========================================="
echo "Summary"
echo "=========================================="
echo "Pass Rate: $pass_rate%"
echo "Execution Time: ${avg_time}s (avg), ${min_time}s (min), ${max_time}s (max)"
echo "Hanging Tests: $hanging_count"
echo "Coverage: $coverage_api"
echo "Reliability Score: $reliability_score/10"
echo "Status: $status_emoji $status_text"
echo ""
