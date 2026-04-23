package health.workflows.server.auth

import com.charleskorn.kaml.Yaml
import io.ktor.server.auth.Principal
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
internal data class KeyEntry(
    val key: String,
    val userId: String,
    val name: String,
)

@Serializable
internal data class KeysConfig(val keys: List<KeyEntry>)

/** Resolved identity for an authenticated API key. */
data class UserIdentity(
    val userId: String,
    val name: String,
) : Principal

/** Resolves raw Bearer tokens to [UserIdentity] instances. Thread-safe after construction. */
class KeyStore private constructor(private val entries: Map<String, UserIdentity>) {

    fun resolve(token: String): UserIdentity? = entries[token]

    companion object {
        fun load(file: File): KeyStore {
            val config = Yaml.default.decodeFromString(KeysConfig.serializer(), file.readText())
            return KeyStore(config.keys.associate { it.key to UserIdentity(it.userId, it.name) })
        }

        /** For tests — build a [KeyStore] directly without reading a file. */
        fun of(vararg pairs: Pair<String, UserIdentity>): KeyStore = KeyStore(mapOf(*pairs))
    }
}
