package io.github.mouadai.octet.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import io.github.mouadai.octet.core.view.DecodeMode

/** Holds the project's decode panel once the Octet tool window has been created. */
@Service(Service.Level.PROJECT)
class OctetPanelHolder {
    @Volatile
    var panel: OctetDecodePanel? = null
}

/** Opens the Octet tool window and decodes [text] there. Used by editor actions and console links. */
object OctetDecodeLauncher {
    const val TOOL_WINDOW_ID = "Octet"

    /** @param mode null keeps the mode currently selected in the tool window */
    fun open(project: Project, text: String, mode: DecodeMode?) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        toolWindow.activate {
            project.service<OctetPanelHolder>().panel?.decodeText(text, mode)
        }
    }
}
