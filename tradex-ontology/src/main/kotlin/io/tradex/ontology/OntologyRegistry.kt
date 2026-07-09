package io.tradex.ontology

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import org.yaml.snakeyaml.Yaml

enum class SchemaStatus { DRAFT, APPROVED }

data class OntologySchema(
    val context: String,
    val type: String,
    val version: Int,
    val status: SchemaStatus,
    val fields: List<String>,
)

data class OntologyTerm(
    val context: String,
    val term: String,
    val definition: String,
)

class OntologyRegistry(
    val terms: List<OntologyTerm>,
    val schemas: Map<String, OntologySchema>,
) {
    fun schemaFor(type: String): OntologySchema? = schemas[type]

    companion object {
        fun load(directory: Path): OntologyRegistry {
            require(Files.isDirectory(directory)) { "온톨로지 디렉토리가 없음: $directory" }
            val yaml = Yaml()
            val terms = mutableListOf<OntologyTerm>()
            val schemas = LinkedHashMap<String, OntologySchema>()

            Files.list(directory).use { paths ->
                paths.filter { it.extension == "yaml" || it.extension == "yml" }.sorted().forEach { file ->
                    val document = yaml.load<Map<String, Any?>>(Files.readString(file))
                        ?: throw OntologyParseException("${file.name}: 빈 문서")
                    val context = document["context"] as? String
                        ?: throw OntologyParseException("${file.name}: context 누락")

                    @Suppress("UNCHECKED_CAST")
                    (document["terms"] as? List<Map<String, Any?>> ?: emptyList()).forEach { term ->
                        terms += OntologyTerm(
                            context = context,
                            term = term["term"] as? String ?: throw OntologyParseException("${file.name}: term 누락"),
                            definition = term["definition"] as? String ?: "",
                        )
                    }

                    @Suppress("UNCHECKED_CAST")
                    (document["schemas"] as? List<Map<String, Any?>> ?: emptyList()).forEach { schema ->
                        val type = schema["type"] as? String
                            ?: throw OntologyParseException("${file.name}: schema.type 누락")
                        if (type in schemas) {
                            throw OntologyParseException("schema '$type'이 여러 파일에 중복 선언됨")
                        }
                        schemas[type] = OntologySchema(
                            context = context,
                            type = type,
                            version = (schema["version"] as? Int)
                                ?: throw OntologyParseException("${file.name}: $type version 누락"),
                            status = (schema["status"] as? String)?.let { SchemaStatus.valueOf(it) }
                                ?: throw OntologyParseException("${file.name}: $type status 누락"),
                            fields = @Suppress("UNCHECKED_CAST") (schema["fields"] as? List<String> ?: emptyList()),
                        )
                    }
                }
            }
            return OntologyRegistry(terms, schemas)
        }
    }
}

class OntologyParseException(message: String) : RuntimeException(message)
