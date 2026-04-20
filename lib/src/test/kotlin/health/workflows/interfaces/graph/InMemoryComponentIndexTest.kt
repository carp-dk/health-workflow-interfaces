package health.workflows.interfaces.graph

import health.workflows.interfaces.model.ComponentRef
import health.workflows.interfaces.model.MethodRef
import health.workflows.interfaces.model.NativeWorkflowAsset
import health.workflows.interfaces.model.PackageMetadata
import health.workflows.interfaces.model.PortSummary
import health.workflows.interfaces.model.WorkflowArtifactPackage
import health.workflows.interfaces.model.WorkflowFormat
import health.workflows.interfaces.model.WorkflowGranularity
import health.workflows.interfaces.api.SearchQuery
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryComponentIndexTest {

    // Runs a suspend block synchronously. Works because InMemoryComponentIndex never suspends.
    private fun runSuspend(block: suspend () -> Unit) {
        var failure: Throwable? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { failure = result.exceptionOrNull() }
        })
        failure?.let { throw it }
    }

    private fun makeWap(
        id: String,
        version: String = "1.0",
        deps: List<ComponentRef> = emptyList(),
        inputs: List<PortSummary> = emptyList(),
        outputs: List<PortSummary> = emptyList(),
        methods: List<MethodRef> = emptyList(),
    ) = WorkflowArtifactPackage(
        id = id,
        version = version,
        contentHash = "sha256:test",
        metadata = PackageMetadata(
            name = "Test $id",
            granularity = WorkflowGranularity.WORKFLOW,
            inputs = inputs,
            outputs = outputs,
            methods = methods,
        ),
        native = NativeWorkflowAsset(WorkflowFormat.CWL, "test"),
        dependencies = deps,
    )

    @Test
    fun `index WAP with two steps creates two StepNodes and CONTAINS edges`() = runSuspend {
        val index = InMemoryComponentIndex()
        val step1 = ComponentRef("step-a", "1.0", "registry.example.org")
        val step2 = ComponentRef("step-b", "1.0", "registry.example.org")
        index.index(makeWap("workflow-1", deps = listOf(step1, step2)))

        val stepNodes = index.nodes.values.filterIsInstance<StepNode>()
        assertEquals(2, stepNodes.size)
        assertTrue(stepNodes.any { it.id == "step-a" && it.version == "1.0" })
        assertTrue(stepNodes.any { it.id == "step-b" && it.version == "1.0" })

        val containsEdges = index.edges.filter { it.type == RelationType.CONTAINS }
        assertEquals(2, containsEdges.size)
        assertTrue(containsEdges.any { it.from == "workflow-1@1.0" && it.to == "step-a@1.0" })
        assertTrue(containsEdges.any { it.from == "workflow-1@1.0" && it.to == "step-b@1.0" })
    }

    @Test
    fun `same step referenced by two workflows produces one StepNode`() = runSuspend {
        val index = InMemoryComponentIndex()
        val sharedStep = ComponentRef("step-shared", "2.0", "registry.example.org")
        index.index(makeWap("workflow-1", deps = listOf(sharedStep)))
        index.index(makeWap("workflow-2", deps = listOf(sharedStep)))

        assertEquals(1, index.nodes.values.filterIsInstance<StepNode>().size)
    }

    @Test
    fun `search by inputType returns matching workflow`() = runSuspend {
        val index = InMemoryComponentIndex()
        index.index(makeWap(
            "workflow-1",
            inputs = listOf(PortSummary("in1", "Dataset", ontologyRef = "https://schema.org/Dataset")),
        ))

        val results = index.search(SearchQuery(inputTypes = listOf("https://schema.org/Dataset")))
        assertEquals(1, results.size)
        assertEquals("workflow-1", results[0].id)
    }

    @Test
    fun `search by inputType does not match when ontologyRef differs`() = runSuspend {
        val index = InMemoryComponentIndex()
        index.index(makeWap(
            "workflow-1",
            inputs = listOf(PortSummary("in1", "Dataset", ontologyRef = "https://schema.org/Dataset")),
        ))

        val results = index.search(SearchQuery(inputTypes = listOf("https://schema.org/Other")))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getRelated by CONTAINS returns step refs`() = runSuspend {
        val index = InMemoryComponentIndex()
        val step = ComponentRef("step-a", "1.0", "registry.example.org")
        index.index(makeWap("workflow-1", deps = listOf(step)))

        val related = index.getRelated("workflow-1", "1.0", RelationType.CONTAINS)
        assertEquals(1, related.size)
        assertEquals("step-a", related[0].id)
        assertEquals("1.0", related[0].version)
    }

    @Test
    fun `index WAP with ontologyRef creates DataTypeNode and DEPENDS_ON edge`() = runSuspend {
        val index = InMemoryComponentIndex()
        index.index(makeWap(
            "workflow-1",
            inputs = listOf(PortSummary("in1", "Dataset", ontologyRef = "https://schema.org/Dataset")),
            outputs = listOf(PortSummary("out1", "Report", ontologyRef = "https://schema.org/Report")),
        ))

        val dtNodes = index.nodes.values.filterIsInstance<DataTypeNode>()
        assertEquals(2, dtNodes.size)
        assertTrue(dtNodes.any { it.ontologyRef == "https://schema.org/Dataset" })
        assertTrue(dtNodes.any { it.ontologyRef == "https://schema.org/Report" })

        val dependsOnEdges = index.edges.filter { it.type == RelationType.DEPENDS_ON }
        assertEquals(2, dependsOnEdges.size)
        assertTrue(dependsOnEdges.all { it.from == "workflow-1@1.0" })
    }

    @Test
    fun `same ontologyRef in two workflows produces one DataTypeNode`() = runSuspend {
        val index = InMemoryComponentIndex()
        val port = PortSummary("in1", "Dataset", ontologyRef = "https://schema.org/Dataset")
        index.index(makeWap("workflow-1", inputs = listOf(port)))
        index.index(makeWap("workflow-2", inputs = listOf(port)))

        assertEquals(1, index.nodes.values.filterIsInstance<DataTypeNode>().size)
        assertEquals(2, index.edges.filter { it.type == RelationType.DEPENDS_ON }.size)
    }

    @Test
    fun `index WAP with method creates MethodNode and IMPLEMENTS edge`() = runSuspend {
        val index = InMemoryComponentIndex()
        index.index(makeWap(
            "workflow-1",
            methods = listOf(MethodRef("heart-rate-analysis", "biobss", toolVersion = "1.2")),
        ))

        val methodNodes = index.nodes.values.filterIsInstance<MethodNode>()
        assertEquals(1, methodNodes.size)
        assertEquals("heart-rate-analysis", methodNodes[0].name)
        assertEquals("1.2", methodNodes[0].version)

        val implementsEdges = index.edges.filter { it.type == RelationType.IMPLEMENTS }
        assertEquals(1, implementsEdges.size)
        assertEquals("workflow-1@1.0", implementsEdges[0].from)
    }

    @Test
    fun `graph state round-trips through JSON without data loss`() = runSuspend {
        val index = InMemoryComponentIndex()
        val step = ComponentRef("step-a", "1.0", "registry.example.org")
        index.index(makeWap("workflow-1", deps = listOf(step)))

        val json = index.encodeToJsonString()
        val restored = InMemoryComponentIndex()
        restored.loadFromJsonString(json)

        assertEquals(index.nodes.size, restored.nodes.size)
        assertEquals(index.edges.size, restored.edges.size)
        assertTrue(restored.nodes.values.filterIsInstance<WorkflowNode>().any { it.id == "workflow-1" })
        assertTrue(restored.nodes.values.filterIsInstance<StepNode>().any { it.id == "step-a" })
    }
}
