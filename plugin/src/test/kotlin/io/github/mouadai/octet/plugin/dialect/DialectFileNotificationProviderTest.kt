package io.github.mouadai.octet.plugin.dialect

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DialectFileNotificationProviderTest : BasePlatformTestCase() {

    fun testBrokenDialectFileGetsItsLoadError() {
        val file = myFixture.addFileToProject(".octet/dialects/broken.json", "{ not json").virtualFile
        val problem = DialectFileNotificationProvider.loadProblem(project, file)
        assertNotNull(problem)
        assertNotNull(DialectFileNotificationProvider().collectNotificationData(project, file))
    }

    fun testValidDialectFileHasNoBanner() {
        val file = myFixture.addFileToProject(
            ".octet/dialects/acme.json",
            """{"id": "test-acme", "name": "Test Acme", "extends": "iso8583-1987-ascii"}""",
        ).virtualFile
        assertNull(DialectFileNotificationProvider.loadProblem(project, file))
        assertNull(DialectFileNotificationProvider().collectNotificationData(project, file))
    }

    fun testOtherJsonFilesAreIgnored() {
        val file = myFixture.addFileToProject("config/broken.json", "{ not json").virtualFile
        assertNull(DialectFileNotificationProvider().collectNotificationData(project, file))
    }
}
