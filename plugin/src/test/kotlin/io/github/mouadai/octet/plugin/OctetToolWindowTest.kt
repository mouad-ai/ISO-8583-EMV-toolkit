package io.github.mouadai.octet.plugin

import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.mouadai.octet.core.view.DecodeNode
import javax.swing.tree.DefaultMutableTreeNode

/** Boots a headless IDE with the plugin installed and checks the tool window is wired up. */
class OctetToolWindowTest : BasePlatformTestCase() {

    fun testToolWindowIsRegistered() {
        val ep = ToolWindowEP.EP_NAME.extensionList.singleOrNull { it.id == "Octet" }
        assertNotNull("Octet tool window extension not registered", ep)
        assertEquals(OctetToolWindowFactory::class.java.name, ep!!.factoryClass)
    }

    fun testDecodeShowsTreeAndHexView() {
        val panel = OctetDecodePanel()
        panel.input.text = "9F02 06 000000001000 5F2A 02 0504"
        panel.decode()

        val root = (panel.treeModel.root as DefaultMutableTreeNode).userObject as DecodeNode
        assertEquals(14, root.length)
        assertTrue(panel.hexView.text, panel.hexView.text.startsWith("0000  9F 02 06 00"))
        assertEquals("Read 14 bytes as hex.", panel.status.text)
    }

    fun testSelectingTheRootHighlightsItsBytes() {
        val panel = OctetDecodePanel()
        panel.input.text = "9A03260925"
        panel.decode()

        panel.tree.setSelectionRow(0)
        assertEquals(2, panel.hexView.highlighter.highlights.size) // hex column + ASCII column
    }

    fun testClickingAByteSelectsItsNode() {
        val panel = OctetDecodePanel()
        panel.input.text = "9A03260925"
        panel.decode()
        assertNull("Nothing is selected right after decoding", panel.tree.selectionPath)

        panel.hexView.caretPosition = 6 // first hex byte
        assertEquals(0, panel.tree.selectionRows?.single())
    }

    fun testInvalidInputShowsAnError() {
        val panel = OctetDecodePanel()
        panel.input.text = "   "
        panel.decode()

        assertNull(panel.treeModel.root)
        assertEquals("Input is empty.", panel.status.text)
    }
}
