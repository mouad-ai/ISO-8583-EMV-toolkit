package io.github.mouadai.octet.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Octet must never touch the network (SPEC section 5). This test fails if any compiled class or
 * source file of `core` references networking or HTTP client APIs.
 */
class NoNetworkTest {

    @Test
    fun `compiled core classes reference no networking APIs`() {
        val classFiles = System.getProperty("octet.core.mainClasses")
            .split(File.pathSeparator)
            .map(::File)
            .filter(File::isDirectory)
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList() }
        assertTrue(classFiles.isNotEmpty(), "No compiled core classes found to scan")

        val violations = classFiles.flatMap { file ->
            forbiddenIn(String(file.readBytes(), Charsets.ISO_8859_1), FORBIDDEN_CLASS_REFERENCES)
                .map { "${file.name}: $it" }
        }
        assertEquals(emptyList<String>(), violations, "Networking APIs referenced from core")
    }

    @Test
    fun `core sources import no networking APIs`() {
        val sources = File(System.getProperty("octet.core.mainSources"))
            .walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .toList()
        assertTrue(sources.isNotEmpty(), "No core sources found to scan")

        val violations = sources.flatMap { file ->
            forbiddenIn(file.readText(), FORBIDDEN_SOURCE_REFERENCES).map { "${file.name}: $it" }
        }
        assertEquals(emptyList<String>(), violations, "Networking APIs referenced from core")
    }

    @Test
    fun `scanner detects a networking reference`() {
        val fakeConstantPool = "\u0001\u0000\u000cjava/net/URL\u0001"
        assertEquals(listOf("java/net/"), forbiddenIn(fakeConstantPool, FORBIDDEN_CLASS_REFERENCES))
        assertEquals(listOf("java.net."), forbiddenIn("import java.net.Socket", FORBIDDEN_SOURCE_REFERENCES))
    }

    private fun forbiddenIn(text: String, forbidden: List<String>): List<String> =
        forbidden.filter { text.contains(it) }

    private companion object {
        /** Package prefixes of JDK and common third-party networking/HTTP APIs. */
        val FORBIDDEN_PACKAGES = listOf(
            "java.net.",
            "javax.net.",
            "java.nio.channels.SocketChannel",
            "java.nio.channels.ServerSocketChannel",
            "java.nio.channels.DatagramChannel",
            "java.nio.channels.AsynchronousSocketChannel",
            "javax.ws.rs.",
            "jakarta.ws.rs.",
            "okhttp3.",
            "org.apache.http.",
            "org.apache.hc.",
            "io.ktor.client.",
            "io.netty.",
            "retrofit2.",
        )
        val FORBIDDEN_SOURCE_REFERENCES = FORBIDDEN_PACKAGES
        val FORBIDDEN_CLASS_REFERENCES = FORBIDDEN_PACKAGES.map { it.replace('.', '/') }
    }
}
