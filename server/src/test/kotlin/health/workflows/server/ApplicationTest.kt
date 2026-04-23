package health.workflows.server

import health.workflows.interfaces.api.DefaultCompatibilityEvaluator
import health.workflows.interfaces.api.PlatformConstraints
import health.workflows.interfaces.api.PlatformProfile
import health.workflows.interfaces.api.SearchQuery
import health.workflows.interfaces.graph.InMemoryComponentIndex
import health.workflows.interfaces.model.DataSensitivity
import health.workflows.interfaces.model.NativeWorkflowAsset
import health.workflows.interfaces.model.PackageMetadata
import health.workflows.interfaces.model.ScriptLanguage
import health.workflows.interfaces.model.WorkflowArtifactPackage
import health.workflows.interfaces.model.WorkflowFormat
import health.workflows.interfaces.model.WorkflowGranularity
import health.workflows.server.auth.KeyStore
import health.workflows.server.auth.UserIdentity
import health.workflows.server.service.RegistryService
import health.workflows.server.store.PackageStore
import health.workflows.server.wfhub.MockWorkflowHubPort
import health.workflows.server.wfhub.StubWorkflowHubPort
import health.workflows.server.wfhub.WorkflowHubPort
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @TempDir
    lateinit var tempDir: File

    private val testIdentity = UserIdentity("bob@carp.dk", "Bob Smith")
    private val keyStore = KeyStore.of("test-token" to testIdentity)
    private val testJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private lateinit var index: InMemoryComponentIndex
    private lateinit var store: PackageStore
    private lateinit var service: RegistryService

    @BeforeEach
    fun setUp() {
        index = InMemoryComponentIndex()
        store = PackageStore(File(tempDir, "packages"))
        service = buildService()
    }

    private fun buildService(workflowHub: WorkflowHubPort = MockWorkflowHubPort()) =
        RegistryService(
            index = index,
            store = store,
            evaluator = DefaultCompatibilityEvaluator,
            graphStateFile = File(tempDir, "graph-state.json"),
            workflowHub = workflowHub,
        )

    // ── Auth ──────────────────────────────────────────────────────────────────

    @Test
    fun `missing bearer token returns 401`() = testApplication {
        application { configureApp(service, index, keyStore) }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/components/x/1.0.0").status)
    }

    @Test
    fun `invalid bearer token returns 401`() = testApplication {
        application { configureApp(service, index, keyStore) }
        val resp = client.get("/api/v1/components/x/1.0.0") {
            header(HttpHeaders.Authorization, "Bearer bad-token")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    // ── getComponent ──────────────────────────────────────────────────────────

    @Test
    fun `getComponent for unknown package returns 404`() = testApplication {
        application { configureApp(service, index, keyStore) }
        val resp = client.get("/api/v1/components/missing/1.0.0") {
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `publish then getComponent returns 200 with correct id`() = testApplication {
        application { configureApp(service, index, keyStore) }
        client.publish(wap("my-workflow"))

        val resp = client.get("/api/v1/components/my-workflow/1.0.0") {
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("my-workflow"))
    }

    // ── Publish ───────────────────────────────────────────────────────────────

    @Test
    fun `publish with wrong content hash returns 400`() = testApplication {
        application { configureApp(service, index, keyStore) }
        val badWap = wap("hash-test").copy(contentHash = "definitely-wrong")
        val resp = client.post("/api/v1/components") {
            header(HttpHeaders.Authorization, "Bearer test-token")
            contentType(ContentType.Application.Json)
            setBody(testJson.encodeToString(badWap))
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `publish injects authenticated user into authors`() = testApplication {
        application { configureApp(service, index, keyStore) }
        client.publish(wap("authored-wf"))

        val resp = client.get("/api/v1/components/authored-wf/1.0.0") {
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("bob@carp.dk"))
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Test
    fun `search returns all published packages`() = testApplication {
        application { configureApp(service, index, keyStore) }
        client.publish(wap("wf-alpha", content = "alpha"))
        client.publish(wap("wf-beta", content = "beta"))

        val resp = client.post("/api/v1/components/search") {
            header(HttpHeaders.Authorization, "Bearer test-token")
            contentType(ContentType.Application.Json)
            setBody(testJson.encodeToString(SearchQuery()))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertTrue(body.contains("wf-alpha"))
        assertTrue(body.contains("wf-beta"))
    }

    // ── DOI ───────────────────────────────────────────────────────────────────

    @Test
    fun `getDOI with mock hub returns 200 with doi`() = testApplication {
        application { configureApp(service, index, keyStore) }
        client.publish(wap("doi-wf"))

        val resp = client.get("/api/v1/components/doi-wf/1.0.0/doi") {
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("10.5281/mock.doi-wf.1.0.0"))
    }

    @Test
    fun `getDOI with stub hub returns 501`() = testApplication {
        val stubService = buildService(workflowHub = StubWorkflowHubPort)
        application { configureApp(stubService, index, keyStore) }
        client.publish(wap("doi-stub-wf"))

        val resp = client.get("/api/v1/components/doi-stub-wf/1.0.0/doi") {
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        assertEquals(HttpStatusCode.NotImplemented, resp.status)
    }

    // ── getRelated ────────────────────────────────────────────────────────────

    @Test
    fun `getRelated with missing relation param returns 400`() = testApplication {
        application { configureApp(service, index, keyStore) }
        val resp = client.get("/api/v1/components/x/1.0.0/related") {
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `getRelated with invalid relation value returns 400`() = testApplication {
        application { configureApp(service, index, keyStore) }
        val resp = client.get("/api/v1/components/x/1.0.0/related?relation=NONSENSE") {
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    // ── Compatibility ─────────────────────────────────────────────────────────

    @Test
    fun `checkCompatibility with compatible profile returns 200 with COMPATIBLE signal`() = testApplication {
        application { configureApp(service, index, keyStore) }
        client.publish(wap("compat-wf"))

        val profile = PlatformProfile(
            platformId = "test-platform",
            supportedFormats = listOf(WorkflowFormat.CARP_DSP),
            constraints = PlatformConstraints(
                maxDependencyDepth = 5,
                requiresDOI = false,
                supportedScriptLanguages = listOf(ScriptLanguage.PYTHON),
            ),
        )
        val resp = client.post("/api/v1/components/compat-wf/1.0.0/compatibility") {
            header(HttpHeaders.Authorization, "Bearer test-token")
            contentType(ContentType.Application.Json)
            setBody(testJson.encodeToString(profile))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("COMPATIBLE"))
    }

    @Test
    fun `checkCompatibility with incompatible format returns INCOMPATIBLE signal`() = testApplication {
        application { configureApp(service, index, keyStore) }
        client.publish(wap("compat-wf2"))

        val profile = PlatformProfile(
            platformId = "other-platform",
            supportedFormats = listOf(WorkflowFormat.RAPIDS),
            constraints = PlatformConstraints(
                maxDependencyDepth = 3,
                requiresDOI = false,
            ),
        )
        val resp = client.post("/api/v1/components/compat-wf2/1.0.0/compatibility") {
            header(HttpHeaders.Authorization, "Bearer test-token")
            contentType(ContentType.Application.Json)
            setBody(testJson.encodeToString(profile))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        assertTrue(resp.bodyAsText().contains("INCOMPATIBLE"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun HttpClient.publish(pkg: WorkflowArtifactPackage) {
        val resp = post("/api/v1/components") {
            header(HttpHeaders.Authorization, "Bearer test-token")
            contentType(ContentType.Application.Json)
            setBody(testJson.encodeToString(pkg))
        }
        assertEquals(HttpStatusCode.OK, resp.status, "publish should succeed for ${pkg.id}@${pkg.version}")
    }

    // Each WAP uses its content as the hash input — pass distinct content per package
    // to avoid hash collisions between test packages.
    private fun wap(
        id: String,
        version: String = "1.0.0",
        content: String = id,
        sensitivity: DataSensitivity = DataSensitivity.PUBLIC,
    ) = WorkflowArtifactPackage(
        id = id,
        version = version,
        contentHash = sha256Hex(content),
        metadata = PackageMetadata(
            name = "Test: $id",
            granularity = WorkflowGranularity.WORKFLOW,
            sensitivityClass = sensitivity,
        ),
        native = NativeWorkflowAsset(WorkflowFormat.CARP_DSP, content),
    )
}

private fun sha256Hex(content: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(content.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
