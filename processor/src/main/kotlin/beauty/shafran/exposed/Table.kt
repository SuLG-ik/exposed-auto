package beauty.shafran.exposed

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import java.io.OutputStream


class ExposedTableSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ExposedTableSymbolProcessor(environment.codeGenerator, environment.logger)
    }


}

class ExposedTableSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked)
            return emptyList()
        logger.info("Start generate utils for tables")
        return resolver.getSymbolsWithAnnotation("beauty.shafran.exposed.Table").filterIsInstance<KSClassDeclaration>()
            .filter {
                it.classKind == ClassKind.OBJECT
            }
            .toList()
            .also {
                logger.info("Tables count: ${it.size}")
                produceClasses(it)
                logger.info("Utils has been generated")
                invoked = true
            }
    }

    private fun produceClasses(tables: List<KSClassDeclaration>) {
        val outputPackage = "beauty.shafran.exposed"
        val filename = "GeneratedTableSchemaUtils"
        val file = getFile(outputPackage, filename)
        codeGenerator.associateWithClasses(
            tables,
            outputPackage,
            filename,
        )
        val tableNames = (tables.joinToString(",\n                        ") { it.qualifiedName!!.asString() })
        file.bufferedWriter().use { writer ->
            writer.appendLine("""
                package beauty.shafran.exposed
                
                import org.jetbrains.exposed.sql.SchemaUtils
                
                fun SchemaUtils.createAnnotatedMissingTablesAndColumns(inBatch: Boolean = false, withLogs: Boolean = true) {
                    createMissingTablesAndColumns(
                        $tableNames${if (tableNames.isNotEmpty()) ",\n                        inBatch = inBatch," else "inBatch = inBatch,"}
                        withLogs = withLogs,
                    )
                }
            """.trimIndent())
        }
    }

    private fun getFile(packageName: String, filename: String): OutputStream {
        return codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            filename
        )
    }

}