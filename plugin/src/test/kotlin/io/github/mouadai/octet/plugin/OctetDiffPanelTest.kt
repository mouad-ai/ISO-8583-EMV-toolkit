package io.github.mouadai.octet.plugin

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.mouadai.octet.core.diff.ChangeKind
import io.github.mouadai.octet.core.view.DecodeNode
import java.awt.datatransfer.DataFlavor

/** Headless checks of the diff tab with a stand-in decoder: one node per input byte. */
class OctetDiffPanelTest : BasePlatformTestCase() {

    private val panel = OctetDiffPanel { bytes ->
        DecodeNode(
            "Input", "", "", 0, bytes.size,
            children = bytes.mapIndexed { i, b -> DecodeNode("B$i", "Byte $i", "%02X".format(b), i, 1) },
        )
    }

    fun testShowsChangedAddedAndRemoved() {
        panel.leftInput.text = "01 02 03"
        panel.rightInput.text = "01 FF"
        panel.compare()
        val diff = panel.lastDiff!!
        assertEquals(
            listOf(ChangeKind.UNCHANGED, ChangeKind.CHANGED, ChangeKind.REMOVED),
            diff.children.map { it.kind },
        )
        assertEquals("0 added, 1 removed, 1 changed", panel.status.text)
        assertEquals(3, panel.treeModel.getChildCount(panel.treeModel.root))
    }

    fun testIdenticalInputs() {
        panel.leftInput.text = "0102"
        panel.rightInput.text = "01 02"
        panel.compare()
        assertEquals("No differences", panel.status.text)
    }

    fun testBadInputReportsTheSide() {
        panel.leftInput.text = "0102"
        panel.rightInput.text = ""
        panel.compare()
        assertNull(panel.lastDiff)
        assertTrue(panel.status.text, panel.status.text.startsWith("Right:"))
    }

    fun testCopyReport() {
        panel.leftInput.text = "01"
        panel.rightInput.text = "02"
        panel.compare()
        panel.copyReport()
        val copied = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
        assertEquals("0 added, 0 removed, 1 changed\n~ B0 Byte 0: 01 -> 02", copied)
    }
}
