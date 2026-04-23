package health.workflows.interfaces.api

import health.workflows.interfaces.model.AdaptationSeverity
import health.workflows.interfaces.model.EnvironmentType
import health.workflows.interfaces.model.ScriptLanguage
import health.workflows.interfaces.model.WorkflowFormat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformProfileContractTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun minimalPlatformProfileFieldAccessWorks() {
        val profile = PlatformProfile(
            platformId = "carp-dsp",
            supportedFormats = listOf(WorkflowFormat.CARP_DSP),
            supportedEnvironments = listOf(EnvironmentType.CONDA),
            supportedOperations = listOf("getComponent", "search", "checkCompatibility"),
            constraints = PlatformConstraints(
                maxDependencyDepth = 3,
                requiresDOI = false,
                supportedScriptLanguages = listOf(ScriptLanguage.PYTHON),
            ),
        )

        assertEquals("carp-dsp", profile.platformId)
        assertEquals(listOf(WorkflowFormat.CARP_DSP), profile.supportedFormats)
        assertEquals(listOf(EnvironmentType.CONDA), profile.supportedEnvironments)
        assertEquals(listOf("getComponent", "search", "checkCompatibility"), profile.supportedOperations)
        assertEquals(3, profile.constraints.maxDependencyDepth)
        assertEquals(false, profile.constraints.requiresDOI)
        assertEquals(listOf(ScriptLanguage.PYTHON), profile.constraints.supportedScriptLanguages)
    }

    @Test
    fun compatibilityReportRoundTripSerializationWorks() {
        val original = CompatibilityReport(
            signal = CompatibilitySignal.COMPATIBLE_WITH_ADAPTATIONS,
            platformId = "aware",
            supportedOperations = listOf("getComponent", "search"),
            unsupportedOperations = listOf("publish"),
            missingEnvironments = listOf(EnvironmentType.DOCKER),
            requiredAdaptations = listOf(
                AdaptationHint(
                    severity = AdaptationSeverity.WARNING,
                    message = "Publish step must be routed through a registry adapter.",
                    field = "publish",
                ),
            ),
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<CompatibilityReport>(encoded)

        assertEquals(original, decoded)
        assertEquals(false, decoded.signal == CompatibilitySignal.INCOMPATIBLE)
        assertEquals(true, decoded.compatible)
        assertEquals(listOf("Publish step must be routed through a registry adapter.", "Unsupported operations: publish", "Missing environments: DOCKER"), decoded.reasons)
    }
}

