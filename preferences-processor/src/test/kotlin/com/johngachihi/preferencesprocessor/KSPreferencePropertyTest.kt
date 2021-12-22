package com.johngachihi.preferencesprocessor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.johngachihi.preferencesprocessor.converters.DurationConverter
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


internal class KSPreferencePropertyTest {
    @Nested
    @DisplayName("Test tryCreateFrom")
    inner class TryCreateFromTest {
        @Test
        fun `When property is annotated with @Preference, then returns non-null result`() {
            @Language("kotlin")
            val propertiesSrc = """
                 @com.johngachihi.preferencesprocessor.Preference
                 var paymentLife: java.time.Duration
            """.trimIndent()

            runInProcessor(propertiesSrc) {
                val preferencePropertyDecl = KSPreferenceProperty
                    .tryCreateFrom(it.first())

                assertThat(preferencePropertyDecl).isNotNull
            }
        }

        @Test
        fun `When property is not annotated with @Preference, then returns null`() {
            @Language("kotlin")
            val src = """
                var paymentLife: java.time.Duration
            """.trimIndent()

            runInProcessor(src) {
                val preferencePropertyDeclaration = KSPreferenceProperty
                    .tryCreateFrom(it.first())

                assertThat(preferencePropertyDeclaration).isNull()
            }
        }
    }

    @Nested
    @DisplayName("TestPreferencePropertyValidation")
    inner class TestPreferencePropertyValidation {
        @Test
        @DisplayName(
            "When PreferenceConverter does not match property type, " +
                    "then returns Invalid result with appropriate error"
        )
        fun testWhenPreferenceConverterTypeDoesNotMatchPropertyType() {
            @Language("kotlin")
            val src = """
                @com.johngachihi.preferencesprocessor.Preference(
                    key = "payment_life_minutes",
                    default = "20",
                    converter = com.johngachihi.preferencesprocessor.converters.DurationConverter::class
                )
                var paymentLife: String
            """.trimIndent()

            runInProcessor(src) {
                val preferenceProperty = KSPreferenceProperty.tryCreateFrom(it.first())!!
                val result = preferenceProperty.runValidation()

                assertThat(result)
                    .isInstanceOf(ValidationResult.Invalid::class.java)

                assertThat((result as ValidationResult.Invalid).error)
                    .isEqualTo(
                        "Preference property's type and converter do not " +
                                "match on property MyPreferences.paymentLife"
                    )
            }
        }

        @Test
        @DisplayName(
            "When PreferenceConverter does not have a no-arg constructor, " +
                    "then returns Invalid result with appropriate error"
        )
        fun testWhenPreferenceConverterDoesNotHaveNoArgConstructor() {
            @Language("kotlin")
            val src = """
                class MyPreferenceConverter(private val arg: String)
                    : com.johngachihi.preferencesprocessor.PreferenceConverter<String>
                {
                    override fun parse(value: String) = value
                    override fun format(value: String) = value
                }

                @com.johngachihi.preferencesprocessor.Preference(
                    key = "payment_life_minutes",
                    default = "20",
                    converter = MyPreferenceConverter::class
                )
                var paymentLife: String
            """.trimIndent()

            runInProcessor(src) { properties ->
                val preferenceProperty = KSPreferenceProperty
                    .tryCreateFrom(properties.first())!!

                preferenceProperty.runValidation().let {
                    assertThat(it)
                        .isInstanceOf(ValidationResult.Invalid::class.java)

                    assertThat((it as ValidationResult.Invalid).error)
                        .isEqualTo(
                            "MyPreferences.MyPreferenceConverter " +
                                    "does not have a no-arg constructor"
                        )
                }
            }
        }
    }

    @Test
    fun `Preference annotation arguments`() {
        val src = """
           @com.johngachihi.preferencesprocessor.Preference(
                key = "payment_life_minutes",
                default = "20",
                converter = com.johngachihi.preferencesprocessor.converters.DurationConverter::class
            )
            var paymentLife: String
        """.trimIndent()

        runInProcessor(src) {
            val preferenceProperty = KSPreferenceProperty.tryCreateFrom(it.first())!!

            val (key, default, converter) = preferenceProperty.preferenceAnnotationArgs

            assertThat(key).isEqualTo("payment_life_minutes")
            assertThat(default).isEqualTo("20")
            assertThat(converter.qualifiedName?.asString())
                .isEqualTo(DurationConverter::class.qualifiedName)
        }
    }

    @Test
    fun `test typeName property`() {
        @Language("kotlin")
        val src = """
           @com.johngachihi.preferencesprocessor.Preference(
                key = "payment_life_minutes",
                default = "20",
                converter = com.johngachihi.preferencesprocessor.converters.DurationConverter::class
            )
            var paymentLife: java.time.Duration
        """.trimIndent()

        runInProcessor(src) {
            val preferenceProperty = KSPreferenceProperty
                .tryCreateFrom(it.first())!!

            assertThat(preferenceProperty.typeName)
                .isEqualTo("java.time.Duration")
        }
    }

    private fun runInProcessor(
        @Language("kotlin") propertiesSrc: String,
        block: (properties: Sequence<KSPropertyDeclaration>) -> Unit
    ): KotlinCompilation.Result {
        val source = SourceFile.kotlin(
            "preference.kt", """
             interface MyPreferences {
                 $propertiesSrc
             }
            """.trimIndent()
        )

        var assertionError: Throwable? = null

        val processor = TestKspProcessor(block) { assertionError = it }

        val result = KotlinCompilation().apply {
            sources = listOf(source)
            symbolProcessorProviders = listOf(processor)
            inheritClassPath = true
        }.compile()

        assertionError?.let { throw it }

        return result
    }

    class TestKspProcessor(
        private val assertionsBlock: (properties: Sequence<KSPropertyDeclaration>) -> Unit,
        private val onAssertionErrorCallback: (error: Throwable) -> Unit
    ) : SymbolProcessor, SymbolProcessorProvider {
        override fun process(resolver: Resolver): List<KSAnnotated> {
            val propertyDeclarations = resolver
                .getClassDeclarationByName("MyPreferences")!!
                .getDeclaredProperties()

            // Circumvent KSP exception handling
            try {
                assertionsBlock(propertyDeclarations)
            } catch (e: Throwable) {
                onAssertionErrorCallback(e)
            }

            return emptyList()
        }

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = this
    }
}