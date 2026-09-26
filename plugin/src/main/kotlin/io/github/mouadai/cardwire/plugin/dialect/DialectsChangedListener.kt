package io.github.mouadai.cardwire.plugin.dialect

import com.intellij.util.messages.Topic

/** Published on the project message bus when a dialect file is added, edited, renamed or deleted. */
fun interface DialectsChangedListener {

    fun dialectsChanged()

    companion object {
        @JvmField
        @Topic.ProjectLevel
        val TOPIC: Topic<DialectsChangedListener> =
            Topic(DialectsChangedListener::class.java, Topic.BroadcastDirection.NONE)
    }
}
