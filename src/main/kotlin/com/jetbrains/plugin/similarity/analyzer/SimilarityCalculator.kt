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
        
        // Weighted average: 60% structural, 40% API
        val overallSimilarity = (structuralSimilarity * 0.6) + (apiSimilarity * 0.4)
        
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
     * Computes Jaccard similarity coefficient: |A ∩ B| / |A ∪ B|
     */
    private fun <T> jaccardSimilarity(set1: Set<T>, set2: Set<T>): Double {
        if (set1.isEmpty() && set2.isEmpty()) return 1.0
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
