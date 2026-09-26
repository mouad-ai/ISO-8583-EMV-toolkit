package io.github.mouadai.cardwire.core.jpos

import io.github.mouadai.cardwire.core.emv.JsonResources
import io.github.mouadai.cardwire.core.emv.asObject
import io.github.mouadai.cardwire.core.iso.Dialect
import io.github.mouadai.cardwire.core.iso.DialectJsonWriter
import io.github.mouadai.cardwire.core.iso.DialectLoadResult
import io.github.mouadai.cardwire.core.iso.DialectLoader
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Outcome of a jPOS import: the dialect JSON and parsed [dialect] on success, plus notes for the user. */
class JposImportResult(
    val json: String?,
    val dialect: Dialect?,
    /** Things the user should check: unmapped classes, guessed settings, dropped fields. */
    val warnings: List<String>,
    val errors: List<String>,
) {
    val isSuccess: Boolean get() = dialect != null
}

/**
 * Converts a jPOS `GenericPackager` XML file into an Cardwire dialect (SPEC 7, M6). Field classes are
 * mapped with the table in `cardwire/jpos/jpos-field-classes.json`; a class missing from it becomes a
 * raw binary field with a warning. Never throws, and never loads the packager's DTD or any other
 * external resource.
 */
object JposPackagerImporter {

    private const val VERSION_WARNING =
        "The ISO 8583 version is not part of a jPOS packager; \"version\" is set to 1987. Change it if your layout follows 1993 or 2003."

    /** Default sensitive data elements (SPEC 6.8). */
    private val SENSITIVE_FIELDS = setOf(2, 14, 34, 35, 36, 45, 52)

    fun import(xml: String, id: String, name: String): JposImportResult {
        val warnings = ArrayList<String>()
        val root = try {
            parse(xml)
        } catch (e: Exception) {
            val where = (e as? SAXParseException)?.let { " (line ${it.lineNumber})" } ?: ""
            return JposImportResult(null, null, warnings, listOf("Not a readable XML file$where: ${e.message}"))
        }
        if (root.tagName != "isopackager") {
            return JposImportResult(null, null, warnings, listOf("Expected an <isopackager> root element, found <${root.tagName}>."))
        }
        val errors = ArrayList<String>()
        val dialect = Converter(warnings, errors).convert(root, id, name) ?: return JposImportResult(null, null, warnings, errors)
        val json = DialectJsonWriter.write(dialect)
        return when (val loaded = DialectLoader.load(json)) {
            is DialectLoadResult.Success -> JposImportResult(json, loaded.dialect, warnings, errors)
            is DialectLoadResult.Failure -> JposImportResult(json, null, warnings, errors + loaded.errors)
        }
    }

    /** A dialect id derived from a file name, e.g. "Acme Packager.xml" -> "acme-packager". */
    fun suggestId(fileName: String): String =
        fileName.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-', '.', '_')
            .ifEmpty { "imported-dialect" }

    private fun parse(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            isExpandEntityReferences = false
            isXIncludeAware = false
            // jPOS packagers declare genericpackager.dtd; never fetch it or any other external entity.
            setFeatureIfSupported("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
            setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
            setFeatureIfSupported(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        builder.setErrorHandler(object : org.xml.sax.ErrorHandler {
            override fun warning(e: SAXParseException) {}
            override fun error(e: SAXParseException) { throw e }
            override fun fatalError(e: SAXParseException) { throw e }
        })
        return builder.parse(InputSource(StringReader(xml))).documentElement
    }

    private fun DocumentBuilderFactory.setFeatureIfSupported(feature: String, value: Boolean) {
        runCatching { setFeature(feature, value) }
    }

    private class ClassMapping(val kind: String, val properties: Map<String, Any?>)

    private val classTable: Map<String, ClassMapping> by lazy {
        JsonResources.load("/cardwire/jpos/jpos-field-classes.json").asObject("jPOS class table")["classes"]
            .asObject("classes").mapValues { (_, v) ->
                val o = v.asObject("class mapping")
                ClassMapping(o["kind"] as String, o - "kind")
            }
    }

    private class Converter(val warnings: MutableList<String>, val errors: MutableList<String>) {

        fun convert(root: Element, id: String, name: String): Map<String, Any?>? {
            var mtiEncoding: String? = null
            var bitmapEncoding: String? = null
            var tertiary = false
            val fields = sortedMapOf<Int, Map<String, Any?>>()

            for (el in children(root)) {
                val number = el.getAttribute("id").toIntOrNull()
                if (number == null) {
                    errors.add("<${el.tagName}> without a numeric id attribute.")
                    continue
                }
                val cls = simpleClass(el)
                val mapping = classTable[cls]
                when {
                    number == 0 -> {
                        val encoding = mapping?.properties?.get("dataEncoding") as? String
                        mtiEncoding = when (encoding) {
                            "ASCII", "EBCDIC" -> encoding
                            "BCD" -> "BCD"
                            else -> null
                        }
                        if (mtiEncoding == null) errors.add("Field 0 (MTI): jPOS class $cls is not a supported MTI class.")
                    }
                    mapping?.kind == "bitmap" -> when (number) {
                        1 -> bitmapEncoding = mapping.properties["bitmapEncoding"] as String
                        65 -> tertiary = true
                        else -> warnings.add("Field $number: bitmap class $cls outside fields 1 and 65 was skipped.")
                    }
                    number == 1 -> errors.add("Field 1 (bitmap): jPOS class $cls is not a bitmap class.")
                    number !in 2..192 -> warnings.add("Field $number is outside 2-192 and was skipped.")
                    else -> field(el, number, number.toString(), topLevel = true)?.let { fields[number] = it }
                }
            }
            if (mtiEncoding == null && errors.none { it.startsWith("Field 0") }) errors.add("The packager has no field 0 (MTI).")
            if (bitmapEncoding == null && errors.none { it.startsWith("Field 1") }) errors.add("The packager has no field 1 (bitmap).")
            if (tertiary) fields.remove(65)
            if (errors.isNotEmpty()) return null

            warnings.add(0, VERSION_WARNING)
            return linkedMapOf(
                "id" to id,
                "name" to name,
                "version" to "1987",
                "description" to "Imported from a jPOS GenericPackager file.",
                "mti" to mapOf("encoding" to mtiEncoding),
                "bitmap" to linkedMapOf<String, Any?>("encoding" to bitmapEncoding).apply { if (tertiary) put("tertiary", true) },
                "framing" to listOf(mapOf("type" to "NONE")),
                "fields" to fields.mapKeys { it.key.toString() },
            )
        }

        /** Converts one `isofield`/`isofieldpackager`; null when it is skipped. */
        fun field(el: Element, number: Int, path: String, topLevel: Boolean): Map<String, Any?>? {
            val cls = simpleClass(el)
            val mapping = classTable[cls]
            val fieldName = el.getAttribute("name").trim().ifEmpty { "Field $path" }
            if (mapping?.kind == "skip") {
                warnings.add("Field $path: $cls carries no data; field skipped.")
                return null
            }
            val length = el.getAttribute("length").toIntOrNull()?.takeIf { it > 0 }
            if (length == null) {
                warnings.add("Field $path: missing or invalid length; field skipped.")
                return null
            }
            if (mapping?.kind == "bitmap") {
                warnings.add("Field $path: bitmap class $cls used as a data field; field skipped.")
                return null
            }

            val out = linkedMapOf<String, Any?>("name" to fieldName)
            if (mapping == null) {
                val guess = guessRaw(cls)
                warnings.add(
                    "Field $path: jPOS class $cls is not in the mapping table; imported as raw bytes " +
                        "(${guess["lengthType"]}${guess["lengthEncoding"]?.let { ", $it length" } ?: ""}). Check its definition.",
                )
                out.putAll(guess)
            } else {
                for ((k, v) in mapping.properties) if (k != "bcdPad") out[k] = v
                if (mapping.properties["bcdPad"] == true) {
                    if (el.getAttribute("pad").equals("true", ignoreCase = true)) {
                        out["dataEncoding"] = "BCD_LEFT_PAD"
                    } else {
                        out["dataEncoding"] = "BCD_RIGHT_PAD"
                        out["padding"] = mapOf("side" to "RIGHT", "char" to "0")
                    }
                }
            }
            // jPOS numeric classes carry track data with its '=' or 'D' separator; Cardwire types it z.
            if (topLevel && (number == 35 || number == 36) && out["type"] == "n") out["type"] = "z"
            out["maxLength"] = clampLength(out["lengthType"] as String, length, path)
            // Keep a stable property order: name, type, lengthType, maxLength, encodings, padding.
            val ordered = linkedMapOf<String, Any?>()
            for (key in listOf("name", "type", "lengthType", "maxLength", "lengthEncoding", "dataEncoding", "padding")) {
                out[key]?.let { ordered[key] = it }
            }
            if (topLevel && number in SENSITIVE_FIELDS) ordered["sensitive"] = true

            if (el.tagName == "isofieldpackager") {
                subfields(el, path)?.let { ordered["subfields"] = it }
            } else if (topLevel && number == 55 && ordered["type"] == "b") {
                ordered["subfields"] = mapOf("layout" to "BER_TLV")
            }
            return ordered
        }

        fun subfields(el: Element, path: String): Map<String, Any?>? {
            val packager = el.getAttribute("packager").substringAfterLast('.')
            val subs = children(el)
            if (packager.contains("TLV", ignoreCase = true) || packager.contains("Tagged", ignoreCase = true)) {
                if (packager.contains("BERTLV", ignoreCase = true)) return mapOf("layout" to "BER_TLV")
                warnings.add("Field $path: tagged sub-field packager $packager cannot be converted; define its subfields by hand (PRIVATE_TLV).")
                return null
            }
            val bitmapEl = subs.firstOrNull { classTable[simpleClass(it)]?.kind == "bitmap" }
            if (bitmapEl != null || el.getAttribute("emitBitmap").equals("true", ignoreCase = true)) {
                if (bitmapEl == null) {
                    warnings.add("Field $path: emitBitmap is set but no bitmap sub-field is defined; subfields skipped.")
                    return null
                }
                val bitmapBytes = bitmapEl.getAttribute("length").toIntOrNull()?.coerceIn(1, 16) ?: 8
                if (bitmapBytes > 8) {
                    warnings.add("Field $path: sub-field bitmap of $bitmapBytes bytes is read as one fixed bitmap (no secondary-bitmap bit).")
                }
                val fields = sortedMapOf<Int, Map<String, Any?>>()
                for (sub in subs) {
                    if (sub === bitmapEl) continue
                    val n = sub.getAttribute("id").toIntOrNull()
                    if (n == null || n !in 1..bitmapBytes * 8) {
                        warnings.add("Field $path: sub-field ${sub.getAttribute("id")} is outside the sub-field bitmap and was skipped.")
                        continue
                    }
                    field(sub, n, "$path.$n", topLevel = false)?.let { fields[n] = it }
                }
                return linkedMapOf(
                    "layout" to "BITMAP",
                    "bitmapLength" to bitmapBytes,
                    "bitmapEncoding" to classTable.getValue(simpleClass(bitmapEl)).properties["bitmapEncoding"],
                    "fields" to fields.mapKeys { it.key.toString() },
                )
            }
            // Positional sub-fields: only fixed-length slices map onto the FIXED layout.
            val slices = ArrayList<Map<String, Any?>>()
            for (sub in subs.sortedBy { it.getAttribute("id").toIntOrNull() ?: Int.MAX_VALUE }) {
                val mapping = classTable[simpleClass(sub)]
                val length = sub.getAttribute("length").toIntOrNull()
                if (mapping?.properties?.get("lengthType") != "FIXED" || length == null || length < 1) {
                    warnings.add("Field $path: positional sub-field ${sub.getAttribute("id")} is not fixed-length; subfields skipped.")
                    return null
                }
                slices.add(linkedMapOf("name" to sub.getAttribute("name").trim().ifEmpty { "Subfield ${sub.getAttribute("id")}" }, "length" to length))
            }
            return if (slices.isEmpty()) null else linkedMapOf("layout" to "FIXED", "fields" to slices)
        }

        /** Best guess for an unmapped class: raw binary with a length prefix read from the class name. */
        fun guessRaw(cls: String): Map<String, Any?> {
            val lengthType = when {
                "LLLL" in cls -> "LLLLVAR"
                "LLL" in cls -> "LLLVAR"
                "LL" in cls -> "LLVAR"
                else -> "FIXED"
            }
            val lengthEncoding = when {
                lengthType == "FIXED" -> null
                cls.startsWith("IFB_") && "H" in cls.removePrefix("IFB_").substringBefore("BINARY") -> "BINARY"
                cls.startsWith("IFB_") -> "BCD"
                cls.startsWith("IFE") -> "EBCDIC"
                else -> "ASCII"
            }
            return linkedMapOf<String, Any?>("type" to "b", "lengthType" to lengthType).apply {
                lengthEncoding?.let { put("lengthEncoding", it) }
                put("dataEncoding", "BINARY")
            }
        }

        fun clampLength(lengthType: String, length: Int, path: String): Int {
            val max = when (lengthType) {
                "LLVAR" -> 99
                "LLLVAR" -> 999
                else -> 9999
            }
            if (length > max) {
                warnings.add("Field $path: length $length does not fit a $lengthType prefix; using $max.")
                return max
            }
            return length
        }

        fun children(el: Element): List<Element> {
            val out = ArrayList<Element>()
            val nodes = el.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i)
                if (n is Element && (n.tagName == "isofield" || n.tagName == "isofieldpackager")) out.add(n)
            }
            return out
        }

        fun simpleClass(el: Element): String = el.getAttribute("class").trim().substringAfterLast('.')
    }
}
