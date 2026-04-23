# Component Index

[`ComponentIndex`](../lib/src/main/kotlin/health/workflows/interfaces/api/ComponentIndex.kt) is the graph query interface for indexing and traversing component relationships.
It maintains a node/edge graph of all published workflow packages, enabling discovery by semantic criteria and traversal by relationship type.

The bundled [`InMemoryComponentIndex`](../lib/src/main/kotlin/health/workflows/interfaces/graph/InMemoryComponentIndex.kt) is the R1 reference implementation — an adjacency-map-backed graph with JSON persistence support.

## Interface

```kotlin
interface ComponentIndex {
    suspend fun index(pkg: WorkflowArtifactPackage)
    suspend fun search(query: SearchQuery): List<ComponentRef>
    suspend fun getRelated(id: String, version: String, relation: RelationType): List<ComponentRef>
}
```

| Operation    | Description                                                                       |
|--------------|-----------------------------------------------------------------------------------|
| `index`      | Add all nodes and edges derived from a package to the graph. Idempotent.          |
| `search`     | Return component references matching the given semantic criteria.                 |
| `getRelated` | Return components reachable from the given node via edges of the specified type.  |

## Graph model

Nodes are keyed by `{id}@{version}`. Indexing two packages that reference the same step ID and version produces one `StepNode` — the step is a single shared node in the graph, not a copy.

### Node types

| Node | Fields | Description |
|---|---|---|
| `WorkflowNode` | `id`, `version`, `metadata` | A published workflow package |
| `StepNode` | `id`, `version`, `registry?` | A workflow step, shareable across workflows |
| `DataTypeNode` | `id`, `ontologyRef` | A data type identified by ontology URI. No version — URIs are unique. |
| `MethodNode` | `id`, `version`, `name`, `level`, `reference?` | An analytical method at a given abstraction level |
| `ToolNode` | `id`, `version`, `name`, `toolId` | An external software tool |
| `ArtifactNode` | `id`, `version`, `artifactType` | An execution output (DATA / MODEL / REPORT / METRIC / LOG) |

### Edge types (`RelationType`)

| Relation | Description |
|---|---|
| `CONTAINS` | Workflow contains a step |
| `PART_OF` | Step belongs to a workflow |
| `DEPENDS_ON` | Component depends on another component or data type |
| `IMPLEMENTS` | Workflow or step implements a method |
| `NEW_VERSION_OF` | This component supersedes a prior version |
| `DERIVED_FROM` | This component was derived from another |
| `ASSEMBLED_FROM` | This component was assembled from multiple others |
| `EXTENDS` | This component extends another |
| `VALIDATED_BY` | Validated against a publication or test suite |
| `DESCRIBED_IN` | Described in an external publication |

### Edges wired by `index(pkg)`

`InMemoryComponentIndex.index()` operates on `WorkflowArtifactPackage` metadata in R1.
The following edges are created automatically on each call:

| From | Relation | To | Source |
|---|---|---|---|
| `WorkflowNode` | `CONTAINS` | `StepNode` | `pkg.dependencies` |
| `WorkflowNode` | `DEPENDS_ON` | `DataTypeNode` | `pkg.metadata.inputs + outputs` (when `ontologyRef` is set) |
| `WorkflowNode` | `IMPLEMENTS` | `MethodNode` | `pkg.metadata.methods` |

All edge creation is idempotent — re-indexing the same package does not produce duplicate edges.

> **Post-R1:** when `StepPackage` indexing is added, semantic edges (`DEPENDS_ON`, `IMPLEMENTS`) will attach to `StepNode` instead of `WorkflowNode`, enabling step-level discovery.

### MethodLevel

`MethodNode.level` describes where in the method hierarchy this node sits:

| Value | Description |
|---|---|
| `DOMAIN` | Broad domain (e.g. digital health) |
| `ANALYTICAL_PARADIGM` | Analytical category (e.g. signal processing) |
| `SPECIFIC_METHOD` | Named method (e.g. actigraphy-based sleep detection) |
| `IMPLEMENTATION` | Concrete tool implementation |

## InMemoryComponentIndex

`InMemoryComponentIndex` is the R1 reference implementation. It uses two in-memory collections:

- `nodes: MutableMap<String, GraphNode>` — all nodes keyed by `{id}@{version}`
- `edges: MutableList<GraphEdge>` — all directed edges

Both are exposed for serialisation.

### JSON persistence

`GraphState` captures the full graph as a serialisable snapshot:

```kotlin
@Serializable
data class GraphState(
    val nodes: Map<String, GraphNode>,
    val edges: List<GraphEdge>,
)
```

Extension functions on `InMemoryComponentIndex` handle serialisation:

```kotlin
// Save to string
val json: String = index.encodeToJsonString()

// Restore from string
index.loadFromJsonString(json)
```

This is used by the R0 server to persist graph state to `data/graph-state.json` on every write and restore it on startup — no re-indexing required after a restart.
The server writes to a temporary sibling file first and then moves it into place with replace semantics, which avoids the Windows rename failures that can happen with `File.renameTo()`.

## Usage

```kotlin
val index = InMemoryComponentIndex()

// Index a package — creates nodes and edges
index.index(pkg)

// Search by semantic criteria
val results = index.search(SearchQuery(
    inputTypes = listOf("https://schema.org/Dataset"),
    methods = listOf("heart-rate-analysis"),
))

// Traverse the graph
val steps = index.getRelated("my-workflow", "1.0", RelationType.CONTAINS)
val methods = index.getRelated("my-workflow", "1.0", RelationType.IMPLEMENTS)
```

## Graph state persistence example

```kotlin
// Server startup: restore from disk if available
val stateFile = File("data/graph-state.json")
if (stateFile.exists()) {
    index.loadFromJsonString(stateFile.readText())
}

// After each write: persist to disk with a temp file and replace-safe move
stateFile.writeText(index.encodeToJsonString()) // implementors should use the server helper
```
