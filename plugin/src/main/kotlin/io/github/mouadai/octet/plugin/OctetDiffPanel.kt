package io.github.mouadai.octet.plugin

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import io.github.mouadai.octet.core.diff.ChangeKind
import io.github.mouadai.octet.core.diff.DiffNode
import io.github.mouadai.octet.core.diff.DiffReport
import io.github.mouadai.octet.core.diff.MessageDiff
import io.github.mouadai.octet.core.input.InputDecoder
import io.github.mouadai.octet.core.input.InputResult
import io.github.mouadai.octet.core.iso.BuiltinDialects
import io.github.mouadai.octet.core.iso.Dialect
import io.github.mouadai.octet.core.view.DecodeMode
import io.github.mouadai.octet.core.view.DecodeView
import java.awt.Font
import java.awt.datatransfer.StringSelection
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Diff tool window tab (SPEC 8.4): two pasted messages, decoded the same way (ISO 8583 with a
 * dialect, or EMV TLV) and compared in `core`. Shows one merged tree with added, removed and
 * changed elements highlighted. Both sides are decoded masked.
 *
 * @param dialectProvider dialects to offer; called again by [reloadDialects] when dialect files change
 */
class OctetDiffPanel(private val dialectProvider: () -> List<Dialect> = { BuiltinDialects.all }) {

    private val modes = listOf(DecodeMode.ISO_8583, DecodeMode.EMV_TLV)
    private var dialects: List<Dialect> = dialectProvider()
    internal val modeCombo = ComboBox(modes.map { it.label }.toTypedArray())
    internal val dialectCombo = ComboBox(dialects.map { it.name }.toTypedArray())

    internal val leftInput = inputArea("Expected, or the request")
    internal val rightInput = inputArea("Actual, or the response")
    internal val status = JBLabel()
    internal val treeModel = DefaultTreeModel(null)
    internal val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = DiffRenderer()
    }
    internal var lastDiff: DiffNode? = null
        private set

    val component: DialogPanel = panel {
        row("Decode as:") {
            cell(modeCombo)
            label("Dialect:")
            cell(dialectCombo)
        }
        row {
            val inputs = JBSplitter(false, 0.5f).apply {
                firstComponent = JBScrollPane(leftInput)
                secondComponent = JBScrollPane(rightInput)
            }
            cell(inputs).align(Align.FILL)
        }.resizableRow()
        row {
            button("Compare") { compare() }
            button("Copy Report") { copyReport() }
            cell(status)
        }
        row {
            cell(JBScrollPane(tree)).align(Align.FILL)
        }.resizableRow()
    }

    init {
        modeCombo.addActionListener { dialectCombo.isEnabled = selectedMode() == DecodeMode.ISO_8583 }
    }

    internal fun selectMode(mode: DecodeMode) {
        modeCombo.selectedIndex = modes.indexOf(mode)
    }

    /** Re-reads the dialect list, keeping the selected dialect when it still exists. */
    fun reloadDialects() {
        val selectedId = dialects.getOrNull(dialectCombo.selectedIndex)?.id
        dialects = dialectProvider()
        dialectCombo.removeAllItems()
        dialects.forEach { dialectCombo.addItem(it.name) }
        dialectCombo.selectedIndex = dialects.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    }

    private fun selectedMode(): DecodeMode = modes[modeCombo.selectedIndex.coerceAtLeast(0)]

    private fun decode(bytes: ByteArray) = DecodeView.decode(
        bytes,
        selectedMode(),
        reveal = false,
        dialect = dialects[dialectCombo.selectedIndex.coerceAtLeast(0)],
    ).root

    internal fun compare() {
        val left = readSide("Left", leftInput.text) ?: return
        val right = readSide("Right", rightInput.text) ?: return
        val diff = MessageDiff.diff(decode(left), decode(right))
        lastDiff = diff
        val root = DefaultMutableTreeNode(diff)
        diff.children.forEach { root.add(toTreeNode(it)) }
        treeModel.setRoot(root)
        TreeUtil.expandAll(tree)
        val s = diff.summary()
        showStatus(
            if (diff.hasChanges) "${s.added} added, ${s.removed} removed, ${s.changed} changed" else "No differences",
            isError = false,
        )
    }

    /** Copies the text report. Values are masked as decoded, so no clear PAN leaves the IDE by default. */
    internal fun copyReport() {
        val diff = lastDiff ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(DiffReport.render(diff)))
    }

    private fun readSide(label: String, text: String): ByteArray? =
        when (val result = InputDecoder.decode(text)) {
            is InputResult.Success -> result.bytes
            is InputResult.Failure -> {
                lastDiff = null
                treeModel.setRoot(null)
                showStatus("$label: ${result.message}", isError = true)
                null
            }
        }

    private fun showStatus(text: String, isError: Boolean) {
        status.foreground = if (isError) UIUtil.getErrorForeground() else UIUtil.getContextHelpForeground()
        status.text = text
    }

    private fun toTreeNode(node: DiffNode): DefaultMutableTreeNode =
        DefaultMutableTreeNode(node).apply { node.children.forEach { add(toTreeNode(it)) } }

    private fun inputArea(hint: String) = JBTextArea(5, 30).apply {
        lineWrap = true
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
        emptyText.text = hint
    }

    private class DiffRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val node = (value as? DefaultMutableTreeNode)?.userObject as? DiffNode ?: return
            val style = when (node.kind) {
                ChangeKind.ADDED -> ADDED
                ChangeKind.REMOVED -> REMOVED
                ChangeKind.CHANGED -> CHANGED
                ChangeKind.UNCHANGED -> SimpleTextAttributes.REGULAR_ATTRIBUTES
            }
            append(MARKER.getValue(node.kind), style)
            append(node.id, style.derive(SimpleTextAttributes.STYLE_BOLD, null, null, null))
            if (node.name.isNotEmpty()) append("  ${node.name}", style)
            when {
                node.valueChanged -> {
                    append("  ${node.left?.value.orEmpty()}", REMOVED)
                    append("  →  ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(node.right?.value.orEmpty(), ADDED)
                }
                else -> (node.right ?: node.left)?.value?.takeIf { it.isNotEmpty() }?.let { append("  $it", style) }
            }
        }
    }

    private companion object {
        val ADDED = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor(0x067D17, 0x6AAB73))
        val REMOVED = SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, JBColor(0xC7222D, 0xF75464))
        val CHANGED = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor(0x0A50A1, 0x56A8F5))
        val MARKER = mapOf(
            ChangeKind.ADDED to "+ ",
            ChangeKind.REMOVED to "− ",
            ChangeKind.CHANGED to "~ ",
            ChangeKind.UNCHANGED to "  ",
        )
    }
}
