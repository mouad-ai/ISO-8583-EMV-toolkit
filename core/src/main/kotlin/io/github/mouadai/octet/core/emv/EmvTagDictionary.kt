package io.github.mouadai.octet.core.emv

/** How a tag's value is rendered. */
enum class DisplayKind { HEX, NUMERIC, CN, TEXT, AMOUNT, DATE, TIME, CURRENCY, COUNTRY, BITFIELD, CVM, CID, DOL, TRACK2 }

/** How a sensitive value is hidden when masking is on (SPEC 6.8). */
enum class MaskRule { PAN, TRACK2, FULL }

data class TagInfo(
    val tag: String,
    val name: String,
    val format: String,
    val source: String,
    val description: String,
    val display: DisplayKind,
    val mask: MaskRule?,
) {
    val sensitive: Boolean get() = mask != null
}

/** EMV tag metadata, loaded from a JSON resource (bundled by default). */
class EmvTagDictionary(entries: Collection<TagInfo>) {
    private val byTag = entries.associateBy { it.tag.uppercase() }

    operator fun get(tag: String): TagInfo? = byTag[tag.uppercase()]
    operator fun get(tag: TlvTag): TagInfo? = byTag[tag.hex]

    val tags: Collection<TagInfo> get() = byTag.values

    companion object {
        const val RESOURCE = "/octet/emv/emv-tags.json"

        val default: EmvTagDictionary by lazy { fromMap(JsonResources.load(RESOURCE).asObject(RESOURCE)) }

        /** Builds a dictionary from JSON text in the same shape as the bundled `emv-tags.json`. */
        fun fromJson(text: String) = fromMap(JsonResources.parse(text).asObject("tag dictionary"))

        private fun fromMap(root: Map<String, Any?>) = EmvTagDictionary(
            root["tags"].asArray("tags").map { el ->
                val o = el.asObject("tag entry")
                TagInfo(
                    tag = o.requireString("tag").uppercase(),
                    name = o.requireString("name"),
                    format = o.string("format") ?: "b",
                    source = o.string("source") ?: "",
                    description = o.string("description") ?: "",
                    display = o.string("display")?.let { DisplayKind.valueOf(it.uppercase()) } ?: DisplayKind.HEX,
                    mask = o.string("mask")?.let { MaskRule.valueOf(it.uppercase()) },
                )
            }
        )
    }
}
