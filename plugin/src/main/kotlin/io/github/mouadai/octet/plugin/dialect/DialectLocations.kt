package io.github.mouadai.octet.plugin.dialect

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/** Where user-editable dialect files live (SPEC 7): a project folder shared via git, and a per-user folder. */
object DialectLocations {

    /** Project dialect folder, relative to the project root or any content root. */
    const val PROJECT_DIR = ".octet/dialects"

    /** Bundled JSON Schema for dialect files, shipped in `core`. */
    const val SCHEMA_RESOURCE = "/octet/dialect/dialect.schema.json"

    /** Per-user dialect folder in the IDE config directory. */
    fun userDir(): Path = PathManager.getConfigDir().resolve("octet").resolve("dialects")

    /** Existing project dialect folders: under the project dir and under each content root. */
    fun projectDirs(project: Project): List<VirtualFile> {
        val roots = buildList {
            project.guessProjectDir()?.let(::add)
            addAll(ProjectRootManager.getInstance(project).contentRoots)
        }
        return roots.mapNotNull { it.findFileByRelativePath(PROJECT_DIR) }.filter { it.isDirectory }.distinct()
    }

    fun userDirFile(): VirtualFile? = LocalFileSystem.getInstance().findFileByNioFile(userDir())?.takeIf { it.isDirectory }

    fun isDialectFile(file: VirtualFile): Boolean {
        if (file.isDirectory || !file.name.endsWith(".json")) return false
        return isDialectDirPath(file.parent?.path ?: return false)
    }

    /** Path-only check, usable for VFS events whose file may already be gone. */
    fun isDialectDirPath(dirPath: String): Boolean {
        val dir = dirPath.replace('\\', '/').trimEnd('/')
        return dir.endsWith("/$PROJECT_DIR") || dir == userDir().toString().replace('\\', '/').trimEnd('/')
    }

    /** Whether a created, deleted, moved or changed path can affect the set of dialect files. */
    fun affectsDialects(path: String): Boolean {
        val p = path.replace('\\', '/').trimEnd('/')
        if (p.endsWith(".json") && isDialectDirPath(p.substringBeforeLast('/'))) return true
        // Creating, deleting or renaming the folder itself (or its .octet parent).
        return p.endsWith("/.octet") || p.endsWith("/$PROJECT_DIR") || isDialectDirPath(p)
    }
}
