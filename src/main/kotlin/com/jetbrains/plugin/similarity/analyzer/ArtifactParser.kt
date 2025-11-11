package com.jetbrains.plugin.similarity.analyzer

import com.jetbrains.plugin.similarity.model.*
import java.io.File
import java.util.zip.ZipFile

/**
 * Parses plugin artifacts (ZIP/JAR) and generates Code DNA fingerprints
 */
class ArtifactParser {
    
    private val bytecodeAnalyzer = BytecodeAnalyzer()
    
    fun parseArtifact(artifactFile: File): CodeDNA {
        require(artifactFile.exists()) { "Artifact file does not exist: ${artifactFile.absolutePath}" }
        require(artifactFile.extension in listOf("zip", "jar")) { 
            "Unsupported file type: ${artifactFile.extension}. Only ZIP and JAR files are supported." 
        }
        
        val classes = mutableListOf<BytecodeAnalyzer.ClassInfo>()
        
        ZipFile(artifactFile).use { zipFile ->
            zipFile.entries().asSequence()
                .filter { entry -> !entry.isDirectory && entry.name.endsWith(".class") }
                .forEach { entry ->
                    try {
                        val bytecode = zipFile.getInputStream(entry).readBytes()
                        val classInfo = bytecodeAnalyzer.analyzeClass(bytecode)
                        classes.add(classInfo)
                    } catch (e: Exception) {
                        // Skip problematic class files
                        println("Warning: Failed to analyze ${entry.name}: ${e.message}")
                    }
                }
        }
        
        return generateCodeDNA(artifactFile.name, classes)
    }
    
    private fun generateCodeDNA(
        artifactName: String,
        classes: List<BytecodeAnalyzer.ClassInfo>
    ): CodeDNA {
        // Generate hashed class identifiers
        val classHashes = classes.map { classInfo ->
            val classStructure = buildString {
                append(classInfo.className)
                append("|")
                append(classInfo.superClass ?: "")
                append("|")
                append(classInfo.interfaces.sorted().joinToString(","))
            }
            BytecodeAnalyzer.hashString(classStructure)
        }.toSet()
        
        // Generate package structure map
        val packageStructure = classes
            .map { it.className.substringBeforeLast('/', "") }
            .groupingBy { it }
            .eachCount()
        
        // Generate inheritance hashes
        val inheritanceHashes = classes.mapNotNull { classInfo ->
            classInfo.superClass?.let {
                BytecodeAnalyzer.hashString("${classInfo.className}:extends:$it")
            }
        }.toSet()
        
        // Generate interface implementation hashes
        val interfaceHashes = classes.flatMap { classInfo ->
            classInfo.interfaces.map { iface ->
                BytecodeAnalyzer.hashString("${classInfo.className}:implements:$iface")
            }
        }.toSet()
        
        // Collect all external API references
        val externalReferences = classes
            .flatMap { it.externalReferences }
            .map { BytecodeAnalyzer.hashString(it) }
            .toSet()
        
        // Generate method signature hashes
        val methodSignatureHashes = classes.flatMap { classInfo ->
            classInfo.methods.map { method ->
                BytecodeAnalyzer.hashString("${classInfo.className}.${method.signature}")
            }
        }.toSet()
        
        // Generate annotation hashes
        val annotationHashes = classes
            .flatMap { it.annotations }
            .map { BytecodeAnalyzer.hashString(it) }
            .toSet()
        
        // Generate behavioral fingerprint from instruction patterns
        val instructionPatternHashes = classes.flatMap { classInfo ->
            classInfo.methods.mapNotNull { method -> method.instructionPattern }
        }.toSet()
        
        // Collect instruction histograms keyed by method signature hash
        val instructionHistograms = classes.flatMap { classInfo ->
            classInfo.methods.mapNotNull { method ->
                method.instructionHistogram?.let { histogram ->
                    val methodKey = BytecodeAnalyzer.hashString("${classInfo.className}.${method.signature}")
                    methodKey to histogram
                }
            }
        }.toMap()
        
        val totalMethods = classes.sumOf { it.methods.size }
        val totalFields = classes.sumOf { it.fields.size }
        
        val metadata = PluginMetadata(
            artifactName = artifactName,
            version = extractVersion(artifactName),
            timestamp = System.currentTimeMillis(),
            totalClasses = classes.size,
            totalMethods = totalMethods,
            totalFields = totalFields
        )
        
        val structure = StructureFingerprint(
            classHashes = classHashes,
            packageStructure = packageStructure,
            inheritanceHashes = inheritanceHashes,
            interfaceHashes = interfaceHashes
        )
        
        val apiFootprint = ApiFootprint(
            externalReferences = externalReferences,
            methodSignatureHashes = methodSignatureHashes,
            annotationHashes = annotationHashes
        )
        
        val behavioral = BehavioralFingerprint(
            instructionPatternHashes = instructionPatternHashes,
            instructionHistograms = instructionHistograms
        )
        
        // Generate overall hash
        val overallHash = generateOverallHash(structure, apiFootprint, behavioral)
        
        return CodeDNA(
            metadata = metadata,
            structure = structure,
            apiFootprint = apiFootprint,
            behavioral = behavioral,
            hash = overallHash
        )
    }
    
    private fun extractVersion(artifactName: String): String? {
        // Try to extract version from artifact name (e.g., plugin-1.2.3.jar -> 1.2.3)
        val versionRegex = """(\d+\.\d+(?:\.\d+)?)""".toRegex()
        return versionRegex.find(artifactName)?.value
    }
    
    private fun generateOverallHash(
        structure: StructureFingerprint,
        apiFootprint: ApiFootprint,
        behavioral: BehavioralFingerprint
    ): String {
        val combinedData = buildString {
            append(structure.classHashes.sorted().joinToString(","))
            append("|")
            append(structure.inheritanceHashes.sorted().joinToString(","))
            append("|")
            append(apiFootprint.externalReferences.sorted().joinToString(","))
            append("|")
            append(apiFootprint.methodSignatureHashes.sorted().joinToString(","))
            append("|")
            append(behavioral.instructionPatternHashes.sorted().joinToString(","))
        }
        return BytecodeAnalyzer.hashString(combinedData)
    }
}
