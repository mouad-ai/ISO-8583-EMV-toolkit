package io.github.mouadai.octet.core.iso

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialectLoaderTest {

    @Test
    fun `built-in dialects load with every field defined`() {
        assertEquals(listOf("iso8583-1987-ascii", "iso8583-1987-binary", "iso8583-1993-ascii"), BuiltinDialects.all.map { it.id })
        assertEquals((2..128).toList(), ascii87.fields.keys.toList())
        assertEquals((2..128).toList(), binary87.fields.keys.toList())
        assertEquals((2..128).toList() - 65, ascii93.fields.keys.toList())
        assertTrue(ascii93.bitmap.tertiary)
        assertEquals("1993", ascii93.version)
    }

    @Test
    fun `built-in encodings and framings`() {
        assertEquals(DataEncoding.BCD_LEFT_PAD, binary87.field(2)!!.dataEncoding)
        assertEquals(LengthEncoding.BCD, binary87.field(2)!!.lengthEncoding)
        assertEquals(DataEncoding.BCD_RIGHT_PAD, binary87.field(35)!!.dataEncoding)
        assertEquals(DataEncoding.BINARY, binary87.field(52)!!.dataEncoding)
        assertEquals(DataEncoding.ASCII, binary87.field(41)!!.dataEncoding)
        assertEquals(DataEncoding.ASCII, ascii87.field(52)!!.dataEncoding)
        assertEquals(SubfieldLayout.BerTlv, binary87.field(55)!!.subfields)
        assertEquals(setOf(2, 14, 34, 35, 36, 45, 52), ascii87.fields.values.filter { it.sensitive }.map { it.id }.toSet())
        assertEquals(
            listOf(FramingSpec.NONE, FramingSpec(LengthPrefix.BINARY_2), FramingSpec(LengthPrefix.BINARY_2, 5), FramingSpec(headerLength = 5)),
            binary87.framings,
        )
    }

    @Test
    fun `reads explicit field options`() {
        val dialect = loadDialect(
            """{"id": "x", "name": "X", "version": "1987", "mti": {"encoding": "BCD"}, "bitmap": {"encoding": "BINARY"},
               "framing": [{"type": "HEADER", "length": 12, "prefix": "LENGTH_4_ASCII"}],
               "fields": {"35": {"name": "Track 2", "type": "z", "lengthType": "LLVAR", "maxLength": 37,
                                 "lengthEncoding": "BINARY", "dataEncoding": "BCD_RIGHT_PAD",
                                 "padding": {"side": "RIGHT", "char": "F"}, "sensitive": true}}}""",
        )
        assertEquals(listOf(FramingSpec(LengthPrefix.ASCII_4, 12)), dialect.framings)
        assertEquals(
            FieldSpec(35, "Track 2", FieldType.Z, LengthType.LLVAR, 37, LengthEncoding.BINARY, DataEncoding.BCD_RIGHT_PAD, Padding(PadSide.RIGHT, 'F'), true),
            dialect.field(35),
        )
    }

    @Test
    fun `extends merges field by field over a built-in base`() {
        val dialect = loadDialect(
            """{"id": "acme", "name": "Acme processor", "extends": "iso8583-1987-ascii",
               "fields": {
                 "2":  {"maxLength": 16},
                 "48": {"remove": true},
                 "63": {"name": "Acme private data", "type": "ans", "lengthType": "LLLVAR", "maxLength": 999,
                        "subfields": {"layout": "PRIVATE_TLV", "tagLength": 2, "lengthLength": 3}}
               }}""",
        )
        assertEquals("Acme processor", dialect.name)
        assertEquals("1987", dialect.version)
        assertEquals(ascii87.field(2)!!.copy(maxLength = 16), dialect.field(2))
        assertNull(dialect.field(48))
        assertEquals(SubfieldLayout.PrivateTlv(2, 3), dialect.field(63)!!.subfields)
        assertEquals(ascii87.bitmap, dialect.bitmap)
        assertEquals(ascii87.field(4), dialect.field(4))
    }

    @Test
    fun `extends chains through user dialects and replaces top-level settings`() {
        val bases = mapOf(
            "acme-base" to """{"id": "acme-base", "name": "Acme base", "extends": "iso8583-1987-binary",
                              "bitmap": {"encoding": "BINARY", "tertiary": true}, "fields": {"65": {"remove": true}}}""",
        )
        val result = DialectLoader.load(
            """{"id": "acme-v2", "name": "Acme v2", "extends": "acme-base", "framing": [{"type": "TPDU"}]}""",
        ) { bases[it] ?: BuiltinDialects.json(it) }
        val dialect = (result as DialectLoadResult.Success).dialect
        assertTrue(dialect.bitmap.tertiary)
        assertEquals(listOf(FramingSpec(headerLength = 5)), dialect.framings)
        assertEquals(binary87.field(2), dialect.field(2))
    }

    @Test
    fun `extends errors`() {
        fun errors(json: String, bases: Map<String, String> = emptyMap()) =
            (DialectLoader.load(json) { bases[it] ?: BuiltinDialects.json(it) } as DialectLoadResult.Failure).errors

        assertEquals(listOf("extends: unknown dialect 'nope'"), errors("""{"id": "a", "name": "A", "extends": "nope"}"""))
        assertEquals(
            listOf("extends: 'a' leads to a cycle (a -> b -> a)"),
            errors("""{"id": "a", "name": "A", "extends": "b"}""", mapOf("b" to """{"id": "b", "name": "B", "extends": "a"}""")),
        )
        assertEquals(
            listOf("fields.2: \"remove\" cannot be combined with other properties"),
            errors("""{"id": "a", "name": "A", "extends": "iso8583-1987-ascii", "fields": {"2": {"remove": true, "name": "x"}}}"""),
        )
        assertEquals(
            listOf("fields.150.name: is required", "fields.150.type: is required", "fields.150.lengthType: is required"),
            errors("""{"id": "a", "name": "A", "extends": "iso8583-1987-ascii", "fields": {"150": {"maxLength": 3}}}"""),
        )
    }

    @Test
    fun `reports every problem with its JSON path`() {
        val result = DialectLoader.load(
            """{"id": "x", "mti": {"encoding": "ASCII"}, "bitmap": {"encoding": "HEX"}, "framing": [{"type": "HEADER"}],
               "fields": {
                 "1": {"name": "Bad id", "type": "n", "lengthType": "FIXED", "maxLength": 2},
                 "2": {"name": "Bad type", "type": "q", "lengthType": "FIXED", "maxLength": 2},
                 "3": {"name": "Too long", "type": "n", "lengthType": "LLVAR", "maxLength": 100},
                 "4": {"name": "Bad subfields", "type": "ans", "lengthType": "FIXED", "maxLength": 4, "subfields": {"layout": "XML"}},
                 "5": {"name": "Bitmap subfields", "type": "ans", "lengthType": "LLLVAR", "maxLength": 99, "subfields": {"layout": "BITMAP"}}
               }}""",
        ) as DialectLoadResult.Failure
        assertEquals(
            listOf(
                "name: is required",
                "bitmap.encoding: unknown value 'HEX' (expected one of BINARY, HEX_ASCII, HEX_EBCDIC)",
                "framing[0].length: is required",
                "fields.1: field numbers must be 2 to 192",
                "fields.2.type: unknown type 'q' (expected n, a, an, ans, b, z, xn or custom)",
                "fields.3.maxLength: 100 does not fit a LLVAR prefix (max 99)",
                "fields.4.subfields.layout: unknown layout 'XML' (expected FIXED, BER_TLV, PRIVATE_TLV or BITMAP)",
                "fields.5.subfields.layout: BITMAP subfields are not supported yet",
            ),
            result.errors,
        )
    }

    @Test
    fun `invalid JSON is a load failure, not an exception`() {
        val result = DialectLoader.load("{ nope") as DialectLoadResult.Failure
        assertTrue(result.errors.single().startsWith("Invalid JSON"))
    }
}
