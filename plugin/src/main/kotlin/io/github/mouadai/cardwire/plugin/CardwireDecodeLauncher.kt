package io.github.mouadai.cardwire.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import io.github.mouadai.cardwire.core.view.DecodeMode

/** Holds the project's decode panel once the Cardwire tool window has been created. */
@Service(Service.Level.PROJECT)
class CardwirePanelHolder {
    @Volatile
    var panel: CardwireDecodePanel? = null
}

/** Opens the Cardwire tool window and decodes [text] there. Used by editor actions and console links. */
object CardwireDecodeLauncher {
    const val TOOL_WINDOW_ID = "Cardwire"

    /** @param mode null keeps the mode currently selected in the tool window */
    fun open(project: Project, text: String, mode: DecodeMode?) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        toolWindow.activate {
            project.service<CardwirePanelHolder>().panel?.decodeText(text, mode)
        }
    }
}
