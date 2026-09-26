package io.github.mouadai.octet.plugin

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class OctetToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val factory = ContentFactory.getInstance()
        toolWindow.contentManager.addContent(factory.createContent(OctetDecodePanel().component, "Decode", false))
        toolWindow.contentManager.addContent(factory.createContent(OctetBuilderPanel().component, "Build", false))
        val panel = OctetDecodePanel()
        project.service<OctetPanelHolder>().panel = panel
        val content = ContentFactory.getInstance().createContent(panel.component, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
