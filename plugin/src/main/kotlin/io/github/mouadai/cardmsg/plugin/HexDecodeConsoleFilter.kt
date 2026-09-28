package io.github.mouadai.cardmsg.plugin

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import io.github.mouadai.cardmsg.core.input.HexRuns
import io.github.mouadai.cardmsg.plugin.licensing.CardMsgLicense
import io.github.mouadai.cardmsg.plugin.licensing.PaidFeature

class CardMsgConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> =
        arrayOf(HexDecodeConsoleFilter {
            CardMsgSettings.getInstance().state.consoleFilterEnabled && CardMsgLicense.isEnabled(PaidFeature.CONSOLE_FILTER)
        })
}

/**
 * Turns hex dumps of 16+ bytes in console output into links that open them in the Card Message Toolkit tool
 * window. Checks [enabled] per line, so the settings toggle applies to new output right away.
 */
class HexDecodeConsoleFilter(private val enabled: () -> Boolean) : Filter, DumbAware {

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        if (!enabled()) return null
        val runs = HexRuns.find(line)
        if (runs.isEmpty()) return null
        val lineStart = entireLength - line.length
        val items = runs.map { range ->
            val hex = line.substring(range)
            Filter.ResultItem(lineStart + range.first, lineStart + range.last + 1, HyperlinkInfo { project ->
                CardMsgDecodeLauncher.open(project, hex, null)
            })
        }
        return Filter.Result(items)
    }
}
