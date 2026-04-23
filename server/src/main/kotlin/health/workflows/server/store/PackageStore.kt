package health.workflows.server.store

import health.workflows.interfaces.model.WorkflowArtifactPackage
import health.workflows.server.io.writeTextReplacingAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File-backed package store. Each package is persisted as a JSON file under [dir],
 * named `{sanitised-id}-{sanitised-version}.json`.
 *
 * Saves are atomic: content is written to a `.tmp` sibling and then renamed, so a
 * partially-written file is never visible to concurrent readers. Publishing the same
 * id+version twice replaces the stored file without error.
 */
class PackageStore(private val dir: File) {

    private val json = Json { ignoreUnknownKeys = true }

    init {
        dir.mkdirs()
    }

    suspend fun save(pkg: WorkflowArtifactPackage) = withContext(Dispatchers.IO) {
        val dest = fileFor(pkg.id, pkg.version)
        writeTextReplacingAtomically(dest, json.encodeToString(pkg))
    }

    suspend fun load(id: String, version: String): WorkflowArtifactPackage? = withContext(Dispatchers.IO) {
        val file = fileFor(id, version)
        if (!file.exists()) null
        else json.decodeFromString<WorkflowArtifactPackage>(file.readText())
    }

    private fun fileFor(id: String, version: String): File =
        File(dir, "${id.sanitise()}-${version.sanitise()}.json")
}

/** Replace filesystem-unsafe characters with underscores to produce a safe filename component. */
private fun String.sanitise(): String = replace(Regex("""[/\\:*?"<>|]"""), "_")
