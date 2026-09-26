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
        val factory = ContentFactory.getInstance()
        toolWindow.contentManager.addContent(factory.createContent(panel.component, "Decode", false))
        toolWindow.contentManager.addContent(factory.createContent(OctetBuilderPanel().component, "Build", false))
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
