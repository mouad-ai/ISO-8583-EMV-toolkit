package io.github.mouadai.octet.plugin

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import io.github.mouadai.octet.core.builder.BuildResult
import io.github.mouadai.octet.core.builder.ExportFormat
import io.github.mouadai.octet.core.builder.Exporters
import io.github.mouadai.octet.core.builder.MessageBuilder
import io.github.mouadai.octet.core.iso.BuiltinDialects
import io.github.mouadai.octet.core.iso.Dialect
import io.github.mouadai.octet.core.iso.FieldSpec
import io.github.mouadai.octet.core.iso.SubfieldLayout
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.table.AbstractTableModel

/**
 * Builder tool window tab (SPEC 8.3): pick a dialect and MTI, fill field values, edit field 55 as
 * EMV tags, then export the message. Validation, encoding and export all live in `core`.
 */
class OctetBuilderPanel(private val dialects: List<Dialect> = BuiltinDialects.all) {

    internal val dialectCombo = ComboBox(dialects.map { it.name }.toTypedArray())
    internal val mtiField = JBTextField("0100", 6)
    internal val framingCombo = ComboBox<String>()
    internal val fieldsModel = FieldsTableModel()
    internal val fieldsTable = JBTable(fieldsModel).apply {
        setShowGrid(false)
        columnModel.getColumn(FieldsTableModel.USE).maxWidth = 40
        columnModel.getColumn(FieldsTableModel.ID).maxWidth = 50
    }
    internal val formatCombo = ComboBox(ExportFormat.entries.map { it.label }.toTypedArray())
    internal val output = JBTextArea(8, 40).apply {
        isEditable = false
        lineWrap = true
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
    }
    internal val status = JBLabel()

    private var builder = MessageBuilder(dialects.first())
    private var lastBuild: BuildResult? = null

    val component: DialogPanel = panel {
        row("Dialect:") { cell(dialectCombo) }
        row("MTI:") {
            cell(mtiField)
            label("Framing:")
            cell(framingCombo)
        }
        row {
            cell(JBScrollPane(fieldsTable)).align(Align.FILL)
        }.resizableRow()
        row {
            button("Edit EMV Tags…") { editTlvField() }
            button("Build") { build() }
            label("Output:")
            cell(formatCombo)
            button("Copy") { copyOutput() }
        }
        row { cell(status) }
        row {
            cell(JBScrollPane(output)).align(Align.FILL)
        }.resizableRow()
    }

    init {
        dialectCombo.addActionListener { selectDialect(dialects[dialectCombo.selectedIndex.coerceAtLeast(0)]) }
        formatCombo.addActionListener { renderOutput() }
        selectDialect(dialects.first())
    }

    private fun selectDialect(dialect: Dialect) {
        builder = MessageBuilder(dialect)
        framingCombo.removeAllItems()
        dialect.framings.forEach { framingCombo.addItem(it.label) }
        fieldsModel.setFields(builder.fields.map { FieldRow(it, builder.formatHint(it)) })
        lastBuild = null
        output.text = ""
        status.text = ""
    }

    /** Values of the rows marked "Use". */
    internal fun values(): Map<Int, String> =
        fieldsModel.rows.filter { it.use }.associate { it.spec.id to it.value }

    internal fun build() {
        if (fieldsTable.isEditing) fieldsTable.cellEditor.stopCellEditing()
        val framing = builder.dialect.framings.getOrNull(framingCombo.selectedIndex) ?: builder.dialect.framings.first()
        // Framings with a header (e.g. TPDU) get a zero-filled one; the decode tab shows where it sits.
        val header = ByteArray(framing.headerLength).takeIf { it.isNotEmpty() }
        val result = builder.build(mtiField.text.trim(), values(), framing, header)
        lastBuild = result
        if (result.isSuccess) {
            showStatus("Built ${result.bytes!!.size} bytes.", isError = false)
        } else {
            val first = result.errors.first().message
            showStatus(if (result.errors.size == 1) first else "$first (+${result.errors.size - 1} more)", isError = true)
        }
        renderOutput()
    }

    private fun renderOutput() {
        val result = lastBuild
        val bytes = result?.bytes
        output.text = if (result == null || bytes == null) {
            result?.errors?.joinToString("\n") { it.message } ?: ""
        } else {
            val format = ExportFormat.entries[formatCombo.selectedIndex.coerceAtLeast(0)]
            Exporters.export(format, bytes, result.data, builder.dialect)
        }
        output.caretPosition = 0
    }

    private fun copyOutput() {
        if (lastBuild?.isSuccess == true) CopyPasteManager.getInstance().setContents(StringSelection(output.text))
    }

    /** The first field with a BER-TLV layout (field 55 in the built-in dialects). */
    internal fun tlvRow(): FieldRow? = fieldsModel.rows.firstOrNull { it.spec.subfields == SubfieldLayout.BerTlv }

    private fun editTlvField() {
        val row = tlvRow() ?: return showStatus("Dialect '${builder.dialect.name}' has no EMV (BER-TLV) field.", isError = true)
        val dialog = TlvEditorDialog(row.value)
        if (dialog.showAndGet()) setTlvValue(dialog.resultHex)
    }

    internal fun setTlvValue(hex: String) {
        val row = tlvRow() ?: return
        row.value = hex
        row.use = hex.isNotEmpty()
        fieldsModel.fireTableDataChanged()
    }

    private fun showStatus(text: String, isError: Boolean) {
        status.foreground = if (isError) UIUtil.getErrorForeground() else UIUtil.getContextHelpForeground()
        status.text = text
    }

    internal class FieldRow(val spec: FieldSpec, val format: String, var use: Boolean = false, var value: String = "")

    internal class FieldsTableModel : AbstractTableModel() {
        var rows: List<FieldRow> = emptyList()
            private set

        fun setFields(newRows: List<FieldRow>) {
            rows = newRows
            fireTableDataChanged()
        }

        override fun getRowCount() = rows.size
        override fun getColumnCount() = NAMES.size
        override fun getColumnName(column: Int) = NAMES[column]
        override fun getColumnClass(column: Int): Class<*> = when (column) {
            USE -> java.lang.Boolean::class.java
            ID -> java.lang.Integer::class.java
            else -> String::class.java
        }
        override fun isCellEditable(row: Int, column: Int) = column == USE || column == VALUE

        override fun getValueAt(row: Int, column: Int): Any = with(rows[row]) {
            when (column) {
                USE -> use
                ID -> spec.id
                NAME -> spec.name
                FORMAT -> format
                else -> value
            }
        }

        override fun setValueAt(aValue: Any?, row: Int, column: Int) {
            val r = rows[row]
            when (column) {
                USE -> r.use = aValue as? Boolean ?: false
                VALUE -> {
                    r.value = aValue?.toString() ?: ""
                    // Typing a value selects the field; clearing it deselects it.
                    r.use = r.value.isNotEmpty()
                }
            }
            fireTableRowsUpdated(row, row)
        }

        companion object {
            const val USE = 0
            const val ID = 1
            const val NAME = 2
            const val FORMAT = 3
            const val VALUE = 4
            val NAMES = listOf("Use", "Field", "Name", "Format", "Value")
        }
    }
}
