package io.github.mouadai.octet.plugin

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
import io.github.mouadai.octet.core.input.HexDump
import io.github.mouadai.octet.core.input.InputDecoder
import io.github.mouadai.octet.core.input.InputFormat
import io.github.mouadai.octet.core.input.InputResult
import io.github.mouadai.octet.core.view.DecodeNode
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

    val component: DialogPanel = panel {
        row("Format:") {
            cell(formatCombo)
            button("Decode") { decode() }
        }
        row {
            cell(JBScrollPane(input)).align(Align.FILL)
        }.resizableRow()
        row {
            cell(status)
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
        hexView.addCaretListener { event ->
            if (!updatingHexView) selectNodeAtByte(dump?.byteOffsetAt(event.dot))
        }
    }

    internal fun decode() {
        val format = formats[formatCombo.selectedIndex.coerceAtLeast(0)].second
        when (val result = InputDecoder.decode(input.text, format)) {
            is InputResult.Failure -> {
                show(null, null)
                status.foreground = UIUtil.getErrorForeground()
                status.text = result.message
            }
            is InputResult.Success -> {
                val bytes = result.bytes
                // Structured ISO 8583 and EMV TLV nodes replace this single node once the core decoders land.
                val node = DecodeNode("Input", "${bytes.size} bytes", result.format.name, offset = 0, length = bytes.size)
                show(node, HexDump(bytes))
                status.foreground = UIUtil.getContextHelpForeground()
                status.text = "Read ${bytes.size} bytes as ${result.format.name.lowercase()}."
            }
        }
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
            if (node.value.isNotEmpty()) append("  ${node.value}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }

    private companion object {
        val HIGHLIGHT = JBColor(Color(0xCCE0FF), Color(0x2F4A6D))
    }
}
