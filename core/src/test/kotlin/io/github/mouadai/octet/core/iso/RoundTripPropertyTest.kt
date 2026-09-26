package io.github.mouadai.octet.core.iso

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random

/**
 * Property-based round trips (SPEC M2): random valid messages for every built-in dialect must
 * encode, decode to the same values, and re-encode byte-identically; damaged bytes must never
 * throw. Seeds are fixed so failures reproduce.
 */
class RoundTripPropertyTest {

    companion object {
        @JvmStatic
        fun dialects(): List<Dialect> = BuiltinDialects.all + ebcdicDialect

        private val ebcdicDialect: Dialect by lazy {
            // The 1987 field layout with every element EBCDIC-encoded.
            val base = BuiltinDialects.load("iso8583-1987-ascii")
            base.copy(
                id = "ebcdic-test", name = "EBCDIC test",
                mtiEncoding = MtiEncoding.EBCDIC,
                bitmap = BitmapSpec(BitmapEncoding.HEX_EBCDIC),
                framings = listOf(FramingSpec.NONE, FramingSpec(LengthPrefix.BINARY_2)),
                fields = base.fields.mapValues { (_, f) -> f.copy(lengthEncoding = LengthEncoding.EBCDIC, dataEncoding = DataEncoding.EBCDIC) },
            )
        }

        private const val ITERATIONS = 300
    }

    private val decoder = IsoDecoder()

    /** A sequence of primitive EMV tags, so field 55 also decodes cleanly into children. */
    private fun randomTlv(maxBytes: Int, random: Random): String {
        val sb = StringBuilder()
        var size = 0
        while (random.nextInt(4) != 0) {
            val tag = listOf("9F02", "5F2A", "95", "9A", "9C", "82", "9F36").random(random)
            val length = random.nextInt(0, 7)
            if (size + tag.length / 2 + 1 + length > maxBytes) break
            sb.append(tag).append("%02X".format(length))
            repeat(length) { sb.append("%02X".format(random.nextInt(256))) }
            size += tag.length / 2 + 1 + length
        }
        return sb.toString()
    }

    private fun randomValue(spec: FieldSpec, random: Random): String {
        if (spec.subfields == SubfieldLayout.BerTlv) return randomTlv(spec.maxLength, random)
        val max = minOf(spec.maxLength, 60)
        val units = if (spec.lengthType == LengthType.FIXED) spec.maxLength else random.nextInt(0, max + 1)
        fun chars(pool: String, n: Int) = String(CharArray(n) { pool[random.nextInt(pool.length)] })
        val digits = "0123456789"
        return when (spec.type) {
            FieldType.N -> chars(digits, units)
            FieldType.Z -> chars(digits, units).let {
                if (it.length > 2) it.substring(0, it.length / 2) + "=" + it.substring(it.length / 2 + 1) else it
            }
            FieldType.XN -> if (units == 0) "" else chars("CD", 1) + chars(digits, units - 1)
            FieldType.B -> chars("0123456789ABCDEF", units * 2)
            FieldType.A -> chars("ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz", units)
            FieldType.AN -> chars("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ", units)
            FieldType.ANS, FieldType.CUSTOM -> chars((' '..'~').joinToString(""), units)
        }
    }

    private fun randomMessage(dialect: Dialect, random: Random): IsoMessageData {
        val ids = dialect.fields.keys.filter { random.nextInt(4) == 0 }
        val version = if (dialect.id.contains("1993")) "1" else "0"
        val mti = version + random.nextInt(1, 9) + random.nextInt(0, 8) + random.nextInt(0, 6)
        return IsoMessageData(mti, ids.associateWith { randomValue(dialect.fields.getValue(it), random) })
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    fun `random messages round-trip byte-identically`(dialect: Dialect) {
        val random = Random(dialect.id.hashCode())
        repeat(ITERATIONS) {
            val data = randomMessage(dialect, random)
            val framing = dialect.framings[random.nextInt(dialect.framings.size)]
            val withHeader = data.copy(header = if (framing.headerLength > 0) random.nextBytes(framing.headerLength) else null)
            val bytes = IsoEncoder.encode(withHeader, dialect, framing).bytes()

            val message = decoder.decode(bytes, dialect, framing).complete()
            assertEquals(withHeader, message.toData())
            assertBytes(bytes, IsoEncoder.encode(message.toData(), dialect, framing).bytes())

            // Offsets and lengths must point back at the exact bytes of each element.
            for (field in message.fields.values) {
                assertBytes(field.raw, bytes.copyOfRange(field.offset, field.offset + field.length))
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    fun `framing auto-detection recovers the framing used`(dialect: Dialect) {
        val random = Random(dialect.id.hashCode() + 1)
        repeat(ITERATIONS) {
            val framing = dialect.framings[random.nextInt(dialect.framings.size)]
            // Deterministic header content keeps detection unambiguous (a TPDU starts with 0x60).
            val data = randomMessage(dialect, random).copy(header = if (framing.headerLength > 0) ByteArray(framing.headerLength) { 0x60 } else null)
            val bytes = IsoEncoder.encode(data, dialect, framing).bytes()
            val message = decoder.decode(bytes, dialect).complete()
            assertEquals(data.fields, message.toData().fields)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dialects")
    fun `truncated and corrupted messages never throw`(dialect: Dialect) {
        val random = Random(dialect.id.hashCode() + 2)
        repeat(ITERATIONS / 3) {
            val bytes = IsoEncoder.encode(randomMessage(dialect, random), dialect).bytes()
            for (cut in bytes.indices) {
                val result = decoder.decode(bytes.copyOfRange(0, cut), dialect, FramingSpec.NONE)
                assertFalse(result.isComplete, "prefix of $cut bytes decoded cleanly")
                assertTrue(result.errors.all { it.offset in 0..cut })
            }
            val corrupted = bytes.copyOf()
            repeat(3) { corrupted[random.nextInt(corrupted.size)] = random.nextInt(256).toByte() }
            decoder.decode(corrupted, dialect) // must not throw
            decoder.decode(random.nextBytes(random.nextInt(0, 200)), dialect)
        }
    }
}
