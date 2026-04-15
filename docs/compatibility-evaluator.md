# Compatibility Evaluator

[`CompatibilityEvaluator`](../lib/src/main/kotlin/carp/interfaces/api/CompatibilityEvaluator.kt) is a stateless component that compares a [`WorkflowArtifactPackage`](workflow-models.md) against a [`PlatformProfile`](platform-profile.md) and produces a [`CompatibilityReport`](platform-profile.md#compatibilityreport).

It is called by `ConsumptionInterface.checkCompatibility` and must remain a pure function — no I/O, no network, no mutable state.

The bundled [`DefaultCompatibilityEvaluator`](../lib/src/main/kotlin/carp/interfaces/api/DefaultCompatibilityEvaluator.kt) is the standard implementation and is exposed as a singleton object.

## Interface

```kotlin
interface CompatibilityEvaluator {
    fun evaluate(pkg: WorkflowArtifactPackage, profile: PlatformProfile): CompatibilityReport
}
```

## Evaluation rules

`DefaultCompatibilityEvaluator` applies the following checks in order, collecting an [`AdaptationHint`](platform-profile.md#adaptationhint) for each failure:

### 1. Workflow format (BLOCKING)

```
pkg.native.format ∉ profile.supportedFormats  →  AdaptationHint(BLOCKING, field = "native.format")
```

If the package's native format is not in the platform's supported format list, the workflow cannot run on that platform at all.
This is the only check that can produce a BLOCKING hint.

### 2. Script languages (WARNING)

```
for each distinct language in pkg.scripts:
    language ∉ profile.constraints.supportedScriptLanguages  →  AdaptationHint(WARNING, field = "scripts[<language>]")
```

One hint is emitted per distinct unsupported language, regardless of how many scripts use it.
The workflow can potentially run with adaptation (e.g. adding a language runtime), so this is a WARNING rather than a BLOCKING failure.

### 3. Environments — CARP_DSP only (BLOCKING)

```
for each distinct environment kind in native.content (CARP_DSP format only):
    kind ∉ profile.supportedEnvironments  →  AdaptationHint(BLOCKING, field = "environments[<kind>]")
```

CARP_DSP workflow YAML declares a top-level `environments` block where each entry carries a `kind` field:

```yaml
environments:
  env-pixi:
    name: "python-pixi"
    kind: "pixi"
    spec:
      dependencies: ["pandas", "numpy"]
      pythonVersion: ["3.11"]
  env-system:
    name: "system"
    kind: "system"
    spec: {}
```

The evaluator scans the workflow content for `kind:` values and maps them to `EnvironmentType` (`pixi`, `conda`, `r`, `system`, `docker`).
Each distinct type that is absent from `profile.supportedEnvironments` produces a BLOCKING hint and is added to `CompatibilityReport.missingEnvironments`.

**This check runs only for `CARP_DSP` packages.** For `RAPIDS` and `CWL`, the internal environment structure is not yet standardised, so the check is skipped and `missingEnvironments` is left empty for those formats.

> **Note on unknown kinds:** if the YAML contains a `kind:` value that does not map to any known `EnvironmentType`, it is silently ignored. This keeps the evaluator forward-compatible with new environment types that may appear in workflow definitions before the enum is extended.

## Signal derivation

After all checks, the overall `CompatibilitySignal` is derived from the collected hints:

| Condition | Signal |
|---|---|
| Any hint has severity `BLOCKING` | `INCOMPATIBLE` |
| Any hint has severity `WARNING` (and no `BLOCKING`) | `COMPATIBLE_WITH_ADAPTATIONS` |
| No hints | `COMPATIBLE` |

`BLOCKING` always takes precedence — a package with both a BLOCKING and a WARNING hint produces `INCOMPATIBLE`.

Checks that currently produce BLOCKING: unsupported format, unsupported environment type (CARP_DSP only).
Checks that currently produce WARNING: unsupported script language.

## Usage

```kotlin
val report = DefaultCompatibilityEvaluator.evaluate(pkg, platformProfile)

when (report.signal) {
    CompatibilitySignal.COMPATIBLE -> println("Ready to run")
    CompatibilitySignal.COMPATIBLE_WITH_ADAPTATIONS -> {
        println("Needs adaptation:")
        report.requiredAdaptations.forEach { println("  [${it.severity}] ${it.message}") }
    }
    CompatibilitySignal.INCOMPATIBLE -> println("Cannot run: ${report.requiredAdaptations.first().message}")
}
```

## Output types

The `CompatibilityReport`, `CompatibilitySignal`, and `AdaptationHint` types are defined alongside `PlatformProfile` and documented in [docs/platform-profile.md](platform-profile.md#compatibility-types).
