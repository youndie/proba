package dev.youndie.proba.resolver

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile
import kotlin.metadata.Visibility
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.Metadata
import kotlin.metadata.jvm.fieldSignature
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.setterSignature
import kotlin.metadata.jvm.signature
import kotlin.metadata.visibility

/**
 * The classes a jar's public API mentions and does not itself declare.
 *
 * This is the half of the question the repository cannot answer. Metadata says what a module
 * declares it needs; only the artefact says what its signatures actually hand out.
 *
 * "Public" here means what a consumer can write, which is narrower than what the JVM calls public:
 *
 * - **Kotlin `internal` compiles to public bytecode.** A generated resource accessor is the ordinary
 *   case — its properties are `INTERNAL` in the Kotlin metadata and `public static` in the class
 *   file — and reading the class file alone reports the types it mentions as handed out by an API
 *   nobody can call.
 * - **Inlining leaves public classes behind.** `…$$inlined$items$default$1` and the lambdas beside it
 *   carry no Kotlin class metadata or say `LOCAL`; a consumer compiling a call resolves the enclosing
 *   member and never touches them.
 *
 * Both kinds arrive with the credibility of the genuine findings beside them, and the suggested fix —
 * promote the dependency to `api` — widens every consumer's compile classpath for a reason that does
 * not hold. So they are excluded here, using what Kotlin records rather than what a name looks like.
 */
object JarApiSurface {
    private val ignoredPrefixes = listOf("java.", "javax.", "jdk.", "sun.", "com.sun.")

    fun of(jar: File): Set<String> {
        if (!jar.isFile) return emptySet()
        val declared = mutableSetOf<String>()
        val mentioned = mutableSetOf<String>()

        JarFile(jar).use { archive ->
            archive
                .entries()
                .asSequence()
                .filter { it.name.endsWith(".class") && !it.isDirectory }
                .forEach { entry ->
                    val node =
                        ClassNode().also { node ->
                            ClassReader(archive.getInputStream(entry).use { it.readBytes() })
                                .accept(
                                    node,
                                    ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
                                )
                        }
                    // Declared regardless of visibility: a type the jar carries is one a consumer
                    // already has, whether or not they were meant to name it.
                    declared += node.name.toClassName()
                    val kotlin = node.kotlinMetadata()
                    if (node.isHidden(kotlin)) return@forEach
                    mentioned += node.surface(kotlin.hiddenSignatures())
                }
        }

        return mentioned
            .asSequence()
            .filter { name -> ignoredPrefixes.none { name.startsWith(it) } }
            .filterNot { it in declared }
            .filterNot { it.substringBefore('$') in declared }
            .toSortedSet()
    }

    /** Whether a consumer could name this class at all. */
    private fun ClassNode.isHidden(kotlin: KotlinClassMetadata?): Boolean {
        if (access and Opcodes.ACC_SYNTHETIC != 0) return true
        if (access and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED) == 0) return true
        // A local or anonymous class: the JVM records the member that encloses it, and nothing else
        // can refer to it by name.
        if (outerMethod != null) return true
        return when (kotlin) {
            is KotlinClassMetadata.SyntheticClass -> {
                true
            }

            is KotlinClassMetadata.Class -> {
                kotlin.kmClass.visibility in setOf(Visibility.INTERNAL, Visibility.PRIVATE, Visibility.LOCAL)
            }

            // No Kotlin metadata at all is not suspicious by itself — a Java class in a Kotlin
            // library is exactly that — and the JVM checks above have already had their say.
            else -> {
                false
            }
        }
    }

    /** JVM signatures of the members Kotlin considers unavailable, whatever the class file says. */
    private fun KotlinClassMetadata?.hiddenSignatures(): Set<String> {
        val hidden = mutableSetOf<String>()

        fun add(signature: kotlin.metadata.jvm.JvmMemberSignature?) {
            signature?.let { hidden += "${it.name}${it.descriptor}" }
        }
        val invisible = setOf(Visibility.INTERNAL, Visibility.PRIVATE, Visibility.PRIVATE_TO_THIS, Visibility.LOCAL)
        when (this) {
            is KotlinClassMetadata.Class -> {
                kmClass.functions.filter { it.visibility in invisible }.forEach { add(it.signature) }
                kmClass.properties.filter { it.visibility in invisible }.forEach {
                    add(it.getterSignature)
                    add(it.setterSignature)
                    add(it.fieldSignature)
                }
                kmClass.constructors.filter { it.visibility in invisible }.forEach { add(it.signature) }
            }

            is KotlinClassMetadata.FileFacade -> {
                kmPackage.functions.filter { it.visibility in invisible }.forEach { add(it.signature) }
                kmPackage.properties.filter { it.visibility in invisible }.forEach {
                    add(it.getterSignature)
                    add(it.setterSignature)
                    add(it.fieldSignature)
                }
            }

            else -> {}
        }
        return hidden
    }

    private fun ClassNode.surface(hidden: Set<String>): Set<String> {
        val found = mutableSetOf<String>()
        superName?.let { found += it.toClassName() }
        interfaces.orEmpty().forEach { found += it.toClassName() }
        signature?.let { found += it.fromSignature() }

        fields.orEmpty().filter { it.access.isVisible() && "${it.name}${it.desc}" !in hidden }.forEach { field ->
            found += Type.getType(field.desc).classNames()
            field.signature?.let { found += it.fromSignature() }
        }
        methods.orEmpty().filter { it.access.isVisible() && "${it.name}${it.desc}" !in hidden }.forEach { method ->
            if (method.access and Opcodes.ACC_SYNTHETIC != 0) return@forEach
            val type = Type.getMethodType(method.desc)
            found += type.returnType.classNames()
            type.argumentTypes.forEach { found += it.classNames() }
            method.exceptions.orEmpty().forEach { found += it.toClassName() }
            method.signature?.let { found += it.fromSignature() }
        }
        return found
    }

    private fun ClassNode.kotlinMetadata(): KotlinClassMetadata? {
        val annotation = visibleAnnotations.orEmpty().firstOrNull { it.desc == "Lkotlin/Metadata;" } ?: return null
        val values =
            annotation.values
                .orEmpty()
                .chunked(2)
                .associate { (key, value) -> key as String to value }

        @Suppress("UNCHECKED_CAST")
        val header =
            Metadata(
                kind = values["k"] as Int?,
                metadataVersion = (values["mv"] as List<Int>?)?.toIntArray(),
                data1 = (values["d1"] as List<String>?)?.toTypedArray(),
                data2 = (values["d2"] as List<String>?)?.toTypedArray(),
                extraInt = values["xi"] as Int?,
            )
        // A metadata version this build cannot read is not a reason to fail: the class then falls back
        // to what the JVM says about it, which is what happened before any of this existed.
        return runCatching { KotlinClassMetadata.readLenient(header) }.getOrNull()
    }

    /** Generic parameters count: a `List<Token>` returned from a public function needs Token nameable. */
    private fun String.fromSignature(): Set<String> {
        val found = mutableSetOf<String>()
        SignatureReader(this).accept(
            object : SignatureVisitor(Opcodes.ASM9) {
                override fun visitClassType(name: String) {
                    found += name.toClassName()
                }

                override fun visitInnerClassType(name: String) { }
            },
        )
        return found
    }

    private fun Type.classNames(): Set<String> =
        when (sort) {
            Type.OBJECT -> setOf(className)
            Type.ARRAY -> elementType.classNames()
            else -> emptySet()
        }

    private fun String.toClassName() = replace('/', '.')

    private fun Int.isVisible() = (this and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) != 0
}
