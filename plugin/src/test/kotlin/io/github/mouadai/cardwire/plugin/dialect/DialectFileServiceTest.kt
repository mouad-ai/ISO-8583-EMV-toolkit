package io.github.mouadai.cardwire.plugin.dialect

import com.intellij.openapi.application.runWriteAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DialectFileServiceTest : BasePlatformTestCase() {

    fun testListsJsonFilesInProjectDialectFolder() {
        myFixture.addFileToProject("${DialectLocations.PROJECT_DIR}/b.json", "{}")
        myFixture.addFileToProject("${DialectLocations.PROJECT_DIR}/a.json", "{}")
        myFixture.addFileToProject("${DialectLocations.PROJECT_DIR}/readme.md", "")
        myFixture.addFileToProject("elsewhere/c.json", "{}")

        val files = DialectFileService.getInstance(project).dialectFiles()
            .filter { it.scope == DialectFileService.Scope.PROJECT }
        assertEquals(listOf("a.json", "b.json"), files.map { it.file.name })
    }

    fun testNoFolderMeansNoProjectDialects() {
        val files = DialectFileService.getInstance(project).dialectFiles()
        assertTrue(files.none { it.scope == DialectFileService.Scope.PROJECT })
    }

    fun testAddingEditingAndDeletingADialectNotifies() {
        var count = 0
        project.messageBus.connect(testRootDisposable).subscribe(DialectsChangedListener.TOPIC, DialectsChangedListener { count++ })

        val file = myFixture.addFileToProject("${DialectLocations.PROJECT_DIR}/acme.json", "{}").virtualFile
        assertTrue("create", count >= 1)

        count = 0
        runWriteAction { file.setBinaryContent("""{"id":"acme"}""".toByteArray()) }
        assertEquals("edit", 1, count)

        count = 0
        runWriteAction { file.rename(this, "acme2.json") }
        assertEquals("rename", 1, count)

        count = 0
        runWriteAction { file.delete(this) }
        assertEquals("delete", 1, count)
    }

    fun testOtherFilesDoNotNotify() {
        var count = 0
        project.messageBus.connect(testRootDisposable).subscribe(DialectsChangedListener.TOPIC, DialectsChangedListener { count++ })
        myFixture.addFileToProject("src/Main.kt", "")
        myFixture.addFileToProject("config/acme.json", "{}")
        assertEquals(0, count)
    }

    fun testPathRules() {
        assertTrue(DialectLocations.affectsDialects("/work/app/.cardwire/dialects/acme.json"))
        assertTrue(DialectLocations.affectsDialects("/work/app/.cardwire/dialects"))
        assertTrue(DialectLocations.affectsDialects("/work/app/.cardwire"))
        assertTrue(DialectLocations.affectsDialects(DialectLocations.userDir().resolve("mine.json").toString()))
        assertFalse(DialectLocations.affectsDialects("/work/app/.cardwire/dialects/sub/acme.json"))
        assertFalse(DialectLocations.affectsDialects("/work/app/dialects/acme.json"))
        assertFalse(DialectLocations.affectsDialects("/work/app/.cardwire/dialects/acme.yaml"))
    }
}
