package io.github.mouadai.cardwire.core.iso

import io.github.mouadai.cardwire.core.emv.JsonResources

/** One dialect file's text; [fileName] names it in problems, [scope] tags its label (e.g. "project"). */
data class DialectSource(val fileName: String, val scope: String, val json: String)

/** A dialect as offered in a picker. Built-ins have a null [scope]. */
data class DialectEntry(val dialect: Dialect, val scope: String?) {
    val label: String get() = if (scope == null) dialect.name else "${dialect.name} ($scope)"
}

/**
 * The dialects a picker offers: built-ins first, then each source that loads, in order. A source
 * may `extend` a built-in or another source by id. Sources that fail to load are left out and
 * reported in [problems] as `fileName: error`.
 */
data class DialectCatalog(val entries: List<DialectEntry>, val problems: List<String>) {

    companion object {
        fun build(sources: List<DialectSource>, builtins: List<Dialect> = BuiltinDialects.all): DialectCatalog {
            val sourceById = sources.mapNotNull { source -> idOf(source.json)?.let { it to source.json } }.toMap()
            val baseJson = { id: String -> BuiltinDialects.json(id) ?: sourceById[id] }
            val entries = builtins.map { DialectEntry(it, null) }.toMutableList()
            val problems = ArrayList<String>()
            for (source in sources) {
                when (val result = DialectLoader.load(source.json, baseJson)) {
                    is DialectLoadResult.Success -> entries += DialectEntry(result.dialect, source.scope)
                    is DialectLoadResult.Failure -> problems += "${source.fileName}: ${result.errors.firstOrNull() ?: "invalid dialect"}"
                }
            }
            return DialectCatalog(entries, problems)
        }

        private fun idOf(json: String): String? =
            runCatching { (JsonResources.parse(json) as? Map<*, *>)?.get("id") as? String }.getOrNull()
    }
}
