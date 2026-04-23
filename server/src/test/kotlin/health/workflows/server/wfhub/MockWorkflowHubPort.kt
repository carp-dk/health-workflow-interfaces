package health.workflows.server.wfhub

import health.workflows.interfaces.api.LineageGraph

/**
 * Test double for [WorkflowHubPort].
 *
 * Returns predictable values so tests can exercise DOI and lineage endpoints
 * without a live WorkflowHub instance.
 *
 * DOI format: `10.5281/mock.{id}.{version}`
 */
class MockWorkflowHubPort : WorkflowHubPort {
    override suspend fun getDOI(id: String, version: String): String =
        "10.5281/mock.$id.$version"

    override suspend fun getLineage(id: String, version: String): LineageGraph = LineageGraph()
}
