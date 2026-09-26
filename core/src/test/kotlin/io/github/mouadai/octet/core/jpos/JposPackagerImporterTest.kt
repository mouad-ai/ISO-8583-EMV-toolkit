package io.github.mouadai.octet.core.jpos

import io.github.mouadai.octet.core.iso.BitmapEncoding
import io.github.mouadai.octet.core.iso.BuiltinDialects
import io.github.mouadai.octet.core.iso.DataEncoding
import io.github.mouadai.octet.core.iso.Dialect
import io.github.mouadai.octet.core.iso.EncodeResult
import io.github.mouadai.octet.core.iso.FieldType
import io.github.mouadai.octet.core.iso.IsoDecoder
import io.github.mouadai.octet.core.iso.IsoEncoder
import io.github.mouadai.octet.core.iso.IsoMessageData
import io.github.mouadai.octet.core.iso.LengthEncoding
import io.github.mouadai.octet.core.iso.LengthType
import io.github.mouadai.octet.core.iso.MtiEncoding
import io.github.mouadai.octet.core.iso.PadSide
import io.github.mouadai.octet.core.iso.Padding
import io.github.mouadai.octet.core.iso.SubfieldLayout
import io.github.mouadai.octet.core.iso.SubfieldSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** All packager files under test/resources/jpos were written for these tests from public jPOS conventions. */
class JposPackagerImporterTest {

    private fun resource(name: String): String =
        javaClass.getResourceAsStream("/jpos/$name")!!.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun import(name: String, id: String = "test"): Pair<Dialect, List<String>> {
        val result = JposPackagerImporter.import(resource(name), id, "Test dialect")
        assertEquals(emptyList<String>(), result.errors)
        return result.dialect!! to result.warnings
    }

    @Test
    fun `ASCII packager maps onto an equivalent dialect`() {
        val (dialect, warnings) = import("ascii-packager.xml")
        assertEquals(MtiEncoding.ASCII, dialect.mtiEncoding)
        assertEquals(BitmapEncoding.HEX_ASCII, dialect.bitmap.encoding)
        assertFalse(dialect.bitmap.tertiary)
        assertEquals(listOf(2, 3, 4, 7, 11, 14, 22, 28, 35, 41, 48, 49, 52, 55, 64, 90, 128), dialect.fields.keys.toList())
        val pan = dialect.field(2)!!
        assertEquals(FieldType.N, pan.type)
        assertEquals(LengthType.LLVAR, pan.lengthType)
        assertEquals(19, pan.maxLength)
        assertTrue(pan.sensitive)
        assertEquals(FieldType.Z, dialect.field(35)!!.type)
        assertEquals(FieldType.XN, dialect.field(28)!!.type)
        assertEquals(FieldType.ANS, dialect.field(41)!!.type)
        assertEquals(DataEncoding.ASCII, dialect.field(52)!!.dataEncoding)
        assertEquals(SubfieldLayout.BerTlv, dialect.field(55)!!.subfields)
        assertEquals(1, warnings.size, warnings.toString())
        assertTrue(warnings.single().contains("version"))
    }

    @Test
    fun `imported ASCII dialect decodes like the built-in 1987 ASCII dialect`() {
        val text = "0100" + "7224040020808200" + "16" + "4111111111111111" + "000000" + "000000001000" + "0925221000" +
            "123456" + "2912" + "051" + "34" + "4111111111111111=29122010000000000" + "TERM0001" + "504" +
            "014" + "9F02060000000010005F2A020504"
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        val (dialect, _) = import("ascii-packager.xml")
        val imported = IsoDecoder().decode(bytes, dialect)
        val builtin = IsoDecoder().decode(bytes, BuiltinDialects.load("iso8583-1987-ascii"))
        assertEquals(emptyList<Any>(), imported.errors)
        assertEquals(builtin.message.toData(), imported.message.toData())
        assertEquals(listOf("9F02", "5F2A"), imported.message.fields[55]!!.children.map { it.id })
    }

    @Test
    fun `binary packager maps padding, prefixes, subfields and the tertiary bitmap`() {
        val (dialect, warnings) = import("binary-packager.xml")
        assertEquals(MtiEncoding.BCD, dialect.mtiEncoding)
        assertEquals(BitmapEncoding.BINARY, dialect.bitmap.encoding)
        assertTrue(dialect.bitmap.tertiary)
        assertNull(dialect.field(65))
        assertNull(dialect.field(61))
        assertNull(dialect.field(62))

        assertEquals(DataEncoding.BCD_LEFT_PAD, dialect.field(2)!!.dataEncoding)
        assertEquals(LengthEncoding.BCD, dialect.field(2)!!.lengthEncoding)
        val track2 = dialect.field(35)!!
        assertEquals(FieldType.Z, track2.type)
        assertEquals(DataEncoding.BCD_RIGHT_PAD, track2.dataEncoding)
        assertEquals(Padding(PadSide.RIGHT, '0'), track2.padding)
        assertEquals(LengthEncoding.BINARY, dialect.field(44)!!.lengthEncoding)
        assertEquals(DataEncoding.ASCII, dialect.field(44)!!.dataEncoding)
        assertEquals(DataEncoding.BINARY, dialect.field(52)!!.dataEncoding)

        val unmapped = dialect.field(60)!!
        assertEquals(FieldType.B, unmapped.type)
        assertEquals(LengthType.LLLVAR, unmapped.lengthType)
        assertTrue(warnings.any { it.startsWith("Field 60: jPOS class IFX_LLLACMEFIELD is not in the mapping table") }, warnings.toString())
        assertTrue(warnings.any { it == "Field 61: IF_NOP carries no data; field skipped." })

        assertEquals(
            SubfieldLayout.Fixed(listOf(SubfieldSpec("1", "TERMINAL TYPE", 2), SubfieldSpec("2", "CARD PRESENT", 1), SubfieldSpec("3", "CAPABILITIES", 8))),
            dialect.field(63)!!.subfields,
        )
        val ext = dialect.field(127)!!.subfields as SubfieldLayout.Bitmapped
        assertEquals(8, ext.bitmapLength)
        assertEquals(BitmapEncoding.BINARY, ext.bitmapEncoding)
        assertEquals(listOf(2, 3, 9), ext.fields.keys.toList())
        assertEquals("127.2", ext.fields.getValue(2).path)
        assertEquals(LengthEncoding.BCD, ext.fields.getValue(2).lengthEncoding)
    }

    @Test
    fun `messages round-trip through the imported binary dialect`() {
        val (dialect, _) = import("binary-packager.xml")
        val ext = "6000000000000000" + "05" + "48454C4C4F" + "123456"
        val data = IsoMessageData("0200", mapOf(2 to "4111111111111111", 35 to "4111111111111111=2912", 63 to "01Y12345678", 127 to ext))
        val bytes = (IsoEncoder.encode(data, dialect) as EncodeResult.Success).bytes
        val decoded = IsoDecoder().decode(bytes, dialect)
        assertEquals(emptyList<Any>(), decoded.errors)
        assertEquals(data, decoded.message.toData())
        val children = decoded.message.fields[127]!!.children
        assertEquals(listOf("127.bitmap", "127.2", "127.3"), children.map { it.id })
        assertEquals(listOf("2,3", "HELLO", "123456"), children.map { it.value })
        assertEquals(listOf("01", "Y", "12345678"), decoded.message.fields[63]!!.children.map { it.value })
    }

    @Test
    fun `EBCDIC packager`() {
        val (dialect, _) = import("ebcdic-packager.xml")
        assertEquals(MtiEncoding.EBCDIC, dialect.mtiEncoding)
        assertEquals(BitmapEncoding.HEX_EBCDIC, dialect.bitmap.encoding)
        assertEquals(LengthEncoding.EBCDIC, dialect.field(2)!!.lengthEncoding)
        assertEquals(DataEncoding.EBCDIC, dialect.field(43)!!.dataEncoding)
    }

    @Test
    fun `generated JSON is the dialect file format`() {
        val result = JposPackagerImporter.import(resource("binary-packager.xml"), "acme", "Acme")
        val json = result.json!!
        assertTrue(json.contains("\"extends\"").not())
        assertTrue(json.contains("\"127\": {\"name\": \"ACME EXTENDED DATA\", \"type\": \"b\", \"lengthType\": \"LLLVAR\""), json)
        assertTrue(json.contains("\"layout\": \"BITMAP\""))
    }

    @Test
    fun `unusable input is an error, not an exception`() {
        fun errors(xml: String) = JposPackagerImporter.import(xml, "x", "X").also { assertNull(it.dialect) }.errors
        assertTrue(errors("not xml").single().startsWith("Not a readable XML file"))
        assertEquals(listOf("Expected an <isopackager> root element, found <packager>."), errors("<packager/>"))
        assertEquals(
            listOf("The packager has no field 0 (MTI).", "The packager has no field 1 (bitmap)."),
            errors("<isopackager><isofield id=\"2\" length=\"19\" name=\"PAN\" class=\"org.jpos.iso.IFA_LLNUM\"/></isopackager>"),
        )
        assertEquals(
            listOf("Field 1 (bitmap): jPOS class IFA_NUMERIC is not a bitmap class."),
            errors("<isopackager><isofield id=\"0\" length=\"4\" name=\"MTI\" class=\"org.jpos.iso.IFA_NUMERIC\"/>" +
                "<isofield id=\"1\" length=\"16\" name=\"BITMAP\" class=\"org.jpos.iso.IFA_NUMERIC\"/></isopackager>"),
        )
    }

    @Test
    fun `external entities are never resolved`() {
        val xml = """<?xml version="1.0"?>
            <!DOCTYPE isopackager [<!ENTITY secret SYSTEM "file:///etc/hostname">]>
            <isopackager>
              <isofield id="0" length="4" name="MTI &secret;" class="org.jpos.iso.IFA_NUMERIC"/>
              <isofield id="1" length="16" name="BITMAP" class="org.jpos.iso.IFA_BITMAP"/>
              <isofield id="3" length="6" name="PROCESSING CODE" class="org.jpos.iso.IFA_NUMERIC"/>
            </isopackager>"""
        val hostname = runCatching { java.io.File("/etc/hostname").readText().trim() }.getOrNull()
        val result = JposPackagerImporter.import(xml, "x", "X")
        if (hostname != null && hostname.isNotEmpty()) assertFalse(result.json.orEmpty().contains(hostname))
    }

    @Test
    fun `suggested ids follow the dialect id pattern`() {
        assertEquals("acme-packager", JposPackagerImporter.suggestId("Acme Packager.xml"))
        assertEquals("iso87ascii", JposPackagerImporter.suggestId("iso87ascii.xml"))
        assertEquals("imported-dialect", JposPackagerImporter.suggestId("___.xml"))
    }
}
