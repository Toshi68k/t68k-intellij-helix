// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.macro

import com.intellij.ide.actionMacro.ActionMacro

data class HelixMacroItem(
    val name: String,
    val isRecent: Boolean,
    val actionCount: Int,
    val previewText: String,
    val macro: ActionMacro,
) {
    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        if (name.lowercase().contains(q)) return true
        if (previewText.lowercase().contains(q)) return true
        return false
    }

    companion object {
        private const val MAX_PREVIEW_ACTIONS = 4
        private const val NO_NAME_MACRO = "<noname>"

        fun fromActionMacro(macro: ActionMacro, isRecent: Boolean = false): HelixMacroItem {
            val rawName = macro.name
            val displayName = if (rawName.isNullOrBlank() || rawName == NO_NAME_MACRO) {
                NO_NAME_MACRO
            } else {
                rawName
            }
            return HelixMacroItem(
                name = displayName,
                isRecent = isRecent || displayName == NO_NAME_MACRO,
                actionCount = macro.actions.size,
                previewText = formatPreview(macro),
                macro = macro,
            )
        }

        private fun formatPreview(macro: ActionMacro): String {
            val actions = macro.actions
            if (actions.isEmpty()) return "(empty)"
            val previewList = actions.take(MAX_PREVIEW_ACTIONS).map { desc ->
                when (desc) {
                    is ActionMacro.TypedDescriptor -> desc.text?.let { "\"$it\"" } ?: desc.toString()
                    is ActionMacro.ShortcutActionDescription -> desc.text ?: desc.toString()
                    is ActionMacro.IdActionDescriptor -> desc.actionId
                    else -> desc.toString()
                }
            }
            val base = previewList.joinToString(", ")
            return if (actions.size > MAX_PREVIEW_ACTIONS) "$base, ..." else base
        }
    }
}
