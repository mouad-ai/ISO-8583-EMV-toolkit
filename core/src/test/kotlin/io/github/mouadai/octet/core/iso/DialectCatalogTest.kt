package io.github.mouadai.octet.core.iso

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class DialectCatalogTest {

    private fun custom(id: String, extends: String) =
        """{"id": "$id", "name": "Test $id", "extends": "$extends", "fields": {"48": {"name": "Additional data", "type": "ans", "lengthType": "LLLVAR", "maxLength": 999}}}"""

    @Test
    fun `built-ins come first with plain labels`() {
        val catalog = DialectCatalog.build(emptyList())
        assertEquals(BuiltinDialects.all.map { it.name }, catalog.entries.map { it.label })
        assertTrue(catalog.problems.isEmpty())
    }

    @Test
    fun `sources are labelled by scope and can extend a built-in`() {
        val catalog = DialectCatalog.build(listOf(DialectSource("acme.json", "project", custom("acme", "iso8583-1987-ascii"))))
        val entry = catalog.entries.last()
        assertEquals("Test acme (project)", entry.label)
        assertEquals("Additional data", entry.dialect.field(48)?.name)
        assertEquals("Primary account number", entry.dialect.field(2)?.name)
    }

    @Test
    fun `a source can extend another source`() {
        val sources = listOf(
            DialectSource("b.json", "user", custom("b", "a")),
            DialectSource("a.json", "project", custom("a", "iso8583-1987-binary")),
        )
        val catalog = DialectCatalog.build(sources)
        assertEquals(listOf("Test b (user)", "Test a (project)"), catalog.entries.drop(BuiltinDialects.IDS.size).map { it.label })
        assertTrue(catalog.problems.isEmpty())
    }

    @Test
    fun `broken sources are skipped and reported by file name`() {
        val catalog = DialectCatalog.build(listOf(DialectSource("bad.json", "user", "{ not json")))
        assertEquals(BuiltinDialects.IDS.size, catalog.entries.size)
        assertEquals(1, catalog.problems.size)
        assertTrue(catalog.problems.single().startsWith("bad.json: "))
    }
}
