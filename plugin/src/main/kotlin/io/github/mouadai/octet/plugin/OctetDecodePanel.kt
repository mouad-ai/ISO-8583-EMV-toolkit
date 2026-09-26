package io.github.mouadai.octet.plugin

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import io.github.mouadai.octet.core.input.HexDump
import io.github.mouadai.octet.core.input.InputDecoder
import io.github.mouadai.octet.core.input.InputFormat
import io.github.mouadai.octet.core.input.InputResult
import io.github.mouadai.octet.core.iso.BuiltinDialects
import io.github.mouadai.octet.core.iso.Dialect
import io.github.mouadai.octet.core.view.DecodeMode
import io.github.mouadai.octet.core.view.DecodeNode
import io.github.mouadai.octet.core.view.DecodeView
import java.awt.Color
import java.awt.Font
import javax.swing.JTree
import javax.swing.text.DefaultHighlighter
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Decode tool window: pasted input, decoded tree and hex view. Selecting a tree node highlights its
 * bytes; clicking a byte in the hex view selects the deepest node that covers it. All decoding lives
 * in `core`; this class only wires results into Swing.
 */
class OctetDecodePanel {

    private val formats = listOf(
        "Auto" to InputFormat.AUTO,
        "Hex" to InputFormat.HEX,
        "Base64" to InputFormat.BASE64,
        "ASCII" to InputFormat.ASCII,
    )

    internal val input = JBTextArea(6, 40).apply {
        lineWrap = true
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
        emptyText.text = "Paste hex, base64 or raw ASCII"
    }
    private val formatCombo = ComboBox(formats.map { it.first }.toTypedArray())
    internal val modeCombo = ComboBox(DecodeMode.entries.map { it.label }.toTypedArray())
    // Built-in dialects for now; project and user dialect folders (M5) plug in here.
    private val dialects: List<Dialect> = BuiltinDialects.all
    internal val dialectCombo = ComboBox(dialects.map { it.name }.toTypedArray())
    internal val framingCombo = ComboBox<String>()
    internal val revealCheckBox = JBCheckBox("Reveal sensitive values")
    internal val status = JBLabel()
    internal val treeModel = DefaultTreeModel(null)
    internal val tree = Tree(treeModel).apply {
        isRootVisible = true
        cellRenderer = NodeRenderer()
    }
    internal val hexView = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
    }
    private val highlightPainter = DefaultHighlighter.DefaultHighlightPainter(HIGHLIGHT)

    private var root: DecodeNode? = null
    private var dump: HexDump? = null
    private var updatingHexView = false
    private var lastInput: InputResult.Success? = null

    val component: DialogPanel = panel {
        row("Input:") {
            cell(formatCombo)
            label("Decode as:")
            cell(modeCombo)
            button("Decode") { decode() }
        }
        row("Dialect:") {
            cell(dialectCombo)
            label("Framing:")
            cell(framingCombo)
        }
        row {
            cell(JBScrollPane(input)).align(Align.FILL)
        }.resizableRow()
        row {
            cell(status)
        }
        row {
            // Session-only: never persisted, so every new tool window starts masked (SPEC 6.8).
            cell(revealCheckBox)
        }
        row {
            val splitter = JBSplitter(false, 0.5f).apply {
                firstComponent = JBScrollPane(tree)
                secondComponent = JBScrollPane(hexView)
            }
            cell(splitter).align(Align.FILL)
        }.resizableRow()
    }

    init {
        tree.addTreeSelectionListener { event ->
            val node = (event.newLeadSelectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject
            highlight(node as? DecodeNode)
        }
        revealCheckBox.addActionListener { lastInput?.let(::render) }
        modeCombo.addActionListener { updateIsoControls() }
        dialectCombo.addActionListener { updateFramings() }
        updateFramings()
        updateIsoControls()
        hexView.addCaretListener { event ->
            if (!updatingHexView) selectNodeAtByte(dump?.byteOffsetAt(event.dot))
        }
    }

    internal fun decode() {
        val format = formats[formatCombo.selectedIndex.coerceAtLeast(0)].second
        when (val result = InputDecoder.decode(input.text, format)) {
            is InputResult.Failure -> {
                lastInput = null
                show(null, null)
                showStatus(result.message, isError = true)
            }
            is InputResult.Success -> {
                lastInput = result
                render(result)
            }
        }
    }

    private fun render(input: InputResult.Success) {
        val bytes = input.bytes
        val view = DecodeView.decode(
            bytes,
            selectedMode(),
            reveal = revealCheckBox.isSelected,
            dialect = selectedDialect(),
            framing = selectedDialect().framings.getOrNull(framingCombo.selectedIndex - 1),
        )
        show(view.root, HexDump(bytes, masked = view.maskedRanges))
        TreeUtil.expandAll(tree)
        val read = "Read ${bytes.size} bytes as ${input.format.name.lowercase()}."
        when (view.problems.size) {
            0 -> showStatus(read, isError = false)
            1 -> showStatus("$read ${view.problems.single()}", isError = true)
            else -> showStatus("$read ${view.problems.first()} (+${view.problems.size - 1} more)", isError = true)
        }
    }

    internal fun selectFormat(format: InputFormat) {
        formatCombo.selectedIndex = formats.indexOfFirst { it.second == format }
    }

    internal fun selectMode(mode: DecodeMode) {
        modeCombo.selectedIndex = mode.ordinal
    }

    private fun selectedMode(): DecodeMode = DecodeMode.entries[modeCombo.selectedIndex.coerceAtLeast(0)]

    private fun selectedDialect(): Dialect = dialects[dialectCombo.selectedIndex.coerceAtLeast(0)]

    /** Framing choices: "Auto" (index 0) then the selected dialect's own framings. */
    private fun updateFramings() {
        framingCombo.removeAllItems()
        framingCombo.addItem("Auto")
        selectedDialect().framings.forEach { framingCombo.addItem(it.label) }
        framingCombo.selectedIndex = 0
    }

    private fun updateIsoControls() {
        val iso = selectedMode() == DecodeMode.ISO_8583
        dialectCombo.isEnabled = iso
        framingCombo.isEnabled = iso
    }

    private fun showStatus(text: String, isError: Boolean) {
        status.foreground = if (isError) UIUtil.getErrorForeground() else UIUtil.getContextHelpForeground()
        status.text = text
    }

    private fun show(node: DecodeNode?, newDump: HexDump?) {
        root = node
        dump = newDump
        treeModel.setRoot(node?.let(::toTreeNode))
        updatingHexView = true
        try {
            hexView.text = newDump?.text.orEmpty()
            hexView.caretPosition = 0
        } finally {
            updatingHexView = false
        }
        hexView.highlighter.removeAllHighlights()
    }

    private fun toTreeNode(node: DecodeNode): DefaultMutableTreeNode =
        DefaultMutableTreeNode(node).apply { node.children.forEach { add(toTreeNode(it)) } }

    private fun highlight(node: DecodeNode?) {
        hexView.highlighter.removeAllHighlights()
        val ranges = node?.let { dump?.highlightRanges(it.offset, it.length) }.orEmpty()
        ranges.forEach { hexView.highlighter.addHighlight(it.first, it.last + 1, highlightPainter) }
    }

    private fun selectNodeAtByte(byteOffset: Int?) {
        val path = byteOffset?.let { root?.pathAt(it) } ?: return
        var treeNode = treeModel.root as? DefaultMutableTreeNode ?: return
        val treePath = mutableListOf<Any>(treeNode)
        for (decodeNode in path.drop(1)) {
            treeNode = treeNode.children().toList()
                .filterIsInstance<DefaultMutableTreeNode>()
                .firstOrNull { it.userObject === decodeNode } ?: break
            treePath += treeNode
        }
        val selection = TreePath(treePath.toTypedArray())
        tree.selectionPath = selection
        tree.scrollPathToVisible(selection)
    }

    private class NodeRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val node = (value as? DefaultMutableTreeNode)?.userObject as? DecodeNode ?: return
            append(node.id, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
            if (node.name.isNotEmpty()) append("  ${node.name}")
            if (node.value.isNotEmpty()) append("  ${node.value}")
            if (node.rawHex.isNotEmpty() && node.rawHex != node.value) {
                append("  ${node.rawHex}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }

    private companion object {
        val HIGHLIGHT = JBColor(Color(0xCCE0FF), Color(0x2F4A6D))
    }
}
