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
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate

class PreferencesProcessor(
    private val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(Preferences::class.qualifiedName!!).constrainOnce()
        val invalidSymbols = symbols.filter { !it.validate() }.toList()

        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .map { it.accept(PreferencesVisitor(), Unit) }

        return invalidSymbols
    }

    inner class PreferencesVisitor : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            if (classDeclaration.classKind != ClassKind.INTERFACE) {
                logger.warn(
                    "Defining preferences holder using a class/object is not supported. " +
                            "The class/object ${classDeclaration.simpleName.asString()} will therefore be ignored"
                )
            }

            val containingFile = classDeclaration.containingFile!!
            val outputFile = codeGenerator.createNewFile(
                Dependencies(false, containingFile),
                "",
                containingFile.fileName
            )
            outputFile.write("".toByteArray())
            outputFile.close()
        }
    }
}

class PreferencesProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return PreferencesProcessor(environment.codeGenerator, environment.logger)
    }
}
