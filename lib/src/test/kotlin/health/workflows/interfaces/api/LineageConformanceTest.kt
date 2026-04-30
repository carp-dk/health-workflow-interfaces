package health.workflows.interfaces.api

import kotlin.test.Test
import kotlin.test.assertTrue

class LineageConformanceTest {

    @Test
    fun `valid graph produces no conformance errors`() {
        val graph = LineageGraph(
            nodes = listOf(
                LineageNode(id = "s1", version = "", type = "step", label = "step"),
                LineageNode(id = "env1", version = "3.11", type = "environment", label = "python"),
                LineageNode(id = "env1:pandas", version = "", type = "package", label = "pandas"),
            ),
            edges = listOf(
                LineageEdge(fromId = "s1", fromVersion = "", toId = "env1", toVersion = "3.11", relation = "USES"),
                LineageEdge(fromId = "env1", fromVersion = "3.11", toId = "env1:pandas", toVersion = "", relation = "CONTAINS"),
            ),
        )

        val errors = LineageConformance.validate(graph)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `invalid graph reports conformance errors`() {
        val graph = LineageGraph(
            nodes = listOf(LineageNode(id = "s1", version = "", type = "invalid", label = "step")),
            edges = listOf(LineageEdge(fromId = "missing", fromVersion = "", toId = "s1", toVersion = "", relation = "UNKNOWN")),
        )

        val errors = LineageConformance.validate(graph)
        assertTrue(errors.isNotEmpty())
    }
}

