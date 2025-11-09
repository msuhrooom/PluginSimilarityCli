# Usage Examples

## Example 1: Analyzing a Single Plugin

Let's say you have a plugin JAR file `my-plugin-1.0.0.jar`:

```bash
# Generate fingerprint
./gradlew run --args="fingerprint my-plugin-1.0.0.jar output/plugin-1.0.0.json --pretty"
```

Output:
```
Analyzing artifact: my-plugin-1.0.0.jar
✓ Fingerprint generated successfully
  Classes: 47
  Methods: 312
  Fields: 89
  Hash: a3f5c8d9e1b2f4a6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0
  Output: /path/to/output/plugin-1.0.0.json
```

## Example 2: Comparing Two Different Plugins

Compare two completely different plugins:

```bash
./gradlew run --args="compare output/plugin-a.json output/plugin-b.json --verbose"
```

Output:
```
Comparing fingerprints:
  [1] plugin-a-1.0.0.jar
  [2] plugin-b-2.3.1.jar

Similarity Scores:
  Overall:    12.45%
  Structural: 15.30%
  API:        8.15%

Detailed Metrics:
  Classes:
    Common: 3
    Total in [1]: 47
    Total in [2]: 52

  API References:
    Common: 5
    Total in [1]: 134
    Total in [2]: 189

  Method Signatures:
    Common: 12
    Total in [1]: 312
    Total in [2]: 401

Interpretation:
  Low similarity - likely different plugins
```

## Example 3: Comparing Two Versions of the Same Plugin

```bash
./gradlew run --args="compare output/plugin-1.0.0.json output/plugin-1.1.0.json"
```

Output:
```
Comparing fingerprints:
  [1] my-plugin-1.0.0.jar
  [2] my-plugin-1.1.0.jar

Similarity Scores:
  Overall:    87.32%
  Structural: 89.45%
  API:        84.12%

Interpretation:
  Extremely similar - likely same plugin or minor variations
```

## Example 4: Computing Version Churn

Track changes between versions:

```bash
./gradlew run --args="churn output/plugin-1.0.0.json output/plugin-1.1.0.json"
```

Output:
```
Computing churn between versions:
  Old: my-plugin-1.0.0.jar (1.0.0)
  New: my-plugin-1.1.0.jar (1.1.0)

Churn Metrics:
  Overall Churn: 8.45%

  Classes:
    Added: 4
    Removed: 1
    Unchanged: 43

  Methods:
    Added: 23
    Removed: 7

  API References:
    Added: 5
    Removed: 2

Assessment:
  Moderate changes - minor update
```

## Example 5: Detecting Copied/Similar Plugins

You can batch process multiple plugins to find similarities:

```bash
# Generate fingerprints for all plugins
for plugin in plugins/*.jar; do
    output_name=$(basename "$plugin" .jar).json
    ./gradlew run --args="fingerprint $plugin fingerprints/$output_name"
done

# Compare a suspicious plugin against all others
for fp in fingerprints/*.json; do
    if [ "$fp" != "fingerprints/suspicious-plugin.json" ]; then
        echo "Comparing with $fp"
        ./gradlew run --args="compare fingerprints/suspicious-plugin.json $fp" | grep "Overall:"
    fi
done
```

This would output similarity scores to help identify copied or suspiciously similar plugins.

## Example 6: Using the Built JAR

After building, you can use the standalone JAR:

```bash
# Build the JAR
./gradlew jar

# Use it directly
java -jar build/libs/plugin-similarity-1.0.0.jar fingerprint my-plugin.jar output.json
java -jar build/libs/plugin-similarity-1.0.0.jar compare output1.json output2.json
```

## Example 7: Marketplace Integration Pipeline

A typical workflow for marketplace processing:

```bash
#!/bin/bash
# Process new plugin submission

PLUGIN_FILE=$1
PLUGIN_NAME=$(basename "$PLUGIN_FILE" .jar)

# 1. Generate fingerprint
./gradlew run --args="fingerprint $PLUGIN_FILE fingerprints/$PLUGIN_NAME.json"

# 2. Compare with all existing plugins
MAX_SIMILARITY=0
SIMILAR_PLUGIN=""

for existing in fingerprints/marketplace/*.json; do
    if [ -f "$existing" ]; then
        SIMILARITY=$(./gradlew run --args="compare fingerprints/$PLUGIN_NAME.json $existing" | grep "Overall:" | awk '{print $2}' | tr -d '%')
        
        if (( $(echo "$SIMILARITY > $MAX_SIMILARITY" | bc -l) )); then
            MAX_SIMILARITY=$SIMILARITY
            SIMILAR_PLUGIN=$existing
        fi
    fi
done

# 3. Flag if suspicious similarity
if (( $(echo "$MAX_SIMILARITY > 70" | bc -l) )); then
    echo "⚠️  WARNING: High similarity ($MAX_SIMILARITY%) with $SIMILAR_PLUGIN"
    echo "This plugin should be manually reviewed for potential copying."
else
    echo "✓ Plugin appears unique (max similarity: $MAX_SIMILARITY%)"
fi

# 4. Store fingerprint for future comparisons
mv fingerprints/$PLUGIN_NAME.json fingerprints/marketplace/
```

## Example 8: Version History Analysis

Track how a plugin evolves over time:

```bash
# Generate fingerprints for all versions
./gradlew run --args="fingerprint plugin-1.0.0.jar fingerprints/v1.0.0.json"
./gradlew run --args="fingerprint plugin-1.1.0.jar fingerprints/v1.1.0.json"
./gradlew run --args="fingerprint plugin-2.0.0.jar fingerprints/v2.0.0.json"

# Analyze churn between consecutive versions
echo "=== 1.0.0 → 1.1.0 ==="
./gradlew run --args="churn fingerprints/v1.0.0.json fingerprints/v1.1.0.json"

echo "=== 1.1.0 → 2.0.0 ==="
./gradlew run --args="churn fingerprints/v1.1.0.json fingerprints/v2.0.0.json"
```

This helps identify:
- Patch vs. minor vs. major changes
- API stability across versions
- Breaking changes in plugin structure
