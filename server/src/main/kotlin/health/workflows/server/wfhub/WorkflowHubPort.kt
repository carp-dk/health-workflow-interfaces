package health.workflows.server.wfhub

import health.workflows.interfaces.api.LineageGraph

/**
 * Port for interacting with WorkflowHub.
 *
 * Swap implementations to control behaviour:
 * - [StubWorkflowHubPort] — default, no WfHub required (DOI returns 501, lineage is empty)
 * - [MockWorkflowHubPort] — for tests, returns predictable data
 * - A future HTTP adapter — for live deployment
 */
interface WorkflowHubPort {
    suspend fun getDOI(id: String, version: String): String
    suspend fun getLineage(id: String, version: String): LineageGraph
}

/**
 * Default no-op implementation used in production until WfHub is wired.
 *
 * [getDOI] throws [NotImplementedError] so callers receive a 501.
 * [getLineage] returns an empty graph.
 */
object StubWorkflowHubPort : WorkflowHubPort {
    override suspend fun getDOI(id: String, version: String): String =
        throw NotImplementedError("DOI minting requires the WorkflowHub adapter (not wired)")

    override suspend fun getLineage(id: String, version: String): LineageGraph = LineageGraph()
}
