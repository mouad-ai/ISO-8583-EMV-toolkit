package io.github.mouadai.octet.core.emv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmvDecoderTest {
    private val decoder = EmvDecoder()

    @Test
    fun `SPEC section 11 samples`() {
        val r = decoder.decode("9F02060000000010005F2A0205049A032609259505" + "0000000000")
        assertTrue(r.errors.isEmpty())

        val amount = r.find("9F02")!!
        assertEquals("Amount, Authorised (Numeric)", amount.name)
        assertEquals("10.00 MAD", amount.value)

        assertEquals("504 MAD (Moroccan Dirham)", r.find("5F2A")!!.value)
        assertEquals("2026-09-25", r.find("9A")!!.value)

        val tvr = r.find("95")!!
        assertEquals("Terminal Verification Results (TVR)", tvr.name)
        assertEquals("0000000000", tvr.value)
        assertTrue(tvr.bits.isEmpty())
    }

    @Test
    fun `amount without currency defaults to two decimals and honours the exponent tag`() {
        assertEquals("10.00", decoder.decode("9F0206000000001000").find("9F02")!!.value)
        assertEquals("1000 JPY", decoder.decode("9F02060000000010005F2A020392").find("9F02")!!.value)
        assertEquals("1.000 KWD", decoder.decode("9F02060000000010005F2A020414").find("9F02")!!.value)
        assertEquals("0.05", decoder.decode("9F03060000000000055F360102").find("9F03")!!.value)
    }

    @Test
    fun `TVR bits`() {
        // Byte 1 bit 8 (ODA not performed), byte 3 bit 3 (online PIN), byte 4 bit 8 (floor limit), byte 5 bit 7.
        val tvr = decoder.decode("95 05 8000048040").find("95")!!
        assertEquals(
            listOf(
                "Byte 1 bit 8: Offline data authentication was not performed",
                "Byte 3 bit 3: Online PIN entered",
                "Byte 4 bit 8: Transaction exceeds floor limit",
                "Byte 5 bit 7: Issuer authentication failed",
            ),
            tvr.bits.map { it.toString() },
        )
    }

    @Test
    fun `RFU bits are reported, not dropped`() {
        val tvr = decoder.decode("95 05 0100000000").find("95")!!
        assertEquals("Byte 1 bit 1: RFU bit set", tvr.bits.single().toString())
    }

    @Test
    fun `AIP bits`() {
        // 1980: byte 1 = 0001 1001 -> bit 5, bit 4, bit 1; byte 2 = 1000 0000 -> kernel specific bit 8.
        val aip = decoder.decode("82 02 1980").find("82")!!
        assertEquals(
            listOf(
                "Cardholder verification is supported",
                "Terminal risk management is to be performed",
                "CDA supported",
                "RFU bit set",
            ),
            aip.bits.map { it.meaning },
        )
    }

    @Test
    fun `TSI bits`() {
        val tsi = decoder.decode("9B 02 E800").find("9B")!!
        assertEquals(
            listOf(
                "Offline data authentication was performed",
                "Cardholder verification was performed",
                "Card risk management was performed",
                "Terminal risk management was performed",
            ),
            tsi.bits.map { it.meaning },
        )
    }

    @Test
    fun `terminal capabilities bits carry their byte label`() {
        val caps = decoder.decode("9F33 03 E0F8C8").find("9F33")!!
        val byLabel = caps.bits.groupBy({ it.label }, { it.meaning })
        assertEquals(listOf("Manual key entry", "Magnetic stripe", "IC with contacts"), byLabel["Byte 1 (card data input capability)"])
        assertEquals(
            listOf("Plaintext PIN for ICC verification", "Enciphered PIN for online verification", "Signature (paper)",
                "Enciphered PIN for offline verification", "No CVM required"),
            byLabel["Byte 2 (CVM capability)"],
        )
        assertEquals(listOf("SDA", "DDA", "CDA"), byLabel["Byte 3 (security capability)"])
    }

    @Test
    fun `CVM results`() {
        val cvm = decoder.decode("9F34 03 420302").find("9F34")!!
        assertEquals(
            listOf(
                "Byte 1 bits 6-1: Enciphered PIN verified online",
                "Byte 1 bit 7: Apply succeeding CV Rule if this CVM is unsuccessful",
                "Byte 2 bits 8-1: If terminal supports the CVM",
                "Byte 3 bits 8-1: Successful",
            ),
            cvm.bits.map { it.toString() },
        )

        val none = decoder.decode("9F34 03 3F0000").find("9F34")!!
        assertEquals(listOf("No CVM performed", "Always", "Unknown"), none.bits.map { it.meaning })

        val sig = decoder.decode("9F34 03 1E0300").find("9F34")!!
        assertEquals("Signature (paper)", sig.bits.first().meaning)
    }

    @Test
    fun `CID`() {
        assertEquals(listOf("ARQC (online authorisation requested)", "No information given"),
            decoder.decode("9F27 01 80").find("9F27")!!.bits.map { it.meaning })
        assertEquals(listOf("TC (transaction approved offline)", "No information given"),
            decoder.decode("9F27 01 40").find("9F27")!!.bits.map { it.meaning })
        assertEquals(listOf("AAC (transaction declined)", "Advice required", "PIN Try Limit exceeded"),
            decoder.decode("9F27 01 0A").find("9F27")!!.bits.map { it.meaning })
    }

    @Test
    fun `bit decoders ignore values of the wrong size`() {
        assertTrue(decoder.decode("9F34 02 4203").find("9F34")!!.bits.isEmpty())
    }

    @Test
    fun `PAN, track 2 and cardholder name are masked by default`() {
        // 4761739001010119 is a widely published EMV test card number.
        val hex = "5A 08 4761739001010119" +
            "57 13 4761739001010119D22122011143804400000F" +
            "5F20 0A 544553542F43415244 20"
        val r = decoder.decode(hex)
        val pan = r.find("5A")!!
        assertTrue(pan.masked)
        assertEquals("476173******0119", pan.value)
        assertEquals("476173******0119", pan.rawHex)
        assertEquals("476173******0119D" + "*".repeat(20), r.find("57")!!.value)
        assertEquals("**********", r.find("5F20")!!.value)
        assertEquals("********************", r.find("5F20")!!.rawHex)

        val clear = decoder.decode(hex, reveal = true)
        assertFalse(clear.find("5A")!!.masked)
        assertEquals("4761739001010119", clear.find("5A")!!.value)
        assertEquals("4761739001010119D22122011143804400000", clear.find("57")!!.value)
        assertEquals("TEST/CARD ", clear.find("5F20")!!.value)
    }

    @Test
    fun `masking applies inside constructed templates`() {
        val r = decoder.decode("70 0A 5A 08 4761739001010119")
        assertEquals("476173******0119", r.find("5A")!!.value)
        assertEquals("Unknown tag", decoder.decode("DF01 01 00").find("DF01")!!.name)
    }

    @Test
    fun `DOLs list tags and lengths`() {
        val r = decoder.decode("8C 0E 9F02 06 9F03 06 9F1A 02 95 05 5F2A 02")
        val cdol = r.find("8C")!!
        assertEquals(listOf("9F02" to 6, "9F03" to 6, "9F1A" to 2, "95" to 5, "5F2A" to 2), cdol.dol.map { it.tag.hex to it.length })
        assertEquals(listOf(2, 5, 8, 11, 13), cdol.dol.map { it.offset })
        assertTrue(r.toText().contains("9F1A (Terminal Country Code) length 2"))
    }

    @Test
    fun `DOL parser reports a dangling tag`() {
        val r = Dol.parse("9F02 06 9F")
        assertEquals(1, r.entries.size)
        assertEquals("DOL tag at offset 0x3 is truncated.", r.errors.single().message)
        assertEquals("DOL tag 9A: missing length at offset 0x1.", Dol.parse("9A").errors.single().message)
    }

    @Test
    fun `country, time and text formatting`() {
        val r = decoder.decode("9F1A 02 0504 9F21 03 134501 9F1E 08 3132333435363738 8A 02 3030 9C 01 00")
        assertEquals("504 MA (Morocco)", r.find("9F1A")!!.value)
        assertEquals("13:45:01", r.find("9F21")!!.value)
        assertEquals("12345678", r.find("9F1E")!!.value)
        assertEquals("00", r.find("8A")!!.value)
        assertEquals("00", r.find("9C")!!.value)
    }

    @Test
    fun `bad values produce warnings, not failures`() {
        val r = decoder.decode("9A 03 261399 9F02 06 00000000100A 50 02 0001")
        assertEquals("261399", r.find("9A")!!.value)
        assertEquals(listOf("Not a valid date"), r.find("9A")!!.warnings)
        assertEquals(listOf("Expected BCD digits"), r.find("9F02")!!.warnings)
        assertEquals("0001", r.find("50")!!.value)
        assertEquals(listOf("Not printable ASCII; shown as hex"), r.find("50")!!.warnings)
    }

    @Test
    fun `malformed input yields partial elements and an error`() {
        val r = decoder.decode("9A 03 260925 9F02 06 000000")
        assertEquals("2026-09-25", r.find("9A")!!.value)
        val amount = r.find("9F02")!!
        assertTrue(amount.warnings.any { it.startsWith("Value truncated") })
        assertEquals(1, r.errors.size)
        assertTrue(r.toText().contains("Error: Tag 9F02: length 6 exceeds remaining 3 bytes at offset 0x8."))
    }

    @Test
    fun `invalid hex is an error, not an exception`() {
        val r = decoder.decode("9F02 zz")
        assertTrue(r.elements.isEmpty())
        assertEquals("Invalid hex character 'z' at position 5", r.errors.single().message)
        assertNull(r.find("9F02"))
    }

    @Test
    fun `text report of a typical field 55`() {
        val field55 = "82 02 1980 95 05 8000048000 9A 03 260925 9C 01 00 9F02 06 000000001000 5F2A 02 0504 " +
            "9F27 01 80 9F34 03 420302"
        val text = decoder.decode(field55).toText()
        assertEquals(
            """
            82 Application Interchange Profile (AIP) [2]: 1980
                Byte 1 bit 5: Cardholder verification is supported
                Byte 1 bit 4: Terminal risk management is to be performed
                Byte 1 bit 1: CDA supported
                Byte 2 bit 8: RFU bit set
            95 Terminal Verification Results (TVR) [5]: 8000048000
                Byte 1 bit 8: Offline data authentication was not performed
                Byte 3 bit 3: Online PIN entered
                Byte 4 bit 8: Transaction exceeds floor limit
            9A Transaction Date [3]: 2026-09-25
            9C Transaction Type [1]: 00
            9F02 Amount, Authorised (Numeric) [6]: 10.00 MAD
            5F2A Transaction Currency Code [2]: 504 MAD (Moroccan Dirham)
            9F27 Cryptogram Information Data (CID) [1]: 80
                Byte 1 bits 8-7: ARQC (online authorisation requested)
                Byte 1 bits 3-1: No information given
            9F34 Cardholder Verification Method (CVM) Results [3]: 420302
                Byte 1 bits 6-1: Enciphered PIN verified online
                Byte 1 bit 7: Apply succeeding CV Rule if this CVM is unsuccessful
                Byte 2 bits 8-1: If terminal supports the CVM
                Byte 3 bits 8-1: Successful

            """.trimIndent(),
            text,
        )
    }
}
