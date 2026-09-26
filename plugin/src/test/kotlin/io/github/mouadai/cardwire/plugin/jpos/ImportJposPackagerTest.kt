package io.github.mouadai.cardwire.plugin.jpos

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ImportJposPackagerTest : BasePlatformTestCase() {

    private val packager = """<?xml version="1.0"?>
        <!DOCTYPE isopackager SYSTEM "genericpackager.dtd">
        <isopackager>
          <isofield id="0" length="4" name="MTI" class="org.jpos.iso.IFA_NUMERIC"/>
          <isofield id="1" length="16" name="BITMAP" class="org.jpos.iso.IFA_BITMAP"/>
          <isofield id="2" length="19" name="PAN" class="org.jpos.iso.IFA_LLNUM"/>
        </isopackager>"""

    fun testActionIsRegistered() {
        assertInstanceOf(ActionManager.getInstance().getAction(ImportJposPackagerAction.ID), ImportJposPackagerAction::class.java)
    }

    fun testImportWritesAProjectDialectWithoutOverwriting() {
        val first = JposDialectImport.importInto(project, packager, "Acme Packager.xml")
        assertNull(first.writeError)
        assertEquals("acme-packager.json", first.file!!.name)
        assertEquals("dialects", first.file!!.parent.name)
        assertEquals(".cardwire", first.file!!.parent.parent.name)
        assertTrue(VfsUtilCore.loadText(first.file!!).contains("\"id\": \"acme-packager\""))

        val second = JposDialectImport.importInto(project, packager, "Acme Packager.xml")
        assertEquals("acme-packager-2.json", second.file!!.name)
        assertTrue(VfsUtilCore.loadText(second.file!!).contains("\"id\": \"acme-packager-2\""))
    }

    fun testBrokenPackagerWritesNothing() {
        val outcome = JposDialectImport.importInto(project, "<nope/>", "broken.xml")
        assertNull(outcome.file)
        assertFalse(outcome.result.errors.isEmpty())
    }
}
