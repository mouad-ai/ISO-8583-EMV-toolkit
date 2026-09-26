package io.github.mouadai.octet.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.mouadai.octet.core.iso.DialectSource
import io.github.mouadai.octet.plugin.dialect.DialectFileService
import io.github.mouadai.octet.plugin.dialect.DialectsChangedListener

class OctetToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = OctetDecodePanel { dialectSources(project) }
        project.service<OctetPanelHolder>().panel = panel
        project.messageBus.connect(toolWindow.disposable).subscribe(
            DialectsChangedListener.TOPIC,
            DialectsChangedListener {
                ApplicationManager.getApplication().invokeLater({ panel.reloadDialects() }, project.disposed)
            },
        )
        val content = ContentFactory.getInstance().createContent(panel.component, null, false)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        /** Project and user dialect files as loader input; unreadable files are skipped. */
        fun dialectSources(project: Project): List<DialectSource> = runReadAction {
            DialectFileService.getInstance(project).dialectFiles().mapNotNull { (file, scope) ->
                runCatching { VfsUtilCore.loadText(file) }.getOrNull()
                    ?.let { DialectSource(file.name, scope.name.lowercase(), it) }
            }
        }
    }
}
