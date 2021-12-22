package com.johngachihi.preferencesprocessor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import java.io.OutputStream

class PreferencesProcessor(
    private val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {

    companion object {
        const val PREFERENCES_PACKAGE = "com.johngachihi.preferencesprocessor.preferences"
        private const val PREF_STORE_ARG_IDENTIFIER = "prefStore"
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(Preferences::class.qualifiedName!!).constrainOnce()

        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(PreferencesVisitor(), Unit) }

        // TODO: Explain why you are returning empty list
        return emptyList()
    }

    inner class PreferencesVisitor : KSVisitorVoid() {
        private lateinit var outputFile: OutputStream

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (!classDeclaration.validateWithWarning()) return

            val classSimpleName = classDeclaration.simpleName.asString()
            val containingFile = classDeclaration.containingFile!!

            outputFile = codeGenerator.createNewFile(
                // Why set aggregating to false
                Dependencies(false, containingFile),
                PREFERENCES_PACKAGE,
                classSimpleName
            )
            outputFile.appendln(
                """
                |package $PREFERENCES_PACKAGE
                |
                |class $classSimpleName(
                |    private val $PREF_STORE_ARG_IDENTIFIER: ${PreferencesStore::class.qualifiedName}
                |) {
            """.trimMargin()
            )

            classDeclaration.getDeclaredProperties().forEach {
                it.accept(this@PreferencesVisitor, Unit)
            }

            outputFile.appendln("}")
            outputFile.close()
        }

        private fun KSClassDeclaration.validateWithWarning(): Boolean {
            if (classKind != ClassKind.INTERFACE) {
                logger.warn(
                    "Defining preferences holder using a class/object is not supported. " +
                            "The class/object ${simpleName.asString()} will therefore be ignored"
                )
                return false
            }
            return true
        }

        // TODO: How do I test that the processor validates preference properties
        //       here
        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
            val preferenceProperty = KSPreferenceProperty.tryCreateFrom(property)
                ?: return

            preferenceProperty.runValidation().let {
                if (it is ValidationResult.Invalid) {
                    logger.error(it.error)
                }
            }

            val typeName = preferenceProperty.typeName
            val (key, default, converter) = preferenceProperty.preferenceAnnotationArgs

            outputFile.appendln(
                """
                |    var ${property.simpleName.asString()}: $typeName
                |        get() {
                |            val converter = ${converter.qualifiedName!!.asString()}()
                |            val rawValue = $PREF_STORE_ARG_IDENTIFIER.read("$key")
                |                ?: "$default"
                |            return converter.parse(rawValue)
                |        }
                |        set(value) {
                |            val converter = ${converter.qualifiedName!!.asString()}()
                |            val rawValue = converter.format(value)
                |            $PREF_STORE_ARG_IDENTIFIER.write("$key", rawValue)
                |        }
                """.trimMargin()
            )
        }

        private fun OutputStream.append(str: String) {
            write(str.toByteArray())
        }

        private fun OutputStream.appendln(str: String = "") {
            append("$str\n")
        }

    }

}


class PreferencesProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return PreferencesProcessor(environment.codeGenerator, environment.logger)
    }
}
