# Plugin Similarity Tool

A CLI tool for generating Code DNA fingerprints from plugin artifacts (ZIP/JAR files) and computing similarity scores between plugins. This tool extracts bytecode structure, class hierarchies, method signatures, and API references to create non-reversible hashed fingerprints.

## Features

- **Code DNA Generation**: Parse plugin artifacts to extract structural information
  - Class hierarchies and inheritance relationships
  - Interface implementations
  - Method signatures
  - API references and external dependencies
  - Package structure
  
- **Similarity Comparison**: Compute similarity between two plugin fingerprints
  - Structural similarity (classes, inheritance, packages)
  - API footprint similarity (external references, method signatures)
  - Behavioral similarity (bytecode instruction patterns and histograms)
  - Overall weighted similarity score
  
- **Fuzzy Mode**: Semantic normalization for better type-agnostic matching
  - Groups similar bytecode instructions (e.g., ILOAD/FLOAD → LOAD)
  - 7x improvement for type-variant code similarity detection
  - Optional flag for more flexible behavioral matching
  
- **Version Churn Analysis**: Calculate changes between plugin versions
  - Added/removed classes and methods
  - API reference changes
  - Overall churn percentage

## Requirements

- Java 17 or higher
- Gradle 7.x or higher

## Building

```bash
./gradlew build
```

To create an executable JAR:

```bash
./gradlew jar
```

The executable JAR will be located at `build/libs/plugin-similarity-1.0.0.jar`

## Usage

### Running with Gradle

```bash
./gradlew run --args="<command> <arguments>"
```

### Running with JAR

```bash
java -jar build/libs/plugin-similarity-1.0.0.jar <command> <arguments>
```

## Commands

### 1. Generate Fingerprint

Generate a Code DNA fingerprint from a plugin artifact:

```bash
# Using Gradle
./gradlew run --args="fingerprint plugin.jar output.json"

# Using JAR
java -jar build/libs/plugin-similarity-1.0.0.jar fingerprint plugin.jar output.json
```

**Options:**
- `--pretty`: Pretty-print the JSON output
- `--fuzzy`: Enable fuzzy mode with semantic opcode normalization

**Example:**
```bash
# Normal mode (exact bytecode matching)
./gradlew run --args="fingerprint my-plugin-1.0.0.jar fingerprint-1.0.0.json --pretty"

# Fuzzy mode (type-agnostic behavioral matching)
./gradlew run --args="fingerprint --fuzzy my-plugin-1.0.0.jar fingerprint-fuzzy.json --pretty"
```

### 2. Compare Fingerprints

Compare two Code DNA fingerprints for similarity:

```bash
# Using Gradle
./gradlew run --args="compare fingerprint1.json fingerprint2.json"

# Using JAR
java -jar build/libs/plugin-similarity-1.0.0.jar compare fingerprint1.json fingerprint2.json
```

**Options:**
- `-v, --verbose`: Show detailed comparison metrics

**Example:**
```bash
./gradlew run --args="compare plugin-a.json plugin-b.json --verbose"
```

**Output:**
```
Comparing fingerprints:
  [1] my-plugin-1.0.0.jar
  [2] my-plugin-2.0.0.jar

Similarity Scores:
  Overall:    78.45%
  Structural: 82.30%
  API:        72.15%
  Behavioral: 85.60%

Interpretation:
  Highly similar - possibly related plugins or different versions
```

### 3. Compute Churn

Analyze changes between two versions of a plugin:

```bash
# Using Gradle
./gradlew run --args="churn old-version.json new-version.json"

# Using JAR
java -jar build/libs/plugin-similarity-1.0.0.jar churn old-version.json new-version.json
```

**Example:**
```bash
./gradlew run --args="churn fingerprint-1.0.0.json fingerprint-2.0.0.json"
```

**Output:**
```
Computing churn between versions:
  Old: my-plugin-1.0.0.jar (1.0.0)
  New: my-plugin-2.0.0.jar (2.0.0)

Churn Metrics:
  Overall Churn: 15.32%

  Classes:
    Added: 12
    Removed: 3
    Unchanged: 145

  Methods:
    Added: 45
    Removed: 18

  API References:
    Added: 8
    Removed: 2

Assessment:
  Moderate changes - minor update
```

## Fuzzy Mode

**Fuzzy mode** uses semantic normalization to group similar bytecode instructions before pattern matching. This makes the tool more tolerant to implementation variations while preserving behavioral differences.

### Quick Example

```java
// Plugin A: Integer calculator
int result = a + b;  // Bytecode: ILOAD, ILOAD, IADD

// Plugin B: Float calculator  
float result = a + b;  // Bytecode: FLOAD, FLOAD, FADD
```

**Without fuzzy mode:** 0% behavioral match (different opcodes)  
**With fuzzy mode:** 100% behavioral match (both normalize to LOAD-LOAD-ARITH)

### Results

**Real test: IntCalculator vs FloatCalculator**

| Mode | Behavioral Similarity | Overall Similarity |
|------|----------------------|-------------------|
| Normal | 9.84% | 24.95% |
| **Fuzzy** | **70.00%** (7x improvement!) | **43.00%** |

### When to Use

✅ **Use fuzzy mode for:**
- Type-agnostic similarity detection (int vs float, long vs double)
- Cross-implementation style matching (static vs instance methods)
- Plugin marketplace search where semantic behavior matters
- Finding functionally similar code with different implementations

❌ **Don't use fuzzy mode for:**
- Exact clone detection or plagiarism checking
- When precision is critical (fuzzy mode may increase false positives)
- Comparing fingerprints generated in normal mode (incompatible!)

**See [FUZZY_MODE.md](FUZZY_MODE.md) for detailed documentation, trade-offs, and limitations.**

## Output Format

The fingerprint JSON contains:

```json
{
  "metadata": {
    "artifactName": "my-plugin-1.0.0.jar",
    "version": "1.0.0",
    "timestamp": 1699564800000,
    "totalClasses": 150,
    "totalMethods": 1200,
    "totalFields": 450
  },
  "structure": {
    "classHashes": ["abc123...", "def456..."],
    "packageStructure": {
      "com/example/plugin": 50,
      "com/example/plugin/utils": 25
    },
    "inheritanceHashes": ["ghi789..."],
    "interfaceHashes": ["jkl012..."]
  },
  "apiFootprint": {
    "externalReferences": ["mno345..."],
    "methodSignatureHashes": ["pqr678..."],
    "annotationHashes": ["stu901..."]
  },
  "behavioral": {
    "instructionPatternHashes": ["xyz567..."],
    "instructionHistograms": {
      "method_hash_1": {21: 10, 96: 5, 172: 2}
    }
  },
  "hash": "vwx234..."
}
```

## How It Works

### Code DNA Generation

1. **Artifact Parsing**: Extracts all `.class` files from ZIP/JAR archives
2. **Bytecode Analysis**: Uses ASM library to analyze bytecode structure:
   - Class names and hierarchies
   - Superclass and interface relationships
   - Method signatures and descriptors
   - Field declarations
   - External API references
3. **Hashing**: Creates SHA-256 hashes of structural elements
4. **Aggregation**: Combines all hashes into a comprehensive fingerprint

### Similarity Calculation

Uses **Jaccard similarity** (intersection over union) for set-based comparisons:

**Overall Score = 40% Structural + 30% API + 30% Behavioral**

- **Structural Similarity** (40% of overall):
  - Class hashes (40%)
  - Inheritance relationships (20%)
  - Interface implementations (20%)
  - Package structure (20%)

- **API Similarity** (30% of overall):
  - External references (50%)
  - Method signatures (30%)
  - Annotations (20%)

- **Behavioral Similarity** (30% of overall):
  - Instruction pattern matching (70%)
  - Instruction histogram similarity (30%)
  - Complexity factor adjustment
  - See [BEHAVIORAL_SIMILARITY.md](BEHAVIORAL_SIMILARITY.md) for details

### Churn Calculation

Computes the delta between versions:
- Set differences for added/removed elements
- Percentage calculation based on total elements

## Use Cases

1. **Marketplace Integration**: Flag suspicious or copied plugins
2. **Version Tracking**: Monitor changes between plugin releases
3. **Duplicate Detection**: Find similar plugins across the marketplace
4. **API Surface Analysis**: Understand plugin dependencies and API usage
5. **Quality Assurance**: Detect unusual changes or patterns

## Architecture

```
src/main/kotlin/com/jetbrains/plugin/similarity/
├── Main.kt                      # CLI entry point
├── model/
│   └── CodeDNA.kt              # Data models
└── analyzer/
    ├── ArtifactParser.kt       # ZIP/JAR parsing
    ├── BytecodeAnalyzer.kt     # ASM-based bytecode analysis
    └── SimilarityCalculator.kt # Similarity and churn computation
```

## Additional Documentation

- [BEHAVIORAL_SIMILARITY.md](BEHAVIORAL_SIMILARITY.md) - Deep dive into bytecode-based behavioral analysis
- [FUZZY_MODE.md](FUZZY_MODE.md) - Comprehensive fuzzy mode guide with examples and trade-offs
- [FALSE_POSITIVE_FIXES.md](FALSE_POSITIVE_FIXES.md) - Mitigation strategies for false positive scenarios
- Test scenarios and examples in `test-scenarios/`

## Dependencies

- **Kotlin**: Core language
- **ASM**: Bytecode manipulation and analysis
- **Kotlinx Serialization**: JSON serialization
- **Clikt**: CLI framework

## License

MIT License
