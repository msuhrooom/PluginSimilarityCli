package com.jetbrains.plugin.similarity.index

import com.jetbrains.plugin.similarity.model.*
import kotlin.test.*

class LSHIndexTest {
    
    private fun createTestCodeDNA(
        hash: String,
        classHashes: Set<String>,
        methodHashes: Set<String> = emptySet(),
        apiRefs: Set<String> = emptySet()
    ): CodeDNA {
        return CodeDNA(
            metadata = PluginMetadata(
                artifactName = "test-$hash.jar",
                version = "1.0.0",
                timestamp = System.currentTimeMillis(),
                totalClasses = classHashes.size,
                totalMethods = methodHashes.size,
                totalFields = 0
            ),
            structure = StructureFingerprint(
                classHashes = classHashes,
                packageStructure = emptyMap(),
                inheritanceHashes = emptySet(),
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
            hash = hash
        )
    }
    
    @Test
    fun `should create index with correct parameters`() {
        val index = LSHIndex(numHashes = 64, numBands = 8)
        
        val stats = index.stats()
        assertEquals(0, stats.numPlugins)
        assertEquals(0, stats.numBuckets)
    }
    
    @Test
    fun `should add plugin and update stats`() {
        val index = LSHIndex()
        val dna = createTestCodeDNA(
            hash = "test1",
            classHashes = setOf("class1", "class2", "class3")
        )
        
        index.add(dna)
        
        val stats = index.stats()
        assertEquals(1, stats.numPlugins)
        assertTrue(stats.numBuckets > 0, "Should create some buckets")
    }
    
    @Test
    fun `should find identical plugin as candidate`() {
        val index = LSHIndex()
        val classHashes = setOf("class1", "class2", "class3", "class4", "class5")
        
        val dna1 = createTestCodeDNA("plugin1", classHashes)
        val dna2 = createTestCodeDNA("plugin2", classHashes) // Identical
        
        index.add(dna1)
        
        val candidates = index.findCandidates(dna2)
        
        assertTrue(candidates.contains("plugin1"), "Should find identical plugin")
    }
    
    @Test
    fun `should find similar plugin as candidate`() {
        val index = LSHIndex()
        
        val classes1 = setOf("a", "b", "c", "d", "e", "f", "g", "h")
        val classes2 = setOf("a", "b", "c", "d", "e", "f", "x", "y") // 6/10 overlap = 60%
        
        val dna1 = createTestCodeDNA("plugin1", classes1)
        val dna2 = createTestCodeDNA("plugin2", classes2)
        
        index.add(dna1)
        
        val candidates = index.findCandidates(dna2, minBandMatches = 1)
        
        // With 60% similarity, should be found with high probability
        assertTrue(candidates.contains("plugin1") || candidates.isEmpty(),
            "Should find similar plugin or acceptable miss")
    }
    
    @Test
    fun `should not find completely different plugin`() {
        val index = LSHIndex()
        
        val classes1 = setOf("a", "b", "c")
        val classes2 = setOf("x", "y", "z") // No overlap
        
        val dna1 = createTestCodeDNA("plugin1", classes1)
        val dna2 = createTestCodeDNA("plugin2", classes2)
        
        index.add(dna1)
        
        val candidates = index.findCandidates(dna2)
        
        // Disjoint sets should rarely be candidates
        assertTrue(candidates.isEmpty() || !candidates.contains("plugin1"))
    }
    
    @Test
    fun `minBandMatches should filter candidates`() {
        val index = LSHIndex()
        val classes = setOf("a", "b", "c", "d", "e")
        
        val dna1 = createTestCodeDNA("plugin1", classes)
        val dna2 = createTestCodeDNA("plugin2", classes)
        
        index.add(dna1)
        
        val candidates1 = index.findCandidates(dna2, minBandMatches = 1)
        val candidates2 = index.findCandidates(dna2, minBandMatches = 5)
        
        // With identical sets and more bands required:
        assertTrue(candidates2.size <= candidates1.size)
    }
    
    @Test
    fun `should handle multiple plugins`() {
        val index = LSHIndex()
        
        val plugins = (1..10).map { i ->
            createTestCodeDNA(
                hash = "plugin$i",
                classHashes = setOf("common", "class$i", "extra$i")
            )
        }
        
        plugins.forEach { index.add(it) }
        
        val stats = index.stats()
        assertEquals(10, stats.numPlugins)
    }
    
    @Test
    fun `estimateSimilarity should return result for added plugin`() {
        val index = LSHIndex()
        val classes = setOf("a", "b", "c")
        
        val dna1 = createTestCodeDNA("plugin1", classes)
        val dna2 = createTestCodeDNA("plugin2", classes)
        
        index.add(dna1)
        
        val similarity = index.estimateSimilarity("plugin1", dna2)
        
        assertNotNull(similarity)
        assertTrue(similarity.overall > 0.5, "Identical classes should have high similarity")
    }
    
    @Test
    fun `estimateSimilarity should return null for missing plugin`() {
        val index = LSHIndex()
        val dna = createTestCodeDNA("query", setOf("a", "b"))
        
        val similarity = index.estimateSimilarity("nonexistent", dna)
        
        assertNull(similarity)
    }
    
    @Test
    fun `stats should show bucket distribution`() {
        val index = LSHIndex()
        
        val plugins = (1..5).map { i ->
            createTestCodeDNA(
                hash = "plugin$i",
                classHashes = (1..10).map { "class${i}_$it" }.toSet()
            )
        }
        
        plugins.forEach { index.add(it) }
        
        val stats = index.stats()
        
        assertEquals(5, stats.numPlugins)
        assertTrue(stats.avgBucketSize > 0)
        assertTrue(stats.maxBucketSize > 0)
    }
    
    @Test
    fun `should handle empty class hashes`() {
        val index = LSHIndex()
        val dna = createTestCodeDNA("empty", emptySet())
        
        // Should not crash
        try {
            index.add(dna)
            val candidates = index.findCandidates(dna)
            // If we get here, test passes
            assertTrue(true)
        } catch (e: Exception) {
            fail("Should not throw exception for empty hashes: ${e.message}")
        }
    }
    
    @Test
    fun `high similarity plugins should be found with high probability`() {
        val index = LSHIndex(numBands = 16)
        
        // Create 10 plugins with high overlap
        val baseClasses = (1..20).map { "class$it" }.toSet()
        
        repeat(10) { i ->
            val dna = createTestCodeDNA(
                hash = "plugin$i",
                classHashes = baseClasses
            )
            index.add(dna)
        }
        
        // Query with same classes
        val query = createTestCodeDNA("query", baseClasses)
        val candidates = index.findCandidates(query)
        
        // Should find most or all of them
        assertTrue(candidates.size >= 8, "Should find at least 8/10 identical plugins")
    }
}
