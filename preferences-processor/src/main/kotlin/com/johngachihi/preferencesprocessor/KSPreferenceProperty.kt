package com.johngachihi.preferencesprocessor

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType

data class PreferenceAnnotationArgs(
    val key: String,
    val default: String,
    val converter: KSDeclaration
)

sealed interface ValidationResult {
    object Success : ValidationResult
    class Invalid(val error: String) : ValidationResult
}

// TODO: Consider renaming to remove "KS" since that
//       makes it seem like a subclass of KSP's KS*
//       types
class KSPreferenceProperty(
    private val property: KSPropertyDeclaration,
    private val preferenceAnnotation: KSAnnotation
) {
    private val propertyType by lazy { property.type.resolve() }

    val typeName: String by lazy {
        propertyType.declaration.qualifiedName!!.asString()
    }

    val preferenceAnnotationArgs: PreferenceAnnotationArgs by lazy {
        val args = preferenceAnnotation.arguments.associateBy(
            { it.name?.asString() },
            { it.value }
        )

        PreferenceAnnotationArgs(
            key = args["key"] as String,
            default = args["default"] as String,
            converter = (args["converter"] as KSType).declaration
        )
    }

    fun runValidation(): ValidationResult {
        runConverterDeclarationValidation().let {
            if (it is ValidationResult.Invalid) return it
        }
        runConverterTypeValidation().let {
            if (it is ValidationResult.Invalid) return it
        }

        return ValidationResult.Success
    }

    private fun runConverterDeclarationValidation(): ValidationResult {
        val converter = preferenceAnnotationArgs.converter

        val hasConstructorWithNoParams = converter
            .closestClassDeclaration()!!
            .getConstructors()
            .any { it.parameters.isEmpty() }

        return if (hasConstructorWithNoParams)
            ValidationResult.Success
        else
            ValidationResult.Invalid(
                "${converter.qualifiedName!!.asString()} does not have a no-arg constructor"
            )
    }

    private fun runConverterTypeValidation(): ValidationResult {
        val converterTypeParam = getPreferenceConverterTypeParam()

        return if (propertyType.isAssignableFrom(converterTypeParam)) {
            ValidationResult.Success
        } else {
            ValidationResult.Invalid(
                "Preference property's type and converter do not " +
                        "match on property ${property.qualifiedName!!.asString()}"
            )
        }
    }

    private fun getPreferenceConverterTypeParam(): KSType {
        /**
         *  Retrieve the type parameter for the PreferenceConverter
         *  from its parse method's return type
         */
        val formatMethod = preferenceAnnotationArgs.converter
            .closestClassDeclaration()!!
            .getDeclaredFunctions()
            .find { it.simpleName.asString() == "parse" }!!

        return formatMethod.returnType!!.resolve()
    }

    companion object {
        fun tryCreateFrom(property: KSPropertyDeclaration): KSPreferenceProperty? {
            val annotation = property.getPreferenceAnnotation()
                ?: return null

            return KSPreferenceProperty(property, annotation)
        }

        private fun KSPropertyDeclaration.getPreferenceAnnotation(): KSAnnotation? {
            fun KSAnnotation.isOfRequiredType() =
                annotationType.resolve().declaration.qualifiedName?.asString() ==
                        Preference::class.qualifiedName

            return annotations
                .filter { it.isOfRequiredType() }
                .firstOrNull()
        }
    }
}