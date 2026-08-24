package dev.youndie.proba.resolver

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import java.io.File
import java.util.jar.JarFile

/**
 * The classes a jar's public API mentions and does not itself declare.
 *
 * This is the half of the question the repository cannot answer. Metadata says what a module
 * declares it needs; only the artefact says what its signatures actually hand out, and the two
 * disagree exactly when the defect is present.
 */
object JarApiSurface {

    private val ignoredPrefixes = listOf("java.", "javax.", "jdk.", "sun.", "com.sun.")

    fun of(jar: File): Set<String> {
        if (!jar.isFile) return emptySet()
        val declared = mutableSetOf<String>()
        val mentioned = mutableSetOf<String>()

        JarFile(jar).use { archive ->
            archive.entries().asSequence()
                .filter { it.name.endsWith(".class") && !it.isDirectory }
                .forEach { entry ->
                    val reader = archive.getInputStream(entry).use { ClassReader(it.readBytes()) }
                    declared += reader.className.toClassName()
                    if (!reader.access.isVisible()) return@forEach
                    mentioned += reader.surface()
                }
        }

        return mentioned
            .asSequence()
            .filter { name -> ignoredPrefixes.none { name.startsWith(it) } }
            .filterNot { it in declared }
            .filterNot { it.substringBefore('$') in declared }
            .toSortedSet()
    }

    private fun ClassReader.surface(): Set<String> {
        val found = mutableSetOf<String>()
        superName?.let { found += it.toClassName() }
        interfaces.forEach { found += it.toClassName() }

        accept(
            object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
                override fun visit(v: Int, access: Int, name: String, sig: String?, superName: String?, ifs: Array<out String>?) {
                    sig?.let { found += it.fromSignature() }
                }

                override fun visitField(access: Int, name: String, descriptor: String, sig: String?, value: Any?): org.objectweb.asm.FieldVisitor? {
                    if (access.isVisible()) {
                        found += Type.getType(descriptor).classNames()
                        sig?.let { found += it.fromSignature() }
                    }
                    return null
                }

                override fun visitMethod(access: Int, name: String, descriptor: String, sig: String?, exceptions: Array<out String>?): org.objectweb.asm.MethodVisitor? {
                    if (access.isVisible()) {
                        val type = Type.getMethodType(descriptor)
                        found += type.returnType.classNames()
                        type.argumentTypes.forEach { found += it.classNames() }
                        exceptions?.forEach { found += it.toClassName() }
                        sig?.let { found += it.fromSignature() }
                    }
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return found
    }

    /** Generic parameters count: a `List<Token>` returned from a public function needs Token nameable. */
    private fun String.fromSignature(): Set<String> {
        val found = mutableSetOf<String>()
        SignatureReader(this).accept(
            object : SignatureVisitor(Opcodes.ASM9) {
                override fun visitClassType(name: String) { found += name.toClassName() }
                override fun visitInnerClassType(name: String) { }
            },
        )
        return found
    }

    private fun Type.classNames(): Set<String> = when (sort) {
        Type.OBJECT -> setOf(className)
        Type.ARRAY -> elementType.classNames()
        else -> emptySet()
    }

    private fun String.toClassName() = replace('/', '.')

    private fun Int.isVisible() = (this and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PROTECTED)) != 0
}
