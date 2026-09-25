package io.github.mouadai.octet.core.view

/**
 * Display-neutral tree of a decoded element, as shown in the decode tool window. Decoders (TLV,
 * ISO 8583) are adapted into this shape; [offset] and [length] locate the element in the input bytes.
 */
data class DecodeNode(
    val id: String,
    val name: String,
    val value: String,
    val offset: Int,
    val length: Int,
    val children: List<DecodeNode> = emptyList(),
    /** Raw value bytes as hex, masked like [value] when the element is sensitive. */
    val rawHex: String = "",
) {
    operator fun contains(byteOffset: Int): Boolean = byteOffset >= offset && byteOffset < offset + length

    /** Nodes from this one down to the deepest descendant covering [byteOffset], or null if none. */
    fun pathAt(byteOffset: Int): List<DecodeNode>? {
        if (byteOffset !in this) return null
        val deeper = children.firstNotNullOfOrNull { it.pathAt(byteOffset) }
        return listOf(this) + deeper.orEmpty()
    }
}
