package carp.interfaces.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class WorkflowFormat {
    @SerialName("CARP_DSP")
    CARP_DSP,

    @SerialName("RAPIDS")
    RAPIDS,

    @SerialName("CWL")
    CWL,
}

@Serializable
enum class EnvironmentType {
    @SerialName("CONDA")
    CONDA,

    @SerialName("PIXI")
    PIXI,

    @SerialName("R")
    R,

    @SerialName("SYSTEM")
    SYSTEM,

    @SerialName("DOCKER")
    DOCKER,
}

@Serializable
enum class ScriptLanguage {
    @SerialName("PYTHON")
    PYTHON,

    @SerialName("R")
    R,

    @SerialName("BASH")
    BASH,

    @SerialName("SHELL")
    SHELL,
}

@Serializable
enum class AdaptationSeverity {
    @SerialName("BLOCKING")
    BLOCKING,

    @SerialName("WARNING")
    WARNING,

    @SerialName("INFO")
    INFO,
}

@Serializable
data class PackageMetadata(
    val name: String,
    val description: String? = null,
    val authors: List<String>? = null,
    val license: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class NativeWorkflowAsset(
    val format: WorkflowFormat,
    val content: String,
)

@Serializable
data class CwlTranslationAsset(
    val content: String,
    val toolVersion: String? = null,
)

@Serializable
data class SupportingScript(
    val name: String,
    val language: ScriptLanguage,
    val content: String,
)

@Serializable
data class RoCrateMetadata(
    val crateId: String,
    val conformsTo: String,
    val content: String,
)

@Serializable
data class ComponentRef(
    val id: String,
    val version: String,
    val registry: String? = null,
)

@Serializable
data class ValidationAssets(
    val schemas: Map<String, String>? = null,
    val testInputs: Map<String, String>? = null,
)

@Serializable
data class WorkflowArtifactPackage(
    val id: String,
    val version: String,
    val contentHash: String,
    val metadata: PackageMetadata,
    val native: NativeWorkflowAsset,
    val cwl: CwlTranslationAsset? = null,
    val scripts: List<SupportingScript>? = null,
    val roCrate: RoCrateMetadata? = null,
    val dependencies: List<ComponentRef>? = null,
    val execution: JsonElement? = null,
    val validation: ValidationAssets? = null,
)

