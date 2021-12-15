import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

class PreferencesProcessorTest {
    @Test
    @DisplayName(
        "Warns user when they provide a class instead" +
                "of an interface to define preferences"
    )
    fun testWarnsWhenTheyProvidePreferencesClassInsteadOfInterface() {
        val kotlinSrc = SourceFile.kotlin(
            "preferences.kt",
            """
                @Preferences
                class PaymentPreferences
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(kotlinSrc)
            symbolProcessorProviders = listOf(PreferencesProcessorProvider())
            inheritClassPath = true
        }.compile()

        assertThat(compilation.messages).contains(
            "Defining preferences holder using a class/object is not supported. " +
                    "The class/object PaymentPreferences will therefore be ignored"
        )
    }

    @Test
    @DisplayName(
        "Creates a kotlin file per preferences interface" +
                "with names corresponding to the interface"
    )
    fun testCreatesKotlinFilesPerPreferencesInterface() {
        val kotlinSrc = SourceFile.kotlin(
            "PaymentPreferences.kt",
            """
                @Preferences
                class PaymentPreferences
            """
        )

        val compiler = KotlinCompilation().apply {
            sources = listOf(kotlinSrc)
            symbolProcessorProviders = listOf(PreferencesProcessorProvider())
            inheritClassPath = true
        }
        compiler.compile()

        assertThat(File(compiler.kspSourcesDir, "kotlin/PaymentPreferences.kt"))
            .exists()

//        assertEquals(compilation.exitCode, KotlinCompilation.ExitCode.OK)
//        assertThat(compilation.generatedFiles).anyMatch { it.name == "PaymentPreferences.kt" }

//        val classLoader = compilation.classLoader
//        assertThat(classLoader.tryLoadClass("PaymentPreferences1")).isNotNull
    }

    private fun File.listFilesRecursively(): List<File> {
        return listFiles().flatMap { file ->
            if (file.isDirectory)
                file.listFilesRecursively()
            else
                listOf(file)
        }
    }


    @Test
    fun a() {
        val numbers = listOf(1, 2, 3)

        assertThat(
            numbers.flatMap abc@{ listOf(it, it) }
        ).isEqualTo(listOf(1, 1, 2, 2, 3, 3))
    }


}