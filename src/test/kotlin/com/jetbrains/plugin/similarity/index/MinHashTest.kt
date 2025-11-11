package com.jetbrains.plugin.similarity.index

import kotlin.test.*

class MinHashTest {
    
    private val minHash = MinHash(numHashes = 128)
    
    @Test
    fun `signature should have correct size`() {
        val set = setOf("hash1", "hash2", "hash3")
        val signature = minHash.signature(set)
        
        assertEquals(128, signature.size)
    }
    
    @Test
    fun `empty set should produce max value signature`() {
        val signature = minHash.signature(emptySet())
        
        assertEquals(128, signature.size)
        assertTrue(signature.all { it == Int.MAX_VALUE })
    }
    
    @Test
    fun `same set should produce same signature`() {
        val set = setOf("apple", "banana", "cherry")
        
        val sig1 = minHash.signature(set)
        val sig2 = minHash.signature(set)
        
        assertContentEquals(sig1, sig2)
    }
    
    @Test
    fun `identical sets should have 100 percent similarity`() {
        val set1 = setOf("a", "b", "c", "d", "e")
        val set2 = setOf("a", "b", "c", "d", "e")
        
        val sig1 = minHash.signature(set1)
        val sig2 = minHash.signature(set2)
        
        val similarity = minHash.estimateSimilarity(sig1, sig2)
        assertEquals(1.0, similarity, 0.01)
    }
    
    @Test
    fun `disjoint sets should have low similarity`() {
        val set1 = setOf("a", "b", "c")
        val set2 = setOf("x", "y", "z")
        
        val sig1 = minHash.signature(set1)
        val sig2 = minHash.signature(set2)
        
        val similarity = minHash.estimateSimilarity(sig1, sig2)
        assertTrue(similarity < 0.1, "Disjoint sets should have very low similarity")
    }
    
    @Test
    fun `partially overlapping sets should have intermediate similarity`() {
        val set1 = setOf("a", "b", "c", "d", "e")
        val set2 = setOf("c", "d", "e", "f", "g")
        
        val sig1 = minHash.signature(set1)
        val sig2 = minHash.signature(set2)
        
        val estimated = minHash.estimateSimilarity(sig1, sig2)
        val exact = minHash.exactJaccard(set1, set2)
        
        // 3 common, 7 total = 0.428 exact Jaccard
        assertEquals(0.428, exact, 0.01)
        
        // Estimated should be within ~10% error
        assertEquals(exact, estimated, 0.15)
    }
    
    @Test
    fun `estimate similarity should match exact jaccard approximately`() {
        val testCases = listOf(
            setOf("1", "2", "3") to setOf("2", "3", "4"),
            setOf("a", "b", "c", "d") to setOf("c", "d", "e", "f"),
            setOf("x") to setOf("x", "y", "z")
        )
        
        testCases.forEach { (set1, set2) ->
            val sig1 = minHash.signature(set1)
            val sig2 = minHash.signature(set2)
            
            val estimated = minHash.estimateSimilarity(sig1, sig2)
            val exact = minHash.exactJaccard(set1, set2)
            
            // Error should be < 20% for 128 hashes
            val error = kotlin.math.abs(estimated - exact)
            assertTrue(error < 0.2, "Error $error too large for sets $set1 and $set2")
        }
    }
    
    @Test
    fun `exact jaccard should handle empty sets`() {
        val empty = emptySet<String>()
        val nonEmpty = setOf("a", "b")
        
        assertEquals(1.0, minHash.exactJaccard(empty, empty))
        assertEquals(0.0, minHash.exactJaccard(empty, nonEmpty))
        assertEquals(0.0, minHash.exactJaccard(nonEmpty, empty))
    }
    
    @Test
    fun `exact jaccard should compute correctly`() {
        val set1 = setOf("a", "b", "c")
        val set2 = setOf("b", "c", "d")
        
        // Intersection: {b, c} = 2
        // Union: {a, b, c, d} = 4
        // Jaccard = 2/4 = 0.5
        val jaccard = minHash.exactJaccard(set1, set2)
        assertEquals(0.5, jaccard, 0.001)
    }
    
    @Test
    fun `different number of hashes should produce different signature sizes`() {
        val minHash64 = MinHash(numHashes = 64)
        val minHash256 = MinHash(numHashes = 256)
        val set = setOf("a", "b", "c")
        
        assertEquals(64, minHash64.signature(set).size)
        assertEquals(256, minHash256.signature(set).size)
    }
    
    @Test
    fun `large sets should work efficiently`() {
        val largeSet = (1..1000).map { "element_$it" }.toSet()
        
        val startTime = System.currentTimeMillis()
        val signature = minHash.signature(largeSet)
        val endTime = System.currentTimeMillis()
        
        assertEquals(128, signature.size)
        assertTrue(endTime - startTime < 100, "Should process 1000 elements in < 100ms")
    }
}
