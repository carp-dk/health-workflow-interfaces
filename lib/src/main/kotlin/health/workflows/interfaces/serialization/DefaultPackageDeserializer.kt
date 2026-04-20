package health.workflows.interfaces.serialization

import health.workflows.interfaces.model.WorkflowArtifactPackage
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

class DefaultPackageDeserializer : PackageDeserializer {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val MANIFEST = "package.json"
        private const val HASH_ALGORITHM = "SHA-256"
        private const val HASH_PREFIX = "sha256:"
    }

    override fun fromZip(path: Path): WorkflowArtifactPackage {
        ZipFile(path.toFile()).use { zip ->
            val manifestEntry = zip.getEntry(MANIFEST)
                ?: throw PackageCorruptedException("Missing $MANIFEST in zip: $path")

            val manifest = zip.getInputStream(manifestEntry).use { it.readBytes().decodeToString() }
            val pkg = json.decodeFromString<WorkflowArtifactPackage>(manifest)

            val computedHash = computeZipHash(zip)
            validateHash(expected = pkg.contentHash, actual = computedHash, source = path)

            return pkg
        }
    }

    override fun fromDirectory(path: Path): WorkflowArtifactPackage {
        val manifestFile = path.resolve(MANIFEST).toFile()
        if (!manifestFile.exists()) {
            throw PackageCorruptedException("Missing $MANIFEST in directory: $path")
        }

        val pkg = json.decodeFromString<WorkflowArtifactPackage>(manifestFile.readText())

        val computedHash = computeDirectoryHash(path)
        validateHash(expected = pkg.contentHash, actual = computedHash, source = path)

        return pkg
    }

    /**
     * SHA-256 over all non-manifest zip entries, sorted by entry name, concatenated in order.
     * An archive containing only the manifest produces the hash of empty input.
     */
    private fun computeZipHash(zip: ZipFile): String {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        zip.entries().asSequence()
            .filter { !it.isDirectory && it.name != MANIFEST }
            .sortedBy { it.name }
            .forEach { entry ->
                zip.getInputStream(entry).use { digest.update(it.readBytes()) }
            }
        return HASH_PREFIX + digest.digest().toHex()
    }

    /**
     * SHA-256 over all non-manifest files under [root], sorted by relative path, concatenated.
     */
    private fun computeDirectoryHash(root: Path): String {
        val rootFile = root.toFile()
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        rootFile.walkTopDown()
            .filter { it.isFile && it.name != MANIFEST }
            .sortedBy { it.relativeTo(rootFile).invariantSeparatorsPath }
            .forEach { digest.update(it.readBytes()) }
        return HASH_PREFIX + digest.digest().toHex()
    }

    private fun validateHash(expected: String, actual: String, source: Path) {
        if (expected != actual) {
            throw PackageCorruptedException(
                "Content hash mismatch in $source — expected=$expected actual=$actual"
            )
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
