package io.github.mouadai.cardmsg.plugin

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

/** Application-wide Card Message Toolkit settings. Stored locally in the IDE config; nothing is ever sent anywhere. */
@Service(Service.Level.APP)
@State(name = "CardMsgSettings", storages = [Storage("cardmsg.xml")])
class CardMsgSettings : SimplePersistentStateComponent<CardMsgSettings.SettingsState>(SettingsState()) {

    class SettingsState : BaseState() {
        /** Console filter that turns hex runs into "decode" links. Off by default (SPEC 8.2). */
        var consoleFilterEnabled by property(false)
    }

    companion object {
        fun getInstance(): CardMsgSettings = service()
    }
}

class CardMsgConfigurable : BoundConfigurable("Card Message Toolkit") {
    override fun createPanel(): DialogPanel {
        val state = CardMsgSettings.getInstance().state
        return panel {
            row {
                checkBox("Detect hex dumps in console output and link them to the Card Message Toolkit decoder")
                    .bindSelected(state::consoleFilterEnabled)
            }
        }
    }
}
