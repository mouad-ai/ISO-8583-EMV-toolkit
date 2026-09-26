package io.github.mouadai.octet.plugin.jpos

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import io.github.mouadai.octet.core.jpos.JposImportResult
import io.github.mouadai.octet.core.jpos.JposPackagerImporter
import io.github.mouadai.octet.plugin.licensing.OctetLicense
import io.github.mouadai.octet.plugin.licensing.PaidFeature

/** Tools | Import jPOS Packager: converts a GenericPackager XML file into a project dialect (SPEC 7, M6). */
class ImportJposPackagerAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!OctetLicense.isEnabled(PaidFeature.JPOS_IMPORT)) {
            OctetLicense.requestLicense(PaidFeature.JPOS_IMPORT)
            return
        }
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle(TITLE)
            .withDescription("Choose a jPOS GenericPackager XML file.")
        val source = FileChooser.chooseFile(descriptor, project, null) ?: return

        val outcome = JposDialectImport.importInto(project, VfsUtilCore.loadText(source), source.name)
        val result = outcome.result
        val target = outcome.file
        if (target == null) {
            val problems = (result.errors + outcome.writeError).filterNotNull()
            Messages.showErrorDialog(project, "The packager could not be imported:\n\n" + problems.joinToString("\n"), TITLE)
            return
        }
        FileEditorManager.getInstance(project).openFile(target, true)
        if (result.warnings.isNotEmpty()) {
            Messages.showWarningDialog(
                project,
                "Imported as ${target.name}. Please check:\n\n" + result.warnings.joinToString("\n") { "• $it" },
                TITLE,
            )
        }
    }

    companion object {
        const val ID = "Octet.ImportJposPackager"
        private const val TITLE = "Import jPOS Packager"
    }
}

/** Import logic without UI, so it can be tested headlessly. */
object JposDialectImport {
    const val DIALECT_DIR = ".octet/dialects"

    class Outcome(val result: JposImportResult, val file: VirtualFile?, val writeError: String?)

    /** Converts [xml] and writes it to `.octet/dialects/<id>.json` in the project, never overwriting a file. */
    fun importInto(project: Project, xml: String, fileName: String): Outcome {
        val baseId = JposPackagerImporter.suggestId(fileName)
        val base = project.guessProjectDir() ?: return Outcome(JposImportResult(null, null, emptyList(), emptyList()), null, "The project has no base directory.")
        val dir = WriteCommandAction.runWriteCommandAction(project, Computable { VfsUtil.createDirectoryIfMissing(base, DIALECT_DIR) })
            ?: return Outcome(JposImportResult(null, null, emptyList(), emptyList()), null, "Could not create $DIALECT_DIR.")

        // Pick a free id first; it goes into the file content as well as the file name.
        var id = baseId
        var n = 2
        while (dir.findChild("$id.json") != null) id = "$baseId-${n++}"

        val result = JposPackagerImporter.import(xml, id, fileName.substringBeforeLast('.'))
        val json = result.json
        if (!result.isSuccess || json == null) return Outcome(result, null, null)
        return try {
            val file = WriteCommandAction.runWriteCommandAction(project, Computable {
                dir.createChildData(this, "$id.json").also { VfsUtil.saveText(it, json) }
            })
            Outcome(result, file, null)
        } catch (e: Exception) {
            Outcome(result, null, "Could not write $id.json: ${e.message}")
        }
    }
}
