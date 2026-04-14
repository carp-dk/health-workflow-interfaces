package carp.interfaces.serialization

import carp.interfaces.model.WorkflowArtifactPackage
import java.nio.file.Path

interface PackageDeserializer {
    /**
     * Loads a [WorkflowArtifactPackage] from a zip archive.
     *
     * Expects a `package.json` manifest at the zip root. The `contentHash` stored in the
     * manifest is validated against an SHA-256 digest computed over all non-manifest entries
     * (sorted by name). Throws [PackageCorruptedException] on mismatch or missing manifest.
     */
    fun fromZip(path: Path): WorkflowArtifactPackage

    /**
     * Loads a [WorkflowArtifactPackage] from a directory.
     *
     * Expects a `package.json` manifest at the directory root. The `contentHash` stored in the
     * manifest is validated against an SHA-256 digest computed over all non-manifest files
     * (sorted by relative path). Throws [PackageCorruptedException] on mismatch or missing manifest.
     */
    fun fromDirectory(path: Path): WorkflowArtifactPackage
}
