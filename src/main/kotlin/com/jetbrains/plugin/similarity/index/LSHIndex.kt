package com.jetbrains.plugin.similarity.index

import com.jetbrains.plugin.similarity.model.CodeDNA

/**
 * LSH (Locality-Sensitive Hashing) Index for fast approximate nearest neighbor search
 * 
 * Theory: Divide MinHash signature into bands. Two items that match in at least
 * one band are candidates for similarity.
 * 
 * Parameters tuning:
 * - numBands: More bands = higher recall, more candidates
 * - rowsPerBand = numHashes / numBands
 * - Threshold t where P(candidate) ≈ 1-(1-t^r)^b
 *   where r = rowsPerBand, b = numBands
 * 
 * Example with 128 hashes, 16 bands (r=8):
 * - Jaccard 0.3 → 10% chance of being candidate
 * - Jaccard 0.5 → 75% chance of being candidate
 * - Jaccard 0.7 → 99% chance of being candidate
 */
class LSHIndex(
    val numHashes: Int = 128,
    val numBands: Int = 16,
    private val minHash: MinHash = MinHash(numHashes)
) {
    init {
        require(numHashes % numBands == 0) {
            "numHashes ($numHashes) must be divisible by numBands ($numBands)"
        }
    }
    
    private val rowsPerBand = numHashes / numBands
    
    // Map: bucket hash -> set of plugin IDs
    private val buckets = mutableMapOf<Int, MutableSet<String>>()
    
    // Store signatures for later retrieval
    private val signatures = mutableMapOf<String, PluginSignature>()
    
    /**
     * Add a plugin to the index
     */
    fun add(codeDNA: CodeDNA) {
        // Generate MinHash signatures for different dimensions
        val classSignature = minHash.signature(codeDNA.structure.classHashes)
        val methodSignature = minHash.signature(codeDNA.apiFootprint.methodSignatureHashes)
        val apiSignature = minHash.signature(codeDNA.apiFootprint.externalReferences)
        
        val pluginSignature = PluginSignature(
            pluginId = codeDNA.hash,  // Use overall hash as ID
            classSignature = classSignature,
            methodSignature = methodSignature,
            apiSignature = apiSignature
        )
        
        signatures[codeDNA.hash] = pluginSignature
        
        // Add to LSH buckets (using class signature as primary)
        addToBuckets(codeDNA.hash, classSignature)
    }
    
    /**
     * Find candidate plugins similar to the query
     * 
     * @param query CodeDNA to search for
     * @param minBandMatches Minimum number of band matches required (higher = more precision, less recall)
     * @return Set of candidate plugin IDs
     */
    fun findCandidates(query: CodeDNA, minBandMatches: Int = 1): Set<String> {
        val querySignature = minHash.signature(query.structure.classHashes)
        
        val candidateCounts = mutableMapOf<String, Int>()
        
        // Check each band
        for (band in 0 until numBands) {
            val bucketHash = computeBandHash(querySignature, band)
            buckets[bucketHash]?.forEach { pluginId ->
                candidateCounts[pluginId] = candidateCounts.getOrDefault(pluginId, 0) + 1
            }
        }
        
        // Return plugins matching at least minBandMatches bands
        return candidateCounts.filter { it.value >= minBandMatches }.keys
    }
    
    /**
     * Estimate similarity to a candidate without full Jaccard calculation
     * Uses stored MinHash signatures
     */
    fun estimateSimilarity(pluginId: String, query: CodeDNA): EstimatedSimilarity? {
        val storedSig = signatures[pluginId] ?: return null
        
        val queryClassSig = minHash.signature(query.structure.classHashes)
        val queryMethodSig = minHash.signature(query.apiFootprint.methodSignatureHashes)
        val queryApiSig = minHash.signature(query.apiFootprint.externalReferences)
        
        val classSim = minHash.estimateSimilarity(queryClassSig, storedSig.classSignature)
        val methodSim = minHash.estimateSimilarity(queryMethodSig, storedSig.methodSignature)
        val apiSim = minHash.estimateSimilarity(queryApiSig, storedSig.apiSignature)
        
        // Use same weighting as SimilarityCalculator
        val structuralSim = classSim * 0.4 + 0.6  // Simplified (missing inheritance/interface)
        val apiSimOverall = apiSim * 0.5 + methodSim * 0.3 + 0.2  // Simplified
        val overallSim = structuralSim * 0.6 + apiSimOverall * 0.4
        
        return EstimatedSimilarity(
            pluginId = pluginId,
            overall = overallSim,
            structural = structuralSim,
            api = apiSimOverall,
            classSimilarity = classSim,
            methodSimilarity = methodSim,
            apiReferenceSimilarity = apiSim
        )
    }
    
    /**
     * Get statistics about the index
     */
    fun stats(): IndexStats {
        val bucketsUsed = buckets.size
        val avgBucketSize = if (bucketsUsed > 0) {
            buckets.values.sumOf { it.size }.toDouble() / bucketsUsed
        } else 0.0
        val maxBucketSize = buckets.values.maxOfOrNull { it.size } ?: 0
        
        return IndexStats(
            numPlugins = signatures.size,
            numBuckets = bucketsUsed,
            avgBucketSize = avgBucketSize,
            maxBucketSize = maxBucketSize
        )
    }
    
    /**
     * Add a plugin to all LSH buckets based on its signature
     */
    private fun addToBuckets(pluginId: String, signature: IntArray) {
        for (band in 0 until numBands) {
            val bucketHash = computeBandHash(signature, band)
            buckets.getOrPut(bucketHash) { mutableSetOf() }.add(pluginId)
        }
    }
    
    /**
     * Compute hash for a specific band of the signature
     */
    private fun computeBandHash(signature: IntArray, band: Int): Int {
        val start = band * rowsPerBand
        val end = start + rowsPerBand
        
        // Hash the band using a simple but effective method
        var hash = 17
        for (i in start until end) {
            hash = hash * 31 + signature[i]
        }
        return hash
    }
}

/**
 * Estimated similarity result from MinHash
 */
data class EstimatedSimilarity(
    val pluginId: String,
    val overall: Double,
    val structural: Double,
    val api: Double,
    val classSimilarity: Double,
    val methodSimilarity: Double,
    val apiReferenceSimilarity: Double
)

/**
 * Index statistics
 */
data class IndexStats(
    val numPlugins: Int,
    val numBuckets: Int,
    val avgBucketSize: Double,
    val maxBucketSize: Int
) {
    override fun toString(): String = """
        Index Statistics:
          Plugins indexed: $numPlugins
          LSH buckets used: $numBuckets
          Average bucket size: ${"%.2f".format(avgBucketSize)}
          Max bucket size: $maxBucketSize
    """.trimIndent()
}
