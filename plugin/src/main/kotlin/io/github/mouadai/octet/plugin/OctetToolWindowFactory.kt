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
import io.github.mouadai.octet.core.iso.Dialect
import io.github.mouadai.octet.core.iso.DialectCatalog
import io.github.mouadai.octet.core.iso.DialectSource
import io.github.mouadai.octet.plugin.dialect.DialectFileService
import io.github.mouadai.octet.plugin.dialect.DialectsChangedListener
import io.github.mouadai.octet.plugin.licensing.OctetLicense
import io.github.mouadai.octet.plugin.licensing.PaidFeature
import io.github.mouadai.octet.plugin.licensing.PaidFeaturePanel

class OctetToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = OctetDecodePanel { dialectSources(project) }
        project.service<OctetPanelHolder>().panel = panel
        var diffPanel: OctetDiffPanel? = null
        val diffTab = PaidFeaturePanel(PaidFeature.DIFF) {
            OctetDiffPanel { dialects(project) }.also { diffPanel = it }.component
        }
        val buildTab = PaidFeaturePanel(PaidFeature.BUILDER) { OctetBuilderPanel(dialects(project)).component }
        project.messageBus.connect(toolWindow.disposable).subscribe(
            DialectsChangedListener.TOPIC,
            DialectsChangedListener {
                ApplicationManager.getApplication().invokeLater({
                    panel.reloadDialects()
                    diffPanel?.reloadDialects()
                }, project.disposed)
            },
        )
        val factory = ContentFactory.getInstance()
        toolWindow.contentManager.addContent(factory.createContent(panel.component, "Decode", false))
        toolWindow.contentManager.addContent(factory.createContent(buildTab.component, "Build", false))
        toolWindow.contentManager.addContent(factory.createContent(diffTab.component, "Diff", false))
    }

    companion object {
        /**
         * Project and user dialect files as loader input; unreadable files are skipped. Empty
         * without an Octet Pro license, so only the built-ins are offered.
         */
        fun dialectSources(project: Project): List<DialectSource> {
            if (!OctetLicense.isEnabled(PaidFeature.CUSTOM_DIALECTS)) return emptyList()
            return runReadAction {
                DialectFileService.getInstance(project).dialectFiles().mapNotNull { (file, scope) ->
                    runCatching { VfsUtilCore.loadText(file) }.getOrNull()
                        ?.let { DialectSource(file.name, scope.name.lowercase(), it) }
                }
            }
        }

        /** Built-in dialects followed by the project and user dialects that load. */
        fun dialects(project: Project): List<Dialect> =
            DialectCatalog.build(dialectSources(project)).entries.map { it.dialect }
    }
}
