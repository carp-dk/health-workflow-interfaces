package health.workflows.interfaces.api

import health.workflows.interfaces.model.ComponentRef
import health.workflows.interfaces.model.WorkflowArtifactPackage
import health.workflows.interfaces.graph.RelationType

/**
 * Graph query interface for indexing and traversing workflow component relationships.
 *
 * Implementations maintain a node/edge graph that supports discovery by semantic
 * criteria and traversal by relationship type. The interface is transport-agnostic
 * and carries no knowledge of storage backends.
 */
interface ComponentIndex {
    /** Add all nodes and edges derived from [pkg] to the index. Idempotent on repeated calls. */
    suspend fun index(pkg: WorkflowArtifactPackage)

    /** Return component references whose metadata satisfies all non-empty fields of [query]. */
    suspend fun search(query: SearchQuery): List<ComponentRef>

    /**
     * Return components reachable from the node identified by [id] + [version] via
     * edges of type [relation].
     */
    suspend fun getRelated(id: String, version: String, relation: RelationType): List<ComponentRef>
}
