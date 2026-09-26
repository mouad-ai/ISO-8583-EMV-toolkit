package io.github.mouadai.cardwire.plugin.dialect

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/** Turns VFS changes under dialect folders into one [DialectsChangedListener] notification per batch. */
class DialectVfsListener(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        if (project.isDisposed) return
        if (events.any(::touchesDialects)) {
            project.messageBus.syncPublisher(DialectsChangedListener.TOPIC).dialectsChanged()
        }
    }

    private fun touchesDialects(event: VFileEvent): Boolean {
        val paths = when (event) {
            is VFileMoveEvent -> listOf(event.oldPath, event.newPath)
            is VFilePropertyChangeEvent -> listOf(event.oldPath, event.newPath)
            else -> listOf(event.path)
        }
        return paths.any(DialectLocations::affectsDialects)
    }
}
