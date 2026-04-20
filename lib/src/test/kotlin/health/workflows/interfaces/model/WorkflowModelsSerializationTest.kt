package health.workflows.interfaces.model

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
                granularity = WorkflowGranularity.WORKFLOW,
                description = "End-to-end DSP workflow package",
                authors = listOf("Alice", "Bob"),
                license = "Apache-2.0",
                tags = listOf("dsp", "cwl", "snakemake"),
                inputs = listOf(
                    PortSummary(
                        id = "patient_table",
                        type = "tabular",
                        format = "csv",
                        ontologyRef = "https://example.org/ontology/patient-table",
                        notes = "Input cohort features",
                    ),
                ),
                outputs = listOf(
                    PortSummary(
                        id = "risk_scores",
                        type = "tabular",
                        format = "parquet",
                    ),
                ),
                methods = listOf(
                    MethodRef(
                        name = "xgboost",
                        toolId = "xgboost",
                        toolVersion = "2.0.0",
                        reference = "https://xgboost.readthedocs.io/",
                    ),
                ),
                sensitivityClass = DataSensitivity.PSEUDONYMISED,
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
                granularity = WorkflowGranularity.SUB_WORKFLOW,
                description = "Description",
                authors = listOf("Author"),
                license = "MIT",
                tags = listOf("tag1", "tag2"),
                inputs = listOf(
                    PortSummary(
                        id = "raw_input",
                        type = "json",
                        format = "application/json",
                        ontologyRef = "https://example.org/ontology/input",
                        notes = "Raw payload",
                    ),
                ),
                outputs = listOf(
                    PortSummary(
                        id = "result",
                        type = "json",
                    ),
                ),
                methods = listOf(
                    MethodRef(
                        name = "rule-engine",
                        toolId = "drools",
                        toolVersion = "8.44.0",
                        reference = "https://www.drools.org/",
                    ),
                ),
                sensitivityClass = DataSensitivity.RESTRICTED,
            ),
        )
    }

    @Test
    fun stepPackageRoundTripSerialization() {
        assertRoundTrip(
            StepPackage(
                metadata = PackageMetadata(
                    name = "Step Package",
                    granularity = WorkflowGranularity.TASK,
                    methods = listOf(MethodRef(name = "normalise", toolId = "pandas")),
                    sensitivityClass = DataSensitivity.PUBLIC,
                ),
                native = NativeWorkflowAsset(
                    format = WorkflowFormat.CARP_DSP,
                    content = "workflow:\n  steps: []",
                ),
                scripts = listOf(
                    SupportingScript(
                        name = "step.py",
                        language = ScriptLanguage.PYTHON,
                        content = "print('step')",
                    ),
                ),
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

    @Test
    fun workflowGranularityEnumRoundTripSerialization() {
        assertEnumRoundTrip(WorkflowGranularity.SUB_WORKFLOW)
    }

    @Test
    fun dataSensitivityEnumRoundTripSerialization() {
        assertEnumRoundTrip(DataSensitivity.IDENTIFIABLE)
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

