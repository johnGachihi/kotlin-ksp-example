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
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import java.io.OutputStream
import kotlin.reflect.KClass

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

        return emptyList()
    }

    inner class PreferencesVisitor : KSVisitorVoid() {
        lateinit var outputFile: OutputStream

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
            outputFile.appendln("""
                |package $PREFERENCES_PACKAGE
                |
                |class $classSimpleName(
                |    private val $PREF_STORE_ARG_IDENTIFIER: ${PreferencesStore::class.qualifiedName}
                |) {
            """.trimMargin())

            classDeclaration.getDeclaredProperties()
                .filter { it.isAnnotationPresent(Preference::class) }
                .forEach { it.accept(this@PreferencesVisitor, Unit) }

            outputFile.appendln("}")
            outputFile.close()
        }

        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
            val qualifiedName = property.type.resolve().declaration.qualifiedName!!.asString()

            val annotation = property.getAnnotationByType(Preference::class)
            val preferenceArgs = annotation!!.getPreferenceArguments()
            val (key, default, converter) = preferenceArgs

            outputFile.appendln(
                """
                |    var ${property.simpleName.asString()}: $qualifiedName
                |        get() {
                |            val converter = $converter()
                |            val rawValue = $PREF_STORE_ARG_IDENTIFIER.read("$key")
                |                ?: "$default"
                |            return converter.parse(rawValue)
                |        }
                |        set(value) {
                |            val converter = $converter()
                |            val rawValue = converter.format(value)
                |            $PREF_STORE_ARG_IDENTIFIER.write("$key", rawValue)
                |        }
                """.trimMargin()
            )
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

        private fun KSAnnotation.getPreferenceArguments(): PreferenceArgs {
            val args = arguments.associateBy(
                { it.name?.asString() },
                { it.value }
            )

            return PreferenceArgs(
                key = args["key"] as String,
                default = args["default"] as String,
                converter = (args["converter"] as KSType).declaration.qualifiedName!!.asString()
            )
        }

        private fun <T : Annotation> KSPropertyDeclaration.getAnnotationByType(
            annotationKClass: KClass<T>
        ): KSAnnotation? {
            fun KSAnnotation.isOfRequiredType() =
                annotationType.resolve().declaration.qualifiedName?.asString() ==
                        annotationKClass.qualifiedName

            return annotations
                .filter { it.isOfRequiredType() }
                .firstOrNull()
        }

        // TODO: Use getAnnotationByType to check if annotation present
        private fun <T : Annotation> KSPropertyDeclaration.isAnnotationPresent(
            annotationKClass: KClass<T>
        ): Boolean {
            annotations.forEach {
                if (it.annotationType.resolve().declaration.qualifiedName?.asString() ==
                    annotationKClass.qualifiedName
                ) {
                    return true
                }
            }
            return false
        }

        private fun OutputStream.append(str: String) {
            write(str.toByteArray())
        }

        private fun OutputStream.appendln(str: String = "") {
            append("$str\n")
        }

    }

    data class PreferenceArgs(
        val key: String,
        val default: String,
        val converter: String
    )
}


class PreferencesProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return PreferencesProcessor(environment.codeGenerator, environment.logger)
    }
}
