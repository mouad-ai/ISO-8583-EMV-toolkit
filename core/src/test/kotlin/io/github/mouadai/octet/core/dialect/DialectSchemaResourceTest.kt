package io.github.mouadai.octet.core.dialect

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialectSchemaResourceTest {

    @Test
    fun `dialect schema is bundled with core`() {
        val text = javaClass.getResourceAsStream("/octet/dialect/dialect.schema.json")?.bufferedReader()?.readText()
        assertNotNull(text, "schema resource missing")
        assertTrue(text!!.contains("\"extends\""))
        assertTrue(text.contains("\"fieldOverride\""))
    }
}
