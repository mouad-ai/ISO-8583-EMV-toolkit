package io.github.mouadai.cardwire.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.github.mouadai.cardwire.core.iso.Dialect
import io.github.mouadai.cardwire.core.iso.DialectCatalog
import io.github.mouadai.cardwire.core.iso.DialectSource
import io.github.mouadai.cardwire.plugin.dialect.DialectFileService
import io.github.mouadai.cardwire.plugin.dialect.DialectsChangedListener
import io.github.mouadai.cardwire.plugin.licensing.CardwireLicense
import io.github.mouadai.cardwire.plugin.licensing.PaidFeature
import io.github.mouadai.cardwire.plugin.licensing.PaidFeaturePanel

class CardwireToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = CardwireDecodePanel { dialectSources(project) }
        project.service<CardwirePanelHolder>().panel = panel
        var diffPanel: CardwireDiffPanel? = null
        val diffTab = PaidFeaturePanel(PaidFeature.DIFF) {
            CardwireDiffPanel { dialects(project) }.also { diffPanel = it }.component
        }
        val buildTab = PaidFeaturePanel(PaidFeature.BUILDER) { CardwireBuilderPanel(dialects(project)).component }
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
         * without an Cardwire Pro license, so only the built-ins are offered.
         */
        fun dialectSources(project: Project): List<DialectSource> {
            if (!CardwireLicense.isEnabled(PaidFeature.CUSTOM_DIALECTS)) return emptyList()
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
