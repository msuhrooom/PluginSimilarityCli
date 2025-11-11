# Technical Documentation

## Overview

This tool implements a "Code DNA" fingerprinting system for JetBrains plugin artifacts. It uses bytecode analysis to generate non-reversible hashed fingerprints that capture the structural and API characteristics of plugins.

## Architecture

### Core Components

#### 1. BytecodeAnalyzer (`analyzer/BytecodeAnalyzer.kt`)

Uses the ASM library to parse Java bytecode and extract:

- **Class Structure**:
  - Class names and packages
  - Superclass relationships
  - Interface implementations
  
- **Members**:
  - Method signatures and descriptors
  - Field declarations
  - Access modifiers
  
- **API References**:
  - External class references
  - Method invocations
  - Type annotations
  
**Key Design Decision**: Filters out standard JDK/Kotlin classes (`java.*`, `javax.*`, `kotlin.*`) to focus on plugin-specific APIs and dependencies.

#### 2. ArtifactParser (`analyzer/ArtifactParser.kt`)

Coordinates the parsing pipeline:

1. Opens ZIP/JAR archives using `java.util.zip.ZipFile`
2. Identifies `.class` files
3. Feeds bytecode to `BytecodeAnalyzer`
4. Aggregates results into `CodeDNA` structure
5. Generates cryptographic hashes (SHA-256)

**Hashing Strategy**:
- Individual components are hashed separately
- Hashes are sorted before combining to ensure determinism
- Overall hash combines: class hashes, inheritance, API refs, method signatures

#### 3. SimilarityCalculator (`analyzer/SimilarityCalculator.kt`)

Implements two key algorithms:

##### Similarity Computation

Uses **Jaccard similarity** (set intersection over union):

```
J(A, B) = |A ∩ B| / |A ∪ B|
```

Components:
- **Structural** (40% weight):
  - Class hashes (40%)
  - Inheritance (20%)
  - Interfaces (20%)
  - Package distribution via cosine similarity (20%)

- **API Footprint** (30% weight):
  - External references (50%)
  - Method signatures (30%)
  - Annotations (20%)

- **Behavioral** (30% weight):
  - Instruction pattern matching (70%)
  - Instruction histogram similarity (30%)
  - Complexity factor adjustment

##### Churn Analysis

Computes set differences between versions:
```
Churn = (Added + Removed) / Total_Old * 100%
```

Tracks:
- Class additions/removals
- Method changes
- API reference changes

#### 4. CLI Interface (`Main.kt`)

Built with [Clikt](https://ajalt.github.io/clikt/) framework.

Three commands:
- **fingerprint**: Generate Code DNA from artifact
- **compare**: Compute similarity between two fingerprints
- **churn**: Analyze changes between versions

## Data Model

### CodeDNA Structure

```kotlin
CodeDNA {
  metadata: PluginMetadata          // High-level stats
  structure: StructureFingerprint   // Class/package hashes
  apiFootprint: ApiFootprint        // External dependencies
  hash: String                      // Overall SHA-256 hash
}
```

All structural elements are **hashed** to:
1. Reduce storage size
2. Make fingerprints non-reversible
3. Enable fast set operations

### Storage Format

JSON serialization via `kotlinx.serialization`:

```json
{
  "metadata": {
    "artifactName": "plugin.jar",
    "version": "1.0.0",
    "timestamp": 1699564800000,
    "totalClasses": 150,
    "totalMethods": 1200,
    "totalFields": 450
  },
  "structure": {
    "classHashes": ["hash1", "hash2", ...],
    "packageStructure": {"com/example": 50},
    "inheritanceHashes": ["hash3", ...],
    "interfaceHashes": ["hash4", ...]
  },
  "apiFootprint": {
    "externalReferences": ["hash5", ...],
    "methodSignatureHashes": ["hash6", ...],
    "annotationHashes": ["hash7", ...]
  },
  "hash": "overall_hash"
}
```

## Algorithm Details

### Why Jaccard Similarity?

Jaccard is ideal for comparing sets of hashes because:
1. Symmetric: J(A,B) = J(B,A)
2. Normalized: 0 ≤ J(A,B) ≤ 1
3. Intuitive: measures overlap
4. Fast: O(n + m) with hash sets

### Why SHA-256 Hashing?

- **Non-reversible**: Cannot reconstruct original code
- **Collision-resistant**: ~0 probability of false matches
- **Deterministic**: Same input always produces same hash
- **Fast**: Suitable for large-scale processing

### Package Similarity via Cosine

Package structure uses **cosine similarity** instead of Jaccard:

```
cosine(A, B) = (A · B) / (||A|| × ||B||)
```

Treats package counts as vectors, capturing distribution patterns rather than exact matches. This handles plugins with similar organization but different package names.

## Performance Considerations

### Memory

- Streams ZIP entries to avoid loading entire archive
- Hashes reduce memory footprint vs. storing raw strings
- Set operations are efficient with hash-based collections

### Speed

- ASM library: ~10-50ms per class file
- Typical plugin (100 classes): 1-5 seconds
- Comparison: O(1) hash lookups, ~1ms per fingerprint pair

### Scalability

For marketplace-scale operations:
1. Generate fingerprints offline/async
2. Store in database with indexes on hash
3. Use approximate nearest neighbor for large-scale similarity search
4. Consider bloom filters for quick "definitely different" checks

## Integration Points

### Marketplace Pipeline

```
Plugin Upload
    ↓
Generate Fingerprint
    ↓
Compare with Existing (parallel)
    ↓
Flag if similarity > threshold
    ↓
Store Fingerprint
```

### Version Tracking

```
New Release
    ↓
Generate Fingerprint
    ↓
Compare with Previous Version
    ↓
Compute Churn
    ↓
Log Metrics
```

## Extensibility

### Adding New Metrics

To add a new similarity component:

1. Extract data in `BytecodeAnalyzer`
2. Hash and store in appropriate model (Structure/API)
3. Add comparison logic in `SimilarityCalculator`
4. Adjust weights in similarity computation

### Custom Hash Functions

Replace `BytecodeAnalyzer.hashString()` to use:
- Different hash algorithms (e.g., BLAKE2)
- Locality-sensitive hashing for fuzzy matching
- Perceptual hashing for structural similarity

## Limitations

1. **Obfuscation**: Heavily obfuscated code may appear different even if functionally identical
2. **Generated Code**: Auto-generated classes may dominate fingerprint
3. **Resource Files**: Only analyzes bytecode, not resources/assets
4. **Dynamic Loading**: Cannot detect runtime-loaded classes

## Future Enhancements

1. **Control Flow Analysis**: Include method CFG signatures
2. **Constant Pool Analysis**: Detect string literal patterns
3. **Dependency Graph**: Build and compare full dependency trees
4. **ML-based Clustering**: Use fingerprints as features for unsupervised grouping
5. **API Evolution Tracking**: Detect breaking vs. compatible changes
6. **Size-normalized Metrics**: Account for plugin size in similarity scoring

## References

- [ASM Framework](https://asm.ow2.io/)
- [Jaccard Index](https://en.wikipedia.org/wiki/Jaccard_index)
- [Cosine Similarity](https://en.wikipedia.org/wiki/Cosine_similarity)
- [SHA-256](https://en.wikipedia.org/wiki/SHA-2)
