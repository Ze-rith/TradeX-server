import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.Yaml

abstract class OntologyValidateTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val ontologyDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @TaskAction
    fun validate() {
        val declared = loadOntologySchemas(ontologyDir.get().asFile)
        val inCode = scanEventSchemas()

        val problems = mutableListOf<String>()
        for ((type, version) in inCode) {
            val yaml = declared[type]
            when {
                yaml == null -> problems += "코드의 이벤트 '$type' v$version 이 ontology/*.yaml에 미등록"
                yaml != version -> problems += "'$type' 버전 불일치: 코드 v$version vs 온톨로지 v$yaml"
            }
        }
        for (type in declared.keys - inCode.keys) {
            problems += "온톨로지의 '$type'에 대응하는 @EventSchema 클래스가 코드에 없음"
        }

        if (problems.isNotEmpty()) {
            throw GradleException(
                "ontologyValidate 실패 (${problems.size}건):\n" + problems.joinToString("\n") { " - $it" },
            )
        }
        logger.lifecycle("ontologyValidate: 코드 스키마 ${inCode.size}건 ↔ 온톨로지 ${declared.size}건 일치")
    }

    private fun scanEventSchemas(): Map<String, Int> {
        val pattern = Regex("""@EventSchema\s*\(\s*type\s*=\s*"([^"]+)"\s*,\s*version\s*=\s*(\d+)\s*\)""")
        val found = mutableMapOf<String, Int>()
        for (file in sourceFiles.files.filter { it.extension == "kt" }) {
            for (match in pattern.findAll(file.readText())) {
                val (type, version) = match.destructured
                found[type] = version.toInt()
            }
        }
        return found
    }

    private fun loadOntologySchemas(dir: File): Map<String, Int> {
        val yaml = Yaml()
        val schemas = mutableMapOf<String, Int>()
        dir.listFiles { f: File -> f.extension == "yaml" || f.extension == "yml" }?.sorted()?.forEach { file ->
            val document = yaml.load<Map<String, Any?>>(file.readText()) ?: return@forEach
            @Suppress("UNCHECKED_CAST")
            (document["schemas"] as? List<Map<String, Any?>> ?: emptyList()).forEach { schema ->
                val type = schema["type"] as? String ?: throw GradleException("${file.name}: schema.type 누락")
                schemas[type] = schema["version"] as? Int ?: throw GradleException("${file.name}: $type version 누락")
            }
        }
        return schemas
    }
}
