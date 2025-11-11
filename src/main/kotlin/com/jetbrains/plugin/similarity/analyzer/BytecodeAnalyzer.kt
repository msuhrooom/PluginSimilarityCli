package com.jetbrains.plugin.similarity.analyzer

import org.objectweb.asm.*
import java.security.MessageDigest

/**
 * Analyzes bytecode to extract structural information for Code DNA generation
 */
class BytecodeAnalyzer(private val useFuzzyMode: Boolean = false) {
    
    /**
     * Semantic opcode categories for fuzzy mode
     */
    private enum class SemanticOpcode {
        LOAD, STORE, INVOKE, ARITH, COMPARE, RETURN, FIELD, ARRAY, CONTROL, NEW, CAST, OTHER
    }
    
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
        val visitor = ClassAnalyzerVisitor(useFuzzyMode)
        reader.accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return visitor.toClassInfo()
    }
    
    private class ClassAnalyzerVisitor(private val useFuzzyMode: Boolean) : ClassVisitor(Opcodes.ASM9) {
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
            
            val visitor = MethodAnalyzerVisitor(externalReferences, useFuzzyMode)
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
        private val externalReferences: MutableSet<String>,
        private val useFuzzyMode: Boolean
    ) : MethodVisitor(Opcodes.ASM9) {
        private val instructions = mutableListOf<String>()
        
        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean
        ) {
            instructions.add(normalizeOpcode(opcode))
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
            instructions.add(normalizeOpcode(opcode))
            if (isExternalReference(owner)) {
                externalReferences.add("$owner.$name")
            }
        }
        
        override fun visitTypeInsn(opcode: Int, type: String) {
            instructions.add(normalizeOpcode(opcode))
            if (isExternalReference(type)) {
                externalReferences.add(type)
            }
        }
        
        // Capture all instruction opcodes for behavioral analysis
        override fun visitInsn(opcode: Int) {
            instructions.add(normalizeOpcode(opcode))
        }
        
        override fun visitIntInsn(opcode: Int, operand: Int) {
            instructions.add(normalizeOpcode(opcode))
        }
        
        override fun visitVarInsn(opcode: Int, varIndex: Int) {
            instructions.add(normalizeOpcode(opcode))
        }
        
        override fun visitJumpInsn(opcode: Int, label: Label) {
            instructions.add(normalizeOpcode(opcode))
        }
        
        override fun visitLdcInsn(value: Any?) {
            instructions.add(normalizeOpcode(Opcodes.LDC))
        }
        
        override fun visitIincInsn(varIndex: Int, increment: Int) {
            instructions.add(normalizeOpcode(Opcodes.IINC))
        }
        
        override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
            instructions.add(normalizeOpcode(Opcodes.TABLESWITCH))
        }
        
        override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
            instructions.add(normalizeOpcode(Opcodes.LOOKUPSWITCH))
        }
        
        override fun visitMultiANewArrayInsn(descriptor: String, numDimensions: Int) {
            instructions.add(normalizeOpcode(Opcodes.MULTIANEWARRAY))
        }
        
        /**
         * Normalize an opcode to either its exact value or semantic category
         */
        private fun normalizeOpcode(opcode: Int): String {
            if (!useFuzzyMode) {
                return opcode.toString()
            }
            
            return when (opcode) {
                // All loads → LOAD
                Opcodes.ALOAD, Opcodes.ILOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.LLOAD,
                Opcodes.IALOAD, Opcodes.LALOAD, Opcodes.FALOAD, Opcodes.DALOAD, Opcodes.AALOAD,
                Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD
                -> SemanticOpcode.LOAD.name
                
                // All stores → STORE
                Opcodes.ASTORE, Opcodes.ISTORE, Opcodes.FSTORE, Opcodes.DSTORE, Opcodes.LSTORE,
                Opcodes.IASTORE, Opcodes.LASTORE, Opcodes.FASTORE, Opcodes.DASTORE, Opcodes.AASTORE,
                Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE
                -> SemanticOpcode.STORE.name
                
                // All method invocations → INVOKE
                Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKESTATIC,
                Opcodes.INVOKEINTERFACE, Opcodes.INVOKEDYNAMIC
                -> SemanticOpcode.INVOKE.name
                
                // All arithmetic operations → ARITH
                Opcodes.IADD, Opcodes.ISUB, Opcodes.IMUL, Opcodes.IDIV, Opcodes.IREM,
                Opcodes.LADD, Opcodes.LSUB, Opcodes.LMUL, Opcodes.LDIV, Opcodes.LREM,
                Opcodes.FADD, Opcodes.FSUB, Opcodes.FMUL, Opcodes.FDIV, Opcodes.FREM,
                Opcodes.DADD, Opcodes.DSUB, Opcodes.DMUL, Opcodes.DDIV, Opcodes.DREM,
                Opcodes.INEG, Opcodes.LNEG, Opcodes.FNEG, Opcodes.DNEG,
                Opcodes.ISHL, Opcodes.LSHL, Opcodes.ISHR, Opcodes.LSHR, Opcodes.IUSHR, Opcodes.LUSHR,
                Opcodes.IAND, Opcodes.LAND, Opcodes.IOR, Opcodes.LOR, Opcodes.IXOR, Opcodes.LXOR,
                Opcodes.IINC
                -> SemanticOpcode.ARITH.name
                
                // All comparisons → COMPARE
                Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE,
                Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE,
                Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE, Opcodes.IF_ACMPEQ, Opcodes.IF_ACMPNE,
                Opcodes.LCMP, Opcodes.FCMPL, Opcodes.FCMPG, Opcodes.DCMPL, Opcodes.DCMPG,
                Opcodes.IFNULL, Opcodes.IFNONNULL
                -> SemanticOpcode.COMPARE.name
                
                // All returns → RETURN
                Opcodes.IRETURN, Opcodes.LRETURN, Opcodes.FRETURN, Opcodes.DRETURN,
                Opcodes.ARETURN, Opcodes.RETURN
                -> SemanticOpcode.RETURN.name
                
                // All field access → FIELD
                Opcodes.GETSTATIC, Opcodes.PUTSTATIC, Opcodes.GETFIELD, Opcodes.PUTFIELD
                -> SemanticOpcode.FIELD.name
                
                // Array operations (length, etc.) → ARRAY
                Opcodes.ARRAYLENGTH, Opcodes.NEWARRAY, Opcodes.ANEWARRAY, Opcodes.MULTIANEWARRAY
                -> SemanticOpcode.ARRAY.name
                
                // Control flow → CONTROL
                Opcodes.GOTO, Opcodes.JSR, Opcodes.RET, Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH
                -> SemanticOpcode.CONTROL.name
                
                // Object creation → NEW
                Opcodes.NEW
                -> SemanticOpcode.NEW.name
                
                // Type operations → CAST
                Opcodes.CHECKCAST, Opcodes.INSTANCEOF,
                Opcodes.I2L, Opcodes.I2F, Opcodes.I2D, Opcodes.L2I, Opcodes.L2F, Opcodes.L2D,
                Opcodes.F2I, Opcodes.F2L, Opcodes.F2D, Opcodes.D2I, Opcodes.D2L, Opcodes.D2F,
                Opcodes.I2B, Opcodes.I2C, Opcodes.I2S
                -> SemanticOpcode.CAST.name
                
                // Everything else → OTHER
                else -> SemanticOpcode.OTHER.name
            }
        }
        
        /**
         * Generate a hash of instruction n-grams (3-grams) to represent method behavior pattern
         */
        fun getInstructionPattern(): String? {
            // Return special marker for empty methods to prevent false positives
            if (instructions.isEmpty()) return hashString("EMPTY_METHOD")
            if (instructions.size < 3) return hashString("TRIVIAL_METHOD:${instructions.joinToString("-")}")
            
            // Filter out very common boilerplate patterns
            val filteredInstructions = filterBoilerplate(instructions)
            if (filteredInstructions.size < 3) return hashString("BOILERPLATE_ONLY:${instructions.size}")
            
            // Create 3-grams of opcodes and hash them
            val trigrams = filteredInstructions.windowed(3, 1)
                .map { it.joinToString("-") }
                .joinToString(",")
            
            return hashString(trigrams)
        }
        
        /**
         * Filters out common boilerplate instruction sequences
         * to reduce false positives from standard initialization patterns
         */
        private fun filterBoilerplate(instrs: List<String>): List<String> {
            // Common patterns to filter:
            // - Simple ALOAD, RETURN sequences (getters)
            // - Constructor boilerplate (ALOAD, INVOKESPECIAL, RETURN)
            // Keep only if method has substantive logic
            
            if (instrs.size <= 5) {
                // In fuzzy mode, check semantic categories; otherwise check exact opcodes
                if (useFuzzyMode) {
                    val isSimpleGetter = instrs.containsAll(listOf("LOAD", "FIELD", "RETURN"))
                    val isSimpleSetter = instrs.containsAll(listOf("LOAD", "FIELD", "RETURN"))
                    if (isSimpleGetter || isSimpleSetter) {
                        return emptyList()
                    }
                } else {
                    val aload = Opcodes.ALOAD.toString()
                    val getfield = Opcodes.GETFIELD.toString()
                    val putfield = Opcodes.PUTFIELD.toString()
                    val areturn = Opcodes.ARETURN.toString()
                    val ireturn = Opcodes.IRETURN.toString()
                    val returnVoid = Opcodes.RETURN.toString()
                    
                    val isSimpleGetter = instrs.containsAll(listOf(aload, getfield, areturn)) ||
                                         instrs.containsAll(listOf(aload, getfield, ireturn))
                    val isSimpleSetter = instrs.containsAll(listOf(aload, putfield, returnVoid))
                    
                    if (isSimpleGetter || isSimpleSetter) {
                        return emptyList()
                    }
                }
            }
            
            return instrs
        }
        
        /**
         * Generate histogram of instruction opcodes to represent overall method complexity
         */
        fun getInstructionHistogram(): Map<Int, Int>? {
            // Return marker for empty methods instead of null
            if (instructions.isEmpty()) return mapOf(-1 to 1)  // Special marker: -1 opcode
            // Convert instruction strings to hashcodes for histogram keys
            return instructions.groupingBy { it.hashCode() }.eachCount()
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
