package com.cjbooms.fabrikt.util

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.core.models.ParseOptions
import java.nio.file.Path
import java.nio.file.Paths

object YamlUtils {

    val objectMapper: ObjectMapper =
        ObjectMapper(
            YAMLFactory()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        )
            .registerKotlinModule()
            .configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true)
    private val internalMapper: ObjectMapper =
        ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))

    private val parser = OpenAPIParser()
    private val parseOptions = ParseOptions()
    fun mergeYamlTrees(mainTree: String, updateTree: String) =
        internalMapper.writeValueAsString(
            mergeNodes(
                internalMapper.readTree(mainTree),
                internalMapper.readTree(updateTree)
            )
        )!!

    init {
        parseOptions.isResolve = true
    }

    fun parseOpenApi(input: String, inputDir: Path = Paths.get("").toAbsolutePath()): OpenAPI =
        try {
            val parseResult = parser.readContents(input, null, parseOptions)
            parseResult.openAPI
        } catch (ex: NullPointerException) {
            throw IllegalArgumentException(
                "The Kaizen openapi-parser library threw a NPE exception when parsing this API. " +
                    "This is commonly due to an external schema reference that is unresolvable, " +
                    "possibly due to a lack of internet connection",
                ex
            )
        }

    /**
     * The below merge function has been shamelessly stolen from Stackoverflow: https://stackoverflow.com/a/32447591/1026785
     * and converted to much nicer Kotlin implementation
     */
    private fun mergeNodes(currentTree: JsonNode, incomingTree: JsonNode): JsonNode {
        incomingTree.fieldNames().forEach { fieldName ->
            val currentNode = currentTree.get(fieldName)
            val incomingNode = incomingTree.get(fieldName)
            if (currentNode is ArrayNode && incomingNode is ArrayNode) {
                incomingNode.forEach {
                    if (currentNode.contains(it)) mergeNodes(
                        currentNode.get(currentNode.indexOf(it)),
                        it
                    )
                    else currentNode.add(it)
                }
            } else if (currentNode is ObjectNode) mergeNodes(currentNode, incomingNode)
            else (currentTree as? ObjectNode)?.replace(fieldName, incomingNode)
        }
        return currentTree
    }
}
