package com.jetbrains.plugin.similarity.analyzer

import org.objectweb.asm.*
import java.security.MessageDigest

/**
 * Analyzes bytecode to extract structural information for Code DNA generation
 */
class BytecodeAnalyzer {
    
    data class ClassInfo(
        val className: String,
        val superClass: String?,
        val interfaces: List<String>,
        val methods: List<MethodInfo>,
        val fields: List<FieldInfo>,
        val annotations: List<String>,
        val externalReferences: Set<String>
    )
    
    data class MethodInfo(
        val name: String,
        val descriptor: String,
        val signature: String,
        val access: Int,
        val instructionPattern: String? = null,
        val instructionHistogram: Map<Int, Int>? = null
    )
    
    data class FieldInfo(
        val name: String,
        val descriptor: String,
        val access: Int
    )
    
    fun analyzeClass(bytecode: ByteArray): ClassInfo {
        val reader = ClassReader(bytecode)
        val visitor = ClassAnalyzerVisitor()
        reader.accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return visitor.toClassInfo()
    }
    
    private class ClassAnalyzerVisitor : ClassVisitor(Opcodes.ASM9) {
        private var className: String = ""
        private var superClass: String? = null
        private val interfaces = mutableListOf<String>()
        private val methods = mutableListOf<MethodInfo>()
        private val fields = mutableListOf<FieldInfo>()
        private val annotations = mutableListOf<String>()
        private val externalReferences = mutableSetOf<String>()
        private val pendingMethodVisitors = mutableListOf<Pair<MethodInfo, MethodAnalyzerVisitor>>()
        
        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?
        ) {
            this.className = name
            this.superClass = superName
            if (interfaces != null) {
                this.interfaces.addAll(interfaces)
            }
            
            // Track external references
            superName?.let { if (isExternalReference(it)) externalReferences.add(it) }
            interfaces?.forEach { if (isExternalReference(it)) externalReferences.add(it) }
        }
        
        override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
            annotations.add(descriptor)
            if (isExternalReference(descriptor)) {
                externalReferences.add(descriptor)
            }
            return null
        }
        
        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            val methodSig = "$name$descriptor"
            val methodInfo = MethodInfo(name, descriptor, methodSig, access)
            
            // Extract type references from method descriptor
            extractTypesFromDescriptor(descriptor).forEach {
                if (isExternalReference(it)) externalReferences.add(it)
            }
            
            val visitor = MethodAnalyzerVisitor(externalReferences)
            pendingMethodVisitors.add(methodInfo to visitor)
            return visitor
        }
        
        override fun visitField(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            value: Any?
        ): FieldVisitor? {
            fields.add(FieldInfo(name, descriptor, access))
            
            // Extract type reference from field descriptor
            extractTypesFromDescriptor(descriptor).forEach {
                if (isExternalReference(it)) externalReferences.add(it)
            }
            
            return null
        }
        
        fun toClassInfo(): ClassInfo {
            // Finalize method info with instruction patterns
            val finalizedMethods = pendingMethodVisitors.map { (methodInfo, visitor) ->
                methodInfo.copy(
                    instructionPattern = visitor.getInstructionPattern(),
                    instructionHistogram = visitor.getInstructionHistogram()
                )
            }
            
            return ClassInfo(
                className = className,
                superClass = superClass,
                interfaces = interfaces,
                methods = finalizedMethods,
                fields = fields,
                annotations = annotations,
                externalReferences = externalReferences
            )
        }
    }
    
    private class MethodAnalyzerVisitor(
        private val externalReferences: MutableSet<String>
    ) : MethodVisitor(Opcodes.ASM9) {
        private val instructions = mutableListOf<Int>()
        
        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            instructions.add(opcode)
            if (isExternalReference(owner)) {
                externalReferences.add("$owner.$name$descriptor")
            }
            extractTypesFromDescriptor(descriptor).forEach {
                if (isExternalReference(it)) externalReferences.add(it)
            }
        }
        
        override fun visitFieldInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String
        ) {
            instructions.add(opcode)
            if (isExternalReference(owner)) {
                externalReferences.add("$owner.$name")
            }
        }
        
        override fun visitTypeInsn(opcode: Int, type: String) {
            instructions.add(opcode)
            if (isExternalReference(type)) {
                externalReferences.add(type)
            }
        }
        
        // Capture all instruction opcodes for behavioral analysis
        override fun visitInsn(opcode: Int) {
            instructions.add(opcode)
        }
        
        override fun visitIntInsn(opcode: Int, operand: Int) {
            instructions.add(opcode)
        }
        
        override fun visitVarInsn(opcode: Int, varIndex: Int) {
            instructions.add(opcode)
        }
        
        override fun visitJumpInsn(opcode: Int, label: Label) {
            instructions.add(opcode)
        }
        
        override fun visitLdcInsn(value: Any?) {
            instructions.add(Opcodes.LDC)
        }
        
        override fun visitIincInsn(varIndex: Int, increment: Int) {
            instructions.add(Opcodes.IINC)
        }
        
        override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
            instructions.add(Opcodes.TABLESWITCH)
        }
        
        override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
            instructions.add(Opcodes.LOOKUPSWITCH)
        }
        
        override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
            instructions.add(Opcodes.MULTIANEWARRAY)
        }
        
        /**
         * Generate a hash of instruction n-grams (3-grams) to represent method behavior pattern
         */
        fun getInstructionPattern(): String? {
            if (instructions.size < 3) return null
            
            // Create 3-grams of opcodes and hash them
            val trigrams = instructions.windowed(3, 1)
                .map { it.joinToString("-") }
                .joinToString(",")
            
            return hashString(trigrams)
        }
        
        /**
         * Generate histogram of instruction opcodes to represent overall method complexity
         */
        fun getInstructionHistogram(): Map<Int, Int>? {
            if (instructions.isEmpty()) return null
            return instructions.groupingBy { it }.eachCount()
        }
    }
    
    companion object {
        private fun isExternalReference(typeRef: String): Boolean {
            // Filter out standard JDK classes to focus on plugin-specific APIs
            return !typeRef.startsWith("java/") && 
                   !typeRef.startsWith("javax/") &&
                   !typeRef.startsWith("kotlin/")
        }
        
        private fun extractTypesFromDescriptor(descriptor: String): List<String> {
            val types = mutableListOf<String>()
            var i = 0
            while (i < descriptor.length) {
                when (descriptor[i]) {
                    'L' -> {
                        val end = descriptor.indexOf(';', i)
                        if (end != -1) {
                            types.add(descriptor.substring(i + 1, end))
                            i = end
                        }
                    }
                }
                i++
            }
            return types
        }
        
        fun hashString(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
