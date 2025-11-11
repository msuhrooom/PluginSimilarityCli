package com.jetbrains.plugin.similarity.analyzer

import com.jetbrains.plugin.similarity.model.*
import kotlin.test.*

class SimilarityCalculatorTest {
    
    private val calculator = SimilarityCalculator()
    
    private fun createTestCodeDNA(
        classHashes: Set<String>,
        methodHashes: Set<String> = emptySet(),
        apiRefs: Set<String> = emptySet(),
        inheritanceHashes: Set<String> = emptySet()
    ): CodeDNA {
        return CodeDNA(
            metadata = PluginMetadata(
                artifactName = "test.jar",
                version = "1.0.0",
                timestamp = 0,
                totalClasses = classHashes.size,
                totalMethods = methodHashes.size,
                totalFields = 0
            ),
            structure = StructureFingerprint(
                classHashes = classHashes,
                packageStructure = emptyMap(),
                inheritanceHashes = inheritanceHashes,
                interfaceHashes = emptySet()
            ),
            apiFootprint = ApiFootprint(
                externalReferences = apiRefs,
                methodSignatureHashes = methodHashes,
                annotationHashes = emptySet()
            ),
            hash = "test-hash"
        )
    }
    
    @Test
    fun `identical plugins should have 100 percent similarity`() {
        val classes = setOf("A", "B", "C")
        val methods = setOf("m1", "m2", "m3")
        val apis = setOf("api1", "api2")
        
        val dna1 = createTestCodeDNA(classes, methods, apis)
        val dna2 = createTestCodeDNA(classes, methods, apis)
        
        val result = calculator.computeSimilarity(dna1, dna2)
        
        assertEquals(1.0, result.overall, 0.01)
        assertEquals(1.0, result.structural, 0.01)
        assertEquals(1.0, result.api, 0.01)
    }
    
    @Test
    fun `completely different plugins should have low similarity`() {
        val dna1 = createTestCodeDNA(
            classHashes = setOf("A", "B", "C"),
            methodHashes = setOf("m1", "m2"),
            apiRefs = setOf("api1")
        )
        
        val dna2 = createTestCodeDNA(
            classHashes = setOf("X", "Y", "Z"),
            methodHashes = setOf("m9", "m10"),
            apiRefs = setOf("api9")
        )
        
        val result = calculator.computeSimilarity(dna1, dna2)
        
        // Should be very low (empty package structure adds some noise)
        assertTrue(result.overall < 0.5, "Different plugins should have low similarity")
    }
    
    @Test
    fun `partial overlap should give intermediate similarity`() {
        val dna1 = createTestCodeDNA(
            classHashes = setOf("A", "B", "C", "D"),
            methodHashes = setOf("m1", "m2", "m3", "m4")
        )
        
        val dna2 = createTestCodeDNA(
            classHashes = setOf("A", "B", "E", "F"), // 2/6 class overlap
            methodHashes = setOf("m1", "m2", "m5", "m6") // 2/6 method overlap
        )
        
        val result = calculator.computeSimilarity(dna1, dna2)
        
        // With 1/3 overlap in both classes and methods, expect ~0.7-0.8
        assertTrue(result.overall > 0.0)
        assertTrue(result.overall < 1.0)
        assertTrue(result.overall > 0.5) // Reasonable partial overlap
    }
    
    @Test
    fun `structural similarity should weigh classes more heavily`() {
        // This is implicit in the weighting (40% classes)
        val dna1 = createTestCodeDNA(
            classHashes = setOf("A", "B"),
            inheritanceHashes = setOf("inh1")
        )
        
        val dna2 = createTestCodeDNA(
            classHashes = setOf("A", "B"),
            inheritanceHashes = setOf("inh2") // Different inheritance
        )
        
        val result = calculator.computeSimilarity(dna1, dna2)
        
        // Should still have high similarity due to class overlap
        assertTrue(result.structural > 0.5)
    }
    
    @Test
    fun `similarity details should have correct counts`() {
        val dna1 = createTestCodeDNA(
            classHashes = setOf("A", "B", "C"),
            methodHashes = setOf("m1", "m2"),
            apiRefs = setOf("api1")
        )
        
        val dna2 = createTestCodeDNA(
            classHashes = setOf("A", "B", "D"),
            methodHashes = setOf("m1", "m3"),
            apiRefs = setOf("api1", "api2")
        )
        
        val result = calculator.computeSimilarity(dna1, dna2)
        
        assertEquals(2, result.details.commonClasses) // A, B
        assertEquals(3, result.details.totalClassesA)
        assertEquals(3, result.details.totalClassesB)
        
        assertEquals(1, result.details.commonMethodSignatures) // m1
        assertEquals(2, result.details.totalMethodSignaturesA)
        assertEquals(2, result.details.totalMethodSignaturesB)
        
        assertEquals(1, result.details.commonApiReferences) // api1
        assertEquals(1, result.details.totalApiReferencesA)
        assertEquals(2, result.details.totalApiReferencesB)
    }
    
    @Test
    fun `churn should track added and removed classes`() {
        val old = createTestCodeDNA(
            classHashes = setOf("A", "B", "C"),
            methodHashes = setOf("m1", "m2")
        )
        
        val new = createTestCodeDNA(
            classHashes = setOf("A", "B", "D", "E"), // Removed C, added D, E
            methodHashes = setOf("m1", "m3", "m4") // Removed m2, added m3, m4
        )
        
        val churn = calculator.computeChurn(old, new)
        
        assertEquals(2, churn.addedClasses) // D, E
        assertEquals(1, churn.removedClasses) // C
        assertEquals(2, churn.unchangedClasses) // A, B
        
        assertEquals(2, churn.addedMethods) // m3, m4
        assertEquals(1, churn.removedMethods) // m2
    }
    
    @Test
    fun `churn percentage should be calculated correctly`() {
        val old = createTestCodeDNA(
            classHashes = setOf("A", "B", "C", "D"), // 4 classes
            methodHashes = setOf("m1", "m2", "m3", "m4", "m5", "m6") // 6 methods
        )
        // Total old elements: 4 + 6 = 10
        
        val new = createTestCodeDNA(
            classHashes = setOf("A", "B", "C", "E"), // Changed 1 class
            methodHashes = setOf("m1", "m2", "m3", "m4", "m7") // Changed 2 methods
        )
        // Classes: removed D, added E = 2 changes
        // Methods: removed m5, m6, added m7 = 3 changes
        // Total changes = 5, churn = 5 / 10 = 50%
        
        val churn = calculator.computeChurn(old, new)
        
        assertEquals(50.0, churn.churnPercentage, 0.1)
    }
    
    @Test
    fun `no changes should give 0 percent churn`() {
        val classes = setOf("A", "B", "C")
        val methods = setOf("m1", "m2")
        
        val old = createTestCodeDNA(classes, methods)
        val new = createTestCodeDNA(classes, methods)
        
        val churn = calculator.computeChurn(old, new)
        
        assertEquals(0.0, churn.churnPercentage)
        assertEquals(0, churn.addedClasses)
        assertEquals(0, churn.removedClasses)
    }
    
    @Test
    fun `complete replacement should give high churn`() {
        val old = createTestCodeDNA(
            classHashes = setOf("A", "B"),
            methodHashes = setOf("m1", "m2")
        )
        
        val new = createTestCodeDNA(
            classHashes = setOf("X", "Y"),
            methodHashes = setOf("m9", "m10")
        )
        
        val churn = calculator.computeChurn(old, new)
        
        // Complete replacement should be very high churn
        assertTrue(churn.churnPercentage >= 90.0, "Complete replacement should have very high churn")
    }
    
    @Test
    fun `similarity should handle empty sets`() {
        val empty = createTestCodeDNA(emptySet(), emptySet(), emptySet())
        val nonEmpty = createTestCodeDNA(setOf("A"), setOf("m1"), setOf("api1"))
        
        val result1 = calculator.computeSimilarity(empty, empty)
        val result2 = calculator.computeSimilarity(empty, nonEmpty)
        
        // Both empty should be considered similar
        assertTrue(result1.overall > 0.5)
        
        // One empty vs non-empty should be different
        assertTrue(result2.overall < 0.5)
    }
    
    @Test
    fun `API similarity should weight external references heavily`() {
        val dna1 = createTestCodeDNA(
            classHashes = setOf("A"),
            methodHashes = setOf("m1"),
            apiRefs = setOf("api1", "api2", "api3") // 3 API refs
        )
        
        val dna2 = createTestCodeDNA(
            classHashes = setOf("A"),
            methodHashes = setOf("m1"),
            apiRefs = setOf("api1", "api2", "api3") // Same API refs
        )
        
        val result = calculator.computeSimilarity(dna1, dna2)
        
        // API similarity should be perfect or near perfect
        assertTrue(result.api > 0.9)
    }
    
    @Test
    fun `overall similarity should be weighted correctly`() {
        // Create scenario where structural is high but API is low
        val dna1 = createTestCodeDNA(
            classHashes = setOf("A", "B", "C"),
            methodHashes = emptySet(),
            apiRefs = setOf("api1", "api2")
        )
        
        val dna2 = createTestCodeDNA(
            classHashes = setOf("A", "B", "C"), // 100% structural match
            methodHashes = emptySet(),
            apiRefs = emptySet() // 0% API match
        )
        
        val result = calculator.computeSimilarity(dna1, dna2)
        
        // Overall should favor structural (60%) over API (40%)
        // With 100% structural and 50% API, expect ~0.8
        assertTrue(result.overall > 0.6, "Should have good similarity due to structural match")
        assertTrue(result.structural > result.api, "Structural should be higher than API")
    }
}
