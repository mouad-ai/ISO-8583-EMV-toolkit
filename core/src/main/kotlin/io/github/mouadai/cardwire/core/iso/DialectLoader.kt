package io.github.mouadai.cardwire.core.iso

import io.github.mouadai.cardwire.core.emv.JsonResources

sealed interface DialectLoadResult {
    data class Success(val dialect: Dialect) : DialectLoadResult

    /** [errors] are user-facing, each naming the JSON path at fault, e.g. `fields.35.maxLength: ...`. */
    data class Failure(val errors: List<String>) : DialectLoadResult
}

/**
 * Reads dialect JSON files in the format documented in `docs/dialects.md` and described by
 * `dialect.schema.json` (SPEC 7): fields keyed by number, optional `extends` to override a base
 * dialect field by field. Omitted `lengthEncoding`/`dataEncoding` default to ASCII.
 */
object DialectLoader {

    /**
     * Loads one dialect. [baseJson] returns the JSON text of a dialect by id, for `extends`; the
     * default resolves built-in dialects only.
     */
    fun load(json: String, baseJson: (String) -> String? = BuiltinDialects::json): DialectLoadResult {
        val errors = ArrayList<String>()
        val merged = resolve(json, baseJson, errors, emptyList()) ?: return DialectLoadResult.Failure(errors)
        val reader = Reader()
        val dialect = reader.dialect(merged)
        return if (dialect != null && reader.errors.isEmpty()) DialectLoadResult.Success(dialect) else DialectLoadResult.Failure(reader.errors)
    }

    /** Parses [json] and, when it `extends` another dialect, merges it over its resolved base. */
    private fun resolve(json: String, baseJson: (String) -> String?, errors: MutableList<String>, chain: List<String>): Map<String, Any?>? {
        val root = try {
            JsonResources.parse(json)
        } catch (e: RuntimeException) {
            errors.add(if (chain.isEmpty()) "Invalid JSON: ${e.message}" else "Base dialect '${chain.last()}' is invalid JSON: ${e.message}")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val o = root as? Map<String, Any?> ?: run { errors.add("Expected a JSON object"); return null }
        val baseId = o["extends"] ?: return o
        if (baseId !is String) { errors.add("extends: expected a string"); return null }
        val id = o["id"] as? String ?: ""
        if (baseId == id || baseId in chain) {
            errors.add("extends: '$baseId' leads to a cycle (${(chain + id + baseId).filter { it.isNotEmpty() }.joinToString(" -> ")})")
            return null
        }
        val text = baseJson(baseId) ?: run { errors.add("extends: unknown dialect '$baseId'"); return null }
        val base = resolve(text, baseJson, errors, chain + id) ?: return null
        return merge(base, o, errors)
    }

    private fun merge(base: Map<String, Any?>, override: Map<String, Any?>, errors: MutableList<String>): Map<String, Any?>? {
        val result = LinkedHashMap(base)
        for ((key, value) in override) if (key != "extends" && key != "fields") result[key] = value
        if (!override.containsKey("description")) result.remove("description")

        @Suppress("UNCHECKED_CAST")
        val baseFields = (base["fields"] as? Map<String, Any?>).orEmpty()
        val overrideFields = override["fields"] ?: return result
        @Suppress("UNCHECKED_CAST")
        overrideFields as? Map<String, Any?> ?: run { errors.add("fields: expected an object keyed by field number"); return null }

        val fields = LinkedHashMap(baseFields)
        for ((number, entry) in overrideFields) {
            @Suppress("UNCHECKED_CAST")
            val change = entry as? Map<String, Any?> ?: run { errors.add("fields.$number: expected an object"); return null }
            when {
                change["remove"] == true -> {
                    if (change.size > 1) errors.add("fields.$number: \"remove\" cannot be combined with other properties")
                    if (fields.remove(number) == null) errors.add("fields.$number: cannot remove a field the base dialect does not define")
                }
                fields.containsKey(number) -> {
                    @Suppress("UNCHECKED_CAST")
                    fields[number] = LinkedHashMap(fields[number] as Map<String, Any?>).apply { putAll(change) }
                }
                else -> fields[number] = change
            }
        }
        result["fields"] = fields
        return if (errors.isEmpty()) result else null
    }

    private class Reader {
        val errors = ArrayList<String>()

        fun error(path: String, message: String): Nothing? {
            errors.add(if (path.isEmpty()) message else "$path: $message")
            return null
        }

        fun obj(value: Any?, path: String): Map<String, Any?>? {
            @Suppress("UNCHECKED_CAST")
            return value as? Map<String, Any?> ?: error(path, "expected an object")
        }

        fun string(o: Map<String, Any?>, key: String, path: String, default: String? = null): String? {
            val v = o[key] ?: return default ?: error(path.dot(key), "is required")
            return v as? String ?: error(path.dot(key), "expected a string")
        }

        fun int(o: Map<String, Any?>, key: String, path: String, default: Int? = null): Int? {
            val v = o[key] ?: return default ?: error(path.dot(key), "is required")
            return (v as? Long)?.takeIf { it in 0..Int.MAX_VALUE }?.toInt() ?: error(path.dot(key), "expected a non-negative integer")
        }

        fun bool(o: Map<String, Any?>, key: String, path: String): Boolean {
            val v = o[key] ?: return false
            return v as? Boolean ?: (error(path.dot(key), "expected true or false") ?: false)
        }

        inline fun <reified E : Enum<E>> enum(value: String?, path: String): E? {
            if (value == null) return null
            return enumValues<E>().firstOrNull { it.name == value }
                ?: error(path, "unknown value '$value' (expected one of ${enumValues<E>().joinToString { it.name }})")
        }

        fun String.dot(key: String) = if (isEmpty()) key else "$this.$key"

        fun dialect(o: Map<String, Any?>): Dialect? {
            val id = string(o, "id", "")
            val name = string(o, "name", "")
            val description = string(o, "description", "", default = "")
            val version = o["version"]?.let { v ->
                (v as? String)?.takeIf { it in setOf("1987", "1993", "2003") } ?: error("version", "expected \"1987\", \"1993\" or \"2003\"")
            }

            val mtiEncoding = obj(o["mti"] ?: return error("mti", "is required"), "mti")
                ?.let { enum<MtiEncoding>(string(it, "encoding", "mti"), "mti.encoding") }

            val bitmap = obj(o["bitmap"] ?: return error("bitmap", "is required"), "bitmap")?.let { b ->
                enum<BitmapEncoding>(string(b, "encoding", "bitmap"), "bitmap.encoding")?.let { BitmapSpec(it, bool(b, "tertiary", "bitmap")) }
            }

            val framings = when (val f = o["framing"]) {
                null -> listOf(FramingSpec.NONE)
                is List<*> -> f.mapIndexedNotNull { i, item -> framing(item, "framing[$i]") }.ifEmpty { listOf(FramingSpec.NONE) }
                else -> error("framing", "expected an array").let { emptyList() }
            }

            val fieldMap = obj(o["fields"] ?: return error("fields", "is required"), "fields") ?: return null
            val fields = sortedMapOf<Int, FieldSpec>()
            for ((key, item) in fieldMap) {
                val number = key.toIntOrNull()?.takeIf { it in 2..192 && it.toString() == key }
                if (number == null) {
                    error("fields.$key", "field numbers must be 2 to 192")
                    continue
                }
                field(number, item, "fields.$key")?.let { fields[number] = it }
            }
            if (bitmap?.tertiary == true && fields.containsKey(65)) {
                error("fields.65", "cannot be defined when bit 65 announces a tertiary bitmap")
            }

            if (id == null || name == null || mtiEncoding == null || bitmap == null || errors.isNotEmpty()) return null
            return Dialect(id, name, description ?: "", version, mtiEncoding, bitmap, framings, fields)
        }

        /** `{"type": NONE | LENGTH_2_BINARY | LENGTH_4_ASCII | TPDU | HEADER, "length": n, "prefix": ...}`. */
        fun framing(item: Any?, path: String): FramingSpec? {
            val o = obj(item, path) ?: return null
            val type = string(o, "type", path) ?: return null
            val prefix = o["prefix"]?.let { p ->
                when (p) {
                    "LENGTH_2_BINARY" -> LengthPrefix.BINARY_2
                    "LENGTH_4_ASCII" -> LengthPrefix.ASCII_4
                    else -> error(path.dot("prefix"), "unknown value '$p' (expected LENGTH_2_BINARY or LENGTH_4_ASCII)")
                }
            }
            if (o.containsKey("prefix") && type != "TPDU" && type != "HEADER") {
                return error(path.dot("prefix"), "only applies to TPDU and HEADER framing")
            }
            return when (type) {
                "NONE" -> FramingSpec.NONE
                "LENGTH_2_BINARY" -> FramingSpec(LengthPrefix.BINARY_2)
                "LENGTH_4_ASCII" -> FramingSpec(LengthPrefix.ASCII_4)
                "TPDU" -> FramingSpec(prefix ?: LengthPrefix.NONE, 5)
                "HEADER" -> {
                    val length = int(o, "length", path) ?: return null
                    if (length < 1) return error(path.dot("length"), "must be at least 1")
                    FramingSpec(prefix ?: LengthPrefix.NONE, length)
                }
                else -> error(path.dot("type"), "unknown value '$type' (expected NONE, LENGTH_2_BINARY, LENGTH_4_ASCII, TPDU or HEADER)")
            }
        }

        fun field(id: Int, item: Any?, path: String, fieldPath: String? = null): FieldSpec? {
            val o = obj(item, path) ?: return null
            val errorCount = errors.size
            val name = string(o, "name", path)
            val type = string(o, "type", path)?.let {
                FieldType.fromCode(it) ?: error(path.dot("type"), "unknown type '$it' (expected n, a, an, ans, b, z, xn or custom)")
            }
            val lengthType = enum<LengthType>(string(o, "lengthType", path), path.dot("lengthType"))
            val maxLength = int(o, "maxLength", path)
            if (maxLength != null && lengthType != null) {
                if (maxLength == 0) error(path.dot("maxLength"), "must be at least 1")
                val limit = when (lengthType) {
                    LengthType.FIXED, LengthType.LLLLVAR -> 9999
                    LengthType.LLVAR -> 99
                    LengthType.LLLVAR -> 999
                }
                if (maxLength > limit) error(path.dot("maxLength"), "$maxLength does not fit a ${lengthType.name} prefix (max $limit)")
            }
            val lengthEncoding = enum<LengthEncoding>(string(o, "lengthEncoding", path, default = "ASCII"), path.dot("lengthEncoding"))
            val dataEncoding = enum<DataEncoding>(string(o, "dataEncoding", path, default = "ASCII"), path.dot("dataEncoding"))
            val padding = o["padding"]?.let { padding(it, path.dot("padding")) }
            val sensitive = bool(o, "sensitive", path)
            val subfields = o["subfields"]?.let { subfields(it, path.dot("subfields"), dataEncoding, fieldPath ?: id.toString()) }
            if (errors.size > errorCount || name == null || type == null || lengthType == null || maxLength == null ||
                lengthEncoding == null || dataEncoding == null
            ) {
                return null
            }
            return FieldSpec(id, name, type, lengthType, maxLength, lengthEncoding, dataEncoding, padding, sensitive, subfields, fieldPath)
        }

        fun padding(item: Any?, path: String): Padding? {
            val o = obj(item, path) ?: return null
            val side = enum<PadSide>(string(o, "side", path), path.dot("side")) ?: return null
            val char = string(o, "char", path) ?: return null
            if (char.length != 1) return error(path.dot("char"), "must be a single character")
            return Padding(side, char[0])
        }

        fun subfields(item: Any?, path: String, dataEncoding: DataEncoding?, parentPath: String): SubfieldLayout? {
            val o = obj(item, path) ?: return null
            return when (val layout = string(o, "layout", path)) {
                null -> null
                "FIXED" -> {
                    val list = o["fields"] as? List<*> ?: return error(path.dot("fields"), "expected an array")
                    SubfieldLayout.Fixed(
                        list.mapIndexedNotNull { i, s ->
                            val p = "$path.fields[$i]"
                            val so = obj(s, p) ?: return@mapIndexedNotNull null
                            val sname = string(so, "name", p) ?: return@mapIndexedNotNull null
                            val len = int(so, "length", p)?.takeIf { it > 0 } ?: return@mapIndexedNotNull error(p.dot("length"), "must be at least 1")
                            SubfieldSpec((i + 1).toString(), sname, len, bool(so, "sensitive", p))
                        },
                    )
                }
                "PRIVATE_TLV" -> {
                    val tag = int(o, "tagLength", path)?.takeIf { it > 0 } ?: return error(path.dot("tagLength"), "must be at least 1")
                    val len = int(o, "lengthLength", path)?.takeIf { it in 1..4 } ?: return error(path.dot("lengthLength"), "must be between 1 and 4")
                    // Tags and lengths are read as characters of the decoded field value.
                    for (key in listOf("tagEncoding", "lengthEncoding")) {
                        val enc = o[key] ?: continue
                        if (dataEncoding?.isCharacter != true || enc != dataEncoding.name) {
                            error(path.dot(key), "only character tags and lengths in the field's own encoding are supported so far")
                        }
                    }
                    val tags = o["tags"]?.let { t ->
                        obj(t, path.dot("tags"))?.mapValues { (k, v) -> v as? String ?: error("$path.tags.$k", "expected a string").let { "" } }
                    }.orEmpty()
                    SubfieldLayout.PrivateTlv(tag, len, tags)
                }
                "BER_TLV" -> SubfieldLayout.BerTlv
                "BITMAP" -> {
                    val length = int(o, "bitmapLength", path) ?: return null
                    if (length !in 1..16) return error(path.dot("bitmapLength"), "must be between 1 and 16")
                    val encoding = enum<BitmapEncoding>(string(o, "bitmapEncoding", path, default = "BINARY"), path.dot("bitmapEncoding")) ?: return null
                    val fieldMap = obj(o["fields"] ?: return error(path.dot("fields"), "is required"), path.dot("fields")) ?: return null
                    val subs = sortedMapOf<Int, FieldSpec>()
                    for ((key, sub) in fieldMap) {
                        val number = key.toIntOrNull()?.takeIf { it in 1..length * 8 && it.toString() == key }
                        if (number == null) {
                            error("$path.fields.$key", "subfield numbers must be 1 to ${length * 8}")
                            continue
                        }
                        field(number, sub, "$path.fields.$key", "$parentPath.$number")?.let { subs[number] = it }
                    }
                    SubfieldLayout.Bitmapped(length, encoding, subs)
                }
                else -> error(path.dot("layout"), "unknown layout '$layout' (expected FIXED, BER_TLV, PRIVATE_TLV or BITMAP)")
            }
        }
    }
}

/** The generic dialects bundled with Cardwire (SPEC 7), read from JSON resources. */
object BuiltinDialects {
    val IDS: List<String> = listOf("iso8583-1987-ascii", "iso8583-1987-binary", "iso8583-1993-ascii")

    /** JSON text of a built-in dialect, or null for an unknown id. */
    fun json(id: String): String? {
        if (id !in IDS) return null
        val stream = BuiltinDialects::class.java.getResourceAsStream("/io/github/mouadai/cardwire/core/dialects/$id.json") ?: return null
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun load(id: String): Dialect {
        val text = json(id) ?: error("No built-in dialect '$id'")
        return when (val result = DialectLoader.load(text)) {
            is DialectLoadResult.Success -> result.dialect
            is DialectLoadResult.Failure -> error("Built-in dialect '$id' is invalid: ${result.errors.joinToString("; ")}")
        }
    }

    val all: List<Dialect> by lazy { IDS.map(::load) }
}
