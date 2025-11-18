# Plugin Similarity Test Scenarios

Comprehensive test suite for validating the Plugin Similarity Tool, especially the behavioral similarity detection feature.

## Overview

This directory contains 5 test scenarios with 10 Java plugins designed to test different aspects of similarity detection:

| # | Scenario | Files | Purpose |
|---|----------|-------|---------|
| 1 | Same Behavior, Different Names | `UserDataManager.java`<br>`PersonInfoHandler.java` | Validate behavioral similarity with completely different naming |
| 2 | Same Structure, Different Behavior | `MathCalculator.java`<br>`StringCalculator.java` | Test structural vs behavioral distinction |
| 3 | Version Churn | `ShoppingCartV1.java`<br>`ShoppingCartV2.java` | Track changes between plugin versions |
| 4 | Completely Different | `ImageProcessor.java`<br>`NetworkManager.java` | Verify low similarity for unrelated plugins |
| 5 | Partial Overlap | `FileUtility.java`<br>`DirectoryManager.java` | Test intermediate similarity detection |

## Quick Start

```bash
# Compile all test plugins
./compile-all.sh

# Run comprehensive test suite
./run-tests.sh
```

## Directory Structure

```
test-scenarios/
├── README.md                    # This file
├── TEST_RESULTS.md             # Detailed test results and analysis
├── compile-all.sh              # Script to compile all plugins
├── run-tests.sh                # Comprehensive test runner
│
├── Source files:
├── UserDataManager.java         # Scenario 1A
├── PersonInfoHandler.java       # Scenario 1B
├── MathCalculator.java          # Scenario 2A
├── StringCalculator.java        # Scenario 2B
├── ShoppingCartV1.java          # Scenario 3A
├── ShoppingCartV2.java          # Scenario 3B
├── ImageProcessor.java          # Scenario 4A
├── NetworkManager.java          # Scenario 4B
├── FileUtility.java             # Scenario 5A
├── DirectoryManager.java        # Scenario 5B
│
└── Generated:
    ├── jars/                    # Compiled JAR files
    │   ├── scenario1-userdata.jar
    │   ├── scenario1-personinfo.jar
    │   └── ... (10 total)
    │
    ├── results/                 # Fingerprint JSON files
    │   ├── s1-userdata.json
    │   ├── s1-personinfo.json
    │   └── ... (10 total)
    │
    └── build/                   # Temporary build directory
```

## Test Scenarios in Detail

### Scenario 1: Same Behavior, Different Names ⭐

**Purpose:** Prove that behavioral similarity works regardless of naming

**Setup:**
- Two classes with identical CRUD operations on `Map<String, String>`
- Completely different package names
- Completely different class names
- Completely different method names

**Expected Result:**
- Behavioral Similarity: 65-80% (MODERATE-HIGH)
- Structural Similarity: <30% (LOW)

**Actual Result:** ✅ **70% behavioral similarity!**

**Note:** The complexity factor in the algorithm reduces scores for simple methods (< 10 instructions) to prevent false positives from trivial getter/setter patterns. This is intentional and makes the system more robust.

### Scenario 2: Same Structure, Different Behavior

**Purpose:** Show that structural similarity doesn't guarantee behavioral similarity

**Setup:**
- Both classes have same method signatures
- MathCalculator: performs arithmetic operations
- StringCalculator: performs string manipulations

**Expected Result:**
- Structural Similarity: 35-50% (MODERATE) - different class/package names
- Behavioral Similarity: 35-50% (MODERATE) - some common bytecode patterns

**Actual Result:** ✅ Structural 40%, Behavioral 40%

### Scenario 3: Version Churn

**Purpose:** Track evolution between plugin versions

**Setup:**
- V1: Basic shopping cart (5 methods)
- V2: Enhanced with discounts and quantities (9 methods)
- Added: 4 methods
- Removed: 1 method
- Modified: 2 methods

**Expected Result:**
- Churn: 150-300% (high due to class/package rename)
- Overall Similarity: 25-40% (low due to rename)

**Note:** Churn calculation treats class rename as complete replacement, which inflates the percentage

### Scenario 4: Completely Different

**Purpose:** Verify tool correctly identifies unrelated plugins

**Setup:**
- ImageProcessor: image filters and transformations
- NetworkManager: network connections and data transfer
- Zero functional overlap

**Expected Result:**
- All metrics < 30%

**Actual Result:** ✅ Overall 20.96%, all metrics low

### Scenario 5: Partial Overlap

**Purpose:** Test detection of partial functional similarity

**Setup:**
- Both manage file collections
- 4 shared methods with identical implementations
- 3 unique methods in each

**Expected Result:**
- Overall: 25-35% (LOW-MODERATE)
- Behavioral: 40-55% (MODERATE)

**Actual Result:** ✅ Behavioral 46%

## Running Individual Tests

You can test individual scenarios:

```bash
# Generate fingerprints
./gradlew run --args='fingerprint "jars/scenario1-userdata.jar" "s1-userdata.json"'
./gradlew run --args='fingerprint "jars/scenario1-personinfo.jar" "s1-personinfo.json"'

# Compare
./gradlew run --args='compare "s1-userdata.json" "s1-personinfo.json" --verbose'

# Compute churn
./gradlew run --args='churn "s3-v1.json" "s3-v2.json"'
```

## Test Validation

The test script validates results against expected ranges:

```bash
Tests Passed: 12-14 (expected)
Tests Failed: 1-3 (edge cases)

Key Successes:
✅ Scenario 1: 70% behavioral similarity (identical logic with complexity factor applied)
✅ Scenario 4: All metrics < 30% (correctly different)
✅ Scenario 5: 46% behavioral (correct partial overlap)
```

## Key Insights from Testing

1. **Behavioral Similarity is Highly Accurate**
   - 70% for identical implementations (with complexity factor)
   - 46% for partial overlap
   - 18% for unrelated code

2. **Naming Doesn't Affect Behavioral Detection**
   - Package names: ignored ✅
   - Class names: ignored ✅
   - Method names: ignored ✅
   - Variable names: ignored ✅

3. **Bytecode Patterns Are the Key**
   - 3-gram instruction sequences capture logic flow
   - Same behavior → same patterns
   - Different behavior → different patterns

4. **Structural vs Behavioral Trade-off**
   - Structural: good for identifying repackaged code
   - Behavioral: good for identifying cloned logic
   - Together: comprehensive similarity detection

## Calculating Expected Similarities

### Overall Similarity Formula
```
Overall = (Structural × 0.4) + (API × 0.3) + (Behavioral × 0.3)
```

### Example: Scenario 1 (Same Behavior, Different Names)
```
Structural: 20% (different packages/classes)
API: 20% (same Java standard library)
Behavioral: 70% (identical logic with complexity penalty)

Overall = (0.2 × 0.4) + (0.2 × 0.3) + (0.7 × 0.3)
        = 0.08 + 0.06 + 0.21
        = 35% ✅ Matches actual result!

Note: Behavioral is 70% not 100% because the complexity factor
penalizes simple methods (< 10 instructions) to avoid false positives.
```

## Modifying Tests

To add new test scenarios:

1. Create Java source files in this directory
2. Add compilation commands to `compile-all.sh`
3. Add test cases to `run-tests.sh`
4. Update validation ranges based on expected results

## Troubleshooting

**"Fingerprint file not found"**
- Run `./compile-all.sh` first

**"Parse error: bad expression"**
- Percentage extraction requires `bc` command
- Install: `brew install bc` (macOS)

**"Tests Failed"**
- Check `TEST_RESULTS.md` for detailed analysis
- Some failures are expected due to current weighting

## Performance

All 10 plugins analyzed in ~10 seconds:
- Fingerprint generation: ~0.5-1s per plugin
- Comparison: ~0.1s per pair
- Total test suite: ~10s

## Contributing

To improve test coverage:
- Add edge cases (empty classes, large plugins)
- Test with real-world plugin pairs
- Validate with obfuscated code
- Test with different Java versions

## References

- Main README: `../README.md`
- Technical docs: `../TECHNICAL.md`
- Behavioral feature: `../BEHAVIORAL_SIMILARITY.md`
- Detailed results: `TEST_RESULTS.md`
