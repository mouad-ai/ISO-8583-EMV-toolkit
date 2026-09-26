package io.github.mouadai.octet.plugin.licensing

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JLabel

class PaidFeaturePanelTest : BasePlatformTestCase() {

    fun testLockedFeatureShowsANoteAndNoContent() {
        var created = 0
        val panel = PaidFeaturePanel(PaidFeature.DIFF, isEnabled = { false }) { created++; JLabel("diff") }
        assertFalse(panel.isUnlocked)
        assertEquals(0, created)
        assertEquals(1, panel.component.componentCount)
    }

    fun testUnlockingCreatesTheContentOnce() {
        var enabled = false
        var created = 0
        val content = JLabel("diff")
        val panel = PaidFeaturePanel(PaidFeature.DIFF, isEnabled = { enabled }) { created++; content }
        enabled = true
        panel.refresh()
        panel.refresh()
        assertTrue(panel.isUnlocked)
        assertEquals(1, created)
        assertSame(content, panel.component.getComponent(0))
    }

    fun testTestsRunWithPaidFeaturesUnlocked() {
        assertTrue(OctetLicense.isEnabled(PaidFeature.BUILDER))
    }
}
