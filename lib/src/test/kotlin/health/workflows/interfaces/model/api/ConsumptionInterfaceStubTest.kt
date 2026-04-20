package health.workflows.interfaces.model.api

import health.workflows.interfaces.model.ComponentRef
import health.workflows.interfaces.model.NativeWorkflowAsset
import health.workflows.interfaces.model.PackageMetadata
import health.workflows.interfaces.model.WorkflowArtifactPackage
import health.workflows.interfaces.model.WorkflowFormat
import health.workflows.interfaces.model.WorkflowGranularity
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsumptionInterfaceStubTest {
    @Test
    fun noOpStubImplementsInterface() {
        val api: ConsumptionInterface = NoOpConsumptionInterface()
        assertEquals(api::class.simpleName?.contains("NoOp"), true)
    }

    @Test
    fun noOpStubSupportsAllOperations() {
        val api: ConsumptionInterface = NoOpConsumptionInterface()
        var failure: Throwable? = null

        suspend {
            api.getComponent("component.alpha", "1.0.0")
            api.search(SearchQuery(keywords = listOf("alpha")))
            api.publish(samplePackage())
            api.getDOI("component.alpha", "1.0.0")
            api.resolveDependencies("component.alpha", "1.0.0")
            api.checkCompatibility("component.alpha", "1.0.0", "aware")
            api.getLineage("component.alpha", "1.0.0")
            Unit
        }.startCoroutine(
            object : Continuation<Unit> {
                override val context: CoroutineContext = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    failure = result.exceptionOrNull()
                }
            },
        )

        if (failure != null) {
            throw failure as Throwable
        }
    }

    private class NoOpConsumptionInterface : ConsumptionInterface {
        override suspend fun getComponent(id: String, version: String): WorkflowArtifactPackage = samplePackage(id, version)

        override suspend fun search(query: SearchQuery): List<WorkflowArtifactPackage> = listOf(samplePackage())

        override suspend fun publish(pkg: WorkflowArtifactPackage): PublishResult =
            PublishResult(accepted = true, id = pkg.id, version = pkg.version)

        override suspend fun getDOI(id: String, version: String): String = "10.0000/$id.$version"

        override suspend fun resolveDependencies(id: String, version: String): List<ComponentRef> = emptyList()

        override suspend fun checkCompatibility(id: String, version: String, platformId: String): CompatibilityReport =
            CompatibilityReport(
                signal = CompatibilitySignal.COMPATIBLE,
                platformId = platformId,
                supportedOperations = listOf(
                    "getComponent",
                    "search",
                    "publish",
                    "getDOI",
                    "resolveDependencies",
                    "checkCompatibility",
                    "getLineage"
                ),
            )

        override suspend fun getLineage(id: String, version: String): LineageGraph = LineageGraph()
    }

    companion object {
        private fun samplePackage(
            id: String = "component.alpha",
            version: String = "1.0.0",
        ): WorkflowArtifactPackage =
            WorkflowArtifactPackage(
                id = id,
                version = version,
                contentHash = "sha256:stub",
                metadata = PackageMetadata(name = "Stub Package", granularity = WorkflowGranularity.WORKFLOW),
                native = NativeWorkflowAsset(format = WorkflowFormat.CARP_DSP, content = "workflow: stub"),
            )
    }
}


