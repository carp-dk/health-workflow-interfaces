package health.workflows.interfaces.model.api

import health.workflows.interfaces.model.WorkflowArtifactPackage

/**
 * Stateless evaluator that compares a [WorkflowArtifactPackage] against a [PlatformProfile]
 * and produces a [CompatibilityReport].
 *
 * Implementations must be pure functions — no I/O, no network, no mutable state.
 */
interface CompatibilityEvaluator {
    fun evaluate(pkg: WorkflowArtifactPackage, profile: PlatformProfile): CompatibilityReport
}
