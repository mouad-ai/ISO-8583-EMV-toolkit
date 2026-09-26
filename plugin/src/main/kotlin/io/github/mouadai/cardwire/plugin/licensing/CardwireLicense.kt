package io.github.mouadai.cardwire.plugin.licensing

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.ui.LicensingFacade

/** Paid features (SPEC 10). Everything else, including decoding with the built-in dialects, is free. */
enum class PaidFeature(val label: String) {
    CUSTOM_DIALECTS("Project and user dialects"),
    JPOS_IMPORT("jPOS packager import"),
    BUILDER("Message builder and code export"),
    DIFF("Message diff"),
    CONSOLE_FILTER("Console hex detection"),
}

/**
 * The only place Cardwire asks about its license. Cardwire is freemium: the `product-descriptor` in
 * plugin.xml is optional, so the plugin always loads and only [PaidFeature]s check [isEnabled].
 */
object CardwireLicense {

    /**
     * Must equal the `code` of `product-descriptor` in plugin.xml. Requested code; confirm it once JetBrains
     * Marketplace assigns the real code to the approved paid plugin.
     */
    const val PRODUCT_CODE = "PCARDWIRE"

    /**
     * Unlocks every paid feature without a license, for `runIde` sandboxes and headless tests. Set
     * by the Gradle build for those tasks only; never in a released plugin.
     */
    const val DEV_PROPERTY = "cardwire.license.dev"

    enum class State { LICENSED, UNLICENSED, UNKNOWN }

    private const val CACHE_MILLIS = 60_000L

    @Volatile
    private var cached: Pair<State, Long>? = null

    /**
     * [State.UNKNOWN] while the IDE's licensing has not started yet (early startup, headless runs).
     * A known state is cached for a minute, since the console filter asks for every output line.
     */
    fun state(): State {
        val now = System.currentTimeMillis()
        cached?.let { (state, at) -> if (now - at < CACHE_MILLIS) return state }
        val facade = LicensingFacade.getInstance() ?: return State.UNKNOWN
        val state = if (LicenseStampVerifier.isValid(facade.getConfirmationStamp(PRODUCT_CODE))) State.LICENSED else State.UNLICENSED
        cached = state to now
        return state
    }

    /** Forgets the cached state, e.g. after the user entered a license. */
    fun invalidate() {
        cached = null
    }

    /** Unknown counts as enabled, so a slow licensing start never locks a paying user out. */
    fun isEnabled(feature: PaidFeature): Boolean =
        System.getProperty(DEV_PROPERTY) == "true" || state() != State.UNLICENSED

    /** Opens the IDE's registration dialog with Cardwire preselected. */
    fun requestLicense(feature: PaidFeature) {
        val message = "${feature.label} is part of Cardwire Pro."
        ApplicationManager.getApplication().invokeLater({ showRegisterDialog(message) }, ModalityState.nonModal())
    }

    private fun showRegisterDialog(message: String) {
        val actions = ActionManager.getInstance()
        // "RegisterPlugins" in open-source IDE builds, "Register" in commercial ones.
        val register = actions.getAction("RegisterPlugins") ?: actions.getAction("Register") ?: return
        val context = DataContext { dataId ->
            when (dataId) {
                "register.product-descriptor.code" -> PRODUCT_CODE
                "register.message" -> message
                else -> null
            }
        }
        ActionUtil.performAction(register, AnActionEvent.createEvent(context, Presentation(), "", ActionUiKind.NONE, null))
    }
}
