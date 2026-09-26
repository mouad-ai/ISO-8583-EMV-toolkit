package io.github.mouadai.cardwire.core.view

import io.github.mouadai.cardwire.core.iso.BuiltinDialects
import io.github.mouadai.cardwire.core.iso.EncodeResult
import io.github.mouadai.cardwire.core.iso.FramingSpec
import io.github.mouadai.cardwire.core.iso.IsoEncoder
import io.github.mouadai.cardwire.core.iso.IsoMessageData
import io.github.mouadai.cardwire.core.iso.LengthPrefix
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Hand-crafted messages (SPEC 11): public test PAN 4111 1111 1111 1111, obviously fake values elsewhere. */
class IsoTreeTest {

    private val ascii87 = BuiltinDialects.load("iso8583-1987-ascii")
    private val binary87 = BuiltinDialects.load("iso8583-1987-binary")

    private val data = IsoMessageData(
        mti = "0100",
        fields = sortedMapOf(
            2 to "4111111111111111",
            3 to "000000",
            4 to "000000001000",
            35 to "4111111111111111=29122010000000000",
            41 to "TERM0001",
            55 to "9F02060000000010005F2A020504",
        ),
    )

    private fun encode(dialect: io.github.mouadai.cardwire.core.iso.Dialect, framing: FramingSpec = FramingSpec.NONE): ByteArray =
        (IsoEncoder.encode(data, dialect, framing) as EncodeResult.Success).bytes

    private fun view(bytes: ByteArray, dialect: io.github.mouadai.cardwire.core.iso.Dialect = ascii87, reveal: Boolean = false) =
        DecodeView.decode(bytes, DecodeMode.ISO_8583, reveal, dialect)

    @Test
    fun `top level shows MTI, bitmap and fields in order`() {
        val bytes = encode(ascii87)
        val root = view(bytes).root
        assertEquals("ISO 8583", root.id)
        assertEquals(bytes.size, root.length)
        assertEquals(listOf("MTI", "Bitmap", "DE 2", "DE 3", "DE 4", "DE 35", "DE 41", "DE 55"), root.children.map { it.id })

        val mti = root.children[0]
        assertEquals("0100", mti.value)
        assertEquals("1987 / Authorization / Request / Acquirer", mti.name)
        assertEquals(0, mti.offset)
        assertEquals(4, mti.length)

        assertEquals("2, 3, 4, 35, 41, 55", root.children[1].value)
    }

    @Test
    fun `PAN and track 2 are masked in the tree and in the hex view`() {
        val bytes = encode(ascii87)
        val view = view(bytes)
        val pan = view.root.children.first { it.id == "DE 2" }
        assertEquals("411111******1111", pan.value)
        assertTrue(pan.rawHex.all { it == '*' })
        val track2 = view.root.children.first { it.id == "DE 35" }
        assertEquals("411111******1111=*****************", track2.value)

        // PAN value follows MTI (4), bitmap (16) and the LL prefix (2).
        assertTrue(22..37 in view.maskedRanges, "${view.maskedRanges}")
        assertEquals("TERM0001", view.root.children.first { it.id == "DE 41" }.value)
    }

    @Test
    fun `reveal shows clear values and masks nothing`() {
        val view = view(encode(ascii87), reveal = true)
        assertEquals("4111111111111111", view.root.children.first { it.id == "DE 2" }.value)
        assertEquals(emptyList<IntRange>(), view.maskedRanges)
    }

    @Test
    fun `field 55 expands into formatted EMV tags located in the message, hex-text dialect`() {
        val bytes = encode(ascii87)
        val de55 = view(bytes).root.children.first { it.id == "DE 55" }
        assertEquals(listOf("9F02", "5F2A"), de55.children.map { it.id })
        val amount = de55.children[0]
        assertEquals("10.00 MAD", amount.value)
        // In the ASCII dialect each byte is two hex characters, so offsets and lengths double.
        assertEquals(de55.offset + 3, amount.offset) // after the LLL prefix
        assertEquals(18, amount.length)
        assertEquals("9F02", String(bytes, amount.offset, 4, Charsets.ISO_8859_1))
    }

    @Test
    fun `field 55 expands with byte offsets in a binary dialect`() {
        val bytes = encode(binary87)
        val de55 = view(bytes, binary87).root.children.first { it.id == "DE 55" }
        val amount = de55.children.first { it.id == "9F02" }
        assertEquals(9, amount.length)
        assertEquals(0x9F.toByte(), bytes[amount.offset])
        assertEquals(0x02.toByte(), bytes[amount.offset + 1])
    }

    @Test
    fun `framing appears as its own node`() {
        val framing = FramingSpec(LengthPrefix.ASCII_4)
        val bytes = encode(ascii87, framing)
        val root = view(bytes).root
        val frame = root.children.first()
        assertEquals("Framing", frame.id)
        assertEquals(0, frame.offset)
        assertEquals(4, frame.length)
        assertEquals("MTI", root.children[1].id)
    }

    @Test
    fun `truncated input keeps the decoded part and reports the problem`() {
        val bytes = encode(ascii87)
        val view = view(bytes.copyOf(30))
        assertEquals(listOf("MTI", "Bitmap"), view.root.children.map { it.id })
        assertEquals(1, view.problems.size)
        assertTrue(view.problems.single().startsWith("Field 2"), view.problems.single())
    }

    @Test
    fun `hand-written 0800 with a secondary bitmap`() {
        val text = "0800" + "8220000000000000" + "0400000000000000" + "0925221000" + "123456" + "301"
        val view = view(text.toByteArray(Charsets.ISO_8859_1))
        assertEquals(emptyList<String>(), view.problems)
        assertEquals(listOf("MTI", "Bitmap", "DE 7", "DE 11", "DE 70"), view.root.children.map { it.id })
        assertEquals("7, 11, 70", view.root.children[1].value)
    }
}
