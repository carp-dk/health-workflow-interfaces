package health.workflows.server.io

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption


internal fun writeTextReplacingAtomically(target: File, content: String) {
    val targetPath = target.toPath()
    targetPath.parent?.let { Files.createDirectories(it) }

    val tempPath = Files.createTempFile(targetPath.parent, target.name, ".tmp")
    try {
        Files.writeString(tempPath, content)
        moveReplacingExisting(tempPath, targetPath)
    } catch (t: Throwable) {
        runCatching { Files.deleteIfExists(tempPath) }
        throw t
    }
}

private fun moveReplacingExisting(source: Path, target: Path) {
    try {
        Files.move(
            source,
            target,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}


