package health.workflows.interfaces.api

/**
 * Shared lineage contract helpers for implementers.
 *
 * Keeps lineage output consistent without forcing HWI to parse native workflows.
 */
object LineageConformance {
    /**
     * Node types a conforming lineage graph may contain.
     *
     * `protocol` represents a study protocol a workflow draws data from, so the
     * relationship between data collection and analysis appears in the same
     * graph as environment and package provenance.
     */
    val allowedNodeTypes: Set<String> = setOf("step", "environment", "package", "protocol")

    /**
     * Edge relations a conforming lineage graph may contain.
     *
     * `CONSUMES_FROM` links a step to a `protocol` node it takes input data from.
     */
    val allowedRelations: Set<String> = setOf("USES", "CONTAINS", "CONSUMES_FROM")

    /**
     * Validate that a lineage graph conforms to the standard contract.
     * Returns a list of human-readable validation errors (empty when valid).
     */
    fun validate(graph: LineageGraph): List<String> {
        val errors = mutableListOf<String>()
        val nodeIds = graph.nodes.map { it.id }
        val nodeIdSet = nodeIds.toSet()

        if (nodeIds.size != nodeIdSet.size) {
            errors.add("LineageGraph contains duplicate node ids.")
        }

        graph.nodes.forEach { node ->
            if (node.type !in allowedNodeTypes) {
                errors.add("Invalid node type '${node.type}' for node '${node.id}'.")
            }
        }

        graph.edges.forEach { edge ->
            if (edge.relation !in allowedRelations) {
                errors.add("Invalid relation '${edge.relation}' from '${edge.fromId}' to '${edge.toId}'.")
            }
            if (edge.fromId !in nodeIdSet) {
                errors.add("Edge fromId '${edge.fromId}' does not match any node id.")
            }
            if (edge.toId !in nodeIdSet) {
                errors.add("Edge toId '${edge.toId}' does not match any node id.")
            }
        }

        return errors
    }
}

