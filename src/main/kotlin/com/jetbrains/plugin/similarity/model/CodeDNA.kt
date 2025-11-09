package com.jetbrains.plugin.similarity.model

import kotlinx.serialization.Serializable

/**
 * Represents the Code DNA fingerprint of a plugin artifact.
 * Contains hashed, non-reversible structural information.
 */
@Serializable
data class CodeDNA(
    val metadata: PluginMetadata,
    val structure: StructureFingerprint,
    val apiFootprint: ApiFootprint,
    val hash: String
)

@Serializable
data class PluginMetadata(
    val artifactName: String,
    val version: String? = null,
    val timestamp: Long,
    val totalClasses: Int,
    val totalMethods: Int,
    val totalFields: Int
)

@Serializable
data class StructureFingerprint(
    val classHashes: Set<String>,
    val packageStructure: Map<String, Int>,
    val inheritanceHashes: Set<String>,
    val interfaceHashes: Set<String>
)

@Serializable
data class ApiFootprint(
    val externalReferences: Set<String>,
    val methodSignatureHashes: Set<String>,
    val annotationHashes: Set<String>
)

/**
 * Represents the similarity score between two Code DNA fingerprints
 */
data class SimilarityScore(
    val overall: Double,
    val structural: Double,
    val api: Double,
    val details: SimilarityDetails
)

data class SimilarityDetails(
    val commonClasses: Int,
    val totalClassesA: Int,
    val totalClassesB: Int,
    val commonApiReferences: Int,
    val totalApiReferencesA: Int,
    val totalApiReferencesB: Int,
    val commonMethodSignatures: Int,
    val totalMethodSignaturesA: Int,
    val totalMethodSignaturesB: Int
)
