package health.workflows.interfaces.graph

import health.workflows.interfaces.graph.GraphEdge
import health.workflows.interfaces.graph.GraphNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Snapshot of the full graph suitable for JSON persistence (e.g. git-backed storage). */
@Serializable
data class GraphState(
    val nodes: Map<String, GraphNode>,
    val edges: List<GraphEdge>,
)

private val graphJson = Json { ignoreUnknownKeys = true }

fun InMemoryComponentIndex.toGraphState(): GraphState = GraphState(nodes.toMap(), edges.toList())

fun InMemoryComponentIndex.loadGraphState(state: GraphState) {
    nodes.clear()
    nodes.putAll(state.nodes)
    edges.clear()
    edges.addAll(state.edges)
}

fun InMemoryComponentIndex.encodeToJsonString(): String =
    graphJson.encodeToString(GraphState.serializer(), toGraphState())

fun InMemoryComponentIndex.loadFromJsonString(json: String): Unit =
    loadGraphState(graphJson.decodeFromString(GraphState.serializer(), json))
