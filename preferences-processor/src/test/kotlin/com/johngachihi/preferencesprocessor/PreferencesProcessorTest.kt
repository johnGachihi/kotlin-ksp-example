package com.johngachihi.preferencesprocessor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.assertj.core.api.AbstractFileAssert
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

class PreferencesProcessorTest {
    @Nested
    @DisplayName("When preferences are defined with a Class or Object")
    inner class TestWhenPreferencesDefinedWithClassOrObject {
        lateinit var compiler: KotlinCompilation

        @BeforeEach
        fun initCompiler() {
            val kotlinSrc = SourceFile.kotlin(
                "preferences.kt",
                """
                @com.johngachihi.preferencesprocessor.Preferences
                class PaymentPreferences
            """
            )

            compiler = KotlinCompilation().apply {
                sources = listOf(kotlinSrc)
                symbolProcessorProviders = listOf(PreferencesProcessorProvider())
                inheritClassPath = true
            }
        }

        @Test
        fun `then warns user`() {
            val compilation = compiler.compile()

            assertThat(compilation.messages).contains(
                "Defining preferences holder using a class/object is not supported. " +
                        "The class/object PaymentPreferences will therefore be ignored"
            )
        }

        @Test
        fun `then ignores`() {
            compiler.compile()

            assertThat(
                File(compiler.kspSourcesDir, "kotlin/preferences/PaymentPreferences.kt")
            ).doesNotExist()
        }
    }

    @Nested
    inner class TestWhenPreferencesDefinedUsingInterface {
        private lateinit var compiler: KotlinCompilation

        @BeforeEach
        fun initCompiler() {
            compiler = KotlinCompilation().apply {
                symbolProcessorProviders = listOf(PreferencesProcessorProvider())
                inheritClassPath = true
            }
        }

        @Test
        @DisplayName(
            "Creates a kotlin file per preferences interface " +
                    "with same name as corresponding interface"
        )
        fun testCreatesKotlinFilesPerPreferencesInterface() {
            val kotlinSrc = SourceFile.kotlin(
                "preferences.kt", """
                    @com.johngachihi.preferencesprocessor.Preferences
                    interface PaymentPreferences
    
                    @com.johngachihi.preferencesprocessor.Preferences
                    interface CurrencyPreferences
                """
            )

            process(kotlinSrc)

            assertGeneratedFile("preferences/PaymentPreferences.kt").exists()
            assertGeneratedFile("preferences/CurrencyPreferences.kt").exists()
        }

        @Test
        @DisplayName(
            "Generates a kotlin Class to hold preferences accessors and setters" +
                    " that is injected with a PreferencesStore"
        )
        fun testGeneratesClassToHoldPreferences() {
            val kotlinSrc = SourceFile.kotlin(
                "preferences.kt", """
                    import com.johngachihi.preferencesprocessor.Preferences

                    @Preferences
                    interface PaymentPreferences
                """
            )

            process(kotlinSrc)

            assertGeneratedFile("preferences/PaymentPreferences.kt").hasCodeContent(
                """
                    package ${PreferencesProcessor.PREFERENCES_PACKAGE}
                    
                    class PaymentPreferences(
                        private val prefStore: com.johngachihi.preferencesprocessor.PreferencesStore
                    ) {
                    }
                """
            )
        }

        @Test
        fun `Generates a var for each preference with appropriate getter and setter code`() {
            val kotlinSrc = SourceFile.kotlin(
                "preferences.kt", """
                    @com.johngachihi.preferencesprocessor.Preferences
                    interface PaymentPreferences {
                        @com.johngachihi.preferencesprocessor.Preference(
                            key = "payment_life_minutes",
                            default = "20",
                            converter = com.johngachihi.preferencesprocessor.converters.DurationConverter::class
                        )
                        var paymentLife: java.time.Duration?
                    }
                """
            )

            process(kotlinSrc)

            assertGeneratedFile("preferences/PaymentPreferences.kt")
                .hasCodeContent(
                    """
                    package ${PreferencesProcessor.PREFERENCES_PACKAGE}

                    class PaymentPreferences(private val prefStore: com.johngachihi.preferencesprocessor.PreferencesStore) {
                        var paymentLife: java.time.Duration
                            get() {
                                val converter = com.johngachihi.preferencesprocessor.DurationConverter()
                                val rawValue =  prefStore.read("payment_life_minutes")
                                    ?: "20"
                                return converter.parse(rawValue)
                            }
                    }
                """.trimIndent()
                )
        }

        private fun process(vararg sourceFile: SourceFile) {
            compiler.sources = listOf(*sourceFile)
            val compilation = compiler.compile()

            assertThat(compilation.exitCode)
                .withFailMessage(
                    "Expecting compiler to exit successfully. " +
                            "Instead exit code ${compilation.exitCode} returned."
                )
                .isEqualTo(KotlinCompilation.ExitCode.OK)

            // Remove
            compiler.kspSourcesDir.walk()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { println(it.absolutePath) }
        }

        private fun assertGeneratedFile(relativePath: String): AbstractFileAssert<*> {
            val generatedFile = File(
                compiler.kspSourcesDir,
                "kotlin/$relativePath"
            )
            return assertThat(generatedFile)
        }

        private fun AbstractFileAssert<*>.hasCodeContent(
            @Language("kotlin") code: String
        ): AbstractFileAssert<*>? = hasContent(code.trimIndent())
    }
}