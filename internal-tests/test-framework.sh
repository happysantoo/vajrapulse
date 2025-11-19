#!/bin/bash

# VajraPulse Test Framework
# Automated test execution and validation framework

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test results
PASSED=0
FAILED=0
TOTAL=0

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Run a single test project
run_test_project() {
    local project_dir=$1
    local project_name=$(basename "$project_dir")
    
    log_info "Running test project: $project_name"
    
    cd "$project_dir"
    
    if [ ! -f "build.gradle.kts" ]; then
        log_error "No build.gradle.kts found in $project_dir"
        return 1
    fi
    
    # Build the project
    if ! ./gradlew build --quiet; then
        log_error "Build failed for $project_name"
        return 1
    fi
    
    # Run the test
    if ! ./gradlew run --quiet; then
        log_error "Test execution failed for $project_name"
        return 1
    fi
    
    log_info "Test project $project_name completed successfully"
    return 0
}

# Run all test projects
run_all() {
    log_info "Running all test projects..."
    
    for project_dir in "$SCRIPT_DIR"/*/; do
        if [ -d "$project_dir" ] && [ -f "$project_dir/build.gradle.kts" ]; then
            TOTAL=$((TOTAL + 1))
            if run_test_project "$project_dir"; then
                PASSED=$((PASSED + 1))
            else
                FAILED=$((FAILED + 1))
            fi
        fi
    done
    
    echo ""
    log_info "Test Summary:"
    echo "  Total: $TOTAL"
    echo "  Passed: $PASSED"
    echo "  Failed: $FAILED"
    
    if [ $FAILED -eq 0 ]; then
        log_info "All tests passed!"
        return 0
    else
        log_error "Some tests failed"
        return 1
    fi
}

# Run specific test project
run_specific() {
    local project_name=$1
    local project_dir="$SCRIPT_DIR/$project_name"
    
    if [ ! -d "$project_dir" ]; then
        log_error "Test project not found: $project_name"
        return 1
    fi
    
    run_test_project "$project_dir"
}

# List all test projects
list_projects() {
    log_info "Available test projects:"
    for project_dir in "$SCRIPT_DIR"/*/; do
        if [ -d "$project_dir" ] && [ -f "$project_dir/build.gradle.kts" ]; then
            echo "  - $(basename "$project_dir")"
        fi
    done
}

# Main
case "${1:-}" in
    run-all)
        run_all
        ;;
    run)
        if [ -z "${2:-}" ]; then
            log_error "Please specify a test project name"
            list_projects
            exit 1
        fi
        run_specific "$2"
        ;;
    list)
        list_projects
        ;;
    *)
        echo "Usage: $0 {run-all|run <project>|list}"
        echo ""
        echo "Commands:"
        echo "  run-all          Run all test projects"
        echo "  run <project>    Run a specific test project"
        echo "  list             List all available test projects"
        exit 1
        ;;
esac

