package io.github.mouadai.cardwire.core.emv

/** A BER-TLV tag (1 or more bytes), compared by value. */
class TlvTag(bytes: ByteArray) {
    private val raw = bytes.copyOf()

    init {
        require(raw.isNotEmpty()) { "Empty tag" }
    }

    val bytes: ByteArray get() = raw.copyOf()
    val size: Int get() = raw.size
    val hex: String = Hex.encode(raw)

    /** Bit 6 of the first byte: the value is itself a sequence of TLV objects. */
    val isConstructed: Boolean get() = raw[0].toInt() and 0x20 != 0

    val tagClass: TagClass get() = TagClass.entries[(raw[0].toInt() and 0xC0) ushr 6]

    override fun equals(other: Any?) = other is TlvTag && other.hex == hex
    override fun hashCode() = hex.hashCode()
    override fun toString() = hex

    companion object {
        fun of(hex: String) = TlvTag(Hex.decode(hex))
    }
}

enum class TagClass { UNIVERSAL, APPLICATION, CONTEXT_SPECIFIC, PRIVATE }

/**
 * One decoded TLV object. [offset] is the absolute position of the first tag byte in the buffer the
 * caller cares about (see `baseOffset` in [BerTlv.decode]); [value] is exactly what was read, which
 * can be shorter than [declaredLength] when the input was truncated.
 */
class TlvNode internal constructor(
    val tag: TlvTag,
    value: ByteArray,
    val declaredLength: Int,
    val offset: Int,
    private val rawLength: ByteArray?,
    val children: List<TlvNode>,
) {
    private val data = value.copyOf()

    val value: ByteArray get() = data.copyOf()
    val valueHex: String get() = Hex.encode(data)
    val length: Int get() = data.size
    val isTruncated: Boolean get() = data.size < declaredLength

    /** Length field as it appeared in the input (keeps non-minimal long forms), or the minimal form. */
    val lengthBytes: ByteArray get() = rawLength?.copyOf() ?: BerTlv.encodeLength(data.size)
    val headerLength: Int get() = tag.size + lengthBytes.size
    val valueOffset: Int get() = offset + headerLength
    val totalLength: Int get() = headerLength + data.size

    fun find(tag: TlvTag): TlvNode? =
        if (this.tag == tag) this else children.firstNotNullOfOrNull { it.find(tag) }

    override fun toString() = "${tag.hex} ${length} $valueHex"

    companion object {
        fun primitive(tag: TlvTag, value: ByteArray): TlvNode {
            require(!tag.isConstructed) { "Tag ${tag.hex} is constructed; use constructed()" }
            return TlvNode(tag, value, value.size, 0, null, emptyList())
        }

        fun primitive(tagHex: String, valueHex: String) = primitive(TlvTag.of(tagHex), Hex.decode(valueHex))

        fun constructed(tag: TlvTag, children: List<TlvNode>): TlvNode {
            require(tag.isConstructed) { "Tag ${tag.hex} is primitive; use primitive()" }
            val value = BerTlv.encode(children)
            return TlvNode(tag, value, value.size, 0, null, children)
        }
    }
}

/** A problem found while parsing, at an absolute byte [offset]. */
data class TlvError(val offset: Int, val message: String) {
    override fun toString() = message
}

/** Best-effort parse outcome: everything that decoded cleanly, plus any errors. */
data class TlvParseResult(val nodes: List<TlvNode>, val errors: List<TlvError>) {
    val isComplete: Boolean get() = errors.isEmpty()

    /** Depth-first search for the first node with this tag. */
    fun find(tag: String): TlvNode? {
        val t = TlvTag.of(tag)
        return nodes.firstNotNullOfOrNull { it.find(t) }
    }
}

/** BER-TLV decoding and encoding as used by EMV (EMV Book 3, Annex B). */
object BerTlv {
    private const val MAX_DEPTH = 32

    fun decode(hex: String): TlvParseResult = decode(Hex.decode(hex))

    /**
     * Decodes [data] as a sequence of TLV objects. Never throws on malformed input: decoding stops at
     * the first structural error and returns what was decoded so far. [baseOffset] is added to every
     * offset, so a field 55 inside a larger message reports positions in that message.
     */
    fun decode(data: ByteArray, baseOffset: Int = 0): TlvParseResult {
        val errors = mutableListOf<TlvError>()
        val nodes = decodeRange(data, 0, data.size, baseOffset, errors, 0)
        return TlvParseResult(nodes, errors)
    }

    private fun decodeRange(
        data: ByteArray, start: Int, end: Int, base: Int, errors: MutableList<TlvError>, depth: Int,
    ): List<TlvNode> {
        val nodes = mutableListOf<TlvNode>()
        var pos = start
        while (pos < end) {
            // EMV Book 3 Annex B: 00 bytes may pad before, between or after data objects.
            if (data[pos].toInt() == 0x00) { pos++; continue }
            val tagStart = pos

            // Tag: low 5 bits all set means more bytes follow while bit 8 is set.
            pos++
            if (data[tagStart].toInt() and 0x1F == 0x1F) {
                while (true) {
                    if (pos >= end) {
                        errors += TlvError(base + tagStart,
                            "Tag ${Hex.encode(data, tagStart, pos)} is truncated at offset ${Hex.offset(base + pos)}.")
                        return nodes
                    }
                    val more = data[pos].toInt() and 0x80 != 0
                    pos++
                    if (!more) break
                }
            }
            val tag = TlvTag(data.copyOfRange(tagStart, pos))

            // Length: short form below 0x80, else 0x81..0x84 followed by 1..4 length bytes.
            if (pos >= end) {
                errors += TlvError(base + pos, "Tag ${tag.hex}: missing length at offset ${Hex.offset(base + pos)}.")
                return nodes
            }
            val lengthStart = pos
            val first = data[pos++].toInt() and 0xFF
            val length: Long
            if (first < 0x80) {
                length = first.toLong()
            } else {
                val count = first and 0x7F
                if (count == 0 || count > 4) {
                    val why = if (count == 0) "indefinite length (0x80) is not allowed" else "length uses $count bytes (max 4)"
                    errors += TlvError(base + lengthStart, "Tag ${tag.hex}: $why at offset ${Hex.offset(base + lengthStart)}.")
                    return nodes
                }
                if (end - pos < count) {
                    errors += TlvError(base + lengthStart,
                        "Tag ${tag.hex}: length needs $count more bytes but only ${end - pos} remain at offset ${Hex.offset(base + pos)}.")
                    return nodes
                }
                var l = 0L
                repeat(count) { l = (l shl 8) or (data[pos++].toLong() and 0xFF) }
                length = l
            }
            val rawLength = data.copyOfRange(lengthStart, pos)

            val remaining = end - pos
            if (length > remaining) {
                errors += TlvError(base + pos,
                    "Tag ${tag.hex}: length $length exceeds remaining $remaining bytes at offset ${Hex.offset(base + pos)}.")
                nodes += TlvNode(tag, data.copyOfRange(pos, end), minOf(length, Int.MAX_VALUE.toLong()).toInt(),
                    base + tagStart, rawLength, emptyList())
                return nodes
            }
            val valueEnd = pos + length.toInt()
            val children = when {
                !tag.isConstructed -> emptyList()
                depth >= MAX_DEPTH -> {
                    errors += TlvError(base + pos, "Tag ${tag.hex}: nesting deeper than $MAX_DEPTH levels at offset ${Hex.offset(base + pos)}.")
                    emptyList()
                }
                else -> decodeRange(data, pos, valueEnd, base, errors, depth + 1)
            }
            nodes += TlvNode(tag, data.copyOfRange(pos, valueEnd), length.toInt(), base + tagStart, rawLength, children)
            pos = valueEnd
        }
        return nodes
    }

    fun encode(nodes: List<TlvNode>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (node in nodes) out.write(encode(node))
        return out.toByteArray()
    }

    fun encode(node: TlvNode): ByteArray {
        val value = node.value
        return node.tag.bytes + node.lengthBytes + value
    }

    /** Minimal BER length encoding. */
    fun encodeLength(length: Int): ByteArray {
        require(length >= 0) { "Negative length" }
        return when {
            length < 0x80 -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            length <= 0xFFFF -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), length.toByte())
            length <= 0xFFFFFF -> byteArrayOf(0x83.toByte(), (length ushr 16).toByte(), (length ushr 8).toByte(), length.toByte())
            else -> byteArrayOf(0x84.toByte(), (length ushr 24).toByte(), (length ushr 16).toByte(), (length ushr 8).toByte(), length.toByte())
        }
    }
}
