package health.workflows.interfaces.graph

import health.workflows.interfaces.model.ComponentRef
import health.workflows.interfaces.model.WorkflowArtifactPackage
import health.workflows.interfaces.api.ComponentIndex
import health.workflows.interfaces.api.SearchQuery

/**
 * Adjacency-map backed implementation of [ComponentIndex].
 *
 * Node identity follows `{id}@{version}`: indexing two packages that reference the
 * same step id/version produces a single [StepNode] and deduplicates edges.
 */
class InMemoryComponentIndex : ComponentIndex {

    /** All nodes keyed by `{id}@{version}`. Exposed for graph-state serialization. */
    val nodes: MutableMap<String, GraphNode> = mutableMapOf()

    /** All directed edges. Exposed for graph-state serialization. */
    val edges: MutableList<GraphEdge> = mutableListOf()

    override suspend fun index(pkg: WorkflowArtifactPackage) {
        val workflowKey = nodeKey(pkg.id, pkg.version)
        nodes.getOrPut(workflowKey) { WorkflowNode(pkg.id, pkg.version, pkg.metadata) }

        for (dep in pkg.dependencies.orEmpty()) {
            val stepKey = nodeKey(dep.id, dep.version)
            nodes.getOrPut(stepKey) { StepNode(dep.id, dep.version, dep.registry) }
            addEdge(GraphEdge(workflowKey, stepKey, RelationType.CONTAINS))
        }

        // DataTypeNodes are keyed by ontologyRef URI — version is not applicable.
        for (port in pkg.metadata.inputs + pkg.metadata.outputs) {
            val ref = port.ontologyRef ?: continue
            val dtKey = nodeKey(ref, "")
            nodes.getOrPut(dtKey) { DataTypeNode(id = ref, ontologyRef = ref) }
            addEdge(GraphEdge(workflowKey, dtKey, RelationType.DEPENDS_ON))
        }

        for (method in pkg.metadata.methods) {
            val ver = method.toolVersion ?: ""
            val methodKey = nodeKey(method.name, ver)
            nodes.getOrPut(methodKey) {
                MethodNode(method.name, ver, method.name, MethodLevel.SPECIFIC_METHOD, method.reference)
            }
            addEdge(GraphEdge(workflowKey, methodKey, RelationType.IMPLEMENTS))
        }
    }

    private fun addEdge(edge: GraphEdge) {
        if (edge !in edges) edges.add(edge)
    }

    override suspend fun search(query: SearchQuery): List<ComponentRef> =
        nodes.values
            .filterIsInstance<WorkflowNode>()
            .filter { matchesQuery(it, query) }
            .map { ComponentRef(it.id, it.version) }

    override suspend fun getRelated(id: String, version: String, relation: RelationType): List<ComponentRef> {
        val fromKey = nodeKey(id, version)
        return edges
            .filter { it.from == fromKey && it.type == relation }
            .map { edge ->
                when (val node = nodes[edge.to]) {
                    is StepNode -> ComponentRef(node.id, node.version, node.registry)
                    is WorkflowNode -> ComponentRef(node.id, node.version)
                    else -> {
                        val (toId, toVer) = splitNodeKey(edge.to)
                        ComponentRef(toId, toVer)
                    }
                }
            }
    }

    private fun matchesQuery(node: WorkflowNode, query: SearchQuery): Boolean {
        if (query.keywords.isNotEmpty()) {
            val text = listOfNotNull(node.metadata.name, node.metadata.description)
                .plus(node.metadata.tags.orEmpty())
                .joinToString(" ").lowercase()
            if (query.keywords.none { it.lowercase() in text }) return false
        }
        if (query.sensitivityClass != null && node.metadata.sensitivityClass != query.sensitivityClass) return false
        if (query.granularity != null && node.metadata.granularity != query.granularity) return false
        if (query.inputTypes.isNotEmpty()) {
            val inputRefs = node.metadata.inputs.mapNotNull { it.ontologyRef }.toSet()
            if (query.inputTypes.none { it in inputRefs }) return false
        }
        if (query.outputTypes.isNotEmpty()) {
            val outputRefs = node.metadata.outputs.mapNotNull { it.ontologyRef }.toSet()
            if (query.outputTypes.none { it in outputRefs }) return false
        }
        if (query.methods.isNotEmpty()) {
            val methodNames = node.metadata.methods.map { it.name }.toSet()
            if (query.methods.none { it in methodNames }) return false
        }
        return true
    }

    private fun nodeKey(id: String, version: String) = "$id@$version"

    private fun splitNodeKey(key: String): Pair<String, String> {
        val lastAt = key.lastIndexOf('@')
        return if (lastAt > 0) key.substring(0, lastAt) to key.substring(lastAt + 1)
        else key to ""
    }
}
