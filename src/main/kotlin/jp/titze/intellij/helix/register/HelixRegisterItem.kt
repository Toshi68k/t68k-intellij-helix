package jp.titze.intellij.helix.register

data class HelixRegisterItem(val register: Char, val description: String, val entry: HelixRegisterEntry?) {
    val previewText: String
        get() = entry?.text?.replace("\r\n", " ")?.replace("\n", "⏎ ") ?: "(empty)"

    val tagText: String?
        get() = when {
            entry == null -> null
            entry.pieces.size > 1 -> "[${entry.pieces.size} pieces]"
            entry.isLinewise -> "[linewise]"
            else -> null
        }

    fun matches(query: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.lowercase()
        return register.toString().lowercase().contains(q) ||
            description.lowercase().contains(q) ||
            (entry?.text?.lowercase()?.contains(q) == true)
    }
}
