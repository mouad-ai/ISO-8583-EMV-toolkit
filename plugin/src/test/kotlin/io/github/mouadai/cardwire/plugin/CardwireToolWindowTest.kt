package io.github.mouadai.cardwire.plugin

import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.mouadai.cardwire.core.input.InputFormat
import io.github.mouadai.cardwire.core.view.DecodeMode
import io.github.mouadai.cardwire.core.view.DecodeNode
import javax.swing.tree.DefaultMutableTreeNode

/** Boots a headless IDE with the plugin installed and checks the tool window is wired up. */
class CardwireToolWindowTest : BasePlatformTestCase() {

    fun testToolWindowIsRegistered() {
        val ep = ToolWindowEP.EP_NAME.extensionList.singleOrNull { it.id == "Cardwire" }
        assertNotNull("Cardwire tool window extension not registered", ep)
        assertEquals(CardwireToolWindowFactory::class.java.name, ep!!.factoryClass)
    }

    fun testDecodeShowsTreeAndHexView() {
        val panel = CardwireDecodePanel()
        panel.selectMode(DecodeMode.EMV_TLV)
        panel.input.text = "9F02 06 000000001000 5F2A 02 0504"
        panel.decode()

        val root = (panel.treeModel.root as DefaultMutableTreeNode).userObject as DecodeNode
        assertEquals(14, root.length)
        assertTrue(panel.hexView.text, panel.hexView.text.startsWith("0000  9F 02 06 00"))
        assertEquals("Read 14 bytes as hex.", panel.status.text)
    }

    fun testSelectingTheRootHighlightsItsBytes() {
        val panel = CardwireDecodePanel()
        panel.selectMode(DecodeMode.EMV_TLV)
        panel.input.text = "9A03260925"
        panel.decode()

        panel.tree.setSelectionRow(0)
        assertEquals(2, panel.hexView.highlighter.highlights.size) // hex column + ASCII column
    }

    fun testClickingAByteSelectsItsNode() {
        val panel = CardwireDecodePanel()
        panel.selectMode(DecodeMode.EMV_TLV)
        panel.input.text = "9A03260925"
        panel.decode()
        assertNull("Nothing is selected right after decoding", panel.tree.selectionPath)

        panel.hexView.caretPosition = 6 // first hex byte
        assertEquals("9A", selectedNode(panel).id)
    }

    fun testEmvTagsAreDecodedAndSensitiveBytesMasked() {
        val panel = CardwireDecodePanel()
        panel.selectMode(DecodeMode.EMV_TLV)
        panel.input.text = "9A03260925 5A084111111111111111"
        panel.decode()

        val root = (panel.treeModel.root as DefaultMutableTreeNode).userObject as DecodeNode
        assertEquals(listOf("9A", "5A"), root.children.map { it.id })
        assertEquals("411111******1111", root.children[1].value)
        assertTrue(panel.hexView.text, panel.hexView.text.contains("5A 08 ** **"))

        panel.revealCheckBox.doClick()
        assertTrue(panel.hexView.text, panel.hexView.text.contains("5A 08 41 11"))
    }

    fun testTlvErrorsAreShownInTheStatus() {
        val panel = CardwireDecodePanel()
        panel.selectMode(DecodeMode.EMV_TLV)
        panel.input.text = "9A0526"
        panel.decode()

        assertTrue(panel.status.text, panel.status.text.contains("exceeds remaining"))
    }

    fun testRawModeShowsASingleNode() {
        val panel = CardwireDecodePanel()
        panel.selectMode(DecodeMode.RAW)
        panel.input.text = "9A03260925"
        panel.decode()

        val root = (panel.treeModel.root as DefaultMutableTreeNode).userObject as DecodeNode
        assertEquals("Input", root.id)
        assertTrue(root.children.isEmpty())
    }

    fun testIso8583ModeIsTheDefaultAndDecodesWithTheSelectedDialect() {
        val panel = CardwireDecodePanel()
        // 1987 ASCII 0800 network management request: fields 7, 11, 70 (hand-crafted, fake values).
        panel.selectFormat(InputFormat.ASCII)
        panel.input.text = "0800" + "8220000000000000" + "0400000000000000" + "0925221000" + "123456" + "301"
        panel.decode()

        val root = (panel.treeModel.root as DefaultMutableTreeNode).userObject as DecodeNode
        assertEquals("ISO 8583", root.id)
        assertEquals(listOf("MTI", "Bitmap", "DE 7", "DE 11", "DE 70"), root.children.map { it.id })
        assertTrue(panel.dialectCombo.isEnabled)
    }

    fun testDialectControlsAreDisabledOutsideIsoMode() {
        val panel = CardwireDecodePanel()
        panel.selectMode(DecodeMode.EMV_TLV)
        assertFalse(panel.dialectCombo.isEnabled)
        assertFalse(panel.framingCombo.isEnabled)
        assertEquals("Auto", panel.framingCombo.getItemAt(0))
    }

    fun testProjectDialectsJoinThePickerAndReloadKeepsTheSelection() {
        myFixture.addFileToProject(
            ".cardwire/dialects/acme.json",
            """{"id": "test-acme", "name": "Test Acme", "extends": "iso8583-1987-ascii", "fields": {}}""",
        )
        myFixture.addFileToProject(".cardwire/dialects/broken.json", "{ not json")
        val panel = CardwireDecodePanel { CardwireToolWindowFactory.dialectSources(project) }
        val labels = (0 until panel.dialectCombo.itemCount).map { panel.dialectCombo.getItemAt(it) }
        assertTrue(labels.toString(), "Test Acme (project)" in labels)
        assertTrue(panel.status.text, panel.status.text.contains("broken.json"))

        panel.dialectCombo.selectedItem = "Test Acme (project)"
        panel.reloadDialects()
        assertEquals("Test Acme (project)", panel.dialectCombo.selectedItem)
    }

    private fun selectedNode(panel: CardwireDecodePanel): DecodeNode =
        (panel.tree.selectionPath!!.lastPathComponent as DefaultMutableTreeNode).userObject as DecodeNode

    fun testInvalidInputShowsAnError() {
        val panel = CardwireDecodePanel()
        panel.selectMode(DecodeMode.EMV_TLV)
        panel.input.text = "   "
        panel.decode()

        assertNull(panel.treeModel.root)
        assertEquals("Input is empty.", panel.status.text)
    }
}
