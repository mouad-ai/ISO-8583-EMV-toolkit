package io.github.mouadai.octet.core.emv

/** A single interpreted part of a bitfield value, e.g. one set TVR bit. */
data class BitMeaning(
    /** 1-based byte index within the value. */
    val byte: Int,
    /** Bits covered, EMV numbering (8 = most significant). Single bit for flags, a range for coded fields. */
    val bits: IntRange,
    val label: String,
    val meaning: String,
) {
    override fun toString(): String {
        val b = if (bits.first == bits.last) "bit ${bits.first}" else "bits ${bits.last}-${bits.first}"
        return "Byte $byte $b: $meaning"
    }
}

/**
 * Bit-level decoders for AIP (82), TVR (95), TSI (9B), Terminal Capabilities (9F33),
 * CVM Results (9F34) and CID (9F27). Meanings come from a JSON resource.
 */
class EmvBitfields private constructor(
    private val flags: Map<String, FlagSpec>,
    private val cvmMethods: Map<Int, String>,
    private val cvmConditions: Map<Int, String>,
    private val cvmResults: Map<Int, String>,
    private val cidTypes: Map<Int, String>,
    private val cidReasons: Map<Int, String>,
) {
    private class ByteSpec(val label: String, val bits: Map<Int, String>)
    private class FlagSpec(val name: String, val bytes: List<ByteSpec>)

    fun supports(tag: String): Boolean = tag.uppercase().let { it in flags || it == "9F34" || it == "9F27" }

    /** Interprets [value] for [tag]; returns an empty list for tags this class does not know. */
    fun decode(tag: String, value: ByteArray): List<BitMeaning> = when (val t = tag.uppercase()) {
        "9F34" -> decodeCvmResults(value)
        "9F27" -> decodeCid(value)
        else -> flags[t]?.let { decodeFlags(it, value) } ?: emptyList()
    }

    private fun decodeFlags(spec: FlagSpec, value: ByteArray): List<BitMeaning> {
        val out = mutableListOf<BitMeaning>()
        value.forEachIndexed { i, byte ->
            val byteSpec = spec.bytes.getOrNull(i)
            val label = byteSpec?.label ?: "Byte ${i + 1}"
            for (bit in 8 downTo 1) {
                if (byte.toInt() and (1 shl (bit - 1)) != 0) {
                    out += BitMeaning(i + 1, bit..bit, label, byteSpec?.bits?.get(bit) ?: "RFU bit set")
                }
            }
        }
        return out
    }

    private fun decodeCvmResults(value: ByteArray): List<BitMeaning> {
        if (value.size != 3) return emptyList()
        val method = value[0].toInt() and 0xFF
        val code = method and 0x3F
        val out = mutableListOf<BitMeaning>()
        out += BitMeaning(1, 1..6, "CVM performed",
            cvmMethods[code] ?: if (code in 0x20..0x2F) "Payment system specific (%02X)".format(code)
            else if (code in 0x30..0x3E) "Issuer specific (%02X)".format(code) else "RFU (%02X)".format(code))
        if (method and 0x40 != 0) out += BitMeaning(1, 7..7, "CVM performed", "Apply succeeding CV Rule if this CVM is unsuccessful")
        if (method and 0x80 != 0) out += BitMeaning(1, 8..8, "CVM performed", "RFU bit set")
        val condition = value[1].toInt() and 0xFF
        out += BitMeaning(2, 1..8, "CVM condition", cvmConditions[condition] ?: "Unknown or proprietary (%02X)".format(condition))
        val result = value[2].toInt() and 0xFF
        out += BitMeaning(3, 1..8, "CVM result", cvmResults[result] ?: "Unknown (%02X)".format(result))
        return out
    }

    private fun decodeCid(value: ByteArray): List<BitMeaning> {
        if (value.size != 1) return emptyList()
        val b = value[0].toInt() and 0xFF
        val out = mutableListOf<BitMeaning>()
        out += BitMeaning(1, 7..8, "Cryptogram type", cidTypes.getValue(b ushr 6))
        if (b and 0x30 != 0) out += BitMeaning(1, 5..6, "Payment system specific", "Value %d".format((b ushr 4) and 0x03))
        if (b and 0x08 != 0) out += BitMeaning(1, 4..4, "Advice", "Advice required")
        val reason = b and 0x07
        out += BitMeaning(1, 1..3, "Reason/advice code", cidReasons[reason] ?: "RFU (%d)".format(reason))
        return out
    }

    companion object {
        const val RESOURCE = "/octet/emv/emv-bitfields.json"

        val default: EmvBitfields by lazy { fromMap(JsonResources.load(RESOURCE).asObject(RESOURCE)) }

        private fun codes(o: Map<String, Any?>, radix: Int): Map<Int, String> =
            o.filterKeys { !it.startsWith("_") }.map { (k, _) -> k.toInt(radix) to o.requireString(k) }.toMap()

        private fun fromMap(root: Map<String, Any?>): EmvBitfields {
            val flags = root["flags"].asObject("flags").map { (tag, v) ->
                val o = v.asObject(tag)
                tag.uppercase() to FlagSpec(o.requireString("name"), o["bytes"].asArray("bytes").map { b ->
                    val bo = b.asObject("byte")
                    val bits = bo["bits"].asObject("bits")
                    ByteSpec(bo.requireString("label"), bits.keys.associate { it.toInt() to bits.requireString(it) })
                })
            }.toMap()
            val cvm = root["cvm"].asObject("cvm")
            val cid = root["cid"].asObject("cid")
            return EmvBitfields(
                flags,
                codes(cvm["methods"].asObject("methods"), 16),
                codes(cvm["conditions"].asObject("conditions"), 16),
                codes(cvm["results"].asObject("results"), 16),
                codes(cid["types"].asObject("types"), 10),
                codes(cid["reasons"].asObject("reasons"), 10),
            )
        }
    }
}
