package health.workflows.interfaces.model

import carp.interfaces.model.AdaptationSeverity
import carp.interfaces.model.ComponentRef
import carp.interfaces.model.CwlTranslationAsset
import carp.interfaces.model.EnvironmentType
import carp.interfaces.model.NativeWorkflowAsset
import carp.interfaces.model.PackageMetadata
import carp.interfaces.model.RoCrateMetadata
import carp.interfaces.model.ScriptLanguage
import carp.interfaces.model.SupportingScript
import carp.interfaces.model.ValidationAssets
import carp.interfaces.model.WorkflowArtifactPackage
import carp.interfaces.model.WorkflowFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WorkflowModelsSerializationTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun workflowArtifactPackageRoundTripSerialization() {
        val original = WorkflowArtifactPackage(
            id = "workflow.pkg.alpha",
            version = "1.0.0",
            contentHash = "sha256:5f16f8d91f2dce13c6d04f87ca9f973641f92be9ed6077dbf637f2ca4490f7ca",
            metadata = PackageMetadata(
                name = "Risk Scoring Workflow",
                description = "End-to-end DSP workflow package",
                authors = listOf("Alice", "Bob"),
                license = "Apache-2.0",
                tags = listOf("dsp", "cwl", "snakemake"),
            ),
            native = NativeWorkflowAsset(
                format = WorkflowFormat.CARP_DSP,
                content = "workflow: risk-scoring",
            ),
            cwl = CwlTranslationAsset(
                content = "cwlVersion: v1.2",
                toolVersion = "v1.2.1",
            ),
            scripts = listOf(
                SupportingScript(
                    name = "prepare_data.py",
                    language = ScriptLanguage.PYTHON,
                    content = "print('prepare')",
                ),
                SupportingScript(
                    name = "run.sh",
                    language = ScriptLanguage.BASH,
                    content = "echo run",
                ),
            ),
            roCrate = RoCrateMetadata(
                crateId = "crate-123",
                conformsTo = "https://w3id.org/ro/crate/1.1",
                content = "{\"@context\":\"https://w3id.org/ro/crate/1.1/context\"}",
            ),
            dependencies = listOf(
                ComponentRef(
                    id = "component.qc",
                    version = "2.1.0",
                    registry = "carp-registry",
                ),
            ),
            execution = buildJsonObject {
                put("runner", "aware")
                put("environment", "conda")
            },
            validation = ValidationAssets(
                schemas = mapOf("workflowSchema" to "{\"type\":\"object\"}"),
                testInputs = mapOf("smoke" to "{\"sample\":\"input\"}"),
            ),
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<WorkflowArtifactPackage>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun packageMetadataRoundTripSerialization() {
        assertRoundTrip(
            PackageMetadata(
                name = "Metadata Name",
                description = "Description",
                authors = listOf("Author"),
                license = "MIT",
                tags = listOf("tag1", "tag2"),
            ),
        )
    }

    @Test
    fun nativeWorkflowAssetRoundTripSerialization() {
        assertRoundTrip(
            NativeWorkflowAsset(
                format = WorkflowFormat.RAPIDS,
                content = "pipeline: rapids",
            ),
        )
    }

    @Test
    fun cwlTranslationAssetRoundTripSerialization() {
        assertRoundTrip(
            CwlTranslationAsset(
                content = "class: Workflow",
                toolVersion = "v1.2",
            ),
        )
    }

    @Test
    fun supportingScriptRoundTripSerialization() {
        assertRoundTrip(
            SupportingScript(
                name = "script.R",
                language = ScriptLanguage.R,
                content = "print('hello')",
            ),
        )
    }

    @Test
    fun roCrateMetadataRoundTripSerialization() {
        assertRoundTrip(
            RoCrateMetadata(
                crateId = "crate-xyz",
                conformsTo = "https://w3id.org/ro/crate/1.1",
                content = "{\"name\":\"crate\"}",
            ),
        )
    }

    @Test
    fun componentRefRoundTripSerialization() {
        assertRoundTrip(
            ComponentRef(
                id = "component-a",
                version = "0.3.1",
                registry = "central",
            ),
        )
    }

    @Test
    fun validationAssetsRoundTripSerialization() {
        assertRoundTrip(
            ValidationAssets(
                schemas = mapOf("schema1" to "{}"),
                testInputs = mapOf("input1" to "{\"value\":1}"),
            ),
        )
    }

    @Test
    fun workflowFormatEnumRoundTripSerialization() {
        assertEnumRoundTrip(WorkflowFormat.CWL)
    }

    @Test
    fun environmentTypeEnumRoundTripSerialization() {
        assertEnumRoundTrip(EnvironmentType.DOCKER)
    }

    @Test
    fun scriptLanguageEnumRoundTripSerialization() {
        assertEnumRoundTrip(ScriptLanguage.SHELL)
    }

    @Test
    fun adaptationSeverityEnumRoundTripSerialization() {
        assertEnumRoundTrip(AdaptationSeverity.WARNING)
    }

    private inline fun <reified T> assertRoundTrip(value: T) {
        val encoded = json.encodeToString(value)
        val decoded = json.decodeFromString<T>(encoded)
        assertEquals(value, decoded)
    }

    private inline fun <reified T> assertEnumRoundTrip(value: T) {
        val encoded = json.encodeToString(value)
        val decoded = json.decodeFromString<T>(encoded)
        assertEquals(value, decoded)
    }
}

