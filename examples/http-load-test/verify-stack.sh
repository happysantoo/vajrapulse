#!/bin/bash
# Verify VajraPulse Observability Stack is working correctly

set -e

echo "🔍 VajraPulse Observability Stack Verification"
echo "=============================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check_service() {
    local service=$1
    local url=$2
    local name=$3
    
    if curl -sf "$url" > /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} $name is responding"
        return 0
    else
        echo -e "${RED}✗${NC} $name is NOT responding"
        return 1
    fi
}

check_metrics() {
    local metric=$1
    local name=$2
    
    result=$(curl -s "http://localhost:9090/api/v1/query?query=$metric" | jq -r '.data.result | length')
    
    if [ "$result" -gt 0 ]; then
        echo -e "${GREEN}✓${NC} $name metric found ($result series)"
        return 0
    else
        echo -e "${YELLOW}⚠${NC} $name metric not found (may need to run a test)"
        return 0
    fi
}

echo "1. Checking Docker Containers..."
if docker-compose ps | grep -q "Up"; then
    echo -e "${GREEN}✓${NC} Docker containers are running"
else
    echo -e "${RED}✗${NC} Docker containers are not running"
    echo "Run: docker-compose up -d"
    exit 1
fi
echo ""

echo "2. Checking Service Health..."
# OTEL Collector doesn't have HTTP health endpoint, just check if port is listening
if nc -z localhost 4317 2>/dev/null; then
    echo -e "${GREEN}✓${NC} OTEL Collector (gRPC port 4317) is listening"
else
    echo -e "${RED}✗${NC} OTEL Collector (gRPC) is NOT listening"
fi
check_service "otel-collector" "http://localhost:8889/metrics" "OTEL Collector (Prometheus exporter)"
check_service "prometheus" "http://localhost:9090/-/healthy" "Prometheus"
check_service "grafana" "http://localhost:3000/api/health" "Grafana"
echo ""

echo "3. Checking Prometheus Metrics..."
check_metrics "vajrapulse_executions_total" "Total Executions"
check_metrics "vajrapulse_executions_success" "Successful Executions"
check_metrics "vajrapulse_success_rate" "Success Rate"
check_metrics "vajrapulse_latency_success_bucket" "Success Latency Histogram"
echo ""

echo "4. Checking Grafana Dashboard..."
dashboard_count=$(curl -s -u admin:vajrapulse "http://localhost:3000/api/search?query=VajraPulse" | jq '. | length')
if [ "$dashboard_count" -gt 0 ]; then
    echo -e "${GREEN}✓${NC} VajraPulse dashboard found"
else
    echo -e "${YELLOW}⚠${NC} Dashboard not found (may need manual import)"
fi
echo ""

echo "5. Summary"
echo "=============================================="
echo -e "Grafana:    ${GREEN}http://localhost:3000${NC}"
echo -e "            Username: admin / Password: vajrapulse"
echo -e "Prometheus: ${GREEN}http://localhost:9090${NC}"
echo -e "OTLP gRPC:  ${GREEN}http://localhost:4317${NC}"
echo -e "OTLP HTTP:  ${GREEN}http://localhost:4318${NC}"
echo ""

if [ "$dashboard_count" -gt 0 ]; then
    echo -e "${GREEN}✓ Stack is fully operational!${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. View dashboard: http://localhost:3000/d/vajrapulse-load-test"
    echo "  2. Run a test: ./gradlew :examples:http-load-test:runOtel"
else
    echo -e "${YELLOW}⚠ Stack is running but may need metrics${NC}"
    echo ""
    echo "Run a load test to generate metrics:"
    echo "  ./gradlew :examples:http-load-test:runOtel"
fi
