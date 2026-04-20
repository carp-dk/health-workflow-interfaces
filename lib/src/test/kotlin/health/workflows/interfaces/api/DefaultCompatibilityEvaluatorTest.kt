package health.workflows.interfaces.api

import health.workflows.interfaces.api.CompatibilitySignal
import health.workflows.interfaces.api.DefaultCompatibilityEvaluator
import health.workflows.interfaces.api.PlatformConstraints
import health.workflows.interfaces.api.PlatformProfile
import health.workflows.interfaces.model.AdaptationSeverity
import health.workflows.interfaces.model.EnvironmentType
import health.workflows.interfaces.model.NativeWorkflowAsset
import health.workflows.interfaces.model.PackageMetadata
import health.workflows.interfaces.model.ScriptLanguage
import health.workflows.interfaces.model.SupportingScript
import health.workflows.interfaces.model.WorkflowArtifactPackage
import health.workflows.interfaces.model.WorkflowFormat
import health.workflows.interfaces.model.WorkflowGranularity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultCompatibilityEvaluatorTest {

    // ── fixtures ────────────────────────────────────────────────────────────

    /** A profile that supports CARP_DSP format and Python/Bash scripts. */
    private val carpProfile = profile(
        platformId = "carp-dsp",
        formats = listOf(WorkflowFormat.CARP_DSP),
        scriptLanguages = listOf(ScriptLanguage.PYTHON, ScriptLanguage.BASH),
    )

    // ── COMPATIBLE ──────────────────────────────────────────────────────────

    @Test
    fun `fully compatible package produces COMPATIBLE with no hints`() {
        val pkg = pkg(
            format = WorkflowFormat.CARP_DSP,
            scripts = listOf(
                script("prepare.py", ScriptLanguage.PYTHON),
                script("run.sh", ScriptLanguage.BASH),
            ),
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, carpProfile)

        assertEquals(CompatibilitySignal.COMPATIBLE, report.signal)
        assertTrue(report.requiredAdaptations.isEmpty())
        assertTrue(report.compatible)
    }

    @Test
    fun `package with no scripts produces COMPATIBLE`() {
        val pkg = pkg(format = WorkflowFormat.CARP_DSP)

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, carpProfile)

        assertEquals(CompatibilitySignal.COMPATIBLE, report.signal)
        assertTrue(report.requiredAdaptations.isEmpty())
    }

    // ── COMPATIBLE_WITH_ADAPTATIONS ──────────────────────────────────────────

    @Test
    fun `unsupported script language produces COMPATIBLE_WITH_ADAPTATIONS and WARNING hint`() {
        val pkg = pkg(
            format = WorkflowFormat.CARP_DSP,
            scripts = listOf(script("analysis.R", ScriptLanguage.R)),
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, carpProfile)

        assertEquals(CompatibilitySignal.COMPATIBLE_WITH_ADAPTATIONS, report.signal)
        assertEquals(1, report.requiredAdaptations.size)
        val hint = report.requiredAdaptations.single()
        assertEquals(AdaptationSeverity.WARNING, hint.severity)
        assertEquals("scripts[R]", hint.field)
    }

    @Test
    fun `multiple unsupported script languages each produce a separate WARNING hint`() {
        val pkg = pkg(
            format = WorkflowFormat.CARP_DSP,
            scripts = listOf(
                script("analysis.R", ScriptLanguage.R),
                script("run.sh", ScriptLanguage.SHELL),
            ),
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, carpProfile)

        assertEquals(CompatibilitySignal.COMPATIBLE_WITH_ADAPTATIONS, report.signal)
        assertEquals(2, report.requiredAdaptations.size)
        assertTrue(report.requiredAdaptations.all { it.severity == AdaptationSeverity.WARNING })
    }

    @Test
    fun `duplicate unsupported script language produces only one hint`() {
        val pkg = pkg(
            format = WorkflowFormat.CARP_DSP,
            scripts = listOf(
                script("a.R", ScriptLanguage.R),
                script("b.R", ScriptLanguage.R),
            ),
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, carpProfile)

        assertEquals(CompatibilitySignal.COMPATIBLE_WITH_ADAPTATIONS, report.signal)
        assertEquals(1, report.requiredAdaptations.size)
    }

    // ── INCOMPATIBLE ─────────────────────────────────────────────────────────

    @Test
    fun `unsupported workflow format produces INCOMPATIBLE and BLOCKING hint`() {
        val pkg = pkg(format = WorkflowFormat.RAPIDS)

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, carpProfile)

        assertEquals(CompatibilitySignal.INCOMPATIBLE, report.signal)
        assertEquals(1, report.requiredAdaptations.size)
        val hint = report.requiredAdaptations.single()
        assertEquals(AdaptationSeverity.BLOCKING, hint.severity)
        assertEquals("native.format", hint.field)
    }

    @Test
    fun `BLOCKING hint takes precedence over WARNING - signal is INCOMPATIBLE`() {
        val pkg = pkg(
            format = WorkflowFormat.RAPIDS,                        // BLOCKING
            scripts = listOf(script("analysis.R", ScriptLanguage.R)), // WARNING
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, carpProfile)

        assertEquals(CompatibilitySignal.INCOMPATIBLE, report.signal)
        assertTrue(report.requiredAdaptations.any { it.severity == AdaptationSeverity.BLOCKING })
        assertTrue(report.requiredAdaptations.any { it.severity == AdaptationSeverity.WARNING })
    }

    // ── environment check (CARP_DSP only) ───────────────────────────────────

    @Test
    fun `CARP_DSP package with all environments supported produces COMPATIBLE`() {
        val pkg = pkg(
            format = WorkflowFormat.CARP_DSP,
            content = carpDspYaml("pixi", "system"),
        )
        val profile = profile(
            platformId = "carp-dsp",
            formats = listOf(WorkflowFormat.CARP_DSP),
            environments = listOf(EnvironmentType.PIXI, EnvironmentType.SYSTEM),
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, profile)

        assertEquals(CompatibilitySignal.COMPATIBLE, report.signal)
        assertTrue(report.missingEnvironments.isEmpty())
    }

    @Test
    fun `CARP_DSP package with unsupported environment produces INCOMPATIBLE and BLOCKING hint`() {
        val pkg = pkg(
            format = WorkflowFormat.CARP_DSP,
            content = carpDspYaml("pixi"),
        )
        val profile = profile(
            platformId = "carp-dsp",
            formats = listOf(WorkflowFormat.CARP_DSP),
            environments = listOf(EnvironmentType.CONDA),
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, profile)

        assertEquals(CompatibilitySignal.INCOMPATIBLE, report.signal)
        assertEquals(listOf(EnvironmentType.PIXI), report.missingEnvironments)
        val hint = report.requiredAdaptations.single()
        assertEquals(AdaptationSeverity.BLOCKING, hint.severity)
        assertEquals("environments[PIXI]", hint.field)
    }

    @Test
    fun `CARP_DSP package with multiple unsupported environments produces one hint per type`() {
        val pkg = pkg(
            format = WorkflowFormat.CARP_DSP,
            content = carpDspYaml("pixi", "conda"),
        )
        val profile = profile(
            platformId = "carp-dsp",
            formats = listOf(WorkflowFormat.CARP_DSP),
            environments = listOf(EnvironmentType.SYSTEM),
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, profile)

        assertEquals(CompatibilitySignal.INCOMPATIBLE, report.signal)
        assertEquals(2, report.missingEnvironments.size)
        assertEquals(2, report.requiredAdaptations.count { it.severity == AdaptationSeverity.BLOCKING })
    }

    @Test
    fun `CARP_DSP package with no environments block produces COMPATIBLE`() {
        val pkg = pkg(format = WorkflowFormat.CARP_DSP, content = "workflow:\n  steps: []")
        val profile = profile(
            platformId = "carp-dsp",
            formats = listOf(WorkflowFormat.CARP_DSP),
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, profile)

        assertEquals(CompatibilitySignal.COMPATIBLE, report.signal)
        assertTrue(report.missingEnvironments.isEmpty())
    }

    @Test
    fun `non-CARP_DSP package skips environment check even if profile has no environments`() {
        val pkg = pkg(format = WorkflowFormat.CWL, content = "class: Workflow\ncwlVersion: v1.2")
        val profile = profile(
            platformId = "cwl-runner",
            formats = listOf(WorkflowFormat.CWL),
            environments = emptyList(),
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, profile)

        // No environment hints — CWL structure is not evaluated
        assertEquals(CompatibilitySignal.COMPATIBLE, report.signal)
        assertTrue(report.missingEnvironments.isEmpty())
    }

    @Test
    fun `duplicate environment kinds in YAML produce only one hint`() {
        // Two entries both with kind: pixi
        val yaml = """
            environments:
              env-a:
                kind: "pixi"
              env-b:
                kind: "pixi"
        """.trimIndent()
        val pkg = pkg(format = WorkflowFormat.CARP_DSP, content = yaml)
        val profile = profile(
            platformId = "carp-dsp",
            formats = listOf(WorkflowFormat.CARP_DSP),
            environments = emptyList(),
        )

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, profile)

        assertEquals(1, report.missingEnvironments.size)
        assertEquals(1, report.requiredAdaptations.size)
    }

    // ── report metadata ──────────────────────────────────────────────────────

    @Test
    fun `report carries the evaluated platform id`() {
        val pkg = pkg(format = WorkflowFormat.CARP_DSP)

        val report = DefaultCompatibilityEvaluator.evaluate(pkg, carpProfile)

        assertEquals("carp-dsp", report.platformId)
    }

    // ── builder helpers ──────────────────────────────────────────────────────

    /**
     * Minimal CARP_DSP workflow YAML with one or more environment entries of the given kinds.
     * Mirrors the structure documented in the CARP_DSP format spec.
     */
    private fun carpDspYaml(vararg kinds: String): String = buildString {
        appendLine("workflow:")
        appendLine("  steps: []")
        appendLine("environments:")
        kinds.forEachIndexed { i, kind ->
            appendLine("  env-$i:")
            appendLine("    name: \"$kind-env\"")
            appendLine("    kind: \"$kind\"")
            appendLine("    spec: {}")
        }
    }

    private fun pkg(
        format: WorkflowFormat,
        content: String = "workflow: test",
        scripts: List<SupportingScript>? = null,
    ) = WorkflowArtifactPackage(
        id = "test.pkg",
        version = "1.0.0",
        contentHash = "sha256:stub",
        metadata = PackageMetadata(name = "Test Package", granularity = WorkflowGranularity.WORKFLOW),
        native = NativeWorkflowAsset(format = format, content = content),
        scripts = scripts,
    )

    private fun script(name: String, language: ScriptLanguage) =
        SupportingScript(name = name, language = language, content = "# stub")

    private fun profile(
        platformId: String,
        formats: List<WorkflowFormat>,
        scriptLanguages: List<ScriptLanguage> = emptyList(),
        environments: List<EnvironmentType> = emptyList(),
    ) = object : PlatformProfile {
        override val platformId = platformId
        override val supportedFormats = formats
        override val supportedEnvironments = environments
        override val supportedOperations = emptyList<String>()
        override val constraints = PlatformConstraints(
            maxDependencyDepth = 5,
            requiresDOI = false,
            supportedScriptLanguages = scriptLanguages,
        )
    }
}
