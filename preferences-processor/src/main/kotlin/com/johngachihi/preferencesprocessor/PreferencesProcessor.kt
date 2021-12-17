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
            if (classDeclaration.classKind != ClassKind.INTERFACE) {
                logger.warn(
                    "Defining preferences holder using a class/object is not supported. " +
                            "The class/object ${classDeclaration.simpleName.asString()} will therefore be ignored"
                )
                return
            }

            val classSimpleName = classDeclaration.simpleName.asString()

            val containingFile = classDeclaration.containingFile!!
            outputFile = codeGenerator.createNewFile(
                // Why set aggregating to false
                Dependencies(false, containingFile),
                "preferences",
                classSimpleName
            )
            outputFile.appendln("package $PREFERENCES_PACKAGE")
            outputFile.appendln()
            outputFile.appendln("class $classSimpleName(")
            outputFile.appendln("    private val prefStore: ${PreferencesStore::class.qualifiedName}")
            outputFile.appendln(") {")

            classDeclaration.getDeclaredProperties()
                .filter { it.isAnnotationPresent(Preference::class) }
                .forEach { it.accept(this@PreferencesVisitor, Unit) }

            outputFile.appendln("}")
            outputFile.close()
        }

        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
            val qualifiedName = property.type.resolve().declaration.qualifiedName!!.asString()

            val annotation = property.getAnnotationByType(Preference::class)
            val preferenceArgs = annotation?.getPreferenceArguments()

            outputFile.appendln("    var ${property.simpleName.asString()}: $qualifiedName")
            outputFile.appendln("        get() {")
            outputFile.appendln("            ")
            outputFile.appendln("        }")
        }

        private fun OutputStream.append(str: String) {
            write(str.toByteArray())
        }

        private fun OutputStream.appendln(str: String = "") {
            append("$str\n")
        }

    }

    data class PreferenceArgs(val key: String, val default: String)

    private fun KSAnnotation.getPreferenceArguments(): PreferenceArgs {
        TODO()
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

    // TODO: Use getAnnotationByType
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
}


class PreferencesProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return PreferencesProcessor(environment.codeGenerator, environment.logger)
    }
}
