@file:Suppress("unused")

package carp.interfaces.api

import carp.interfaces.model.ComponentRef
import carp.interfaces.model.WorkflowArtifactPackage
import carp.interfaces.model.WorkflowFormat
import kotlinx.serialization.Serializable

/**
 * Contract for consuming workflow artefacts from a registry-like backend.
 *
 * Implementations may back these operations with local catalogues, remote services,
 * or hybrid resolvers. The API is intentionally transport-agnostic and contains
 * no DSP-specific behaviour.
 */
interface ConsumptionInterface {
    /**
     * Retrieve a specific workflow artefact package by immutable identifier and version.
     *
     * Implementations should fail when the package is not found.
     */
    suspend fun getComponent(id: String, version: String): WorkflowArtifactPackage

    /**
     * Find artefact packages matching user-supplied discovery criteria.
     *
     * Implementations should return an empty list when there are no matches.
     */
    suspend fun search(query: SearchQuery): List<WorkflowArtifactPackage>

    /**
     * Publish a workflow artefact package into the backing registry.
     *
     * Implementations should validate package integrity and return publish status.
     */
    suspend fun publish(pkg: WorkflowArtifactPackage): PublishResult

    /**
     * Resolve the DOI for a package identity and version.
     *
     * R1 implementations may throw [NotImplementedError] when DOI minting is not wired yet.
     */
    suspend fun getDOI(id: String, version: String): String

    /**
     * Resolve direct dependencies for a package identity and version.
     *
     * Implementations should return an empty list when no dependencies are declared.
     */
    suspend fun resolveDependencies(id: String, version: String): List<ComponentRef>

    /**
     * Perform live compatibility evaluation for a package against a target platform.
     *
     * Results are expected to be computed at request time rather than served from stale snapshots.
     */
    suspend fun checkCompatibility(id: String, version: String, platformId: String): CompatibilityReport

    /**
     * Retrieve lineage information for a package identity and version.
     *
     * R1 implementations may return an empty graph while lineage tracking matures.
     */
    suspend fun getLineage(id: String, version: String): LineageGraph
}

@Serializable
data class SearchQuery(
    val keywords: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val format: WorkflowFormat? = null,
    val platformId: String? = null,
)

@Serializable
data class PublishResult(
    val accepted: Boolean,
    val id: String,
    val version: String,
    val message: String? = null,
)

@Serializable
data class CompatibilityReport(
    val compatible: Boolean,
    val reasons: List<String> = emptyList(),
)

@Serializable
data class LineageGraph(
    val nodes: List<LineageNode> = emptyList(),
    val edges: List<LineageEdge> = emptyList(),
)

@Serializable
data class LineageNode(
    val id: String,
    val version: String,
)

@Serializable
data class LineageEdge(
    val fromId: String,
    val fromVersion: String,
    val toId: String,
    val toVersion: String,
    val relation: String,
)

@Serializable
data class GetComponentRequest(
    val id: String,
    val version: String,
)

@Serializable
data class GetComponentResponse(
    val pkg: WorkflowArtifactPackage,
)

@Serializable
data class SearchRequest(
    val query: SearchQuery,
)

@Serializable
data class SearchResponse(
    val results: List<WorkflowArtifactPackage>,
)

@Serializable
data class PublishRequest(
    val pkg: WorkflowArtifactPackage,
)

@Serializable
data class PublishResponse(
    val result: PublishResult,
)

@Serializable
data class GetDoiRequest(
    val id: String,
    val version: String,
)

@Serializable
data class GetDoiResponse(
    val doi: String,
)

@Serializable
data class ResolveDependenciesRequest(
    val id: String,
    val version: String,
)

@Serializable
data class ResolveDependenciesResponse(
    val dependencies: List<ComponentRef>,
)

@Serializable
data class CheckCompatibilityRequest(
    val id: String,
    val version: String,
    val platformId: String,
)

@Serializable
data class CheckCompatibilityResponse(
    val report: CompatibilityReport,
)

@Serializable
data class GetLineageRequest(
    val id: String,
    val version: String,
)

@Serializable
data class GetLineageResponse(
    val graph: LineageGraph,
)

