package com.jetbrains.plugin.similarity.analyzer

import com.jetbrains.plugin.similarity.model.CodeDNA
import com.jetbrains.plugin.similarity.model.SimilarityDetails
import com.jetbrains.plugin.similarity.model.SimilarityScore

/**
 * Calculates similarity between two Code DNA fingerprints
 */
class SimilarityCalculator {
    
    /**
     * Computes similarity score between two Code DNA fingerprints using Jaccard similarity
     * for structural and API components
     */
    fun computeSimilarity(dna1: CodeDNA, dna2: CodeDNA): SimilarityScore {
        val structuralSimilarity = computeStructuralSimilarity(dna1, dna2)
        val apiSimilarity = computeApiSimilarity(dna1, dna2)
        val behavioralSimilarity = computeBehavioralSimilarity(dna1, dna2)
        
        // Weighted average: 40% structural, 30% API, 30% behavioral
        val overallSimilarity = (structuralSimilarity * 0.4) + (apiSimilarity * 0.3) + (behavioralSimilarity * 0.3)
        
        val details = SimilarityDetails(
            commonClasses = intersectionSize(dna1.structure.classHashes, dna2.structure.classHashes),
            totalClassesA = dna1.structure.classHashes.size,
            totalClassesB = dna2.structure.classHashes.size,
            commonApiReferences = intersectionSize(dna1.apiFootprint.externalReferences, dna2.apiFootprint.externalReferences),
            totalApiReferencesA = dna1.apiFootprint.externalReferences.size,
            totalApiReferencesB = dna2.apiFootprint.externalReferences.size,
            commonMethodSignatures = intersectionSize(dna1.apiFootprint.methodSignatureHashes, dna2.apiFootprint.methodSignatureHashes),
            totalMethodSignaturesA = dna1.apiFootprint.methodSignatureHashes.size,
            totalMethodSignaturesB = dna2.apiFootprint.methodSignatureHashes.size
        )
        
        return SimilarityScore(
            overall = overallSimilarity,
            structural = structuralSimilarity,
            api = apiSimilarity,
            behavioral = behavioralSimilarity,
            details = details
        )
    }
    
    private fun computeStructuralSimilarity(dna1: CodeDNA, dna2: CodeDNA): Double {
        val classJaccard = jaccardSimilarity(
            dna1.structure.classHashes,
            dna2.structure.classHashes
        )
        
        val inheritanceJaccard = jaccardSimilarity(
            dna1.structure.inheritanceHashes,
            dna2.structure.inheritanceHashes
        )
        
        val interfaceJaccard = jaccardSimilarity(
            dna1.structure.interfaceHashes,
            dna2.structure.interfaceHashes
        )
        
        // Package structure similarity (cosine similarity of package distributions)
        val packageSimilarity = computePackageSimilarity(
            dna1.structure.packageStructure,
            dna2.structure.packageStructure
        )
        
        // Weighted average of structural components
        return (classJaccard * 0.4) + 
               (inheritanceJaccard * 0.2) + 
               (interfaceJaccard * 0.2) + 
               (packageSimilarity * 0.2)
    }
    
    private fun computeApiSimilarity(dna1: CodeDNA, dna2: CodeDNA): Double {
        val externalRefJaccard = jaccardSimilarity(
            dna1.apiFootprint.externalReferences,
            dna2.apiFootprint.externalReferences
        )
        
        val methodSigJaccard = jaccardSimilarity(
            dna1.apiFootprint.methodSignatureHashes,
            dna2.apiFootprint.methodSignatureHashes
        )
        
        val annotationJaccard = jaccardSimilarity(
            dna1.apiFootprint.annotationHashes,
            dna2.apiFootprint.annotationHashes
        )
        
        // Weighted average: external refs most important for API footprint
        return (externalRefJaccard * 0.5) + 
               (methodSigJaccard * 0.3) + 
               (annotationJaccard * 0.2)
    }
    
    /**
     * Computes behavioral similarity based on instruction patterns
     * This captures similar behavior even when function names differ
     */
    private fun computeBehavioralSimilarity(dna1: CodeDNA, dna2: CodeDNA): Double {
        // If both have no behavioral data (e.g., all trivial methods filtered), 
        // return neutral score to not penalize structural/API match
        val hasBehavioralData1 = dna1.behavioral.instructionPatternHashes.isNotEmpty()
        val hasBehavioralData2 = dna2.behavioral.instructionPatternHashes.isNotEmpty()
        
        if (!hasBehavioralData1 && !hasBehavioralData2) {
            // Both lack behavioral data - return neutral (not 0, not 1)
            return 0.5  // Neutral: doesn't boost or penalize overall similarity
        }
        
        if (!hasBehavioralData1 || !hasBehavioralData2) {
            // One has behavioral data, one doesn't - low similarity
            return 0.1
        }
        
        // Compare instruction pattern hashes (3-grams of bytecode opcodes)
        val patternJaccard = jaccardSimilarity(
            dna1.behavioral.instructionPatternHashes,
            dna2.behavioral.instructionPatternHashes
        )
        
        // Compare instruction histogram similarity
        val histogramSimilarity = computeHistogramSimilarity(
            dna1.behavioral.instructionHistograms,
            dna2.behavioral.instructionHistograms
        )
        
        // Calculate method complexity factor to reduce weight of trivial methods
        val complexityFactor = computeComplexityFactor(
            dna1.behavioral.instructionHistograms,
            dna2.behavioral.instructionHistograms
        )
        
        // Weighted: patterns more important than overall histograms
        // Apply complexity factor to reduce impact of simple getters/setters
        val rawSimilarity = (patternJaccard * 0.7) + (histogramSimilarity * 0.3)
        return rawSimilarity * complexityFactor
    }
    
    /**
     * Computes complexity factor based on average method size
     * Reduces similarity weight for trivial methods (getters/setters)
     */
    private fun computeComplexityFactor(
        histograms1: Map<String, Map<Int, Int>>,
        histograms2: Map<String, Map<Int, Int>>
    ): Double {
        if (histograms1.isEmpty() || histograms2.isEmpty()) return 1.0
        
        // Calculate average instructions per method
        val avgSize1 = histograms1.values.map { it.values.sum() }.average()
        val avgSize2 = histograms2.values.map { it.values.sum() }.average()
        val avgSize = (avgSize1 + avgSize2) / 2
        
        // Scale factor: trivial methods (< 5 instructions) get reduced weight
        return when {
            avgSize < 3 -> 0.3   // Very trivial (simple getter/setter)
            avgSize < 5 -> 0.5   // Trivial
            avgSize < 10 -> 0.7  // Simple
            avgSize < 20 -> 0.9  // Moderate
            else -> 1.0          // Complex - full weight
        }
    }
    
    /**
     * Computes similarity between instruction histograms across all methods
     * Uses cosine similarity on the aggregate histogram
     */
    private fun computeHistogramSimilarity(
        histograms1: Map<String, Map<Int, Int>>,
        histograms2: Map<String, Map<Int, Int>>
    ): Double {
        // Empty histograms should not match - prevents false positives
        if (histograms1.isEmpty() && histograms2.isEmpty()) return 0.0  // Changed from 1.0
        if (histograms1.isEmpty() || histograms2.isEmpty()) return 0.0
        
        // Aggregate all histograms into one
        val aggregate1 = histograms1.values.flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { it.value.sum() }
        
        val aggregate2 = histograms2.values.flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { it.value.sum() }
        
        // Compute cosine similarity
        val allOpcodes = aggregate1.keys + aggregate2.keys
        
        var dotProduct = 0.0
        var magnitude1 = 0.0
        var magnitude2 = 0.0
        
        for (opcode in allOpcodes) {
            val count1 = aggregate1[opcode]?.toDouble() ?: 0.0
            val count2 = aggregate2[opcode]?.toDouble() ?: 0.0
            
            dotProduct += count1 * count2
            magnitude1 += count1 * count1
            magnitude2 += count2 * count2
        }
        
        val denominator = kotlin.math.sqrt(magnitude1) * kotlin.math.sqrt(magnitude2)
        val cosineSimilarity = if (denominator > 0) dotProduct / denominator else 0.0
        
        // Apply size disparity penalty to prevent false matches
        // Example: 1 method with 100 instructions vs 10 methods with 10 each
        val size1 = histograms1.size
        val size2 = histograms2.size
        val sizeRatio = kotlin.math.min(size1, size2).toDouble() / kotlin.math.max(size1, size2).toDouble()
        
        // Reduce similarity if method counts differ significantly
        return cosineSimilarity * sizeRatio
    }
    
    /**
     * Computes Jaccard similarity coefficient: |A ∩ B| / |A ∪ B|
     */
    private fun <T> jaccardSimilarity(set1: Set<T>, set2: Set<T>): Double {
        // Empty sets match for structural/API components (classes, methods, etc.)
        if (set1.isEmpty() && set2.isEmpty()) return 1.0  // Restored
        if (set1.isEmpty() || set2.isEmpty()) return 0.0
        
        val intersection = set1.intersect(set2).size.toDouble()
        val union = set1.union(set2).size.toDouble()
        
        return intersection / union
    }
    
    private fun <T> intersectionSize(set1: Set<T>, set2: Set<T>): Int {
        return set1.intersect(set2).size
    }
    
    /**
     * Computes cosine similarity between two package distribution vectors
     */
    private fun computePackageSimilarity(
        packages1: Map<String, Int>,
        packages2: Map<String, Int>
    ): Double {
        if (packages1.isEmpty() && packages2.isEmpty()) return 1.0
        if (packages1.isEmpty() || packages2.isEmpty()) return 0.0
        
        val allPackages = packages1.keys + packages2.keys
        
        var dotProduct = 0.0
        var magnitude1 = 0.0
        var magnitude2 = 0.0
        
        for (pkg in allPackages) {
            val count1 = packages1[pkg]?.toDouble() ?: 0.0
            val count2 = packages2[pkg]?.toDouble() ?: 0.0
            
            dotProduct += count1 * count2
            magnitude1 += count1 * count1
            magnitude2 += count2 * count2
        }
        
        val denominator = kotlin.math.sqrt(magnitude1) * kotlin.math.sqrt(magnitude2)
        return if (denominator > 0) dotProduct / denominator else 0.0
    }
    
    /**
     * Computes the churn (delta) between two versions
     * Returns percentage of changes
     */
    fun computeChurn(oldVersion: CodeDNA, newVersion: CodeDNA): ChurnMetrics {
        val addedClasses = newVersion.structure.classHashes - oldVersion.structure.classHashes
        val removedClasses = oldVersion.structure.classHashes - newVersion.structure.classHashes
        val unchangedClasses = oldVersion.structure.classHashes.intersect(newVersion.structure.classHashes)
        
        val addedMethods = newVersion.apiFootprint.methodSignatureHashes - oldVersion.apiFootprint.methodSignatureHashes
        val removedMethods = oldVersion.apiFootprint.methodSignatureHashes - newVersion.apiFootprint.methodSignatureHashes
        
        val addedApis = newVersion.apiFootprint.externalReferences - oldVersion.apiFootprint.externalReferences
        val removedApis = oldVersion.apiFootprint.externalReferences - newVersion.apiFootprint.externalReferences
        
        val totalOldElements = oldVersion.structure.classHashes.size + 
                               oldVersion.apiFootprint.methodSignatureHashes.size
        
        val totalChanges = addedClasses.size + removedClasses.size + 
                          addedMethods.size + removedMethods.size
        
        val churnPercentage = if (totalOldElements > 0) {
            (totalChanges.toDouble() / totalOldElements.toDouble()) * 100
        } else {
            100.0
        }
        
        return ChurnMetrics(
            churnPercentage = churnPercentage,
            addedClasses = addedClasses.size,
            removedClasses = removedClasses.size,
            unchangedClasses = unchangedClasses.size,
            addedMethods = addedMethods.size,
            removedMethods = removedMethods.size,
            addedApiReferences = addedApis.size,
            removedApiReferences = removedApis.size
        )
    }
}

data class ChurnMetrics(
    val churnPercentage: Double,
    val addedClasses: Int,
    val removedClasses: Int,
    val unchangedClasses: Int,
    val addedMethods: Int,
    val removedMethods: Int,
    val addedApiReferences: Int,
    val removedApiReferences: Int
)
