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
            behavioral = BehavioralFingerprint(
                instructionPatternHashes = emptySet(),
                instructionHistograms = emptyMap()
            ),
            hash = "test-hash"
        )
    }
    
    @Test
    fun `identical plugins should have high similarity with neutral behavioral`() {
        val classes = setOf("A", "B", "C")
        val methods = setOf("m1", "m2", "m3")
        val apis = setOf("api1", "api2")
        
        val dna1 = createTestCodeDNA(classes, methods, apis)
        val dna2 = createTestCodeDNA(classes, methods, apis)
        
        val result = calculator.computeSimilarity(dna1, dna2)
        
        // With no behavioral data (neutral 0.5), overall = (1.0*0.4) + (1.0*0.3) + (0.5*0.3) = 0.85
        assertEquals(0.85, result.overall, 0.01)
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
        
        // Should be very low (empty behavioral patterns = 100% match but weighted at 30%)
        // With new weights (40% structural, 30% API, 30% behavioral), empty behavioral adds noise
        assertTrue(result.overall < 0.7, "Different plugins should have low similarity")
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
        
        // With behavioral neutral (0.5), expect moderate similarity
        assertTrue(result.overall > 0.0)
        assertTrue(result.overall < 1.0)
        assertTrue(result.overall > 0.3) // Reasonable partial overlap
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
        
        // API similarity metric itself should be perfect
        assertTrue(result.api > 0.9, "API similarity should be high when APIs match")
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
        
        // With new weights: (1.0*0.4) + (0*0.3) + (0.5*0.3) = 0.55
        assertTrue(result.overall > 0.4, "Should have moderate similarity due to structural match")
        assertTrue(result.structural > result.api, "Structural should be higher than API")
    }
}
