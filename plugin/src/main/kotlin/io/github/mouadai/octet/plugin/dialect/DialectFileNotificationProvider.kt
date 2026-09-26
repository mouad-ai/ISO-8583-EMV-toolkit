package io.github.mouadai.octet.plugin.dialect

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import io.github.mouadai.octet.core.iso.DialectCatalog
import io.github.mouadai.octet.plugin.OctetToolWindowFactory
import io.github.mouadai.octet.plugin.licensing.OctetLicense
import io.github.mouadai.octet.plugin.licensing.PaidFeature
import java.util.function.Function
import javax.swing.JComponent

/**
 * Banner on dialect files (M5): the load error when the saved file does not load, or a note that
 * project and user dialects need Octet Pro.
 */
class DialectFileNotificationProvider : EditorNotificationProvider, DumbAware {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!DialectLocations.isDialectFile(file)) return null
        if (!OctetLicense.isEnabled(PaidFeature.CUSTOM_DIALECTS)) {
            return Function { editor ->
                EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).apply {
                    text = "${PaidFeature.CUSTOM_DIALECTS.label} are part of Octet Pro. This dialect is not offered in the decoder."
                    createActionLabel("Enter license...") { OctetLicense.requestLicense(PaidFeature.CUSTOM_DIALECTS) }
                }
            }
        }
        val problem = loadProblem(project, file) ?: return null
        return Function { editor ->
            EditorNotificationPanel(editor, EditorNotificationPanel.Status.Error).apply {
                text = "Octet cannot load this dialect: $problem"
            }
        }
    }

    companion object {
        /** Why the saved [file] fails to load as a dialect, or null when it loads. */
        fun loadProblem(project: Project, file: VirtualFile): String? {
            val prefix = "${file.name}: "
            return DialectCatalog.build(OctetToolWindowFactory.dialectSources(project)).problems
                .firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)
        }
    }
}

/** Refreshes the banners when dialect files change, since one file can break another's `extends`. */
class DialectBannerRefresher(private val project: Project) : DialectsChangedListener {
    override fun dialectsChanged() {
        EditorNotifications.getInstance(project).updateAllNotifications()
    }
}
