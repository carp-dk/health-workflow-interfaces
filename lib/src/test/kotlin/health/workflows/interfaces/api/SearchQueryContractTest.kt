package health.workflows.interfaces.api

import health.workflows.interfaces.model.DataSensitivity
import health.workflows.interfaces.model.WorkflowFormat
import health.workflows.interfaces.model.WorkflowGranularity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SearchQueryContractTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun searchQueryRoundTripSerializationIncludesSemanticFields() {
        val original = SearchQuery(
            keywords = listOf("risk", "score"),
            tags = listOf("dsp", "clinical"),
            format = WorkflowFormat.CARP_DSP,
            platformId = "aware-rapids",
            granularity = WorkflowGranularity.WORKFLOW,
            inputTypes = listOf("tabular", "json"),
            outputTypes = listOf("tabular"),
            methods = listOf("xgboost", "normalise"),
            sensitivityClass = DataSensitivity.PSEUDONYMISED,
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SearchQuery>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun searchQueryDecodesMissingSemanticFieldsToDefaults() {
        val decoded = json.decodeFromString<SearchQuery>("""
            {
              "keywords": ["risk"],
              "tags": ["dsp"],
              "format": "RAPIDS",
              "platformId": "aware"
            }
        """.trimIndent())

        assertEquals(listOf("risk"), decoded.keywords)
        assertEquals(listOf("dsp"), decoded.tags)
        assertEquals(WorkflowFormat.RAPIDS, decoded.format)
        assertEquals("aware", decoded.platformId)
        assertEquals(null, decoded.granularity)
        assertTrue(decoded.inputTypes.isEmpty())
        assertTrue(decoded.outputTypes.isEmpty())
        assertTrue(decoded.methods.isEmpty())
        assertEquals(null, decoded.sensitivityClass)
    }

    @Test
    fun searchRequestRoundTripSerializationPreservesQuery() {
        val request = SearchRequest(
            query = SearchQuery(
                keywords = listOf("cohort"),
                granularity = WorkflowGranularity.SUB_WORKFLOW,
                methods = listOf("rule-engine"),
                sensitivityClass = DataSensitivity.RESTRICTED,
            ),
        )

        val encoded = json.encodeToString(request)
        val decoded = json.decodeFromString<SearchRequest>(encoded)

        assertEquals(request, decoded)
    }
}

