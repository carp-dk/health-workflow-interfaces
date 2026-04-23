package health.workflows.server.auth

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiKeyAuthTest {

    private fun extractGeneratedKeys(keysFile: File): List<String> {
        val keyRegex = Regex("""key:\s*"?([a-f0-9]{64})"?""")
        return keyRegex.findAll(keysFile.readText()).map { it.groupValues[1] }.toList()
    }

    private fun extractSingleGeneratedKey(keysFile: File): String {
        return extractGeneratedKeys(keysFile).singleOrNull()
            ?: error("Should write exactly one generated key to keys.yaml")
    }

    // ── KeyStore.of ───────────────────────────────────────────────────────────

    @Test
    fun `resolve returns identity for valid token`() {
        val store = KeyStore.of("token-abc" to UserIdentity("user1", "User One"))
        val identity = store.resolve("token-abc")
        assertNotNull(identity)
        assertEquals("user1", identity.userId)
        assertEquals("User One", identity.name)
    }

    @Test
    fun `resolve returns null for unknown token`() {
        val store = KeyStore.of("token-abc" to UserIdentity("user1", "User One"))
        assertNull(store.resolve("unknown-token"))
    }

    @Test
    fun `resolve returns null for empty string`() {
        val store = KeyStore.of("token-abc" to UserIdentity("user1", "User One"))
        assertNull(store.resolve(""))
    }

    @Test
    fun `empty KeyStore resolves nothing`() {
        val store = KeyStore.of()
        assertNull(store.resolve("any-token"))
    }

    // ── KeyStore.load ─────────────────────────────────────────────────────────

    @Test
    fun `load reads single key from YAML file`(@TempDir tempDir: File) {
        val keysFile = File(tempDir, "keys.yaml")
        keysFile.writeText(
            """
            keys:
              - key: "test-key-123"
                userId: "test@example.com"
                name: "Test User"
            """.trimIndent()
        )

        val store = KeyStore.load(keysFile)
        val identity = store.resolve("test-key-123")
        assertNotNull(identity)
        assertEquals("test@example.com", identity.userId)
        assertEquals("Test User", identity.name)
    }

    @Test
    fun `load with multiple keys resolves each independently`(@TempDir tempDir: File) {
        val keysFile = File(tempDir, "keys.yaml")
        keysFile.writeText(
            """
            keys:
              - key: "key-a"
                userId: "user-a"
                name: "User A"
              - key: "key-b"
                userId: "user-b"
                name: "User B"
            """.trimIndent()
        )

        val store = KeyStore.load(keysFile)
        assertEquals("user-a", store.resolve("key-a")?.userId)
        assertEquals("user-b", store.resolve("key-b")?.userId)
        assertNull(store.resolve("key-c"))
    }

    @Test
    fun `load with empty key list resolves nothing`(@TempDir tempDir: File) {
        val keysFile = File(tempDir, "keys.yaml")
        keysFile.writeText("keys: []\n")

        val store = KeyStore.load(keysFile)
        assertNull(store.resolve("any-token"))
    }

    // ── generateSecureKey ─────────────────────────────────────────────────────

    @Test
    fun `generated key is 64 hex characters`() {
        val key = generateSecureKey()
        assertEquals(64, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' }, "Key must be lowercase hex")
    }

    @Test
    fun `two generated keys are not equal`() {
        val key1 = generateSecureKey()
        val key2 = generateSecureKey()
        assertTrue(key1 != key2, "Consecutive keys must differ")
    }

    // ── GenerateKey main ──────────────────────────────────────────────────────

    @Test
    fun `generateKey main writes a new key to keys yaml`(@TempDir tempDir: File) {
        main(arrayOf("bob@carp.dk", "Bob Smith", tempDir.absolutePath))

        val keysFile = File(tempDir, "keys.yaml")
        assertTrue(keysFile.exists())

        // Extract the key from the generated YAML file
        val generatedKey = extractSingleGeneratedKey(keysFile)

        // Load store and verify the key resolves to correct identity
        val store = KeyStore.load(keysFile)
        val identity = store.resolve(generatedKey)
        assertNotNull(identity)
        assertEquals("bob@carp.dk", identity.userId)
        assertEquals("Bob Smith", identity.name)
    }

    @Test
    fun `generateKey main appends to existing keys yaml`(@TempDir tempDir: File) {
        fun generateAndCaptureKey(userId: String, name: String): String {
            main(arrayOf(userId, name, tempDir.absolutePath))

            val keysFile = File(tempDir, "keys.yaml")
            return extractGeneratedKeys(keysFile).lastOrNull()
                ?: error("Should write at least one generated key for $userId")
        }

        val firstKey = generateAndCaptureKey("first@example.com", "First User")
        val secondKey = generateAndCaptureKey("second@example.com", "Second User")

        val content = File(tempDir, "keys.yaml").readText()
        assertTrue(content.contains("first@example.com"))
        assertTrue(content.contains("second@example.com"))

        val store = KeyStore.load(File(tempDir, "keys.yaml"))
        assertEquals("first@example.com", store.resolve(firstKey)?.userId)
        assertEquals("First User", store.resolve(firstKey)?.name)
        assertEquals("second@example.com", store.resolve(secondKey)?.userId)
        assertEquals("Second User", store.resolve(secondKey)?.name)
    }
}
