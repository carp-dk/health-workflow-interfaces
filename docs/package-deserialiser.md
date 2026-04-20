# Package Deserializer

[`PackageDeserialiser`](../lib/src/main/kotlin/health/workflows/interfaces/model/serialization/PackageDeserializer.kt) provides shared logic for loading a [`WorkflowArtifactPackage`](workflow-models.md) from a zip archive or a directory.
It is implemented once here so that both CARP-DSP and Aware can consume packages without duplicating deserialization or integrity-checking logic.

The bundled [`DefaultPackageDeserialiser`](../lib/src/main/kotlin/health/workflows/interfaces/model/serialization/DefaultPackageDeserializer.kt) is the standard implementation.
It reads the `package.json` manifest, validates content integrity via SHA-256, and deserializes the full package.

## Interface

```kotlin
interface PackageDeserializer {
    fun fromZip(path: Path): WorkflowArtifactPackage
    fun fromDirectory(path: Path): WorkflowArtifactPackage
}
```

| Method                | Description                                                                            |
|-----------------------|----------------------------------------------------------------------------------------|
| `fromZip(path)`       | Load a package from a zip archive. Expects `package.json` at the archive root.         |
| `fromDirectory(path)` | Load a package from a directory on disk. Expects `package.json` at the directory root. |

Both methods throw [`PackageCorruptedException`](#packagecorruptedexception) when the manifest is missing or the content hash does not match.

## Zip archive format

A valid package archive is a standard zip file containing:

```
my-workflow-1.0.0.zip
├── package.json          ← manifest (required)
└── ...                   ← any additional asset files
```

`package.json` is a JSON serialization of `WorkflowArtifactPackage` produced by `kotlinx.serialization`.
All optional fields (`cwl`, `scripts`, `roCrate`, `execution`, `validation`) may be absent from the JSON — missing fields default to `null` on deserialization.

## Hash algorithm

`contentHash` (stored inside `package.json`) is an SHA-256 digest of the archive contents, formatted as:

```
sha256:<64-character lowercase hex>
```

**What is hashed:** the byte contents of every entry in the archive (or every file in the directory) **except `package.json` itself**, fed to the SHA-256 digest in **ascending lexicographic order by entry name**. Sorting by name makes the hash deterministic regardless of the order entries appear in the archive.

An archive or directory containing only `package.json` has a content hash equal to the SHA-256 of empty input:

```
sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

## DefaultPackageDeserializer

Drop-in implementation. No constructor arguments are required.

```kotlin
val deserializer = DefaultPackageDeserialiser()

// from a zip file
val pkg = deserializer.fromZip(Path.of("workflow-1.0.0.zip"))

// from an unpacked directory
val pkg = deserializer.fromDirectory(Path.of("/data/workflows/workflow-1.0.0/"))
```

**Deserialization is lenient** — `ignoreUnknownKeys = true` is set on the internal `Json` instance, so archives produced by a newer version of the library that contain fields unknown to the current version will load without error.

## PackageCorruptedException

Thrown by both `fromZip` and `fromDirectory` when:

- `package.json` is not present at the expected location, or
- the `contentHash` stored in the manifest does not match the hash computed from the archive contents.

```kotlin
class PackageCorruptedException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

The exception message identifies the source path and, in the case of a hash mismatch, includes both the expected and actual hash values to aid diagnosis.
