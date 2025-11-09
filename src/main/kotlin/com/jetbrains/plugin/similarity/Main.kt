package com.jetbrains.plugin.similarity

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.jetbrains.plugin.similarity.analyzer.ArtifactParser
import com.jetbrains.plugin.similarity.analyzer.SimilarityCalculator
import com.jetbrains.plugin.similarity.model.CodeDNA
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

class PluginSimilarityCli : CliktCommand(
    name = "plugin-similarity",
    help = """
        Plugin Similarity Tool - Generate Code DNA fingerprints and compare plugin artifacts
        
        This tool analyzes plugin artifacts (ZIP/JAR files) to generate non-reversible
        fingerprints based on bytecode structure, class hierarchies, and API usage.
    """.trimIndent()
) {
    override fun run() = Unit
}

class FingerprintCommand : CliktCommand(
    name = "fingerprint",
    help = "Generate a Code DNA fingerprint from a plugin artifact"
) {
    private val input by argument(
        name = "INPUT",
        help = "Path to plugin artifact (ZIP or JAR file)"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    private val output by argument(
        name = "OUTPUT",
        help = "Path to output JSON file"
    ).file(canBeDir = false)
    
    private val pretty by option(
        "--pretty",
        help = "Pretty-print the JSON output"
    ).flag(default = false)
    
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun run() {
        echo("Analyzing artifact: ${input.name}")
        
        try {
            val parser = ArtifactParser()
            val codeDNA = parser.parseArtifact(input)
            
            val json = if (pretty) {
                Json { 
                    prettyPrint = true
                    prettyPrintIndent = "  "
                }
            } else {
                Json
            }
            
            val jsonContent = json.encodeToString(codeDNA)
            output.writeText(jsonContent)
            
            echo("✓ Fingerprint generated successfully")
            echo("  Classes: ${codeDNA.metadata.totalClasses}")
            echo("  Methods: ${codeDNA.metadata.totalMethods}")
            echo("  Fields: ${codeDNA.metadata.totalFields}")
            echo("  Hash: ${codeDNA.hash}")
            echo("  Output: ${output.absolutePath}")
            
        } catch (e: Exception) {
            echo("✗ Error: ${e.message}", err = true)
            exitProcess(1)
        }
    }
}

class CompareCommand : CliktCommand(
    name = "compare",
    help = "Compare two Code DNA fingerprints for similarity"
) {
    private val file1 by argument(
        name = "FILE1",
        help = "Path to first fingerprint JSON file"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    private val file2 by argument(
        name = "FILE2",
        help = "Path to second fingerprint JSON file"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    private val verbose by option(
        "-v", "--verbose",
        help = "Show detailed comparison metrics"
    ).flag(default = false)
    
    override fun run() {
        try {
            val json = Json
            val dna1 = json.decodeFromString<CodeDNA>(file1.readText())
            val dna2 = json.decodeFromString<CodeDNA>(file2.readText())
            
            echo("Comparing fingerprints:")
            echo("  [1] ${dna1.metadata.artifactName}")
            echo("  [2] ${dna2.metadata.artifactName}")
            echo()
            
            val calculator = SimilarityCalculator()
            val similarity = calculator.computeSimilarity(dna1, dna2)
            
            // Display similarity scores
            echo("Similarity Scores:")
            echo("  Overall:    ${formatPercentage(similarity.overall)}")
            echo("  Structural: ${formatPercentage(similarity.structural)}")
            echo("  API:        ${formatPercentage(similarity.api)}")
            echo()
            
            if (verbose) {
                displayDetailedMetrics(similarity.details)
            }
            
            // Provide interpretation
            echo("Interpretation:")
            echo("  " + when {
                similarity.overall >= 0.9 -> "Extremely similar - likely same plugin or minor variations"
                similarity.overall >= 0.7 -> "Highly similar - possibly related plugins or different versions"
                similarity.overall >= 0.5 -> "Moderately similar - shared patterns or similar functionality"
                similarity.overall >= 0.3 -> "Somewhat similar - some common elements"
                else -> "Low similarity - likely different plugins"
            })
            
        } catch (e: Exception) {
            echo("✗ Error: ${e.message}", err = true)
            exitProcess(1)
        }
    }
    
    private fun displayDetailedMetrics(details: com.jetbrains.plugin.similarity.model.SimilarityDetails) {
        echo("Detailed Metrics:")
        echo("  Classes:")
        echo("    Common: ${details.commonClasses}")
        echo("    Total in [1]: ${details.totalClassesA}")
        echo("    Total in [2]: ${details.totalClassesB}")
        echo()
        echo("  API References:")
        echo("    Common: ${details.commonApiReferences}")
        echo("    Total in [1]: ${details.totalApiReferencesA}")
        echo("    Total in [2]: ${details.totalApiReferencesB}")
        echo()
        echo("  Method Signatures:")
        echo("    Common: ${details.commonMethodSignatures}")
        echo("    Total in [1]: ${details.totalMethodSignaturesA}")
        echo("    Total in [2]: ${details.totalMethodSignaturesB}")
        echo()
    }
    
    private fun formatPercentage(value: Double): String {
        return "%.2f%%".format(value * 100)
    }
}

class ChurnCommand : CliktCommand(
    name = "churn",
    help = "Compute churn (changes) between two versions of a plugin"
) {
    private val oldVersion by argument(
        name = "OLD",
        help = "Path to old version fingerprint JSON file"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    private val newVersion by argument(
        name = "NEW",
        help = "Path to new version fingerprint JSON file"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    override fun run() {
        try {
            val json = Json
            val oldDNA = json.decodeFromString<CodeDNA>(oldVersion.readText())
            val newDNA = json.decodeFromString<CodeDNA>(newVersion.readText())
            
            echo("Computing churn between versions:")
            echo("  Old: ${oldDNA.metadata.artifactName} (${oldDNA.metadata.version ?: "unknown"})")
            echo("  New: ${newDNA.metadata.artifactName} (${newDNA.metadata.version ?: "unknown"})")
            echo()
            
            val calculator = SimilarityCalculator()
            val churn = calculator.computeChurn(oldDNA, newDNA)
            
            echo("Churn Metrics:")
            echo("  Overall Churn: %.2f%%".format(churn.churnPercentage))
            echo()
            echo("  Classes:")
            echo("    Added: ${churn.addedClasses}")
            echo("    Removed: ${churn.removedClasses}")
            echo("    Unchanged: ${churn.unchangedClasses}")
            echo()
            echo("  Methods:")
            echo("    Added: ${churn.addedMethods}")
            echo("    Removed: ${churn.removedMethods}")
            echo()
            echo("  API References:")
            echo("    Added: ${churn.addedApiReferences}")
            echo("    Removed: ${churn.removedApiReferences}")
            echo()
            
            echo("Assessment:")
            echo("  " + when {
                churn.churnPercentage < 5 -> "Minimal changes - patch update"
                churn.churnPercentage < 20 -> "Moderate changes - minor update"
                churn.churnPercentage < 50 -> "Significant changes - major update"
                else -> "Extensive changes - major refactoring or new version"
            })
            
        } catch (e: Exception) {
            echo("✗ Error: ${e.message}", err = true)
            exitProcess(1)
        }
    }
}

fun main(args: Array<String>) = PluginSimilarityCli()
    .subcommands(
        FingerprintCommand(),
        CompareCommand(),
        ChurnCommand()
    )
    .main(args)
