package jp.titze.intellij.helix.ui

import jp.titze.intellij.helix.settings.HelixSettings

enum class HelixPromptCategory {
    SEARCH,
    REGEX,
    SHELL,
}

fun HelixPromptType.toCategory(): HelixPromptCategory = when (this) {
    HelixPromptType.SEARCH, HelixPromptType.RSEARCH -> HelixPromptCategory.SEARCH

    HelixPromptType.SELECT, HelixPromptType.SPLIT, HelixPromptType.KEEP, HelixPromptType.REMOVE ->
        HelixPromptCategory.REGEX

    HelixPromptType.PIPE, HelixPromptType.INSERT_OUTPUT, HelixPromptType.APPEND_OUTPUT,
    HelixPromptType.KEEP_PIPE, HelixPromptType.PIPE_TO,
    -> HelixPromptCategory.SHELL
}

object HelixPromptHistory {
    fun add(category: HelixPromptCategory, query: String) {
        if (query.isBlank()) return
        val list = HelixSettings.instance.getPromptHistoryList(category)
        if (list.lastOrNull() == query) return
        list.remove(query)
        list.add(query)
        trimToCapacity()
    }

    fun get(category: HelixPromptCategory): List<String> =
        HelixSettings.instance.getPromptHistoryList(category).toList()

    fun trimToCapacity() {
        val limit = HelixSettings.instance.promptHistoryMaxEntries
        for (category in HelixPromptCategory.values()) {
            val list = HelixSettings.instance.getPromptHistoryList(category)
            while (list.size > limit) {
                list.removeAt(0)
            }
        }
    }

    fun clear() {
        for (category in HelixPromptCategory.values()) {
            HelixSettings.instance.getPromptHistoryList(category).clear()
        }
    }
}

class HelixPromptHistoryNavigator(private val category: HelixPromptCategory) {
    private var index = -1
    private var draft = ""

    fun onUp(currentText: String): String? {
        val history = HelixPromptHistory.get(category)
        if (history.isEmpty()) return null

        if (index == -1) {
            draft = currentText
            index = history.size - 1
            return history[index]
        }

        if (index > 0) {
            index--
            return history[index]
        }

        return null
    }

    fun onDown(): String? {
        val history = HelixPromptHistory.get(category)
        if (index == -1) return null

        if (index < history.size - 1) {
            index++
            return history[index]
        }

        index = -1
        return draft
    }

    fun reset() {
        index = -1
        draft = ""
    }
}
