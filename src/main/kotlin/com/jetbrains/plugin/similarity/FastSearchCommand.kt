package com.jetbrains.plugin.similarity

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.jetbrains.plugin.similarity.analyzer.SimilarityCalculator
import com.jetbrains.plugin.similarity.index.LSHIndex
import com.jetbrains.plugin.similarity.model.CodeDNA
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

class BuildIndexCommand : CliktCommand(
    name = "build-index",
    help = "Build LSH index from a directory of fingerprint JSON files for fast similarity search"
) {
    private val directory by argument(
        name = "DIRECTORY",
        help = "Directory containing fingerprint JSON files"
    ).file(mustExist = true, canBeFile = false, mustBeReadable = true)
    
    private val output by argument(
        name = "OUTPUT",
        help = "Path to save the index (not implemented yet - index stays in memory)"
    )
    
    private val numHashes by option(
        "--num-hashes",
        help = "Number of MinHash functions (default: 128)"
    ).int().default(128)
    
    private val numBands by option(
        "--num-bands",
        help = "Number of LSH bands (default: 16)"
    ).int().default(16)
    
    override fun run() {
        echo("Building LSH index from fingerprints in: ${directory.absolutePath}")
        echo("Parameters: $numHashes hashes, $numBands bands")
        echo()
        
        try {
            val json = Json { ignoreUnknownKeys = true }
            val lshIndex = LSHIndex(numHashes = numHashes, numBands = numBands)
            
            var count = 0
            val buildTime = measureTimeMillis {
                directory.listFiles { file -> file.extension == "json" }?.forEach { file ->
                    try {
                        val codeDNA = json.decodeFromString<CodeDNA>(file.readText())
                        lshIndex.add(codeDNA)
                        count++
                        if (count % 100 == 0) {
                            echo("Indexed $count plugins...")
                        }
                    } catch (e: Exception) {
                        echo("Warning: Failed to load ${file.name}: ${e.message}")
                    }
                }
            }
            
            echo()
            echo("✓ Index built successfully in ${buildTime}ms")
            echo()
            echo(lshIndex.stats().toString())
            echo()
            echo("Note: Index persistence not yet implemented. Use 'search' command immediately.")
            
        } catch (e: Exception) {
            echo("✗ Error: ${e.message}", err = true)
            exitProcess(1)
        }
    }
}

class SearchCommand : CliktCommand(
    name = "search",
    help = "Fast similarity search using LSH index"
) {
    private val query by argument(
        name = "QUERY",
        help = "Path to query fingerprint JSON file"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    private val database by argument(
        name = "DATABASE",
        help = "Directory containing database fingerprint JSON files"
    ).file(mustExist = true, canBeFile = false, mustBeReadable = true)
    
    private val threshold by option(
        "-t", "--threshold",
        help = "Minimum similarity threshold (default: 0.7)"
    ).double().default(0.7)
    
    private val topK by option(
        "-k", "--top-k",
        help = "Return top K results (default: 10)"
    ).int().default(10)
    
    private val exact by option(
        "--exact",
        help = "Compute exact Jaccard for candidates (slower but accurate)"
    ).flag(default = false)
    
    private val verbose by option(
        "-v", "--verbose",
        help = "Show detailed timing and statistics"
    ).flag(default = false)
    
    override fun run() {
        try {
            val json = Json { ignoreUnknownKeys = true }
            
            // Load query
            if (verbose) echo("Loading query fingerprint...")
            val queryDNA = json.decodeFromString<CodeDNA>(query.readText())
            echo("Query: ${queryDNA.metadata.artifactName}")
            echo()
            
            // Build index
            if (verbose) echo("Building LSH index...")
            val lshIndex = LSHIndex()
            val indexBuildTime = measureTimeMillis {
                database.listFiles { file -> file.extension == "json" }?.forEach { file ->
                    try {
                        val codeDNA = json.decodeFromString<CodeDNA>(file.readText())
                        lshIndex.add(codeDNA)
                    } catch (e: Exception) {
                        if (verbose) echo("Warning: Skipped ${file.name}")
                    }
                }
            }
            
            if (verbose) {
                echo("Index built in ${indexBuildTime}ms")
                echo(lshIndex.stats().toString())
                echo()
            }
            
            // Search
            echo("Searching for similar plugins...")
            val candidates: Set<String>
            val searchTime = measureTimeMillis {
                candidates = lshIndex.findCandidates(queryDNA, minBandMatches = 1)
            }
            
            if (verbose) {
                echo("Found ${candidates.size} candidates in ${searchTime}ms")
                echo()
            }
            
            // Score candidates
            val results = if (exact) {
                // Exact scoring (load full CodeDNA and compute Jaccard)
                echo("Computing exact similarities...")
                val calculator = SimilarityCalculator()
                val dnaCache = mutableMapOf<String, CodeDNA>()
                
                // Pre-load all DNAs
                database.listFiles { file -> file.extension == "json" }?.forEach { file ->
                    try {
                        val codeDNA = json.decodeFromString<CodeDNA>(file.readText())
                        dnaCache[codeDNA.hash] = codeDNA
                    } catch (e: Exception) {
                        // Skip
                    }
                }
                
                candidates.mapNotNull { pluginId ->
                    dnaCache[pluginId]?.let { candidateDNA ->
                        val similarity = calculator.computeSimilarity(queryDNA, candidateDNA)
                        Triple(candidateDNA.metadata.artifactName, pluginId, similarity.overall)
                    }
                }
            } else {
                // Fast estimation using MinHash
                candidates.mapNotNull { pluginId ->
                    lshIndex.estimateSimilarity(pluginId, queryDNA)?.let {
                        Triple("Unknown", pluginId, it.overall)
                    }
                }
            }
            
            // Filter and sort
            val filtered = results
                .filter { it.third >= threshold }
                .sortedByDescending { it.third }
                .take(topK)
            
            // Display results
            echo("Top ${filtered.size} similar plugins (threshold: ${threshold * 100}%):")
            echo("─".repeat(80))
            filtered.forEachIndexed { index, (name, id, score) ->
                echo("${index + 1}. ${name.padEnd(40)} ${formatPercentage(score)}")
                if (verbose) echo("   ID: $id")
            }
            
            if (filtered.isEmpty()) {
                echo("No similar plugins found above threshold.")
            }
            
        } catch (e: Exception) {
            echo("✗ Error: ${e.message}", err = true)
            e.printStackTrace()
            exitProcess(1)
        }
    }
    
    private fun formatPercentage(value: Double): String {
        return "%.2f%%".format(value * 100)
    }
}
