// Copyright (c) Thorsten Titze
// SPDX-License-Identifier: MIT

package jp.titze.intellij.helix.register

data class HelixRegisterItem(val register: Char, val description: String, val entry: HelixRegisterEntry?) {
    val previewText: String = entry?.text?.replace("\r\n", " ")?.replace("\n", "⏎ ") ?: "(empty)"

    val tagText: String? = when {
        entry == null -> null
        entry.pieces.size > 1 -> "[${entry.pieces.size} pieces]"
        entry.isLinewise -> "[linewise]"
        else -> null
    }

    val registerLower: String = register.lowercaseChar().toString()
    val descriptionLower: String = description.lowercase()
    private val textForMatching: String = entry?.text?.let {
        if (it.length > MAX_MATCH_CHARS) it.substring(0, MAX_MATCH_CHARS) else it
    }?.lowercase() ?: ""

    fun matches(query: String): Boolean = matchesNormalized(query.lowercase())

    internal fun matchesNormalized(q: String): Boolean {
        if (q.isEmpty()) return true
        return registerLower.contains(q) ||
            descriptionLower.contains(q) ||
            textForMatching.contains(q)
    }

    companion object {
        private const val MAX_MATCH_CHARS = 1000
    }
}
