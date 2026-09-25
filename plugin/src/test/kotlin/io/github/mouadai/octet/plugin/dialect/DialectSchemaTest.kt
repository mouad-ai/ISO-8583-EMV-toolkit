package io.github.mouadai.octet.plugin.dialect

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.impl.inspections.JsonSchemaComplianceInspection

/** Dialect files under `.octet/dialects` get the bundled schema: validation and completion. */
class DialectSchemaTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(JsonSchemaComplianceInspection::class.java)
    }

    fun testSchemaResourceIsBundled() {
        assertNotNull(DialectSchemaProvider.getSchemaFile())
    }

    fun testOnlyFilesInDialectFoldersGetTheSchema() {
        val dialect = myFixture.addFileToProject("$DIR/acme.json", "{}").virtualFile
        val other = myFixture.addFileToProject("config/acme.json", "{}").virtualFile
        val notJson = myFixture.addFileToProject("$DIR/notes.txt", "").virtualFile
        assertTrue(DialectSchemaProvider.isAvailable(dialect))
        assertFalse(DialectSchemaProvider.isAvailable(other))
        assertFalse(DialectSchemaProvider.isAvailable(notJson))
    }

    fun testValidBaseDialectHasNoWarnings() {
        assertEquals(emptyList<String>(), warnings(BASE))
    }

    fun testValidOverrideDialectHasNoWarnings() {
        assertEquals(emptyList<String>(), warnings(OVERRIDE))
    }

    fun testLlvarLongerThan99IsFlagged() {
        val text = BASE.replace("\"maxLength\": 19", "\"maxLength\": 120")
        assertFalse(warnings(text).isEmpty())
    }

    fun testUnknownFieldNumberIsFlagged() {
        val text = BASE.replace("\"2\": {", "\"200\": {")
        assertFalse(warnings(text).isEmpty())
    }

    fun testBaseFieldMustBeComplete() {
        val text = BASE.replace("\"type\": \"n\", \"lengthType\": \"LLVAR\",", "\"lengthType\": \"LLVAR\",")
        assertFalse(warnings(text).isEmpty())
    }

    fun testOverrideMayListOnlyChangedProperties() {
        // Same partial field without "extends" is an incomplete base field.
        val text = OVERRIDE.replace("\"extends\": \"iso8583-1987-ascii\",", "")
        assertFalse(warnings(text).isEmpty())
    }

    fun testRemoveCannotBeCombinedWithOtherProperties() {
        val text = OVERRIDE.replace("\"48\": { \"remove\": true }", "\"48\": { \"remove\": true, \"name\": \"x\" }")
        assertFalse(warnings(text).isEmpty())
    }

    fun testLengthTypeCompletion() {
        val file = myFixture.addFileToProject("$DIR/complete.json", """{"id": "x", "name": "x", "fields": {"2": {"lengthType": <caret>}}}""")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings.orEmpty().map { it.trim('"') }
        assertTrue(items.toString(), items.containsAll(listOf("FIXED", "LLVAR", "LLLVAR", "LLLLVAR")))
    }

    private fun warnings(text: String): List<String> {
        val file = myFixture.addFileToProject("$DIR/d${counter++}.json", text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return myFixture.doHighlighting()
            .filter { it.severity >= HighlightSeverity.WEAK_WARNING }
            .mapNotNull { it.description }
    }

    private var counter = 0

    private companion object {
        const val DIR = DialectLocations.PROJECT_DIR

        val BASE = """
            {
              "id": "acme-base",
              "name": "Acme base",
              "version": "1987",
              "mti": { "encoding": "ASCII" },
              "bitmap": { "encoding": "HEX_ASCII" },
              "framing": [{ "type": "NONE" }, { "type": "LENGTH_2_BINARY" }],
              "fields": {
                "2": { "name": "Primary account number", "type": "n", "lengthType": "LLVAR", "maxLength": 19, "lengthEncoding": "ASCII", "sensitive": true },
                "4": { "name": "Amount, transaction", "type": "n", "lengthType": "FIXED", "maxLength": 12, "padding": { "side": "LEFT", "char": "0" } },
                "48": { "name": "Additional data, private", "type": "ans", "lengthType": "LLLVAR", "maxLength": 999,
                        "subfields": { "layout": "PRIVATE_TLV", "tagLength": 2, "lengthLength": 3 } },
                "55": { "name": "ICC data", "type": "b", "lengthType": "LLLVAR", "maxLength": 255, "subfields": { "layout": "BER_TLV" } }
              }
            }
        """.trimIndent()

        val OVERRIDE = """
            {
              "id": "acme",
              "name": "Acme processor",
              "extends": "iso8583-1987-ascii",
              "fields": {
                "2": { "maxLength": 16 },
                "48": { "remove": true },
                "63": { "name": "Acme private data", "type": "ans", "lengthType": "LLLVAR", "maxLength": 999 }
              }
            }
        """.trimIndent()
    }
}
