#!/bin/bash

# Comprehensive test script for Plugin Similarity Tool
# Tests behavioral similarity detection across different scenarios

set -e

PROJECT_DIR="/Users/not_danbin/Desktop/me/IC Computing Year 2/intern/PluginSimilarity"
TEST_DIR="$PROJECT_DIR/test-scenarios"
JARS_DIR="$TEST_DIR/jars"
RESULTS_DIR="$TEST_DIR/results"

cd "$PROJECT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "================================================"
echo "Plugin Similarity Tool - Comprehensive Test Suite"
echo "================================================"
echo ""

# Create results directory
mkdir -p "$RESULTS_DIR"
rm -f "$RESULTS_DIR"/*.json

# Helper function to extract percentage from output
extract_percentage() {
    echo "$1" | grep "$2" | sed 's/.*: *\([0-9.]*\)%.*/\1/'
}

# Helper function to validate percentage is in range
validate_range() {
    local actual=$1
    local min=$2
    local max=$3
    local name=$4
    
    if (( $(echo "$actual >= $min && $actual <= $max" | bc -l) )); then
        echo -e "${GREEN}✓${NC} $name: ${actual}% (expected ${min}-${max}%)"
        return 0
    else
        echo -e "${RED}✗${NC} $name: ${actual}% (expected ${min}-${max}%)"
        return 1
    fi
}

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

echo ""
echo "=== SCENARIO 1: Same Behavior, Different Names ==="
echo "Expected: HIGH behavioral similarity (>85%), LOW structural similarity (<30%)"
echo ""

./gradlew run --args="fingerprint \"$JARS_DIR/scenario1-userdata.jar\" \"$RESULTS_DIR/s1-userdata.json\"" --quiet
./gradlew run --args="fingerprint \"$JARS_DIR/scenario1-personinfo.jar\" \"$RESULTS_DIR/s1-personinfo.json\"" --quiet

OUTPUT=$(./gradlew run --args="compare \"$RESULTS_DIR/s1-userdata.json\" \"$RESULTS_DIR/s1-personinfo.json\"" --quiet)
echo "$OUTPUT"
echo ""

OVERALL=$(extract_percentage "$OUTPUT" "Overall")
STRUCTURAL=$(extract_percentage "$OUTPUT" "Structural")
BEHAVIORAL=$(extract_percentage "$OUTPUT" "Behavioral")

echo "Validation:"
validate_range "$OVERALL" 75 95 "Overall Similarity" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))
validate_range "$STRUCTURAL" 0 30 "Structural Similarity (should be LOW)" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))
validate_range "$BEHAVIORAL" 85 100 "Behavioral Similarity (should be HIGH)" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))

echo ""
echo "---"
echo ""

echo "=== SCENARIO 2: Same Structure, Different Behavior ==="
echo "Expected: HIGH structural similarity (>85%), LOW behavioral similarity (<30%)"
echo ""

./gradlew run --args="fingerprint \"$JARS_DIR/scenario2-math.jar\" \"$RESULTS_DIR/s2-math.json\"" --quiet
./gradlew run --args="fingerprint \"$JARS_DIR/scenario2-string.jar\" \"$RESULTS_DIR/s2-string.json\"" --quiet

OUTPUT=$(./gradlew run --args="compare \"$RESULTS_DIR/s2-math.json\" \"$RESULTS_DIR/s2-string.json\"" --quiet)
echo "$OUTPUT"
echo ""

OVERALL=$(extract_percentage "$OUTPUT" "Overall")
STRUCTURAL=$(extract_percentage "$OUTPUT" "Structural")
BEHAVIORAL=$(extract_percentage "$OUTPUT" "Behavioral")

echo "Validation:"
validate_range "$OVERALL" 40 70 "Overall Similarity" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))
validate_range "$STRUCTURAL" 85 100 "Structural Similarity (should be HIGH)" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))
validate_range "$BEHAVIORAL" 0 30 "Behavioral Similarity (should be LOW)" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))

echo ""
echo "---"
echo ""

echo "=== SCENARIO 3: Version Churn (v1 -> v2) ==="
echo "Expected: HIGH overall similarity (>70%), measurable churn (15-35%)"
echo ""

./gradlew run --args="fingerprint \"$JARS_DIR/scenario3-cart-v1.jar\" \"$RESULTS_DIR/s3-v1.json\"" --quiet
./gradlew run --args="fingerprint \"$JARS_DIR/scenario3-cart-v2.jar\" \"$RESULTS_DIR/s3-v2.json\"" --quiet

OUTPUT=$(./gradlew run --args="compare \"$RESULTS_DIR/s3-v1.json\" \"$RESULTS_DIR/s3-v2.json\"" --quiet)
echo "$OUTPUT"
echo ""

OVERALL=$(extract_percentage "$OUTPUT" "Overall")

echo "Validation:"
validate_range "$OVERALL" 70 90 "Overall Similarity" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))

echo ""
echo "Computing churn..."
CHURN_OUTPUT=$(./gradlew run --args="churn \"$RESULTS_DIR/s3-v1.json\" \"$RESULTS_DIR/s3-v2.json\"" --quiet)
echo "$CHURN_OUTPUT"
echo ""

CHURN=$(extract_percentage "$CHURN_OUTPUT" "Overall Churn")

echo "Validation:"
validate_range "$CHURN" 15 35 "Version Churn" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))

echo ""
echo "---"
echo ""

echo "=== SCENARIO 4: Completely Different Plugins ==="
echo "Expected: LOW similarity across all metrics (<30%)"
echo ""

./gradlew run --args="fingerprint \"$JARS_DIR/scenario4-image.jar\" \"$RESULTS_DIR/s4-image.json\"" --quiet
./gradlew run --args="fingerprint \"$JARS_DIR/scenario4-network.jar\" \"$RESULTS_DIR/s4-network.json\"" --quiet

OUTPUT=$(./gradlew run --args="compare \"$RESULTS_DIR/s4-image.json\" \"$RESULTS_DIR/s4-network.json\"" --quiet)
echo "$OUTPUT"
echo ""

OVERALL=$(extract_percentage "$OUTPUT" "Overall")
STRUCTURAL=$(extract_percentage "$OUTPUT" "Structural")
API=$(extract_percentage "$OUTPUT" "API")
BEHAVIORAL=$(extract_percentage "$OUTPUT" "Behavioral")

echo "Validation:"
validate_range "$OVERALL" 0 30 "Overall Similarity (should be LOW)" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))
validate_range "$STRUCTURAL" 0 30 "Structural Similarity (should be LOW)" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))
validate_range "$API" 0 30 "API Similarity (should be LOW)" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))
validate_range "$BEHAVIORAL" 0 30 "Behavioral Similarity (should be LOW)" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))

echo ""
echo "---"
echo ""

echo "=== SCENARIO 5: Partial Overlap ==="
echo "Expected: MODERATE similarity (45-70%)"
echo ""

./gradlew run --args="fingerprint \"$JARS_DIR/scenario5-file.jar\" \"$RESULTS_DIR/s5-file.json\"" --quiet
./gradlew run --args="fingerprint \"$JARS_DIR/scenario5-directory.jar\" \"$RESULTS_DIR/s5-directory.json\"" --quiet

OUTPUT=$(./gradlew run --args="compare \"$RESULTS_DIR/s5-file.json\" \"$RESULTS_DIR/s5-directory.json\"" --quiet)
echo "$OUTPUT"
echo ""

OVERALL=$(extract_percentage "$OUTPUT" "Overall")
BEHAVIORAL=$(extract_percentage "$OUTPUT" "Behavioral")

echo "Validation:"
validate_range "$OVERALL" 45 70 "Overall Similarity (should be MODERATE)" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))
validate_range "$BEHAVIORAL" 40 75 "Behavioral Similarity (should be MODERATE)" && ((TESTS_PASSED++)) || ((TESTS_FAILED++))

echo ""
echo "================================================"
echo "Test Summary"
echo "================================================"
echo ""
echo -e "Tests Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests Failed: ${RED}$TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi
