package carp.interfaces.serialization

import carp.interfaces.model.NativeWorkflowAsset
import carp.interfaces.model.PackageMetadata
import carp.interfaces.model.WorkflowArtifactPackage
import carp.interfaces.model.WorkflowFormat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultPackageDeserializerTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val deserializer = DefaultPackageDeserializer()

    // --- fromZip ---

    @Test
    fun `fromZip round-trip returns equal package`() {
        val assetBytes = "native workflow content".toByteArray()
        val pkg = minimalPackage(contentHash = hashOf(assetBytes))

        val zipBytes = buildZip(
            MANIFEST to json.encodeToString(pkg).toByteArray(),
            "workflow.main" to assetBytes,
        )

        val loaded = withTempZip(zipBytes) { deserializer.fromZip(it) }

        assertEquals(pkg, loaded)
    }

    @Test
    fun `fromZip with manifest-only zip uses empty hash`() {
        val emptyHash = hashOf()
        val pkg = minimalPackage(contentHash = emptyHash)

        val zipBytes = buildZip(MANIFEST to json.encodeToString(pkg).toByteArray())

        val loaded = withTempZip(zipBytes) { deserializer.fromZip(it) }

        assertEquals(pkg, loaded)
    }

    @Test
    fun `fromZip throws PackageCorruptedException on hash mismatch`() {
        val pkg = minimalPackage(contentHash = "sha256:deadbeef")

        val zipBytes = buildZip(
            MANIFEST to json.encodeToString(pkg).toByteArray(),
            "workflow.main" to "tampered content".toByteArray(),
        )

        withTempZip(zipBytes) { path ->
            assertFailsWith<PackageCorruptedException> {
                deserializer.fromZip(path)
            }
        }
    }

    @Test
    fun `fromZip throws PackageCorruptedException when manifest is absent`() {
        val zipBytes = buildZip("unrelated.txt" to "hello".toByteArray())

        withTempZip(zipBytes) { path ->
            assertFailsWith<PackageCorruptedException> {
                deserializer.fromZip(path)
            }
        }
    }

    // --- fromDirectory ---

    @Test
    fun `fromDirectory round-trip returns equal package`() {
        val assetBytes = "directory workflow content".toByteArray()
        val pkg = minimalPackage(contentHash = hashOf(assetBytes))

        val loaded = withTempDir { dir ->
            dir.resolve(MANIFEST).toFile().writeText(json.encodeToString(pkg))
            dir.resolve("workflow.main").toFile().writeBytes(assetBytes)
            deserializer.fromDirectory(dir)
        }

        assertEquals(pkg, loaded)
    }

    @Test
    fun `fromDirectory with no extra files uses empty hash`() {
        val pkg = minimalPackage(contentHash = hashOf())

        val loaded = withTempDir { dir ->
            dir.resolve(MANIFEST).toFile().writeText(json.encodeToString(pkg))
            deserializer.fromDirectory(dir)
        }

        assertEquals(pkg, loaded)
    }

    @Test
    fun `fromDirectory throws PackageCorruptedException on hash mismatch`() {
        val pkg = minimalPackage(contentHash = "sha256:deadbeef")

        withTempDir { dir ->
            dir.resolve(MANIFEST).toFile().writeText(json.encodeToString(pkg))
            dir.resolve("workflow.main").toFile().writeBytes("tampered".toByteArray())

            assertFailsWith<PackageCorruptedException> {
                deserializer.fromDirectory(dir)
            }
        }
    }

    @Test
    fun `fromDirectory throws PackageCorruptedException when manifest is absent`() {
        withTempDir { dir ->
            assertFailsWith<PackageCorruptedException> {
                deserializer.fromDirectory(dir)
            }
        }
    }

    // --- hash ordering ---

    @Test
    fun `fromZip hash is entry-order independent`() {
        val a = "content-a".toByteArray()
        val b = "content-b".toByteArray()
        val hash = hashOf(a, b) // sorted: a.bin < b.bin

        val pkg = minimalPackage(contentHash = hash)
        val manifest = json.encodeToString(pkg).toByteArray()

        val zipBytes = buildZip(
            MANIFEST to manifest,
            "b.bin" to b,
            "a.bin" to a,
        )

        val loaded = withTempZip(zipBytes) { deserializer.fromZip(it) }
        assertEquals(pkg, loaded)
    }

    // --- helpers ---

    private fun minimalPackage(contentHash: String) = WorkflowArtifactPackage(
        id = "test.workflow.pkg",
        version = "1.0.0",
        contentHash = contentHash,
        metadata = PackageMetadata(name = "Test Workflow"),
        native = NativeWorkflowAsset(format = WorkflowFormat.CARP_DSP, content = "workflow: test"),
    )

    /** SHA-256 over [chunks] concatenated in the given order, prefixed with "sha256:". */
    private fun hashOf(vararg chunks: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        chunks.sortedBy { it.decodeToString() } // mirror sort-by-name used by impl
        chunks.forEach { digest.update(it) }
        return "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun buildZip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun <T> withTempZip(bytes: ByteArray, block: (java.nio.file.Path) -> T): T {
        val path = Files.createTempFile("wap-test", ".zip")
        return try {
            path.toFile().writeBytes(bytes)
            block(path)
        } finally {
            path.toFile().delete()
        }
    }

    private fun <T> withTempDir(block: (java.nio.file.Path) -> T): T {
        val dir = Files.createTempDirectory("wap-dir-test")
        return try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    companion object {
        private const val MANIFEST = "package.json"
    }
}
