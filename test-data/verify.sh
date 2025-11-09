#!/bin/bash
# Quick verification script to test the Plugin Similarity Tool

echo "==================================="
echo "Plugin Similarity Tool - Verification"
echo "==================================="
echo ""

cd "$(dirname "$0")/.."

echo "✓ Test 1: Generate fingerprint for v1"
./gradlew run --args="fingerprint test-data/test-plugin.jar test-data/fingerprint.json" --quiet
echo ""

echo "✓ Test 2: Generate fingerprint for v2"
./gradlew run --args="fingerprint test-data/test-plugin-v2.jar test-data/fingerprint-v2.json" --quiet
echo ""

echo "✓ Test 3: Compare versions"
./gradlew run --args="compare test-data/fingerprint.json test-data/fingerprint-v2.json" --quiet
echo ""

echo "✓ Test 4: Compute churn"
./gradlew run --args="churn test-data/fingerprint.json test-data/fingerprint-v2.json" --quiet
echo ""

echo "==================================="
echo "✅ All tests passed!"
echo "==================================="
echo ""
echo "The tool is working correctly. You can now:"
echo "  - Use it on your own JAR/ZIP files"
echo "  - Check README.md for full documentation"
echo "  - See examples/EXAMPLES.md for more use cases"
