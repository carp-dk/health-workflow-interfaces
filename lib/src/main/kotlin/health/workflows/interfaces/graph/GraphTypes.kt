package health.workflows.interfaces.graph

import health.workflows.interfaces.model.PackageMetadata
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RelationType {
    @SerialName("CONTAINS") CONTAINS,
    @SerialName("PART_OF") PART_OF,
    @SerialName("DEPENDS_ON") DEPENDS_ON,
    @SerialName("IMPLEMENTS") IMPLEMENTS,
    @SerialName("NEW_VERSION_OF") NEW_VERSION_OF,
    @SerialName("DERIVED_FROM") DERIVED_FROM,
    @SerialName("ASSEMBLED_FROM") ASSEMBLED_FROM,
    @SerialName("EXTENDS") EXTENDS,
    @SerialName("VALIDATED_BY") VALIDATED_BY,
    @SerialName("DESCRIBED_IN") DESCRIBED_IN,
}

@Serializable
enum class MethodLevel {
    @SerialName("DOMAIN") DOMAIN,
    @SerialName("ANALYTICAL_PARADIGM") ANALYTICAL_PARADIGM,
    @SerialName("SPECIFIC_METHOD") SPECIFIC_METHOD,
    @SerialName("IMPLEMENTATION") IMPLEMENTATION,
}

@Serializable
enum class ArtifactType {
    @SerialName("DATA") DATA,
    @SerialName("MODEL") MODEL,
    @SerialName("REPORT") REPORT,
    @SerialName("METRIC") METRIC,
    @SerialName("LOG") LOG,
}

@Serializable
sealed class GraphNode {
    abstract val id: String
    abstract val version: String
}

@Serializable
@SerialName("workflow")
data class WorkflowNode(
    override val id: String,
    override val version: String,
    val metadata: PackageMetadata,
) : GraphNode()

@Serializable
@SerialName("step")
data class StepNode(
    override val id: String,
    override val version: String,
    val registry: String? = null,
) : GraphNode()

@Serializable
@SerialName("datatype")
data class DataTypeNode(
    override val id: String,
    /** Data types are identified by URI — version is not applicable. */
    override val version: String = "",
    val ontologyRef: String,
) : GraphNode()

@Serializable
@SerialName("tool")
data class ToolNode(
    override val id: String,
    override val version: String,
    val name: String,
    val toolId: String,
) : GraphNode()

@Serializable
@SerialName("method")
data class MethodNode(
    override val id: String,
    override val version: String,
    val name: String,
    val level: MethodLevel = MethodLevel.SPECIFIC_METHOD,
    val reference: String? = null,
) : GraphNode()

@Serializable
@SerialName("artifact")
data class ArtifactNode(
    override val id: String,
    override val version: String,
    val artifactType: ArtifactType,
) : GraphNode()

@Serializable
data class GraphEdge(
    val from: String,
    val to: String,
    val type: RelationType,
)
