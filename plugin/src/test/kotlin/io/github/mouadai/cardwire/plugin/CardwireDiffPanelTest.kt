package io.github.mouadai.cardwire.plugin

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.mouadai.cardwire.core.diff.ChangeKind
import io.github.mouadai.cardwire.core.view.DecodeMode
import java.awt.datatransfer.DataFlavor

/** Headless checks of the diff tab, comparing hand-made EMV TLV samples (SPEC 11). */
class CardwireDiffPanelTest : BasePlatformTestCase() {

    private val panel = CardwireDiffPanel().apply { selectMode(DecodeMode.EMV_TLV) }

    fun testShowsChangedAddedAndRemoved() {
        panel.leftInput.text = "9F02 06 000000001000 5F2A 02 0504 9A 03 260925"
        panel.rightInput.text = "9F02 06 000000001250 5F2A 02 0504 95 05 0000000000"
        panel.compare()
        val kinds = panel.lastDiff!!.children.associate { it.id to it.kind }
        assertEquals(ChangeKind.CHANGED, kinds["9F02"])
        assertEquals(ChangeKind.UNCHANGED, kinds["5F2A"])
        assertEquals(ChangeKind.REMOVED, kinds["9A"])
        assertEquals(ChangeKind.ADDED, kinds["95"])
        assertEquals("1 added, 1 removed, 1 changed", panel.status.text)
    }

    fun testIdenticalInputs() {
        panel.leftInput.text = "9A03260925"
        panel.rightInput.text = "9A 03 26 09 25"
        panel.compare()
        assertEquals("No differences", panel.status.text)
    }

    fun testBadInputReportsTheSide() {
        panel.leftInput.text = "9A03260925"
        panel.rightInput.text = ""
        panel.compare()
        assertNull(panel.lastDiff)
        assertTrue(panel.status.text, panel.status.text.startsWith("Right:"))
    }

    fun testReportIsMasked() {
        panel.leftInput.text = "5A084111111111111111"
        panel.rightInput.text = "5A084111111111111129"
        panel.compare()
        panel.copyReport()
        val copied = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor).orEmpty()
        assertTrue(copied, copied.startsWith("0 added, 0 removed, 1 changed"))
        assertTrue(copied, copied.contains("411111******1111 -> 411111******1129"))
        assertFalse(copied, copied.contains("4111111111111111"))
    }
}
