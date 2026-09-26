package io.github.mouadai.cardwire.core.diff

import io.github.mouadai.cardwire.core.view.DecodeNode

enum class ChangeKind { UNCHANGED, ADDED, REMOVED, CHANGED }

/**
 * One element of a structural diff. [left] and [right] are the compared nodes; one of them is null
 * for [ChangeKind.ADDED] and [ChangeKind.REMOVED]. A node is [ChangeKind.CHANGED] when its own value
 * differs ([valueChanged]) or any descendant changed.
 */
data class DiffNode(
    /** Matching key among siblings: the node id, with `#n` for its n-th repeat (n >= 2). */
    val key: String,
    val id: String,
    val name: String,
    val kind: ChangeKind,
    val valueChanged: Boolean,
    val left: DecodeNode?,
    val right: DecodeNode?,
    val children: List<DiffNode>,
) {
    val hasChanges: Boolean get() = kind != ChangeKind.UNCHANGED

    /**
     * Counts elements with their own change: added and removed subtrees count once at their top, and
     * a changed parent counts only if its own value changed.
     */
    fun summary(): DiffSummary {
        var added = 0
        var removed = 0
        var changed = 0
        fun visit(node: DiffNode) {
            when (node.kind) {
                ChangeKind.ADDED -> added++
                ChangeKind.REMOVED -> removed++
                ChangeKind.CHANGED -> {
                    if (node.valueChanged) changed++
                    node.children.forEach(::visit)
                }
                ChangeKind.UNCHANGED -> Unit
            }
        }
        visit(this)
        return DiffSummary(added, removed, changed)
    }
}

data class DiffSummary(val added: Int, val removed: Int, val changed: Int)

/**
 * Structural diff of two decoded messages (SPEC 8.4), e.g. request vs response or expected vs
 * actual. Works on the display tree, so it covers ISO 8583 fields and EMV TLV tags alike: siblings
 * are matched by id (repeated ids by occurrence), values compared as displayed.
 */
object MessageDiff {

    fun diff(left: DecodeNode, right: DecodeNode): DiffNode = pair(left.id, left, right)

    private fun pair(key: String, left: DecodeNode, right: DecodeNode): DiffNode {
        val children = diffChildren(left.children, right.children)
        val valueChanged = left.value != right.value
        val kind = if (valueChanged || children.any { it.hasChanges }) ChangeKind.CHANGED else ChangeKind.UNCHANGED
        return DiffNode(key, right.id, right.name, kind, valueChanged, left, right, children)
    }

    private fun oneSided(key: String, node: DecodeNode, kind: ChangeKind): DiffNode {
        val children = keyed(node.children).map { (k, child) -> oneSided(k, child, kind) }
        val (left, right) = if (kind == ChangeKind.ADDED) null to node else node to null
        return DiffNode(key, node.id, node.name, kind, valueChanged = false, left, right, children)
    }

    /**
     * Keeps the right side's order and slots each removed element in right after the element that
     * preceded it on the left.
     */
    private fun diffChildren(left: List<DecodeNode>, right: List<DecodeNode>): List<DiffNode> {
        val leftKeyed = keyed(left)
        val rightKeyed = keyed(right)
        val leftIndex = leftKeyed.withIndex().associate { (i, kv) -> kv.first to i }
        val matchedLeft = rightKeyed.mapNotNull { leftIndex[it.first] }.toSet()

        val result = ArrayList<DiffNode>(maxOf(left.size, right.size))
        val emittedLeft = BooleanArray(leftKeyed.size)
        fun emitRemovedFrom(start: Int) {
            var i = start
            while (i < leftKeyed.size && i !in matchedLeft && !emittedLeft[i]) {
                emittedLeft[i] = true
                val (k, node) = leftKeyed[i]
                result += oneSided(k, node, ChangeKind.REMOVED)
                i++
            }
        }
        emitRemovedFrom(0)
        for ((key, rightNode) in rightKeyed) {
            val m = leftIndex[key]
            if (m == null) {
                result += oneSided(key, rightNode, ChangeKind.ADDED)
            } else {
                emittedLeft[m] = true
                result += pair(key, leftKeyed[m].second, rightNode)
                emitRemovedFrom(m + 1)
            }
        }
        return result
    }

    private fun keyed(nodes: List<DecodeNode>): List<Pair<String, DecodeNode>> {
        val seen = HashMap<String, Int>()
        return nodes.map { node ->
            val n = seen.merge(node.id, 1, Int::plus)!!
            (if (n == 1) node.id else "${node.id}#$n") to node
        }
    }
}
