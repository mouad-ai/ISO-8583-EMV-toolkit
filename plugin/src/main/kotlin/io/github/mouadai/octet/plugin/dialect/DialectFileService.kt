package io.github.mouadai.octet.plugin.dialect

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Lists the dialect files a project can use. The loader (M2) turns them into dialects. */
@Service(Service.Level.PROJECT)
class DialectFileService(private val project: Project) {

    enum class Scope { PROJECT, USER }

    data class DialectFile(val file: VirtualFile, val scope: Scope)

    /** Project dialects first, then user dialects; each group sorted by file name. */
    fun dialectFiles(): List<DialectFile> {
        val projectFiles = DialectLocations.projectDirs(project).flatMap { jsonChildren(it) }
            .distinct().sortedBy { it.name }.map { DialectFile(it, Scope.PROJECT) }
        val userFiles = DialectLocations.userDirFile()?.let(::jsonChildren).orEmpty()
            .sortedBy { it.name }.map { DialectFile(it, Scope.USER) }
        return projectFiles + userFiles
    }

    private fun jsonChildren(dir: VirtualFile): List<VirtualFile> =
        dir.children.filter { DialectLocations.isDialectFile(it) }

    companion object {
        fun getInstance(project: Project): DialectFileService = project.service()
    }
}
