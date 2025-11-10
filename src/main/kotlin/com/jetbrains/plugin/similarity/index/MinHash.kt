package com.jetbrains.plugin.similarity.index

import kotlin.random.Random

/**
 * MinHash: Approximates Jaccard similarity using compact signatures
 * 
 * Theory: For two sets A and B,
 *   P(minHash_i(A) == minHash_i(B)) = Jaccard(A, B)
 * 
 * By using k hash functions, we can estimate Jaccard similarity as:
 *   estimatedJaccard = (number of matching hashes) / k
 * 
 * Typical error: ε ≈ 1/√k (e.g., k=128 → error ~8.8%)
 */
class MinHash(
    val numHashes: Int = 128,
    private val seed: Long = 42
) {
    // Pre-generate hash function parameters for consistency
    private val hashSeeds = Random(seed).let { rng ->
        IntArray(numHashes) { rng.nextInt() }
    }
    
    /**
     * Generate MinHash signature for a set of strings
     * 
     * @param set The input set of strings (e.g., class hashes)
     * @return Signature of size [numHashes]
     */
    fun signature(set: Set<String>): IntArray {
        if (set.isEmpty()) {
            return IntArray(numHashes) { Int.MAX_VALUE }
        }
        
        val signature = IntArray(numHashes) { Int.MAX_VALUE }
        
        // For each hash function
        for (i in 0 until numHashes) {
            // Find minimum hash value across all set elements
            for (element in set) {
                val hash = hashElement(element, hashSeeds[i])
                if (hash < signature[i]) {
                    signature[i] = hash
                }
            }
        }
        
        return signature
    }
    
    /**
     * Estimate Jaccard similarity between two signatures
     * 
     * @param sig1 First MinHash signature
     * @param sig2 Second MinHash signature
     * @return Estimated Jaccard similarity [0.0, 1.0]
     */
    fun estimateSimilarity(sig1: IntArray, sig2: IntArray): Double {
        require(sig1.size == sig2.size) { "Signatures must have same length" }
        
        val matches = sig1.zip(sig2).count { (a, b) -> a == b }
        return matches.toDouble() / sig1.size
    }
    
    /**
     * Hash a single element with a specific seed
     * Uses a simple but effective hash mixing function
     */
    private fun hashElement(element: String, seed: Int): Int {
        var hash = element.hashCode()
        hash = hash xor seed
        hash = hash xor (hash ushr 16)
        hash = (hash * 0x85ebca6b).toInt()
        hash = hash xor (hash ushr 13)
        hash = (hash * 0xc2b2ae35).toInt()
        hash = hash xor (hash ushr 16)
        return hash
    }
    
    /**
     * Compute exact Jaccard similarity for comparison
     * (Not used in production, useful for validation)
     */
    fun exactJaccard(set1: Set<String>, set2: Set<String>): Double {
        if (set1.isEmpty() && set2.isEmpty()) return 1.0
        if (set1.isEmpty() || set2.isEmpty()) return 0.0
        
        val intersection = set1.intersect(set2).size.toDouble()
        val union = set1.union(set2).size.toDouble()
        return intersection / union
    }
}

/**
 * Data class to hold a plugin's MinHash signatures
 */
data class PluginSignature(
    val pluginId: String,
    val classSignature: IntArray,
    val methodSignature: IntArray,
    val apiSignature: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PluginSignature) return false
        return pluginId == other.pluginId
    }
    
    override fun hashCode(): Int = pluginId.hashCode()
}
