package io.github.mouadai.octet.plugin

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.mouadai.octet.core.view.DecodeMode
import io.github.mouadai.octet.core.view.DecodeNode
import javax.swing.tree.DefaultMutableTreeNode

class EditorAndConsoleTest : BasePlatformTestCase() {

    private val icc = "9F02060000000010005F2A0205049A03260925"

    fun testDecodeActionsAreRegisteredInEditorAndConsoleMenus() {
        val actions = ActionManager.getInstance()
        assertTrue(actions.getAction("Octet.DecodeSelectionAsIso") is DecodeSelectionAsIsoAction)
        assertTrue(actions.getAction("Octet.DecodeSelectionAsEmv") is DecodeSelectionAsEmvAction)
        assertNotNull(actions.getAction("Octet.DecodeSelection"))
    }

    fun testConsoleFilterIsOffByDefault() {
        assertFalse(OctetSettings.getInstance().state.consoleFilterEnabled)
        val filter = OctetConsoleFilterProvider().getDefaultFilters(project).single()
        assertNull(filter.applyFilter("icc=$icc\n", 100))
    }

    fun testConsoleFilterLinksHexRunsAtTheirPositionInTheConsole() {
        val filter = HexDecodeConsoleFilter { true }
        val line = "DEBUG icc=$icc done\n"
        val entireLength = 500 // offset of the end of this line in the whole console text
        val items = filter.applyFilter(line, entireLength)!!.resultItems
        val item = items.single()
        val lineStart = entireLength - line.length
        assertEquals(lineStart + line.indexOf(icc), item.highlightStartOffset)
        assertEquals(lineStart + line.indexOf(icc) + icc.length, item.highlightEndOffset)
        assertNotNull(item.hyperlinkInfo)
    }

    fun testConsoleFilterIgnoresLinesWithoutHexDumps() {
        assertNull(HexDecodeConsoleFilter { true }.applyFilter("Build finished in 3.2 s\n", 30))
    }

    fun testDecodeTextSwitchesModeAndDecodes() {
        val panel = OctetDecodePanel()
        panel.decodeText(icc, DecodeMode.EMV_TLV)

        val root = (panel.treeModel.root as DefaultMutableTreeNode).userObject as DecodeNode
        assertEquals(listOf("9F02", "5F2A", "9A"), root.children.map { it.id })
    }
}
