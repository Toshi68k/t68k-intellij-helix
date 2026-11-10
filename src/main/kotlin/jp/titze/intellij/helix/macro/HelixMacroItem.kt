// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.macro

data class HelixMacroItem(
    val name: String,
    val isRecent: Boolean,
    val actionCount: Int,
    val previewText: String,
    val rawMacro: Any? = null,
) {
    val nameLower: String = name.lowercase()
    val previewTextLower: String = previewText.lowercase()

    fun matches(query: String): Boolean = matchesNormalized(query.lowercase())

    internal fun matchesNormalized(q: String): Boolean {
        if (q.isEmpty()) return true
        return nameLower.contains(q) || previewTextLower.contains(q)
    }

    companion object {
        const val NO_NAME_MACRO = "<noname>"
        private const val MAX_PREVIEW_ACTIONS = 4

        fun fromActionMacro(macro: Any, isRecent: Boolean = false): HelixMacroItem {
            val rawName = try {
                macro.javaClass.getMethod("getName").invoke(macro) as? String
            } catch (_: Throwable) {
                null
            }
            val displayName = if (rawName.isNullOrBlank() || rawName == NO_NAME_MACRO) {
                NO_NAME_MACRO
            } else {
                rawName
            }
            val actions = extractActions(macro)
            return HelixMacroItem(
                name = displayName,
                isRecent = isRecent || displayName == NO_NAME_MACRO,
                actionCount = actions.size,
                previewText = formatPreview(actions),
                rawMacro = macro,
            )
        }

        private fun extractActions(macro: Any): List<*> = try {
            val getActions = macro.javaClass.getMethod("getActions")
            when (val result = getActions.invoke(macro)) {
                is Array<*> -> result.toList()
                is List<*> -> result
                else -> emptyList<Any>()
            }
        } catch (_: Throwable) {
            emptyList<Any>()
        }

        private fun formatPreview(actions: List<*>): String {
            if (actions.isEmpty()) return "(empty)"
            val previewList = actions.take(MAX_PREVIEW_ACTIONS).map { desc ->
                formatDescriptor(desc)
            }
            val base = previewList.joinToString(", ")
            return if (actions.size > MAX_PREVIEW_ACTIONS) "$base, ..." else base
        }

        private fun formatDescriptor(desc: Any?): String {
            if (desc == null) return ""
            val className = desc.javaClass.simpleName
            return try {
                when {
                    className.contains("TypedDescriptor") -> {
                        val text = desc.javaClass.getMethod("getText").invoke(desc) as? String
                        text?.let { "\"$it\"" } ?: desc.toString()
                    }

                    className.contains("ShortcutActionDescription") -> {
                        (desc.javaClass.getMethod("getText").invoke(desc) as? String) ?: desc.toString()
                    }

                    className.contains("IdActionDescriptor") -> {
                        (desc.javaClass.getMethod("getActionId").invoke(desc) as? String) ?: desc.toString()
                    }

                    else -> desc.toString()
                }
            } catch (_: Throwable) {
                desc.toString()
            }
        }
    }
}
