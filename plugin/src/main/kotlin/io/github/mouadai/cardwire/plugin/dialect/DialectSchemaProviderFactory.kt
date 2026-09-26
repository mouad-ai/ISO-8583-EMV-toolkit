package io.github.mouadai.cardwire.plugin.dialect

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

/** Maps dialect files to the bundled schema, for autocomplete and validation while editing them. */
class DialectSchemaProviderFactory : JsonSchemaProviderFactory {

    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(DialectSchemaProvider)
}

object DialectSchemaProvider : JsonSchemaFileProvider {

    override fun isAvailable(file: VirtualFile): Boolean = DialectLocations.isDialectFile(file)

    override fun getName(): String = "Cardwire dialect"

    override fun getSchemaFile(): VirtualFile? =
        JsonSchemaProviderFactory.getResourceFile(DialectSchemaProviderFactory::class.java, DialectLocations.SCHEMA_RESOURCE)

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
}
