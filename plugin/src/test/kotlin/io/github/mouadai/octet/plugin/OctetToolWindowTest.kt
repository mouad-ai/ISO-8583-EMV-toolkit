package io.github.mouadai.octet.plugin

import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Boots a headless IDE with the plugin installed and checks the tool window is wired up. */
class OctetToolWindowTest : BasePlatformTestCase() {

    fun testToolWindowIsRegistered() {
        val ep = ToolWindowEP.EP_NAME.extensionList.singleOrNull { it.id == "Octet" }
        assertNotNull("Octet tool window extension not registered", ep)
        assertEquals(OctetToolWindowFactory::class.java.name, ep!!.factoryClass)
    }

    fun testPanelBuilds() {
        assertTrue(OctetToolWindowPanel.create().componentCount > 0)
    }
}
