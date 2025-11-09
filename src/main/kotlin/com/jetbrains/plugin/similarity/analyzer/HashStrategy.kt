package com.jetbrains.plugin.similarity.analyzer

import java.security.MessageDigest

/**
 * Strategy pattern for different hashing algorithms
 * Allows easy swapping without changing core logic
 */
interface HashStrategy {
    fun hash(input: String): String
    fun name(): String
}

/**
 * SHA-256 implementation (current default)
 * Good balance of security, speed, and compatibility
 */
class SHA256Strategy : HashStrategy {
    override fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    override fun name() = "SHA-256"
}

/**
 * SHA-512 implementation
 * Stronger but slower, produces longer hashes
 */
class SHA512Strategy : HashStrategy {
    override fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    override fun name() = "SHA-512"
}

/**
 * SHA-3 implementation (if available)
 * Modern alternative to SHA-2 family
 */
class SHA3Strategy : HashStrategy {
    override fun hash(input: String): String {
        try {
            val digest = MessageDigest.getInstance("SHA3-256")
            val hashBytes = digest.digest(input.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // SHA-3 not available, fallback to SHA-256
            println("SHA-3 not available, using SHA-256")
            return SHA256Strategy().hash(input)
        }
    }
    
    override fun name() = "SHA3-256"
}

/**
 * Fast non-cryptographic hash for performance-critical scenarios
 * WARNING: Not secure, only for deduplication
 */
class FastHashStrategy : HashStrategy {
    override fun hash(input: String): String {
        // Using built-in hashCode (not cryptographic)
        val hash = input.hashCode()
        return "%08x".format(hash)
    }
    
    override fun name() = "FastHash"
}

/**
 * Composite hash: combines multiple algorithms for extra security
 * Format: "sha256:hash1|sha512:hash2"
 */
class CompositeHashStrategy : HashStrategy {
    private val strategies = listOf(SHA256Strategy(), SHA512Strategy())
    
    override fun hash(input: String): String {
        return strategies.joinToString("|") { 
            "${it.name()}:${it.hash(input)}" 
        }
    }
    
    override fun name() = "Composite"
}

/**
 * Example: Simulated Locality-Sensitive Hashing (LSH)
 * Produces similar hashes for similar inputs
 * 
 * NOTE: This is a simplified demonstration. Real LSH (like MinHash or SimHash)
 * would be more sophisticated.
 */
class SimpleSimHashStrategy : HashStrategy {
    override fun hash(input: String): String {
        // Extract "features" (words/tokens)
        val tokens = input.split(Regex("\\W+")).filter { it.isNotEmpty() }
        
        // Create a vector
        val vector = IntArray(64)
        
        tokens.forEach { token ->
            val tokenHash = token.hashCode()
            for (i in 0 until 64) {
                // Check if bit i is set
                if ((tokenHash shr i) and 1 == 1) {
                    vector[i]++
                } else {
                    vector[i]--
                }
            }
        }
        
        // Convert to binary string
        val binaryHash = vector.map { if (it >= 0) '1' else '0' }.joinToString("")
        
        // Convert to hex for readability
        return binaryHash.chunked(4)
            .map { it.toInt(2).toString(16) }
            .joinToString("")
    }
    
    override fun name() = "SimHash"
    
    /**
     * Calculate Hamming distance between two SimHashes
     * (number of differing bits)
     */
    fun hammingDistance(hash1: String, hash2: String): Int {
        require(hash1.length == hash2.length) { "Hashes must be same length" }
        
        return hash1.zip(hash2).count { (a, b) -> a != b }
    }
}

// Example usage in BytecodeAnalyzer
object HashingConfig {
    // Easy to swap: just change this line!
    var strategy: HashStrategy = SHA256Strategy()
    
    // Or allow configuration:
    fun setStrategy(algorithmName: String) {
        strategy = when (algorithmName.uppercase()) {
            "SHA-256", "SHA256" -> SHA256Strategy()
            "SHA-512", "SHA512" -> SHA512Strategy()
            "SHA3", "SHA-3" -> SHA3Strategy()
            "FAST" -> FastHashStrategy()
            "COMPOSITE" -> CompositeHashStrategy()
            "SIMHASH" -> SimpleSimHashStrategy()
            else -> throw IllegalArgumentException("Unknown hash algorithm: $algorithmName")
        }
    }
}
