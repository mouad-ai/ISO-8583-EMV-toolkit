package io.github.mouadai.octet.core.diff

/**
 * Plain-text list of differences, for copying into a ticket or test failure. Values come from the
 * display tree, so sensitive values are as masked as they were when the messages were decoded.
 */
object DiffReport {

    fun render(diff: DiffNode): String {
        if (!diff.hasChanges) return "No differences"
        val s = diff.summary()
        val lines = mutableListOf("${s.added} added, ${s.removed} removed, ${s.changed} changed")
        // The roots are the two whole messages; report paths below them.
        if (diff.valueChanged) lines += line(diff, emptyList())
        diff.children.forEach { collect(it, emptyList(), lines) }
        return lines.joinToString("\n")
    }

    private fun collect(node: DiffNode, parents: List<String>, out: MutableList<String>) {
        when (node.kind) {
            ChangeKind.UNCHANGED -> Unit
            ChangeKind.ADDED, ChangeKind.REMOVED -> out += line(node, parents)
            ChangeKind.CHANGED -> {
                if (node.valueChanged) out += line(node, parents)
                node.children.forEach { collect(it, parents + node.key, out) }
            }
        }
    }

    private fun line(node: DiffNode, parents: List<String>): String {
        val path = (parents + node.key).joinToString(" / ")
        val label = if (node.name.isBlank()) path else "$path ${node.name}"
        return when (node.kind) {
            ChangeKind.ADDED -> "+ $label: ${node.right?.value.orEmpty()}"
            ChangeKind.REMOVED -> "- $label: ${node.left?.value.orEmpty()}"
            else -> "~ $label: ${node.left?.value.orEmpty()} -> ${node.right?.value.orEmpty()}"
        }
    }
}
