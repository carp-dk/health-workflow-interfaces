package health.workflows.server.auth

import com.charleskorn.kaml.Yaml
import java.io.File
import java.security.SecureRandom
import kotlin.system.exitProcess

/**
 * Generates a secure API key (64 hex chars / 256-bit entropy).
 *
 * Internal tool for making keys for tests, it can be called from tests and the CLI .
 */
internal fun generateSecureKey(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * CLI tool for generating a new API key and registering it in keys.yaml.
 *
 * Usage via Gradle:
 *   ./gradlew :server:generateKey -PuserId=bob@carp.dk -Pname="Bob Smith"
 *
 * Optional:
 *   -PconfigDir=path/to/config   (default: config/)
 *
 * The generated key is printed to stdout. keys.yaml is created if it does not exist.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("Usage: generateKey <userId> <name> [configDir]")
        System.err.println("  e.g. generateKey bob@carp.dk \"Bob Smith\"")
        exitProcess(1)
    }

    val userId = args[0]
    val name = args[1]
    val configDir = File(args.getOrElse(2) { "config" })
    val keysFile = File(configDir, "keys.yaml")

    configDir.mkdirs()

    val existing = if (keysFile.exists()) {
        Yaml.default.decodeFromString(KeysConfig.serializer(), keysFile.readText())
    } else {
        KeysConfig(emptyList())
    }

    val newKey = generateSecureKey()
    val updated = existing.copy(keys = existing.keys + KeyEntry(newKey, userId, name))
    keysFile.writeText(Yaml.default.encodeToString(KeysConfig.serializer(), updated))

    println()
    println("  Key generated for '$name' ($userId)")
    println("  Key : $newKey")
    println("  File: ${keysFile.absolutePath}")
    println()
    println("  Pass this key in the Authorization header:")
    println("  Authorization: Bearer $newKey")
    println()
}
