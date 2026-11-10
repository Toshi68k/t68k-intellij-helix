// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.keymap

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil

data class HelixIntelliJAction(
    val actionId: String,
    val displayName: String,
    val description: String,
    val shortcutText: String,
) {
    val tomlCommand: String get() = ":action $actionId"
    val actionIdLower: String = actionId.lowercase()
    val displayNameLower: String = displayName.lowercase()
    val descriptionLower: String = description.lowercase()

    fun matches(terms: List<String>): Boolean = terms.all { term ->
        actionIdLower.contains(term) || displayNameLower.contains(term) || descriptionLower.contains(term)
    }
}

/**
 * Extracts and filters registered IntelliJ actions for keybinding discovery and configuration.
 */
object HelixActionExtractor {

    fun extractActions(actionManager: ActionManager? = null): List<HelixIntelliJAction> {
        val am = actionManager ?: ActionManager.getInstance() ?: return emptyList()
        val keymap = try {
            KeymapManager.getInstance().activeKeymap
        } catch (_: Throwable) {
            null
        }

        val actionIds = am.getActionIdList("")
        val validActionIds = actionIds.filter { !shouldSkipActionId(it) }
        val result = ArrayList<HelixIntelliJAction>(validActionIds.size)

        for (id in validActionIds) {
            val action = am.getAction(id) ?: continue
            val presentation = action.templatePresentation
            val text = presentation.text?.replace("_", "")?.trim() ?: id
            val desc = presentation.description?.trim() ?: ""
            val shortcuts = keymap?.getShortcuts(id)
            val shortcutText = shortcuts?.firstOrNull()?.let { KeymapUtil.getShortcutText(it) } ?: ""

            result.add(
                HelixIntelliJAction(
                    actionId = id,
                    displayName = text.ifEmpty { id },
                    description = desc,
                    shortcutText = shortcutText,
                ),
            )
        }

        return result.sortedWith(compareBy({ it.displayNameLower }, { it.actionIdLower }))
    }

    private fun shouldSkipActionId(id: String): Boolean {
        if (id.isBlank()) return true
        if (id.startsWith("$") && !id.startsWith($$"$Undo") && !id.startsWith($$"$Redo")) {
            return true
        }
        return false
    }

    fun filterActions(actions: List<HelixIntelliJAction>, query: String): List<HelixIntelliJAction> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return actions
        val terms = trimmed.split(" ").filter { it.isNotEmpty() }
        if (terms.isEmpty()) return actions
        return actions.filter { item -> item.matches(terms) }
    }
}
