package io.github.mouadai.cardwire.core.diff

import io.github.mouadai.cardwire.core.view.DecodeNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageDiffTest {

    private fun leaf(id: String, value: String, name: String = "Tag $id") = DecodeNode(id, name, value, offset = 0, length = 1)
    private fun root(vararg children: DecodeNode) = DecodeNode("TLV", "EMV data", "", offset = 0, length = 1, children = children.toList())

    private val amount = leaf("9F02", "10.00")
    private val currency = leaf("5F2A", "504")
    private val date = leaf("9A", "2026-09-25")
    private val tvr = leaf("95", "0000000000")

    @Test
    fun `identical trees have no changes`() {
        val diff = MessageDiff.diff(root(amount, currency), root(amount, currency))
        assertEquals(ChangeKind.UNCHANGED, diff.kind)
        assertFalse(diff.hasChanges)
        assertEquals(DiffSummary(0, 0, 0), diff.summary())
    }

    @Test
    fun `changed value is reported with both sides`() {
        val diff = MessageDiff.diff(root(amount, currency), root(leaf("9F02", "12.50"), currency))
        assertEquals(ChangeKind.CHANGED, diff.kind)
        val changed = diff.children.single { it.id == "9F02" }
        assertEquals(ChangeKind.CHANGED, changed.kind)
        assertTrue(changed.valueChanged)
        assertEquals("10.00", changed.left?.value)
        assertEquals("12.50", changed.right?.value)
        assertEquals(ChangeKind.UNCHANGED, diff.children.single { it.id == "5F2A" }.kind)
        assertEquals(DiffSummary(added = 0, removed = 0, changed = 1), diff.summary())
    }

    @Test
    fun `added and removed elements`() {
        val diff = MessageDiff.diff(root(amount, currency, date), root(amount, date, tvr))
        assertEquals(listOf("9F02", "5F2A", "9A", "95"), diff.children.map { it.id })
        assertEquals(
            listOf(ChangeKind.UNCHANGED, ChangeKind.REMOVED, ChangeKind.UNCHANGED, ChangeKind.ADDED),
            diff.children.map { it.kind },
        )
        val removed = diff.children[1]
        assertNull(removed.right)
        val added = diff.children[3]
        assertNull(added.left)
        assertEquals(DiffSummary(added = 1, removed = 1, changed = 0), diff.summary())
    }

    @Test
    fun `an element added at the front keeps the right side order`() {
        val diff = MessageDiff.diff(root(amount, currency), root(tvr, amount, currency))
        assertEquals(listOf("95", "9F02", "5F2A"), diff.children.map { it.id })
        assertEquals(ChangeKind.ADDED, diff.children[0].kind)
    }

    @Test
    fun `nested change marks parents as changed without a value change`() {
        val leftTemplate = DecodeNode("70", "Template", "", 0, 1, children = listOf(amount, currency))
        val rightTemplate = DecodeNode("70", "Template", "", 0, 1, children = listOf(amount, leaf("5F2A", "978")))
        val diff = MessageDiff.diff(root(leftTemplate), root(rightTemplate))
        val template = diff.children.single()
        assertEquals(ChangeKind.CHANGED, template.kind)
        assertFalse(template.valueChanged)
        assertEquals(ChangeKind.CHANGED, template.children.single { it.id == "5F2A" }.kind)
        // Only leaves with their own change are counted.
        assertEquals(DiffSummary(0, 0, 1), diff.summary())
    }

    @Test
    fun `children of added and removed subtrees are all added or removed`() {
        val template = DecodeNode("70", "Template", "", 0, 1, children = listOf(amount, currency))
        val diff = MessageDiff.diff(root(), root(template))
        val added = diff.children.single()
        assertEquals(ChangeKind.ADDED, added.kind)
        assertEquals(listOf(ChangeKind.ADDED, ChangeKind.ADDED), added.children.map { it.kind })
        assertEquals(DiffSummary(added = 1, removed = 0, changed = 0), diff.summary())
    }

    @Test
    fun `repeated ids are matched by occurrence`() {
        val left = root(leaf("DF01", "a"), leaf("DF01", "b"))
        val right = root(leaf("DF01", "a"), leaf("DF01", "c"), leaf("DF01", "d"))
        val diff = MessageDiff.diff(left, right)
        assertEquals(
            listOf(ChangeKind.UNCHANGED, ChangeKind.CHANGED, ChangeKind.ADDED),
            diff.children.map { it.kind },
        )
        assertEquals(listOf("DF01", "DF01#2", "DF01#3"), diff.children.map { it.key })
    }

    @Test
    fun `reordered elements are matched, not reported as added and removed`() {
        val diff = MessageDiff.diff(root(amount, currency, date), root(date, amount, currency))
        assertEquals(3, diff.children.size)
        assertTrue(diff.children.all { it.kind == ChangeKind.UNCHANGED })
        assertEquals(listOf("9A", "9F02", "5F2A"), diff.children.map { it.id })
    }

    @Test
    fun `different roots are compared by value`() {
        val diff = MessageDiff.diff(root(amount), root(amount).copy(value = "x"))
        assertEquals(ChangeKind.CHANGED, diff.kind)
        assertTrue(diff.valueChanged)
    }

    @Test
    fun `report lists only changes, with paths`() {
        val leftTemplate = DecodeNode("70", "Template", "", 0, 1, children = listOf(amount, currency))
        val rightTemplate = DecodeNode("70", "Template", "", 0, 1, children = listOf(leaf("9F02", "12.50"), currency))
        val diff = MessageDiff.diff(root(leftTemplate, date), root(rightTemplate, tvr))
        assertEquals(
            """
            1 added, 1 removed, 1 changed
            ~ 70 / 9F02 Tag 9F02: 10.00 -> 12.50
            - 9A Tag 9A: 2026-09-25
            + 95 Tag 95: 0000000000
            """.trimIndent(),
            DiffReport.render(diff),
        )
    }

    @Test
    fun `report for identical messages`() {
        assertEquals("No differences", DiffReport.render(MessageDiff.diff(root(amount), root(amount))))
    }

    @Test
    fun `every element of both sides appears exactly once`() {
        val ids = listOf("A", "B", "C", "D", "E", "F")
        val random = java.util.Random(8583)
        repeat(200) {
            val left = ids.filter { random.nextBoolean() }.shuffled(random).map { leaf(it, "${random.nextInt(2)}") }
            val right = ids.filter { random.nextBoolean() }.shuffled(random).map { leaf(it, "${random.nextInt(2)}") }
            val diff = MessageDiff.diff(root(*left.toTypedArray()), root(*right.toTypedArray()))
            assertEquals(left.map { it.id }.sorted(), diff.children.mapNotNull { it.left?.id }.sorted())
            assertEquals(right.map { it.id }.sorted(), diff.children.mapNotNull { it.right?.id }.sorted())
            assertEquals(right.map { it.id }, diff.children.mapNotNull { it.right?.id })
        }
    }
}
