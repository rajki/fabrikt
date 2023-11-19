package com.cjbooms.fabrikt.model

import com.beust.jcommander.ParameterException
import com.cjbooms.fabrikt.util.KaizenParserExtensions.isNotDefined
import com.cjbooms.fabrikt.util.YamlUtils
import com.cjbooms.fabrikt.validation.ValidationError
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import java.nio.file.Path
import java.nio.file.Paths
import java.util.logging.Logger

data class SchemaInfo(val name: String, val schema: Schema<*>) {
    val typeInfo: KotlinTypeInfo = KotlinTypeInfo.from(schema, name)
}

data class SourceApi(
    private val rawApiSpec: String,
    val baseDir: Path = Paths.get("").toAbsolutePath(),
) {
    companion object {
        private val logger = Logger.getGlobal()

        fun create(
            baseApi: String,
            apiFragments: Collection<String>,
            baseDir: Path = Paths.get("").toAbsolutePath(),
        ): SourceApi {
            val combinedApi =
                apiFragments.fold(baseApi) { acc: String, fragment -> YamlUtils.mergeYamlTrees(acc, fragment) }
            return SourceApi(combinedApi, baseDir)
        }
    }

    val openApi3: OpenAPI = YamlUtils.parseOpenApi(rawApiSpec, baseDir)
    val allSchemas: List<SchemaInfo>

    init {
        validateSchemaObjects(openApi3).let {
            if (it.isNotEmpty()) throw ParameterException("Invalid models or api file:\n${it.joinToString("\n\t")}")
        }
        allSchemas = openApi3.components.schemas.entries.map { it.key to it.value }
            .map { (key, schema) -> SchemaInfo(key, schema) }
    }

    private fun validateSchemaObjects(api: OpenAPI): List<ValidationError> {
        val schemaErrors = api.components.schemas.entries.fold(emptyList<ValidationError>()) { errors, entry ->
            val name = entry.key
            val schema = entry.value
            if (schema.type == OasType.Object.type && (schema.oneOf.isNotEmpty() || schema.allOf.isNotEmpty() || schema.anyOf.isNotEmpty())
            ) {
                errors + listOf(
                    ValidationError(
                        "'$name' schema contains an invalid combination of properties and `oneOf | anyOf | allOf`. " +
                                "Do not use properties and a combiner at the same level.",
                    ),
                )
            } else if (schema.type == null && schema.properties?.isNotEmpty() == true) {
                logger.warning("Schema '$name' has 'type: null' but defines properties. Assuming: 'type: object'")
                errors
            } else {
                errors
            }
        }

        return api.components.schemas.map { it.value.properties }.flatMap { it.entries }
            .fold(schemaErrors) { lst, entry ->
                val name = entry.key
                val schema = entry.value
                if (schema.type == OasType.Array.type && schema.items.isNotDefined()) {
                    lst + listOf(ValidationError("Array type '$name' cannot be parsed to a Schema. Check your input"))
                } else if (schema.isNotDefined()) {
                    lst + listOf(ValidationError("Property '$name' cannot be parsed to a Schema. Check your input"))
                } else {
                    lst
                }
            }
    }
}

/**
 * Enclosing schema can either be provided as:
 * - a schema from Kaizen as OAS3 model.
 * - provided as schema name based on names provided in spec file from SchemaInfo
 */
enum class EnclosingSchemaInfoType {
    NAME,
    OAS_MODEL,
}

interface EnclosingSchemaInfo {
    val type: EnclosingSchemaInfoType
}

data class EnclosingSchemaInfoName(val name: String) : EnclosingSchemaInfo {
    override val type = EnclosingSchemaInfoType.NAME
}

data class EnclosingSchemaInfoOasModel(val schema: Schema<*>) : EnclosingSchemaInfo {
    override val type = EnclosingSchemaInfoType.OAS_MODEL
}

fun Schema<*>.toEnclosingSchemaInfo() = EnclosingSchemaInfoOasModel(this)

fun String.toEnclosingSchemaInfo() = EnclosingSchemaInfoName(this)
