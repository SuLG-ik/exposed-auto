package ru.sulgik.exposed

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
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
        if (invoked) return emptyList()
        logger.info("Start generate utils for tables")
        return resolver.getSymbolsWithAnnotation("ru.sulgik.exposed.TableToCreation")
            .filterIsInstance<KSClassDeclaration>().filter {
                if (it.classKind != ClassKind.OBJECT)
                    logger.error("Only objects is able to be annotated as TableToCreation", it)
                if ("org.jetbrains.exposed.sql.Table" !in it.flatSuperTypes())
                    logger.error("Only exposed Table() is able to be annotated as TableToCreation", it)
                true
            }.toList().also {
                logger.info("Tables count: ${it.size}")
                produceClasses(it)
                logger.info("Utils has been generated")
                invoked = true
            }
    }

    private fun KSDeclaration.flatSuperTypes(): List<Any?> {
        if (this is KSClassDeclaration) {
            return superTypes.flatMap {
                val declaration = it.resolve().declaration
                val name = declaration.qualifiedName?.asString()
                if (name.isNullOrEmpty()) {
                    declaration.flatSuperTypes()
                } else {
                    declaration.flatSuperTypes() + name
                }
            }.distinct().toList()
        }
        return emptyList()
    }


    private fun produceClasses(tables: List<KSClassDeclaration>) {
        val outputPackage = "ru.sulgik.exposed.generated"
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
                package $outputPackage
                
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
        return codeGenerator.createNewFile(Dependencies(false), packageName, filename)
    }

}