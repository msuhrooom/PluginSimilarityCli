# Quick Start Guide

Get started with the Plugin Similarity Tool in 5 minutes.

## Prerequisites

- Java 17+ installed
- A plugin JAR or ZIP file to analyze

## Step 1: Build the Tool

```bash
./gradlew build
```

## Step 2: Generate Your First Fingerprint

```bash
./gradlew run --args="fingerprint /path/to/your-plugin.jar fingerprint.json --pretty"
```

This will:
- Parse the plugin JAR file
- Extract bytecode structure
- Generate a Code DNA fingerprint
- Save it as `fingerprint.json`

## Step 3: Compare Two Plugins

If you have two plugin files:

```bash
# Generate fingerprints
./gradlew run --args="fingerprint plugin-a.jar fingerprint-a.json"
./gradlew run --args="fingerprint plugin-b.jar fingerprint-b.json"

# Compare them
./gradlew run --args="compare fingerprint-a.json fingerprint-b.json --verbose"
```

## Step 4: Track Version Changes

If you have two versions of the same plugin:

```bash
# Generate fingerprints
./gradlew run --args="fingerprint plugin-1.0.0.jar v1.json"
./gradlew run --args="fingerprint plugin-2.0.0.jar v2.json"

# Compute churn
./gradlew run --args="churn v1.json v2.json"
```

## Understanding the Output

### Fingerprint Output
- **Classes**: Number of classes in the plugin
- **Methods**: Total method count
- **Fields**: Total field count
- **Hash**: Unique SHA-256 hash of the plugin structure

### Similarity Scores
- **90-100%**: Same plugin or trivial variations
- **70-90%**: Highly similar, possibly different versions
- **50-70%**: Moderately similar, shared patterns
- **30-50%**: Somewhat similar, some common elements
- **0-30%**: Different plugins

### Churn Metrics
- **<5%**: Patch update (bug fixes)
- **5-20%**: Minor update (new features)
- **20-50%**: Major update (significant changes)
- **>50%**: Major refactoring or rewrite

## Next Steps

- See [README.md](README.md) for detailed documentation including:
  - **Search command** for fast similarity search across large databases
  - **Fuzzy mode** for type-agnostic behavioral matching
  - **LSH indexing** for scalable plugin discovery
- Check [examples/EXAMPLES.md](examples/EXAMPLES.md) for more use cases
- Use `./gradlew run --args="--help"` to see all available commands

## Troubleshooting

### "File not found" error
Make sure the path to your JAR file is correct. Use absolute paths if relative paths don't work.

### "Unsupported file type" error
The tool only supports `.jar` and `.zip` files. Make sure your plugin file has the correct extension.

### Out of memory errors
For very large plugins, you may need to increase JVM memory:
```bash
./gradlew run --args="fingerprint large-plugin.jar output.json" -Dorg.gradle.jvmargs="-Xmx2g"
```
