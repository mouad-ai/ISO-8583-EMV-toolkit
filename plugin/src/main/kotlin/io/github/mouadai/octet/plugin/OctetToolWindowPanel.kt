package io.github.mouadai.octet.plugin

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import io.github.mouadai.octet.core.Octet

/** Placeholder content for the Octet tool window; the decoder UI replaces it in M3. */
object OctetToolWindowPanel {

    fun create(): DialogPanel = panel {
        row {
            label(Octet.PRODUCT_NAME).bold()
        }
        row {
            comment("Offline ISO 8583 and EMV TLV decoder. Decoding arrives in a later milestone.")
        }
    }
}
