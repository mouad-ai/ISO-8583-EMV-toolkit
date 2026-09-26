package io.github.mouadai.cardwire.core.iso

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** The dialects shipped in `samples/dialects` for users to copy must keep loading. */
class SampleDialectsTest {

    private fun load(name: String): Dialect {
        val text = File("../samples/dialects/$name").readText()
        val result = DialectLoader.load(text)
        assertTrue(result is DialectLoadResult.Success, result.toString())
        return (result as DialectLoadResult.Success).dialect
    }

    @Test
    fun `example acquirer overrides the 1987 ASCII layout`() {
        val dialect = load("example-acquirer.json")
        assertEquals(16, dialect.field(2)!!.maxLength)
        assertNull(dialect.field(48))
        assertEquals("Private data (example TLV)", dialect.field(63)!!.name)
        assertEquals(BuiltinDialects.load("iso8583-1987-ascii").field(3), dialect.field(3))
    }

    @Test
    fun `example binary host frames with a length-prefixed TPDU`() {
        val dialect = load("example-binary-host.json")
        assertEquals(listOf(FramingSpec(LengthPrefix.BINARY_2, 5), FramingSpec.NONE), dialect.framings)
    }
}
