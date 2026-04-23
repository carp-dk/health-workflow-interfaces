package health.workflows.server

import health.workflows.interfaces.api.DefaultCompatibilityEvaluator
import health.workflows.interfaces.graph.InMemoryComponentIndex
import health.workflows.interfaces.graph.loadFromJsonString
import health.workflows.server.auth.KeyStore
import health.workflows.server.service.RegistryService
import health.workflows.server.store.PackageStore
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.File

fun main() {
    val dataDir = File(System.getProperty("data.dir", "data"))
    val configDir = File(System.getProperty("config.dir", "config"))
    val port = System.getProperty("server.port", "8080").toInt()

    val keysFile = File(configDir, "keys.yaml")
    require(keysFile.exists()) {
        "Missing ${keysFile.path} — copy config/keys.example.yaml and populate with real keys"
    }
    val keyStore = KeyStore.load(keysFile)

    val graphStateFile = File(dataDir, "graph-state.json")
    val index = InMemoryComponentIndex()
    if (graphStateFile.exists()) {
        index.loadFromJsonString(graphStateFile.readText())
    }

    val service = RegistryService(
        index = index,
        store = PackageStore(File(dataDir, "packages")),
        evaluator = DefaultCompatibilityEvaluator,
        graphStateFile = graphStateFile,
    )

    embeddedServer(CIO, port = port) {
        configureApp(service, index, keyStore)
    }.start(wait = true)
}
