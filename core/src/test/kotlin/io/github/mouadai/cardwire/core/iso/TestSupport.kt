package io.github.mouadai.cardwire.core.iso

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail

internal fun hex(s: String): ByteArray {
    val clean = s.filterNot { it.isWhitespace() }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

internal fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }

internal fun ascii(s: String): ByteArray = s.toByteArray(Charsets.ISO_8859_1)

internal fun loadDialect(json: String): Dialect = when (val r = DialectLoader.load(json)) {
    is DialectLoadResult.Success -> r.dialect
    is DialectLoadResult.Failure -> fail("Dialect failed to load: ${r.errors}")
}

internal fun EncodeResult.bytes(): ByteArray = when (this) {
    is EncodeResult.Success -> bytes
    is EncodeResult.Failure -> fail("Encoding failed: $errors")
}

internal fun assertBytes(expected: ByteArray, actual: ByteArray) =
    assertEquals(expected.toHexString(), actual.toHexString())

internal fun DecodeResult.complete(): IsoMessage {
    if (errors.isNotEmpty()) fail<Unit>("Unexpected decode errors: $errors")
    return message
}

internal val ascii87: Dialect by lazy { BuiltinDialects.load("iso8583-1987-ascii") }
internal val binary87: Dialect by lazy { BuiltinDialects.load("iso8583-1987-binary") }
internal val ascii93: Dialect by lazy { BuiltinDialects.load("iso8583-1993-ascii") }
