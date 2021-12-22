package com.johngachihi.preferencesprocessor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.KotlinCompilation.Result
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
    private lateinit var compiler: KotlinCompilation

    @Nested
    @DisplayName("When preferences are defined with a Class or Object")
    inner class TestWhenPreferencesDefinedWithClassOrObject {
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

            assertGeneratedPreferencesFile("PaymentPreferences.kt").doesNotExist()
        }
    }

    @Nested
    inner class TestWhenPreferencesDefinedUsingInterface {
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

            assertGeneratedPreferencesFile("PaymentPreferences.kt").exists()
            assertGeneratedPreferencesFile("CurrencyPreferences.kt").exists()
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

            assertGeneratedPreferencesFile("PaymentPreferences.kt").hasCodeContent(
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
                        var paymentLife: java.time.Duration
                        
                        @com.johngachihi.preferencesprocessor.Preference(
                            key = "payment_session_life_minutes",
                            default = "10",
                            converter = com.johngachihi.preferencesprocessor.converters.DurationConverter::class
                        )
                        val paymentSessionLife: java.time.Duration
                    }
                """
            )

            process(kotlinSrc)

            assertGeneratedPreferencesFile("PaymentPreferences.kt").hasCodeContent(
                """
                |package ${PreferencesProcessor.PREFERENCES_PACKAGE}
                |
                |class PaymentPreferences(
                |    private val prefStore: com.johngachihi.preferencesprocessor.PreferencesStore
                |) {
                |    var paymentLife: java.time.Duration
                |        get() {
                |            val converter = com.johngachihi.preferencesprocessor.converters.DurationConverter()
                |            val rawValue = prefStore.read("payment_life_minutes")
                |                ?: "20"
                |            return converter.parse(rawValue)
                |        }
                |        set(value) {
                |            val converter = com.johngachihi.preferencesprocessor.converters.DurationConverter()
                |            val rawValue = converter.format(value)
                |            prefStore.write("payment_life_minutes", rawValue)
                |        }
                |    var paymentSessionLife: java.time.Duration
                |        get() {
                |            val converter = com.johngachihi.preferencesprocessor.converters.DurationConverter()
                |            val rawValue = prefStore.read("payment_session_life_minutes")
                |                ?: "10"
                |            return converter.parse(rawValue)
                |        }
                |        set(value) {
                |            val converter = com.johngachihi.preferencesprocessor.converters.DurationConverter()
                |            val rawValue = converter.format(value)
                |            prefStore.write("payment_session_life_minutes", rawValue)
                |        }
                |}
                """.trimMargin(),
                trimIndent = false
            )
        }

        @Test
        fun `Skips variables that are not annotated with @Preference`() {
            val sourceFile = SourceFile.kotlin(
                "preferences.kt", """
                @com.johngachihi.preferencesprocessor.Preferences
                interface PaymentPreferences {
                    var nonPreferenceVar: String
                }
            """.trimIndent()
            )

            process(sourceFile)

            assertGeneratedPreferencesFile("PaymentPreferences.kt").hasCodeContent(
                """
                package ${PreferencesProcessor.PREFERENCES_PACKAGE}
                
                class PaymentPreferences(
                    private val prefStore: com.johngachihi.preferencesprocessor.PreferencesStore
                ) {
                }
            """.trimIndent()
            )
        }

        private fun process(
            vararg sourceFile: SourceFile,
            expectedExitCode: ExitCode = ExitCode.OK
        ): Result {
            compiler.sources = listOf(*sourceFile)
            val compilationResult = compiler.compile()

//            TODO: Extract this assertion
            assertThat(compilationResult.exitCode)
                .withFailMessage(
                    "Expected KSP compiler exit-code $expectedExitCode " +
                            "but was ${compilationResult.exitCode}\n\n" +
                            compilationResult.messages
                )
                .isEqualTo(expectedExitCode)

            return compilationResult
        }
    }

    private fun assertGeneratedFile(relativePath: String): AbstractFileAssert<*> {
        val generatedFile = File(
            compiler.kspSourcesDir,
            "kotlin/$relativePath"
        )
        return assertThat(generatedFile)
    }

    private fun assertGeneratedPreferencesFile(fileName: String): AbstractFileAssert<*> {
        val preferencesRelativePath =
            PreferencesProcessor.PREFERENCES_PACKAGE.replace(".", "/")

        return assertGeneratedFile("$preferencesRelativePath/$fileName")
    }

    private fun AbstractFileAssert<*>.hasCodeContent(
        @Language("kotlin") code: String,
        trimIndent: Boolean = true
    ): AbstractFileAssert<*>? = hasContent(if (trimIndent) code.trimIndent() else code)
}