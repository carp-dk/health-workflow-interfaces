package health.workflows.interfaces.api

import health.workflows.interfaces.model.AdaptationSeverity
import health.workflows.interfaces.model.EnvironmentType
import health.workflows.interfaces.model.WorkflowArtifactPackage
import health.workflows.interfaces.model.WorkflowFormat

/**
 * Default stateless implementation of [CompatibilityEvaluator].
 *
 * Evaluation rules (applied in order):
 * 1. **Format** — if `pkg.native.format` is not in `profile.supportedFormats`, emit a BLOCKING hint.
 * 2. **Script languages** — for each distinct script language in `pkg.scripts` that is absent from
 *    `profile.constraints.supportedScriptLanguages`, emit a WARNING hint.
 * 3. **Environments (CARP_DSP only)** — the `environments` block in the workflow YAML is scanned for
 *    `kind:` values, which are mapped to [EnvironmentType]. Each type absent from
 *    `profile.supportedEnvironments` produces a BLOCKING hint.
 *    This check is skipped for other formats whose internal structure is not yet defined.
 *
 * Signal derivation:
 * - [CompatibilitySignal.INCOMPATIBLE] if any hint is BLOCKING
 * - [CompatibilitySignal.COMPATIBLE_WITH_ADAPTATIONS] if any hint is WARNING (and none are BLOCKING)
 * - [CompatibilitySignal.COMPATIBLE] otherwise
 */
object DefaultCompatibilityEvaluator : CompatibilityEvaluator {

    /**
     * Matches `kind: "pixi"` or `kind: pixi` — the environment kind value in a CARP_DSP workflow YAML.
     */
    private val KIND_PATTERN = Regex("""kind:\s+"?(\w+)"?""")

    override fun evaluate(pkg: WorkflowArtifactPackage, profile: PlatformProfile): CompatibilityReport {
        val hints = mutableListOf<AdaptationHint>()
        val missingEnvs = mutableListOf<EnvironmentType>()

        checkFormat(pkg, profile, hints)
        checkScriptLanguages(pkg, profile, hints)
        checkEnvironments(pkg, profile, hints, missingEnvs)

        return CompatibilityReport(
            signal = deriveSignal(hints),
            platformId = profile.platformId,
            requiredAdaptations = hints,
            missingEnvironments = missingEnvs,
        )
    }

    private fun checkFormat(
        pkg: WorkflowArtifactPackage,
        profile: PlatformProfile,
        hints: MutableList<AdaptationHint>,
    ) {
        if (pkg.native.format !in profile.supportedFormats) {
            hints += AdaptationHint(
                severity = AdaptationSeverity.BLOCKING,
                message = "Workflow format '${pkg.native.format}' is not supported by '${profile.platformId}'. " +
                        "Supported formats: ${profile.supportedFormats.joinToString()}.",
                field = "native.format",
            )
        }
    }

    private fun checkScriptLanguages(
        pkg: WorkflowArtifactPackage,
        profile: PlatformProfile,
        hints: MutableList<AdaptationHint>,
    ) {
        val unsupported = pkg.scripts
            .orEmpty()
            .map { it.language }
            .distinct()
            .filter { it !in profile.constraints.supportedScriptLanguages }

        for (language in unsupported) {
            hints += AdaptationHint(
                severity = AdaptationSeverity.WARNING,
                message = "Script language '$language' is not supported by '${profile.platformId}'. " +
                        "Supported languages: ${profile.constraints.supportedScriptLanguages.joinToString()}.",
                field = "scripts[$language]",
            )
        }
    }

    /**
     * Environment check is only performed for [WorkflowFormat.CARP_DSP] packages, whose workflow
     * YAML contains a top-level `environments` block with entries of the form:
     *
     * ```yaml
     * environments:
     *   my-env:
     *     kind: "pixi"   # or conda / r / system / docker
     *     ...
     * ```
     *
     * The `kind` field is extracted via regex and mapped to [EnvironmentType]. Each type that is
     * absent from [PlatformProfile.supportedEnvironments] produces a BLOCKING hint.
     *
     * For other formats (RAPIDS, CWL) the environment structure is not yet standardized, so the
     * check is skipped and [missingEnvs] is left unchanged.
     */
    private fun checkEnvironments(
        pkg: WorkflowArtifactPackage,
        profile: PlatformProfile,
        hints: MutableList<AdaptationHint>,
        missingEnvs: MutableList<EnvironmentType>,
    ) {
        if (pkg.native.format != WorkflowFormat.CARP_DSP) return

        val required = KIND_PATTERN.findAll(pkg.native.content)
            .mapNotNull { kindToEnvironmentType(it.groupValues[1]) }
            .distinct()
            .toList()

        val unsupported = required.filter { it !in profile.supportedEnvironments }
        missingEnvs += unsupported

        for (env in unsupported) {
            hints += AdaptationHint(
                severity = AdaptationSeverity.BLOCKING,
                message = "Environment type '$env' is required by the workflow but not supported by '${profile.platformId}'. " +
                        "Supported environments: ${profile.supportedEnvironments.joinToString()}.",
                field = "environments[$env]",
            )
        }
    }

    private fun kindToEnvironmentType(kind: String): EnvironmentType? = when (kind.lowercase()) {
        "system" -> EnvironmentType.SYSTEM
        "pixi"   -> EnvironmentType.PIXI
        "conda"  -> EnvironmentType.CONDA
        "r"      -> EnvironmentType.R
        "docker" -> EnvironmentType.DOCKER
        else     -> null
    }

    private fun deriveSignal(hints: List<AdaptationHint>): CompatibilitySignal = when {
        hints.any { it.severity == AdaptationSeverity.BLOCKING } -> CompatibilitySignal.INCOMPATIBLE
        hints.any { it.severity == AdaptationSeverity.WARNING }  -> CompatibilitySignal.COMPATIBLE_WITH_ADAPTATIONS
        else                                                      -> CompatibilitySignal.COMPATIBLE
    }
}
