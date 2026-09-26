package io.github.mouadai.cardwire.core.emv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmvResourcesTest {
    @Test
    fun `dictionary covers the SPEC minimum tag set`() {
        val required = listOf(
            "4F", "50", "57", "5A", "5F20", "5F24", "5F2A", "5F34", "82", "84", "8A", "8E", "91", "95", "9A", "9B", "9C",
            "9F02", "9F03", "9F07", "9F09", "9F0D", "9F0E", "9F0F", "9F10", "9F12", "9F1A", "9F1E", "9F21", "9F26", "9F27",
            "9F33", "9F34", "9F35", "9F36", "9F37", "9F41", "9F53", "9F6E",
        )
        val dict = EmvTagDictionary.default
        val missing = required.filter { dict[it] == null }
        assertTrue(missing.isEmpty(), "Missing tags: $missing")
    }

    @Test
    fun `dictionary entries are valid`() {
        for (info in EmvTagDictionary.default.tags) {
            val tag = Hex.decode(info.tag)
            // Tag must be exactly one well-formed BER tag.
            val parsed = BerTlv.decode(tag + byteArrayOf(0))
            assertEquals(info.tag, parsed.nodes.singleOrNull()?.tag?.hex, "Malformed tag ${info.tag}")
            assertTrue(info.name.isNotBlank() && info.description.isNotBlank(), info.tag)
        }
    }

    @Test
    fun `sensitive defaults match SPEC 6_8`() {
        val dict = EmvTagDictionary.default
        assertEquals(MaskRule.PAN, dict["5A"]!!.mask)
        assertEquals(MaskRule.TRACK2, dict["57"]!!.mask)
        assertEquals(MaskRule.FULL, dict["5F20"]!!.mask)
        assertEquals(MaskRule.FULL, dict["9F1F"]!!.mask)
        assertEquals(null, dict["9F26"]!!.mask)
    }

    @Test
    fun `every bitfield tag in the dictionary has a decoder`() {
        val bitfields = EmvBitfields.default
        val bitTags = EmvTagDictionary.default.tags
            .filter { it.display in setOf(DisplayKind.BITFIELD, DisplayKind.CVM, DisplayKind.CID) }.map { it.tag }
        assertEquals(setOf("82", "95", "9B", "9F33", "9F34", "9F27"), bitTags.toSet())
        bitTags.forEach { assertTrue(bitfields.supports(it), it) }
    }

    @Test
    fun `reference tables`() {
        val t = ReferenceTables.default
        assertEquals("MAD", t.currency("504")!!.alpha)
        assertEquals(2, t.currency("978")!!.exponent)
        assertEquals(0, t.currency("392")!!.exponent)
        assertEquals(3, t.currency("414")!!.exponent)
        assertEquals("MA", t.country("504")!!.alpha2)
        assertEquals("US", t.country("840")!!.alpha2)
        assertNotNull(t.country("0250")) // BCD n3 in 2 bytes carries a leading zero
    }

    @Test
    fun `custom dictionary from JSON`() {
        val dict = EmvTagDictionary.fromJson(
            """{"tags":[{"tag":"df01","name":"Private tag","description":"Test","display":"text","mask":"full"}]}"""
        )
        val r = EmvDecoder(dictionary = dict).decode("DF01 02 4142")
        assertEquals("Private tag", r.elements.single().name)
        assertEquals("**", r.elements.single().value)
    }

    @Test
    fun `json reader handles the shapes the resources use and rejects bad input`() {
        val v = JsonResources.parse("""{"a": [1, -2.5, true, null, "x\u0041\n"], "b": {}}""").asObject("root")
        assertEquals(listOf(1L, -2.5, true, null, "xA\n"), v["a"])
        assertEquals(emptyMap<String, Any?>(), v["b"])
        assertThrows(IllegalArgumentException::class.java) { JsonResources.parse("""{"a": 1,}""") }
        assertThrows(IllegalArgumentException::class.java) { JsonResources.parse("""[1] 2""") }
    }
}
