package io.github.mouadai.octet.plugin.licensing

import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tool window tab for a [PaidFeature]: shows the feature once it is licensed, otherwise a short
 * note with a way to enter a license. [create] runs at most once, the first time it is unlocked.
 */
class PaidFeaturePanel(
    private val feature: PaidFeature,
    private val isEnabled: () -> Boolean = { OctetLicense.isEnabled(feature) },
    private val create: () -> JComponent,
) {
    val component = JPanel(BorderLayout())
    private var content: JComponent? = null

    val isUnlocked: Boolean get() = content != null

    init {
        refresh()
    }

    /** Shows the feature if it has become available since the last check. */
    fun refresh() {
        if (content != null) return
        component.removeAll()
        if (isEnabled()) {
            content = create().also { component.add(it, BorderLayout.CENTER) }
        } else {
            component.add(lockedView(), BorderLayout.NORTH)
        }
        component.revalidate()
        component.repaint()
    }

    private fun lockedView(): JComponent = panel {
        row { label("${feature.label} is part of Octet Pro.") }
        row {
            button("Enter License...") { OctetLicense.requestLicense(feature) }
            button("Check Again") {
                OctetLicense.invalidate()
                refresh()
            }
        }
    }
}
