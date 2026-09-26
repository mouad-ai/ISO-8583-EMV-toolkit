package io.github.mouadai.octet.plugin

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.mouadai.octet.core.builder.ExportFormat

/** Drives the Build tab headlessly: fill the form, build, export. */
class OctetBuilderPanelTest : BasePlatformTestCase() {

    private fun OctetBuilderPanel.set(field: Int, value: String) {
        val row = fieldsModel.rows.indexOfFirst { it.spec.id == field }
        fieldsModel.setValueAt(value, row, OctetBuilderPanel.FieldsTableModel.VALUE)
    }

    fun testBuildsHexFromTheForm() {
        val panel = OctetBuilderPanel()
        panel.mtiField.text = "0100"
        panel.set(3, "000000")
        panel.set(4, "1000")
        panel.build()

        assertEquals("Built 38 bytes.", panel.status.text)
        val ascii = "0100" + "3000000000000000" + "000000" + "000000001000"
        assertEquals(ascii.toByteArray().joinToString("") { "%02X".format(it) }, panel.output.text)
    }

    fun testExportFormatsSwitchTheOutput() {
        val panel = OctetBuilderPanel()
        panel.set(11, "000001")
        panel.build()
        panel.formatCombo.selectedIndex = ExportFormat.JPOS.ordinal
        assertTrue(panel.output.text, panel.output.text.contains("msg.set(11, \"000001\");"))
        panel.formatCombo.selectedIndex = ExportFormat.JAVA_BYTES.ordinal
        assertTrue(panel.output.text, panel.output.text.startsWith("byte[] message = {"))
    }

    fun testErrorsAreShownWithTheirField() {
        val panel = OctetBuilderPanel()
        panel.set(2, "4111-1111")
        panel.build()
        assertTrue(panel.status.text, panel.status.text.startsWith("Field 2 (Primary account number)"))
        assertTrue(panel.output.text.contains("digits only"))
    }

    fun testClearingAValueDeselectsTheField() {
        val panel = OctetBuilderPanel()
        panel.set(4, "1000")
        assertEquals(mapOf(4 to "1000"), panel.values())
        panel.set(4, "")
        assertEquals(emptyMap<Int, String>(), panel.values())
    }

    fun testTlvEditorRowsBuildField55FromFriendlyValues() {
        val rows = TlvRows()
        rows.add("9F02", "10.00")
        rows.add("5F2A", "MAD")
        rows.add("9A", "2026-09-25")
        rows.add("95", "0000000000")
        assertEquals(TlvRows.Built.Ok("9F02060000000010005F2A0205049A0326092595050000000000"), rows.build())
        assertEquals("Amount, Authorised (Numeric)", rows.getValueAt(0, TlvRows.NAME))
        assertEquals("amount, e.g. 10.00", rows.getValueAt(0, TlvRows.HINT))

        rows.add("9A", "2026-13-01")
        assertEquals(TlvRows.Built.Error("Row 5 (9A): '2026-13-01' is not a valid date."), rows.build())
    }

    fun testTlvEditorLoadsExistingField55() {
        val rows = TlvRows.fromHex("9F02060000000010005F2A0205049A03260925")
        assertEquals(listOf("10.00", "MAD", "2026-09-25"), rows.rows.map { it.input })
        assertEquals(TlvRows.Built.Ok("9F02060000000010005F2A0205049A03260925"), rows.build())
    }

    fun testEditedTagsFillTheEmvField() {
        val panel = OctetBuilderPanel()
        panel.setTlvValue("9A03260925")
        assertEquals("9A03260925", panel.values()[55])
        panel.build()
        assertEquals("Built ${4 + 16 + 3 + 10} bytes.", panel.status.text)
    }
}
