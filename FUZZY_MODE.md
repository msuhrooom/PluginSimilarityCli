# Fuzzy Mode: Semantic Normalization

## Overview

Fuzzy mode uses **semantic normalization** to group similar bytecode instructions into broader categories before pattern matching. This makes similarity detection more tolerant to implementation variations while still preserving behavioral differences.

## What Problem Does It Solve?

### Without Fuzzy Mode (Normal Mode)
```java
// Plugin A: Integer arithmetic
public int calculate(int a, int b) {
    return a + b;
}

// Plugin B: Float arithmetic  
public float calculate(float a, float b) {
    return a + b;
}
```

**Bytecode:**
- Plugin A: `ILOAD, ILOAD, IADD, IRETURN`
- Plugin B: `FLOAD, FLOAD, FADD, FRETURN`

**Result:** 0% pattern match ❌ (Different opcodes even though logic is identical!)

### With Fuzzy Mode
**Normalized bytecode:**
- Plugin A: `LOAD, LOAD, ARITH, RETURN`
- Plugin B: `LOAD, LOAD, ARITH, RETURN`

**Result:** 100% pattern match ✅ (Same semantic behavior!)

---

## How It Works

### Semantic Opcode Categories

Fuzzy mode maps specific JVM opcodes to 12 semantic categories:

| Category | Description | Examples |
|----------|-------------|----------|
| **LOAD** | Load variables/arrays | `ALOAD`, `ILOAD`, `FLOAD`, `IALOAD`, `AALOAD` |
| **STORE** | Store variables/arrays | `ASTORE`, `ISTORE`, `FSTORE`, `IASTORE`, `AASTORE` |
| **INVOKE** | Method calls | `INVOKEVIRTUAL`, `INVOKESTATIC`, `INVOKEINTERFACE`, `INVOKESPECIAL`, `INVOKEDYNAMIC` |
| **ARITH** | Arithmetic operations | `IADD`, `FADD`, `IMUL`, `FDIV`, `ISHL`, `IAND` |
| **COMPARE** | Comparisons | `IF_ICMPEQ`, `IFEQ`, `LCMP`, `IFNULL` |
| **RETURN** | Return statements | `IRETURN`, `ARETURN`, `RETURN` |
| **FIELD** | Field access | `GETFIELD`, `PUTFIELD`, `GETSTATIC`, `PUTSTATIC` |
| **ARRAY** | Array operations | `ARRAYLENGTH`, `NEWARRAY`, `ANEWARRAY` |
| **CONTROL** | Control flow | `GOTO`, `TABLESWITCH`, `LOOKUPSWITCH` |
| **NEW** | Object creation | `NEW` |
| **CAST** | Type conversions | `CHECKCAST`, `I2F`, `D2L`, `INSTANCEOF` |
| **OTHER** | Everything else | All other opcodes |

### Example Normalization

```kotlin
// Normal mode (exact opcodes):
instructions = [21, 27, 96, 172]  // ILOAD, ILOAD, IADD, IRETURN

// Fuzzy mode (semantic categories):
instructions = ["LOAD", "LOAD", "ARITH", "RETURN"]
```

---

## Use Cases

### ✅ Where Fuzzy Mode Helps

#### 1. Type Variations
```java
// Same logic, different types
int sum = a + b;      // ILOAD, ILOAD, IADD
float sum = a + b;    // FLOAD, FLOAD, FADD

// Fuzzy mode sees both as: LOAD-LOAD-ARITH ✅
```

#### 2. Implementation Style Changes
```java
// Static utility
String result = StringUtils.format(s);  // INVOKESTATIC

// Instance method
String result = formatter.format(s);    // INVOKEVIRTUAL

// Fuzzy mode sees both as: LOAD-INVOKE ✅
```

#### 3. Numeric Type Conversions
```java
// Different conversion paths
int x = (int) floatVal;     // F2I
long y = (long) doubleVal;  // D2L

// Fuzzy mode sees both as: CAST ✅
```

#### 4. Array vs Variable Load
```java
int val1 = array[0];  // IALOAD
int val2 = variable;  // ILOAD

// Fuzzy mode sees both as: LOAD ✅
```

---

### ❌ Where Fuzzy Mode DOES NOT Help

#### 1. Method Extraction (Refactoring)
```java
// Monolithic
int result = a + b + c;
// Pattern: LOAD-LOAD-ARITH-LOAD-ARITH

// Refactored
int result = add(a, b, c);
// Pattern: LOAD-LOAD-LOAD-INVOKE

// Different patterns even in fuzzy mode! ❌
```

**Why?** Extracting methods changes the instruction sequence fundamentally. The 3-gram patterns are completely different.

#### 2. Different Algorithms
```java
// Bubble sort
for (i) for (j) if (a[j] > a[j+1]) swap();

// Quick sort  
partition(); quickSort(left); quickSort(right);

// Different control flow patterns ❌
```

#### 3. Different Business Logic
```java
// File operations
file.read(); file.write(); file.close();

// Network operations
socket.connect(); socket.send(); socket.disconnect();

// Both become: LOAD-INVOKE-LOAD-INVOKE-LOAD-INVOKE
// Similar patterns despite different semantics! (False positive risk)
```

---

## Test Results

### Real-World Example: Type-Agnostic Calculator

**Test:** Compare `IntCalculator` vs `FloatCalculator` (identical logic, different types)

| Mode | Behavioral Similarity | Overall Similarity |
|------|----------------------|-------------------|
| Normal | 9.84% ❌ | 24.95% |
| **Fuzzy** | **70.00% ✅** | **43.00%** |

**Improvement:** 7x better behavioral detection!

### Refactoring Test (Known Limitation)

**Test:** Compare monolithic vs refactored `UserValidator` (same logic, extracted methods)

| Mode | Behavioral Similarity | Overall Similarity |
|------|----------------------|-------------------|
| Normal | 17.09% | 19.13% |
| Fuzzy | 17.24% | 19.17% |

**Improvement:** Only 0.15% (fuzzy mode doesn't solve refactoring false negatives)

---

## Usage

### CLI

```bash
# Enable fuzzy mode with --fuzzy flag
./gradlew run --args="fingerprint --fuzzy plugin.jar output.json"

# Compare fuzzy-mode fingerprints
./gradlew run --args="compare fuzzy1.json fuzzy2.json"
```

### When to Use Fuzzy Mode

✅ **Use fuzzy mode when:**
- Comparing plugins that may use different data types (int vs long, float vs double)
- Looking for functionally similar code regardless of implementation style
- Building a search system that should match semantic behavior
- Analyzing cross-language plugins (e.g., Java vs Kotlin bytecode differences)

❌ **Don't use fuzzy mode when:**
- You need exact bytecode matching
- Precision is more important than recall
- You're detecting exact clones or plagiarism
- You want to distinguish between similar algorithms

---

## Trade-offs

| Aspect | Normal Mode | Fuzzy Mode |
|--------|-------------|------------|
| **Precision** | High (exact matches) | Lower (semantic matches) |
| **Recall** | Lower (misses type variations) | Higher (catches more variants) |
| **False Positives** | Very low | Slightly higher |
| **False Negatives** | Higher (type-sensitive) | Lower (type-agnostic) |
| **Use Case** | Exact clone detection | Similar behavior detection |

---

## Technical Details

### Implementation

Fuzzy mode is implemented in `BytecodeAnalyzer.kt`:

```kotlin
class BytecodeAnalyzer(private val useFuzzyMode: Boolean = false) {
    
    private enum class SemanticOpcode {
        LOAD, STORE, INVOKE, ARITH, COMPARE, RETURN, 
        FIELD, ARRAY, CONTROL, NEW, CAST, OTHER
    }
    
    private fun normalizeOpcode(opcode: Int): String {
        if (!useFuzzyMode) {
            return opcode.toString()  // Normal mode: "21", "27", etc.
        }
        
        return when (opcode) {
            Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.ALOAD -> "LOAD"
            Opcodes.IADD, Opcodes.FADD, Opcodes.LADD -> "ARITH"
            // ... more mappings
        }
    }
}
```

### Fingerprint Compatibility

⚠️ **Important:** Fuzzy and normal mode fingerprints are **NOT compatible**!

- Fuzzy fingerprints can only be compared with other fuzzy fingerprints
- Normal fingerprints can only be compared with other normal fingerprints
- Mixing modes will produce meaningless results

The fingerprint hash is different between modes to prevent accidental mixing.

---

## Future Enhancements

Potential improvements to fuzzy mode:

1. **Hybrid Mode:** Combine exact + fuzzy matching with configurable weights
2. **Adaptive Granularity:** Use fuzzy mode for some operations, exact for others
3. **Custom Categories:** Allow users to define their own semantic groupings
4. **Variable N-grams:** Adjust n-gram size based on method complexity

---

## Conclusion

Fuzzy mode is a powerful feature for detecting **semantic similarity** across implementation variations. It trades some precision for significantly better recall, making it ideal for:
- Plugin marketplace similarity search
- Cross-version compatibility analysis  
- Type-agnostic behavioral comparison
- Duplicate functionality detection

However, it's **not a silver bullet** for refactoring detection. For that use case, consider:
- Aggregate-level behavioral comparison
- Control flow graph similarity
- Abstract syntax tree analysis
- Or use fuzzy mode as one signal among many
