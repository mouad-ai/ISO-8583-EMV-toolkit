package io.github.mouadai.octet.plugin

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import io.github.mouadai.octet.core.builder.EmvValueEncoder
import io.github.mouadai.octet.core.builder.TlvBuilder
import io.github.mouadai.octet.core.builder.TlvEntry
import io.github.mouadai.octet.core.emv.EmvTagDictionary
import javax.swing.JComponent
import javax.swing.table.AbstractTableModel

/**
 * Field 55 sub-editor: one row per EMV tag, values typed in the tag's own format (amounts, dates,
 * currencies...) or as `hex:...`. Conversion and checks come from `core` ([TlvRows]).
 */
class TlvEditorDialog(initialHex: String) : DialogWrapper(true) {

    internal val rows = TlvRows.fromHex(initialHex)
    internal val table = JBTable(rows)

    /** Hex of the edited TLV; valid after the dialog closes with OK. */
    var resultHex: String = ""
        private set

    init {
        title = "Edit EMV Tags"
        init()
    }

    override fun createCenterPanel(): JComponent =
        ToolbarDecorator.createDecorator(table)
            .setAddAction { rows.add(); table.editCellAt(rows.rowCount - 1, TlvRows.TAG) }
            .setRemoveAction { table.selectedRows.sortedDescending().forEach(rows::remove) }
            .createPanel()

    override fun doValidate(): ValidationInfo? {
        if (table.isEditing) table.cellEditor.stopCellEditing()
        return when (val r = rows.build()) {
            is TlvRows.Built.Ok -> null
            is TlvRows.Built.Error -> ValidationInfo(r.message, table)
        }
    }

    override fun doOKAction() {
        val r = rows.build()
        if (r is TlvRows.Built.Ok) {
            resultHex = r.hex
            super.doOKAction()
        }
    }
}

/** Table model for [TlvEditorDialog]: tag, value as typed, and the tag's name and input hint. */
internal class TlvRows(
    private val encoder: EmvValueEncoder = EmvValueEncoder(),
    private val dictionary: EmvTagDictionary = EmvTagDictionary.default,
) : AbstractTableModel() {

    class Row(var tag: String, var input: String)

    val rows = mutableListOf<Row>()

    sealed interface Built {
        data class Ok(val hex: String) : Built
        data class Error(val message: String) : Built
    }

    fun add(tag: String = "", input: String = "") {
        rows += Row(tag, input)
        fireTableRowsInserted(rows.size - 1, rows.size - 1)
    }

    fun remove(index: Int) {
        rows.removeAt(index)
        fireTableRowsDeleted(index, index)
    }

    /** Converts every row with [EmvValueEncoder] (amounts use the currency row, 5F2A) and builds the TLV. */
    fun build(): Built {
        val currency = rows.firstOrNull { it.tag.equals("5F2A", ignoreCase = true) }?.input?.trim()
        val entries = rows.mapIndexed { i, row ->
            TlvBuilder.checkTag(row.tag.trim())?.let { return Built.Error("Row ${i + 1}: $it") }
            val hex = when (val r = encoder.encode(row.tag.trim(), row.input, currency = currency.takeIf { row.tag != "5F2A" })) {
                is EmvValueEncoder.Result.Ok -> r.hex
                is EmvValueEncoder.Result.Error -> return Built.Error("Row ${i + 1} (${row.tag.uppercase()}): ${r.message}")
            }
            TlvEntry(row.tag.trim(), hex)
        }
        val result = TlvBuilder.build(entries)
        return result.hex?.let { Built.Ok(it) }
            ?: Built.Error(result.errors.first().let { "Row ${it.index + 1}: ${it.message}" })
    }

    override fun getRowCount() = rows.size
    override fun getColumnCount() = NAMES.size
    override fun getColumnName(column: Int) = NAMES[column]
    override fun isCellEditable(row: Int, column: Int) = column == TAG || column == VALUE

    override fun getValueAt(row: Int, column: Int): Any = with(rows[row]) {
        when (column) {
            TAG -> tag
            VALUE -> input
            NAME -> dictionary[tag.trim()]?.name ?: if (tag.isBlank()) "" else "Unknown tag"
            else -> encoder.inputHint(tag.trim())
        }
    }

    override fun setValueAt(aValue: Any?, row: Int, column: Int) {
        val text = aValue?.toString() ?: ""
        when (column) {
            TAG -> rows[row].tag = text.trim().uppercase()
            VALUE -> rows[row].input = text
        }
        fireTableRowsUpdated(row, row)
    }

    companion object {
        const val TAG = 0
        const val VALUE = 1
        const val NAME = 2
        const val HINT = 3
        val NAMES = listOf("Tag", "Value", "Name", "Input")

        /** Rows for existing field 55 hex; values are shown in each tag's own format. */
        fun fromHex(hex: String, encoder: EmvValueEncoder = EmvValueEncoder()): TlvRows {
            val model = TlvRows(encoder)
            val entries = TlvBuilder.rows(hex).orEmpty()
            val currency = entries.firstOrNull { it.tag == "5F2A" }?.valueHex
            entries.forEach { model.add(it.tag, encoder.toInput(it.tag, it.valueHex, currency = currency.takeIf { _ -> it.tag != "5F2A" })) }
            return model
        }
    }
}
