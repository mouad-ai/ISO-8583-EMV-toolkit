package io.github.mouadai.cardmsg.plugin

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import io.github.mouadai.cardmsg.core.view.DecodeMode

/** Holds the project's decode panel once the Card Message Toolkit tool window has been created. */
@Service(Service.Level.PROJECT)
class CardMsgPanelHolder {
    @Volatile
    var panel: CardMsgDecodePanel? = null
}

/** Opens the Card Message Toolkit tool window and decodes [text] there. Used by editor actions and console links. */
object CardMsgDecodeLauncher {
    const val TOOL_WINDOW_ID = "Card Message Toolkit"

    /** @param mode null keeps the mode currently selected in the tool window */
    fun open(project: Project, text: String, mode: DecodeMode?) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        toolWindow.activate {
            project.service<CardMsgPanelHolder>().panel?.decodeText(text, mode)
        }
    }
}
