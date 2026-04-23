package health.workflows.server.service

import health.workflows.interfaces.api.CompatibilityEvaluator
import health.workflows.interfaces.api.CompatibilityReport
import health.workflows.interfaces.api.ConsumptionInterface
import health.workflows.interfaces.api.LineageGraph
import health.workflows.interfaces.api.PlatformProfile
import health.workflows.interfaces.api.PublishResult
import health.workflows.interfaces.api.SearchQuery
import health.workflows.interfaces.graph.InMemoryComponentIndex
import health.workflows.interfaces.graph.encodeToJsonString
import health.workflows.interfaces.model.ComponentRef
import health.workflows.interfaces.model.WorkflowArtifactPackage
import health.workflows.server.ComponentNotFoundException
import health.workflows.server.ContentHashMismatchException
import health.workflows.server.io.writeTextReplacingAtomically
import health.workflows.server.store.PackageStore
import health.workflows.server.wfhub.StubWorkflowHubPort
import health.workflows.server.wfhub.WorkflowHubPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Generic server-side [ConsumptionInterface] implementation.
 *
 * Platform-neutral — any platform client submits its own [PlatformProfile] when
 * calling [checkCompatibility]. This service has no opinion about which platforms exist.
 *
 * All writes are serialized by [writeMutex] — concurrent publishes cannot produce a
 * partially-updated graph state or interleaved file writes.
 *
 * Graph state is persisted atomically after each [publish] via tmp→rename.
 *
 * [getDOI] and [getLineage] are delegated to [workflowHub]. Use [StubWorkflowHubPort]
 * (default) when WorkflowHub is not connected, or a test double in tests.
 */
class RegistryService(
    internal val index: InMemoryComponentIndex,
    private val store: PackageStore,
    private val evaluator: CompatibilityEvaluator,
    private val graphStateFile: File,
    private val workflowHub: WorkflowHubPort = StubWorkflowHubPort,
    private val writeMutex: Mutex = Mutex(),
) : ConsumptionInterface {

    override suspend fun publish(pkg: WorkflowArtifactPackage): PublishResult {
        val actual = sha256Hex(pkg.native.content)
        if (actual != pkg.contentHash) {
            throw ContentHashMismatchException(pkg.id, pkg.version, pkg.contentHash, actual)
        }
        writeMutex.withLock {
            store.save(pkg)
            index.index(pkg)
            persistGraphState()
        }
        return PublishResult(accepted = true, id = pkg.id, version = pkg.version)
    }

    override suspend fun search(query: SearchQuery): List<WorkflowArtifactPackage> {
        val refs = index.search(query)
        return refs.mapNotNull { ref ->
            runCatching { store.load(ref.id, ref.version) }.getOrNull()
        }
    }

    override suspend fun getComponent(id: String, version: String): WorkflowArtifactPackage =
        store.load(id, version) ?: throw ComponentNotFoundException(id, version)

    override suspend fun checkCompatibility(
        id: String,
        version: String,
        profile: PlatformProfile,
    ): CompatibilityReport {
        val pkg = getComponent(id, version)
        return evaluator.evaluate(pkg, profile)
    }

    override suspend fun resolveDependencies(id: String, version: String): List<ComponentRef> =
        getComponent(id, version).dependencies ?: emptyList()

    override suspend fun getDOI(id: String, version: String): String =
        workflowHub.getDOI(id, version)

    override suspend fun getLineage(id: String, version: String): LineageGraph =
        workflowHub.getLineage(id, version)

    private suspend fun persistGraphState() = withContext(Dispatchers.IO) {
        writeTextReplacingAtomically(graphStateFile, index.encodeToJsonString())
    }
}

private fun sha256Hex(content: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(content.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
