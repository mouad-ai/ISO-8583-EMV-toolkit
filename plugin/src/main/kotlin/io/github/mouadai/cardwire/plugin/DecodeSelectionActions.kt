package io.github.mouadai.cardwire.plugin

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import io.github.mouadai.cardwire.core.view.DecodeMode

/**
 * Decodes the text selected in any editor, including the Run/Debug console, in the Cardwire tool
 * window. Enabled only when there is a selection.
 */
abstract class DecodeSelectionAction(private val mode: DecodeMode) : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = e.project != null && editor != null && editor.selectionModel.hasSelection()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val text = e.getData(CommonDataKeys.EDITOR)?.selectionModel?.selectedText
        if (text.isNullOrBlank()) return
        CardwireDecodeLauncher.open(project, text, mode)
    }
}

class DecodeSelectionAsIsoAction : DecodeSelectionAction(DecodeMode.ISO_8583)

class DecodeSelectionAsEmvAction : DecodeSelectionAction(DecodeMode.EMV_TLV)
